package app.gamenative.ui.component.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.alorma.compose.settings.ui.SettingsGroup

fun Modifier.frontendFullWidth() = this.then(FrontendFullWidthModifier)

private object FrontendFullWidthModifier : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = true
}

@Composable
fun FrontendAwareSettingsGroup(
    content: @Composable ColumnScope.() -> Unit
) {
    val isFrontend = LocalIsFrontend.current
    if (isFrontend) {
        FrontendGridLayout {
            // We use a Column just to provide the ColumnScope, but the grid layout will measure its children directly
            // Wait, if we use a Column, the grid layout will see ONE measurable (the Column).
            // So we shouldn't use a Column inside the grid layout.
            // But `content` expects a ColumnScope receiver.
            // We can pass a fake ColumnScope or just not require ColumnScope if we change `content` to `@Composable () -> Unit`.
        }
    } else {
        SettingsGroup(content = content)
    }
}

// Since we need to support existing tabs that use SettingsGroup with ColumnScope,
// we create a version that doesn't need ColumnScope, or we implement our own ColumnScope.
@Composable
fun FrontendAwareSettingsGroupNoScope(
    content: @Composable () -> Unit
) {
    val isFrontend = LocalIsFrontend.current
    if (isFrontend) {
        FrontendGridLayout(content = content)
    } else {
        SettingsGroup(content = { content() })
    }
}

@Composable
fun FrontendGridLayout(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        if (measurables.isEmpty()) return@Layout layout(0, 0) {}

        val padding = 8.dp.roundToPx()
        val itemsPerRow = 3
        val itemWidth = (constraints.maxWidth - padding * (itemsPerRow - 1)) / itemsPerRow

        val placeables = measurables.map { measurable ->
            val isFullWidth = measurable.parentData as? Boolean == true
            val width = if (isFullWidth) constraints.maxWidth else itemWidth
            measurable.measure(Constraints(minWidth = width, maxWidth = width))
        }

        var x = 0
        var y = 0
        var rowHeight = 0
        val positions = mutableListOf<Pair<Int, Int>>()

        placeables.forEach { placeable ->
            if (x > 0 && x + placeable.width > constraints.maxWidth) {
                x = 0
                y += rowHeight + padding
                rowHeight = 0
            }
            positions.add(x to y)
            rowHeight = maxOf(rowHeight, placeable.height)
            x += placeable.width + padding
        }
        val totalHeight = y + rowHeight

        layout(constraints.maxWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val pos = positions[index]
                placeable.place(pos.first, pos.second)
            }
        }
    }
}
