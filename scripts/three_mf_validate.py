#!/usr/bin/env python3
"""Independent structural validator for the 3MF projects Lumen writes.

Why this exists: the Kotlin test suite asserts our own output against our own expectations, so it can
only catch a change we noticed. This reads the actual bytes with a different language and a different
parser (Python's zipfile + ElementTree) and checks the structure against what the ENGINE's importer
requires, which was read out of libslic3r/Format/bbs_3mf.cpp rather than assumed.

Checks, each one a rule the engine enforces or depends on:
  1. the four parts exist, and [Content_Types].xml is first (readers look for it before anything else);
  2. _rels/.rels points at 3D/3dmodel.model;
  3. every <item objectid> in <build> has a matching <object id> in <resources>;
  4. every <triangle>'s v1/v2/v3 index is inside the vertex list -- an out-of-range index is a file the
     engine cannot slice, and is the exact bug this script was written after finding in our writer;
  5. EVERY <object> in Metadata/model_settings.config has a <part> child, because an object with
     settings and no part gets an EMPTY volume list from the importer and silently prints nothing;
  6. object ids are unique;
  7. no coordinate contains a comma -- "1,5000" is what a comma-decimal locale produces, and it splits
     one number into two attributes.

Usage: three_mf_validate.py <file.3mf> [...more.3mf]
Exit code 0 = structurally valid, 1 = at least one problem (each printed with the rule it breaks).
"""
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

CORE_NS = "http://schemas.microsoft.com/3dmanufacturing/core/2015/02"
MODEL = "3D/3dmodel.model"
CONFIG = "Metadata/model_settings.config"
CONTENT_TYPES = "[Content_Types].xml"
RELS = "_rels/.rels"


def validate(path):
    problems = []
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        for required in (CONTENT_TYPES, RELS, MODEL, CONFIG):
            if required not in names:
                problems.append(f"[1] missing part {required!r}; archive holds {names}")
        if problems:
            return problems
        if names[0] != CONTENT_TYPES:
            problems.append(f"[1] {CONTENT_TYPES!r} is not the first entry (it is {names[0]!r})")

        rels = z.read(RELS).decode("utf-8")
        if f'Target="/{MODEL}"' not in rels:
            problems.append(f"[2] {RELS} does not reference /{MODEL}")
        if "3dmanufacturing/2013/01/3dmodel" not in rels:
            problems.append(f"[2] {RELS} is missing the 3dmodel relationship type")

        model_xml = z.read(MODEL).decode("utf-8")
        config_xml = z.read(CONFIG).decode("utf-8")

    if re.search(r'="\s*-?\d+,\d+', model_xml):
        problems.append("[7] a coordinate in the model contains a comma (locale-formatted number)")

    root = ET.fromstring(model_xml)
    resources = root.find(f"{{{CORE_NS}}}resources")
    build = root.find(f"{{{CORE_NS}}}build")
    if resources is None or build is None:
        problems.append("[1] the model has no <resources> or no <build>")
        return problems

    vertex_counts = {}
    for obj in resources.findall(f"{{{CORE_NS}}}object"):
        oid = obj.get("id")
        mesh = obj.find(f"{{{CORE_NS}}}mesh")
        if mesh is None:
            problems.append(f"[4] object {oid} has no <mesh>")
            continue
        vertices = mesh.find(f"{{{CORE_NS}}}vertices")
        triangles = mesh.find(f"{{{CORE_NS}}}triangles")
        n_vertices = 0 if vertices is None else len(vertices)
        vertex_counts[oid] = n_vertices
        for tri in ([] if triangles is None else triangles):
            for attr in ("v1", "v2", "v3"):
                raw = tri.get(attr)
                if raw is None:
                    problems.append(f"[4] object {oid}: triangle has no {attr}")
                    break
                value = int(raw)
                if not 0 <= value < n_vertices:
                    problems.append(
                        f"[4] object {oid}: {attr}={value} is outside the {n_vertices} vertices"
                    )
                    break

    items = build.findall(f"{{{CORE_NS}}}item")
    if not items:
        problems.append("[3] <build> has no <item>, so nothing is on the plate")
    for item in items:
        if item.get("objectid") not in vertex_counts:
            problems.append(f"[3] build item {item.get('objectid')} has no matching <object>")

    config_root = ET.fromstring(config_xml)
    seen = set()
    for obj in config_root.findall("object"):
        oid = obj.get("id")
        if oid in seen:
            problems.append(f"[6] duplicated object id {oid} in {CONFIG}")
        seen.add(oid)
        if obj.find("part") is None:
            problems.append(
                f"[5] object {oid} has per-object settings but NO <part>: the importer leaves its "
                f"volume list empty and the object prints nothing"
            )
        if oid not in vertex_counts:
            problems.append(f"[3] {CONFIG} mentions object {oid}, absent from the model")

    return problems


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    failed = False
    for path in argv[1:]:
        problems = validate(path)
        if problems:
            failed = True
            print(f"INVALID {path}")
            for p in problems:
                print(f"  {p}")
        else:
            print(f"ok      {path}  (structurally what the OrcaSlicer importer expects)")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
