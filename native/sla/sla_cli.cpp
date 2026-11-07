// sla_cli.cpp — minimal host/device harness to exercise the PrusaSlicer SLA engine.
// Loads an STL, slices it with SLAPrint (supports + hollowing + rasterize), writes an SL1 archive.
#include <cstdio>
#include <cstdlib>
#include <string>
#include <libslic3r/Model.hpp>
#include <libslic3r/Format/STL.hpp>
#include <libslic3r/SLAPrint.hpp>
#include <libslic3r/PrintConfig.hpp>

using namespace Slic3r;

int main(int argc, char** argv)
{
    if (argc < 3) { std::fprintf(stderr, "usage: sla_cli in.stl out.sl1 [layer_height] [exposure]\n"); return 2; }

    Model model;
    try { model = Model::read_from_file(argv[1]); }
    catch (const std::exception& e) { std::fprintf(stderr, "load failed: %s\n", e.what()); return 1; }
    if (model.objects.empty()) { std::fprintf(stderr, "no objects\n"); return 1; }
    model.add_default_instances();

    DynamicPrintConfig cfg;
    cfg.apply(SLAFullPrintConfig::defaults());
    cfg.set_key_value("printer_technology", new ConfigOptionEnum<PrinterTechnology>(ptSLA));
    if (argc > 3) cfg.set_key_value("layer_height", new ConfigOptionFloat(std::atof(argv[3])));
    if (argc > 4) cfg.set_key_value("initial_exposure_time", new ConfigOptionFloats{ std::atof(argv[4]) });

    SLAPrint print;
    print.set_status_callback([](const PrintBase::SlicingStatus& s) {
        if (s.percent >= 0) std::fprintf(stderr, "  %3d%% %s\n", s.percent, s.text.c_str());
    });

    print.apply(model, cfg);
    std::string err = print.validate();
    if (!err.empty()) { std::fprintf(stderr, "validate: %s\n", err.c_str()); return 1; }
    if (print.empty()) { std::fprintf(stderr, "nothing to print\n"); return 1; }

    print.process();
    print.export_print(argv[2]);
    std::printf("OK wrote %s\n", argv[2]);
    return 0;
}
