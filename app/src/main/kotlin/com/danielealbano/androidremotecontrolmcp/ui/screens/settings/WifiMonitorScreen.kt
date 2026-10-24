@file:Suppress("FunctionNaming", "LongMethod")

package com.danielealbano.androidremotecontrolmcp.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danielealbano.androidremotecontrolmcp.R
import com.danielealbano.androidremotecontrolmcp.ui.components.TopBarBackButton
import com.danielealbano.androidremotecontrolmcp.ui.components.TopBarTitle
import com.danielealbano.androidremotecontrolmcp.ui.viewmodels.ChannelViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiMonitorScreen(
    viewModel: ChannelViewModel,
    onNavigateBack: () -> Unit,
) {
    val config by viewModel.eventChannelConfig.collectAsStateWithLifecycle()
    var newSsid by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { TopBarTitle(stringResource(R.string.wifi_monitor_title)) },
                navigationIcon = { TopBarBackButton(onClick = onNavigateBack) },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            items(config.wifi.ssids.toList()) { ssid ->
                ListItem(
                    headlineContent = { Text(ssid) },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeWifiSsid(ssid) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.wifi_monitor_remove_ssid, ssid),
                            )
                        }
                    },
                )
            }
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newSsid,
                        onValueChange = { newSsid = it },
                        label = { Text(stringResource(R.string.wifi_monitor_ssid_label)) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    IconButton(
                        onClick = {
                            if (newSsid.isNotBlank()) {
                                viewModel.addWifiSsid(newSsid.trim())
                                newSsid = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, stringResource(R.string.wifi_monitor_add_ssid))
                    }
                }
            }
            item {
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = config.wifi.notifyOnDiscovered,
                            role = Role.Switch,
                            onValueChange = { viewModel.updateWifiNotifyOnDiscovered(it) },
                        ),
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_discovered)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_scan_based)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnDiscovered,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    },
                )
            }
            item {
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = config.wifi.notifyOnLost,
                            role = Role.Switch,
                            onValueChange = { viewModel.updateWifiNotifyOnLost(it) },
                        ),
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_lost)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_scan_based)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnLost,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    },
                )
            }
            item {
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = config.wifi.notifyOnConnected,
                            role = Role.Switch,
                            onValueChange = { viewModel.updateWifiNotifyOnConnected(it) },
                        ),
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_connected)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_real_time)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnConnected,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    },
                )
            }
            item {
                ListItem(
                    modifier =
                        Modifier.toggleable(
                            value = config.wifi.notifyOnDisconnected,
                            role = Role.Switch,
                            onValueChange = { viewModel.updateWifiNotifyOnDisconnected(it) },
                        ),
                    headlineContent = { Text(stringResource(R.string.wifi_monitor_notify_disconnected)) },
                    supportingContent = { Text(stringResource(R.string.wifi_monitor_real_time)) },
                    trailingContent = {
                        Switch(
                            checked = config.wifi.notifyOnDisconnected,
                            onCheckedChange = null,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        )
                    },
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(
                        stringResource(R.string.wifi_monitor_throttling_note),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
