@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.data.model.NotificationFilterMode
import com.danielealbano.androidremotecontrolmcp.ui.components.TopBarBackButton
import com.danielealbano.androidremotecontrolmcp.ui.components.TopBarTitle
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationFilterScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TopBarTitle(stringResource(R.string.notification_filter_title)) },
                navigationIcon = { TopBarBackButton(onClick = onNavigateBack) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            val modes = NotificationFilterMode.entries
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = config.notifications.filterMode == mode,
                        onClick = { viewModel.updateNotificationFilterMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(stringResource(notificationFilterModeLabel(mode)))
                    }
                }
            }

            if (config.notifications.filterMode != NotificationFilterMode.ALL) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.notification_filter_search_label)) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    singleLine = true,
                )

                Text(
                    pluralStringResource(
                        R.plurals.notification_filter_selected_count,
                        config.notifications.filterApps.size,
                        config.notifications.filterApps.size,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                val filtered =
                    installedApps.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                            it.packageId.contains(searchQuery, ignoreCase = true)
                    }
                val appIcons by viewModel.appIcons.collectAsStateWithLifecycle()
                LazyColumn {
                    items(filtered, key = { it.packageId }) { app ->
                        ListItem(
                            modifier =
                                Modifier.toggleable(
                                    value = app.packageId in config.notifications.filterApps,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        viewModel.toggleNotificationFilterApp(app.packageId, checked)
                                    },
                                ),
                            headlineContent = { Text(app.name) },
                            supportingContent = {
                                Text(app.packageId, style = MaterialTheme.typography.bodySmall)
                            },
                            leadingContent = {
                                val bitmap = appIcons[app.packageId]
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        // Decorative: the app's name is the row's headline
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        // Decorative: the app's name is the row's headline
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                Checkbox(
                                    checked = app.packageId in config.notifications.filterApps,
                                    onCheckedChange = null,
                                    modifier = Modifier.minimumInteractiveComponentSize(),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@StringRes
private fun notificationFilterModeLabel(mode: NotificationFilterMode): Int =
    when (mode) {
        NotificationFilterMode.ALL -> R.string.notification_filter_mode_all
        NotificationFilterMode.WHITELIST -> R.string.notification_filter_mode_whitelist
        NotificationFilterMode.BLACKLIST -> R.string.notification_filter_mode_blacklist
    }
