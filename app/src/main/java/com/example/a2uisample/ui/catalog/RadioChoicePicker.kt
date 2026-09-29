package com.example.a2uisample.ui.catalog

import androidx.a2ui.compose.runtime.A2uiComponentScope
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.ChoicePicker.DisplayStyle
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.ChoicePicker.Option
import androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.ChoicePicker.Variant
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.a2ui.catalog.MaterialA2uiBasicCatalogV1Defaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A client-side override of the Basic Catalog's `ChoicePicker`.
 *
 * The stock Material 3 renderer shows single-choice pickers as a dropdown. This one renders them
 * as radio buttons instead and hands every other variant to the stock renderer. The agent's JSON
 * doesn't change: how a component looks is the client's decision.
 */
object RadioChoicePicker : A2uiBasicCatalogV1.ChoicePicker {
    private val stock = MaterialA2uiBasicCatalogV1Defaults.choicePicker

    @Composable
    override fun A2uiComponentScope.TypedContent(
        label: String?,
        options: List<Option>,
        value: List<String>,
        variant: Variant,
        displayStyle: DisplayStyle,
        filterable: Boolean,
        onValueChange: (List<String>) -> Unit,
        enabled: Boolean,
        accessibility: A2uiBasicCatalogV1.AccessibilityAttributes?,
        checks: List<A2uiBasicCatalogV1.CheckRule>,
        modifier: Modifier,
    ) {
        if (variant != Variant.MutuallyExclusive || displayStyle != DisplayStyle.Checkbox) {
            with(stock) {
                TypedContent(
                    label,
                    options,
                    value,
                    variant,
                    displayStyle,
                    filterable,
                    onValueChange,
                    enabled,
                    accessibility,
                    checks,
                    modifier,
                )
            }
            return
        }

        Column(modifier = modifier.selectableGroup()) {
            if (!label.isNullOrEmpty()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            options.forEach { option ->
                val selected = option.value in value
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                enabled = enabled,
                                role = Role.RadioButton,
                                onClick = { onValueChange(listOf(option.value)) },
                            ).padding(vertical = 4.dp),
                ) {
                    // onClick is null because the whole row handles selection.
                    RadioButton(
                        selected = selected,
                        onClick = null,
                        enabled = enabled,
                    )
                    Text(
                        text = option.label,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}
