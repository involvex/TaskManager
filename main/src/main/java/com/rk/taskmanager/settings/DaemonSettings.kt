package com.rk.taskmanager.settings

import android.widget.Toast
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.rk.commons.settings.Settings
import com.rk.components.SettingsToggle
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.commons.getString
import com.rk.commons.strings
import com.rk.taskmanager.R
import com.rk.taskmanager.widget.WidgetUpdateService

@Composable
fun DaemonSettings(modifier: Modifier = Modifier) {
    PreferenceLayout(label = stringResource(strings.daemon)) {
        val context = LocalContext.current
        val selectedMode = remember { mutableIntStateOf(Settings.workingMode) }

        PreferenceGroup(heading = stringResource(strings.working_mode)) {
            WorkingMode.entries.forEach { mode ->
                if (mode != WorkingMode.NOT_SET){
                    SettingsToggle(
                        label = stringResource(mode.nameRes!!),
                        description = null,
                        default = selectedMode.intValue == mode.id,
                        sideEffect = {
                            Settings.workingMode = mode.id
                            selectedMode.intValue = mode.id

                            Toast.makeText(context, strings.requires_daemon_restart.getString(), Toast.LENGTH_SHORT).show()
                        },
                        showSwitch = false,
                        startWidget = {
                            RadioButton(selected = selectedMode.intValue == mode.id, onClick = {
                                Settings.workingMode = mode.id
                                selectedMode.intValue = mode.id
                                Toast.makeText(context, strings.requires_daemon_restart.getString(), Toast.LENGTH_SHORT)
                                    .show()

                            })
                        },
                    )
                }

            }
        }

        PreferenceGroup(heading = stringResource(R.string.widget_group)) {
            SettingsToggle(
                label = stringResource(R.string.widget_live_updates),
                description = stringResource(R.string.widget_live_updates_desc),
                default = Settings.widgetLive,
                showSwitch = true,
                sideEffect = {
                    Settings.widgetLive = it
                    // Starts the service when enabled, stops it when disabled, but only while a
                    // widget is actually placed.
                    WidgetUpdateService.sync(context)
                }
            )
        }
    }
}