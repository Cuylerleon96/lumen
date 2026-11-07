package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

/**
 * The one shell both slicers' plate screens share: top bar (app name + model), bottom bar, the
 * "Slice" FAB, the snackbar, and the 3D-plate body slot. FDM and resin differ only in their icons,
 * their summary card and the engine behind [onSlice] — so they use this single code path and cannot
 * drift apart visually.
 */
@Composable
fun PlateShell(
    title: String,
    modelName: String?,
    onSlice: () -> Unit,
    message: String?,
    onMessageConsumed: () -> Unit,
    topActions: @Composable RowScope.() -> Unit = {},
    bottomActions: @Composable RowScope.() -> Unit = {},
    fabText: String = "Slice",
    content: @Composable BoxScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(message, snackbar, onMessageConsumed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        Text(
                            modelName ?: "No model",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = topActions,
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = bottomActions,
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = onSlice,
                        icon = { Icon(Icons.Default.PlayArrow, null) },
                        text = { Text(fabText) },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), content = content)
    }
}
