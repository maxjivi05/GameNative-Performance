package app.gamenative.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

@Composable
fun PluviaSettingsGroup(
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isFrontend = LocalIsFrontend.current

    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    androidx.compose.material3.ProvideTextStyle(value = MaterialTheme.typography.titleMedium) {
                        title()
                    }
                }
            }
        }
        if (isFrontend) {
            FrontendGridLayout(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                content = { Column { content() } } // The actual items are inside a column
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun FrontendGridLayout(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        // Assuming the content is a single Column containing the items
        val columnMeasurable = measurables.firstOrNull()
        if (columnMeasurable == null) {
            return@Layout layout(0, 0) {}
        }
        
        // We actually want to measure the children of the Column as grid items.
        // But Compose's Layout only sees the Column. 
        // To fix this, we'll let the Column measure itself with the full width,
        // but wait, if the Column's children have `fillMaxWidth`, they'll take the full width.
        // Let's just measure the column normally and return it.
        // This means the grid logic must be applied at the item level, not here.
        val placeable = columnMeasurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

