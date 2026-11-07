package dev.lumen.slicer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.lumen.slicer.BuildConfig
import dev.lumen.slicer.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val thirdParty = listOf(
    "OrcaSlicer (libslic3r), AGPL-3.0, by SoftFever and contributors, based on Bambu Studio, PrusaSlicer and Slic3r",
    "PrusaSlicer (SLA engine), AGPL-3.0, by Prusa Research",
    "Boost, Boost Software License 1.0",
    "oneTBB, Apache-2.0",
    "CGAL, GPL-3.0 / LGPL-3.0",
    "Eigen, MPL-2.0",
    "Open CASCADE Technology, LGPL-2.1 with exception",
    "OpenCV, Apache-2.0",
    "GMP and MPFR, LGPL-3.0",
    "NLopt, MIT / LGPL-2.1",
    "Z3, MIT",
    "OpenVDB, MPL-2.0",
    "Draco, Apache-2.0",
    "libnoise, LGPL-2.1",
    "cereal, BSD-3-Clause",
    "expat, MIT",
    "libpng, PNG Reference Library License",
    "libjpeg-turbo, IJG / BSD-3-Clause",
    "FreeType, FreeType License",
    "Clipper and Clipper2, Boost Software License 1.0",
    "Qhull, Qhull License",
    "admesh, libigl, mcut, miniz, nlohmann/json and the other libraries bundled with OrcaSlicer and PrusaSlicer",
    "Kotlin, kotlinx.coroutines, kotlinx.serialization, AndroidX and Jetpack Compose, Apache-2.0",
)

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("updates", Context.MODE_PRIVATE) }
    var baseUrl by remember { mutableStateOf(prefs.getString("baseUrl", "") ?: "") }
    // Default the manifest to the project's GitHub repo (raw master), so updates come from the same
    // place the source and the releases live. A local/LAN server can still be typed in as an override
    // for offline testing.
    val effectiveBase = baseUrl.ifBlank { "https://raw.githubusercontent.com/Cuylerleon96/lumen/master" }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About Lumen") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Lumen ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.headlineSmall)
            Text("FDM engine: OrcaSlicer ${BuildConfig.ORCA_VERSION} · Resin engine: PrusaSlicer SLA. Both run entirely on this phone.")
            Text(
                "Lumen is free software: you can redistribute it and/or modify it under the terms of the " +
                    "GNU Affero General Public License version 3 or later. It comes with no warranty. " +
                    "Its complete source code is available from the project repository. " +
                    "Lumen began as a fork of the OrcaMicro project and builds on OrcaSlicer and PrusaSlicer (both AGPL-3.0).",
            )
            OutlinedButton(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SOURCE_URL)))
                    } catch (e: ActivityNotFoundException) {
                        // No browser installed; nothing to do.
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Source code") }

            Text("Updates", style = MaterialTheme.typography.titleMedium)
            Text(
                "Updates come from the project's GitHub repository by default. Point this at a local " +
                    "server address instead to test a build that has not been published yet.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; prefs.edit().putString("baseUrl", it.trim()).apply() },
                label = { Text("Update server URL") },
                placeholder = { Text("http://192.168.1.x:8799") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    val base = effectiveBase.trim()
                    busy = true; status = "Checking…"
                    scope.launch {
                        try {
                            val info = withContext(Dispatchers.IO) { Updater.check(base, BuildConfig.VERSION_CODE) }
                            if (info.versionCode <= BuildConfig.VERSION_CODE) {
                                status = "Up to date (${BuildConfig.VERSION_NAME})."
                            } else {
                                status = "Downloading v${info.version}…"
                                val apk = withContext(Dispatchers.IO) {
                                    Updater.download(context, info) { p -> status = "Downloading… $p%" }
                                }
                                if (!Updater.verifySha256(apk, info.sha256)) {
                                    status = "Checksum mismatch — aborting."
                                } else {
                                    status = "Installing v${info.version}…"
                                    Updater.install(context, apk)
                                }
                            }
                        } catch (e: Exception) {
                            status = "Update failed: ${e.message}"
                        }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Working…" else "Check for updates") }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)

            Text("Built with", style = MaterialTheme.typography.titleMedium)
            for (line in thirdParty) Text("• $line", style = MaterialTheme.typography.bodySmall)
            Text(
                "Printer, process and filament profiles come from OrcaSlicer and its contributors.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
