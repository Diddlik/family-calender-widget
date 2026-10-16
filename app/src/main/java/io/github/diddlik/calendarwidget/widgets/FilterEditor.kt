package io.github.diddlik.calendarwidget.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.diddlik.calendarwidget.R
import io.github.diddlik.calendarwidget.filter.All
import io.github.diddlik.calendarwidget.filter.AllDay
import io.github.diddlik.calendarwidget.filter.AnyOf
import io.github.diddlik.calendarwidget.filter.FilterNode
import io.github.diddlik.calendarwidget.filter.Not
import io.github.diddlik.calendarwidget.filter.TextField
import io.github.diddlik.calendarwidget.filter.TextMatch
import io.github.diddlik.calendarwidget.filter.TextOperator

/** Deeper nesting leaves too little width for a rule on a phone. */
private const val MAX_GROUP_DEPTH = 2

/** [NONE] is stored as NOT(ANY), so a NOT group always has exactly one child. */
private enum class GroupMode { ALL, ANY, NONE }

private data class Group(val mode: GroupMode, val children: List<FilterNode>) {
    fun toNode(): FilterNode = when (mode) {
        GroupMode.ALL -> All(children)
        GroupMode.ANY -> AnyOf(children)
        GroupMode.NONE -> Not(AnyOf(children))
    }
}

private fun FilterNode.asGroup(): Group? = when (this) {
    is All -> Group(GroupMode.ALL, children)
    is AnyOf -> Group(GroupMode.ANY, children)
    is Not -> (child as? AnyOf)?.let { Group(GroupMode.NONE, it.children) }
    else -> null
}

/** Visual editor for a filter tree. The root is always shown as a group so rules can be added to it. */
@Composable
fun FilterEditor(root: FilterNode, onChange: (FilterNode) -> Unit) {
    val group = root.asGroup() ?: Group(GroupMode.ALL, listOf(root))
    GroupEditor(group, depth = 0, onChange = { onChange(it.toNode()) }, onRemove = null)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupEditor(group: Group, depth: Int, onChange: (Group) -> Unit, onRemove: (() -> Unit)?) {
    val nested = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp)
    Column(
        modifier = Modifier.fillMaxWidth().then(if (depth > 0) nested else Modifier),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                Dropdown(GroupMode.entries, group.mode, { groupModeLabel(it) }) { onChange(group.copy(mode = it)) }
            }
            if (onRemove != null) RemoveButton(onRemove)
        }
        if (group.children.isEmpty() && depth == 0) {
            Text(stringResource(R.string.filter_empty), style = MaterialTheme.typography.bodyMedium)
        }
        group.children.forEachIndexed { index, child ->
            RuleEditor(
                node = child,
                depth = depth,
                onChange = { node -> onChange(group.copy(children = group.children.toMutableList().also { it[index] = node })) },
                onRemove = { onChange(group.copy(children = group.children.filterIndexed { i, _ -> i != index })) },
            )
        }
        val add = { node: FilterNode -> onChange(group.copy(children = group.children + node)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddButton(R.string.filter_add_text) { add(TextMatch(TextField.TITLE, TextOperator.CONTAINS, "")) }
            AddButton(R.string.filter_add_all_day) { add(AllDay(true)) }
            if (depth < MAX_GROUP_DEPTH) AddButton(R.string.filter_add_group) { add(AnyOf(emptyList())) }
        }
    }
}

@Composable
private fun RuleEditor(node: FilterNode, depth: Int, onChange: (FilterNode) -> Unit, onRemove: () -> Unit) {
    val group = node.asGroup()
    when {
        group != null -> GroupEditor(group, depth + 1, { onChange(it.toNode()) }, onRemove)
        node is TextMatch -> TextRule(node, onChange, onRemove)
        node is AllDay -> RuleRow(onRemove) {
            Dropdown(listOf(true, false), node.value, {
                stringResource(if (it) R.string.filter_all_day_yes else R.string.filter_all_day_no)
            }) { onChange(AllDay(it)) }
        }
        // Rules the editor cannot create stay intact; they can only be removed.
        else -> RuleRow(onRemove) { Text(stringResource(R.string.filter_unsupported), style = MaterialTheme.typography.bodyMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextRule(rule: TextMatch, onChange: (FilterNode) -> Unit, onRemove: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RuleRow(onRemove) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown(TextField.entries, rule.field, { textFieldLabel(it) }) { onChange(rule.copy(field = it)) }
                Dropdown(TextOperator.entries, rule.operator, { operatorLabel(it) }) { onChange(rule.copy(operator = it)) }
            }
        }
        OutlinedTextField(
            value = rule.value,
            onValueChange = { onChange(rule.copy(value = it)) },
            label = { Text(stringResource(R.string.filter_value)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RuleRow(onRemove: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) { content() }
        RemoveButton(onRemove)
    }
}

@Composable
private fun RemoveButton(onRemove: () -> Unit) {
    IconButton(onClick = onRemove) {
        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.filter_remove))
    }
}

@Composable
private fun AddButton(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun <T> Dropdown(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, contentPadding = PaddingValues(start = 12.dp, end = 8.dp)) {
            Text(label(selected))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(label(option)) }, onClick = {
                    expanded = false
                    onSelect(option)
                })
            }
        }
    }
}

@Composable
private fun groupModeLabel(mode: GroupMode) = stringResource(
    when (mode) {
        GroupMode.ALL -> R.string.filter_mode_all
        GroupMode.ANY -> R.string.filter_mode_any
        GroupMode.NONE -> R.string.filter_mode_none
    }
)

@Composable
private fun textFieldLabel(field: TextField) = stringResource(
    when (field) {
        TextField.TITLE -> R.string.filter_field_title
        TextField.DESCRIPTION -> R.string.filter_field_description
        TextField.LOCATION -> R.string.filter_field_location
    }
)

@Composable
private fun operatorLabel(operator: TextOperator) = stringResource(
    when (operator) {
        TextOperator.CONTAINS -> R.string.filter_op_contains
        TextOperator.NOT_CONTAINS -> R.string.filter_op_not_contains
        TextOperator.STARTS_WITH -> R.string.filter_op_starts_with
        TextOperator.EQUALS -> R.string.filter_op_equals
    }
)
