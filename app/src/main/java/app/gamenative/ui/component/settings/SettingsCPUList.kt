package app.gamenative.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gamenative.R
import com.alorma.compose.settings.ui.base.internal.LocalSettingsGroupEnabled
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults
import com.alorma.compose.settings.ui.base.internal.SettingsTileScaffold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCPUList(
    modifier: Modifier = Modifier,
    enabled: Boolean = LocalSettingsGroupEnabled.current,
    value: String,
    onValueChange: (String) -> Unit,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    colors: SettingsTileColors = SettingsTileDefaults.colors(),
    tonalElevation: Dp = ListItemDefaults.Elevation,
    shadowElevation: Dp = ListItemDefaults.Elevation,
    action: @Composable (() -> Unit)? = null,
) {
    SettingsTileScaffold(
        modifier = modifier,
        enabled = enabled,
        title = title,
        icon = icon,
        colors = colors,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        action = action,
        subtitle = {
            val cpuAffinity = value.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start,
                maxItemsInEachRow = 4,
            ) {
                for (cpu in 0 until Runtime.getRuntime().availableProcessors()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Checkbox(
                            checked = cpuAffinity.contains(cpu),
                            onCheckedChange = {
                                val newAffinity = if (it) {
                                    (cpuAffinity + cpu).distinct().sorted()
                                } else {
                                    cpuAffinity.filter { it != cpu }
                                }
                                onValueChange(newAffinity.joinToString(","))
                            },
                        )
                        Text(stringResource(R.string.cpu_label, cpu), modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
            }
        },
    )
}
