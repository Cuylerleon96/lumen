package dev.lumen.slicer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.lumen.slicer.core.Technology

/**
 * First-launch technology picker (also reachable from Settings to switch). Choosing FDM or resin
 * routes into that slicer's normal flow.
 */
@Composable
fun TechnologyScreen(state: UiState, viewModel: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("What are you printing?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick a slicer. You can switch any time from Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Technology.entries.forEach { tech ->
            TechnologyCard(
                tech = tech,
                selected = state.technology == tech,
                onClick = { viewModel.chooseTechnology(tech) },
            )
        }
    }
}

@Composable
private fun TechnologyCard(tech: Technology, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tech.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(tech.blurb, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
