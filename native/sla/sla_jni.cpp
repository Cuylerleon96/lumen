// sla_jni.cpp — JNI bridge exposing the PrusaSlicer SLA engine (libslic3r) to the Android app.
// Kotlin side: io.github.whoxllm.orcamicro.core.resin.SlaEngine.nativeSlice(in, out, settingsJson)
#include <jni.h>
#include <cstdio>
#include <string>
#include <android/log.h>
#include <libslic3r/Model.hpp>
#include <libslic3r/FileReader.hpp>
#include <libslic3r/SLAPrint.hpp>
#include <libslic3r/PrintConfig.hpp>
#include <nlohmann/json.hpp>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  "sla_jni", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "sla_jni", __VA_ARGS__)

using namespace Slic3r;

extern "C" JNIEXPORT jint JNICALL
Java_io_github_whoxllm_orcamicro_core_resin_SlaEngine_nativeSlice(
    JNIEnv* env, jobject /*this*/, jstring inPath, jstring outPath, jstring settingsJson)
{
    const char* in = env->GetStringUTFChars(inPath, nullptr);
    const char* out = env->GetStringUTFChars(outPath, nullptr);
    const char* js = env->GetStringUTFChars(settingsJson, nullptr);
    int rc = 0;
    try {
        Model model = FileReader::load_model(in);
        if (model.objects.empty()) { LOGE("no objects"); rc = 3; }

        DynamicPrintConfig cfg;
        cfg.apply(SLAFullPrintConfig::defaults());
        cfg.option<ConfigOptionEnumGeneric>("printer_technology", true)->value = (int)ptSLA;

        // Settings overrides: JSON object of key -> value (string or number).
        if (js && *js) {
            LOGI("settings json: %s", js);
            auto j = nlohmann::json::parse(js);
            for (auto it = j.begin(); it != j.end(); ++it) {
                LOGI("setting %s = %s", it.key().c_str(), it.value().dump().c_str());
                if (it.value().is_string())
                    cfg.set(it.key(), it.value().get<std::string>());
                else if (it.value().is_number())
                    cfg.set(it.key(), std::to_string(it.value().get<double>()));
                else if (it.value().is_boolean())
                    cfg.set(it.key(), it.value().get<bool>() ? "1" : "0");
            }
        }

        if (rc == 0) {
            SLAPrint print;
            print.set_status_callback([](const PrintBase::SlicingStatus& s) {
                if (s.percent >= 0) LOGI("%3d%% %s", s.percent, s.text.c_str());
            });
            print.apply(model, cfg);
            std::string err = print.validate();
            if (!err.empty()) { LOGE("validate: %s", err.c_str()); rc = 2; }
            else if (print.empty()) { LOGE("nothing to print"); rc = 3; }
            else { print.process(); print.export_print(out); LOGI("wrote %s", out); rc = 0; }
        }
    } catch (const std::exception& e) {
        LOGE("sla_jni error: %s", e.what());
        rc = 1;
    }
    env->ReleaseStringUTFChars(inPath, in);
    env->ReleaseStringUTFChars(outPath, out);
    env->ReleaseStringUTFChars(settingsJson, js);
    return rc;
}
