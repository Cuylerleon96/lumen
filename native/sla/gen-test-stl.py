#!/usr/bin/env python3
# Generates an ASCII STL cube for testing the SLA engine. usage: gen-test-stl.py out.stl [size_mm]
import sys
out = sys.argv[1]
s = float(sys.argv[2]) if len(sys.argv) > 2 else 20.0
v = [(0,0,0),(s,0,0),(s,s,0),(0,s,0),(0,0,s),(s,0,s),(s,s,s),(0,s,s)]
faces = [(0,1,2),(0,2,3),(4,6,5),(4,7,6),(0,4,5),(0,5,1),(1,5,6),(1,6,2),(2,6,7),(2,7,3),(3,7,4),(3,4,0)]
with open(out, "w") as f:
    f.write("solid cube\n")
    for a,b,c in faces:
        f.write("facet normal 0 0 0\n outer loop\n")
        for i in (a,b,c):
            f.write(f"  vertex {v[i][0]} {v[i][1]} {v[i][2]}\n")
        f.write(" endloop\nendfacet\n")
    f.write("endsolid cube\n")
print("wrote", out)
