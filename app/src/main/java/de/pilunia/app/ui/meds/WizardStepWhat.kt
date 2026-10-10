package de.pilunia.app.ui.meds

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.pilunia.app.data.MedColor
import de.pilunia.app.data.MedForm
import de.pilunia.app.ui.components.ChoiceChip
import de.pilunia.app.ui.components.palette
import de.pilunia.app.ui.components.pClickable
import de.pilunia.app.ui.theme.Pilunia

private val ColorNames = mapOf(
    MedColor.ROSE to "Rosé", MedColor.GELB to "Gelb", MedColor.MINT to "Mint",
    MedColor.LAVENDEL to "Lavendel", MedColor.BLAU to "Blau", MedColor.GRAU to "Grau",
)

/** 15 · Schritt 1 „Was nimmst du ein?“ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WizardStepWhat(
    draft: WizardDraft,
    onChange: (WizardDraft) -> Unit,
    nameFocus: FocusRequester,
    onNameDone: () -> Unit,
) {
    val c = Pilunia.colors
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        WizardHeadline("Was nimmst du ein?")

        WizardFieldGroup("Name") {
            WizardTextField(
                value = draft.name,
                onValueChange = { onChange(draft.copy(name = it.take(60))) },
                placeholder = "z. B. Ramipril",
                focusRequester = nameFocus,
                capitalization = KeyboardCapitalization.Words,
            )
        }

        WizardFieldGroup("Wofür? (optional)") {
            WizardTextField(
                value = draft.purpose,
                onValueChange = { onChange(draft.copy(purpose = it.take(60))) },
                placeholder = "Blutdruck",
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WizardFieldGroup("Stärke", Modifier.weight(1f)) {
                WizardTextField(
                    value = draft.strength,
                    onValueChange = { onChange(draft.copy(strength = sanitizeStrength(it))) },
                    placeholder = "z. B. 50",
                    keyboardType = KeyboardType.Decimal,
                    capitalization = KeyboardCapitalization.None,
                )
            }
            WizardFieldGroup("Einheit", Modifier.width(128.dp)) {
                WizardUnitSelect(draft.unit, { onChange(draft.copy(unit = it)) }, Modifier.fillMaxWidth())
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WizardLabel("Form")
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MedForm.entries.forEach { f ->
                    ChoiceChip(f.label, selected = draft.form == f, onClick = { onChange(draft.copy(form = f)) })
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WizardLabel("Farbe in der App")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MedColor.entries.forEach { mc ->
                    val selected = draft.color == mc
                    Box(
                        Modifier
                            .size(48.dp)
                            .border(2.dp, if (selected) c.tinte else Color.Transparent, CircleShape)
                            .pClickable(CircleShape, role = Role.RadioButton) { onChange(draft.copy(color = mc)) }
                            .semantics {
                                contentDescription = "Farbe ${ColorNames[mc]}"
                                this.selected = selected
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(36.dp).background(c.palette(mc).swatch, CircleShape))
                    }
                }
            }
        }

        WizardFieldGroup("Hinweis (optional)") {
            WizardTextField(
                value = draft.hint,
                onValueChange = { onChange(draft.copy(hint = it.take(80))) },
                placeholder = "z. B. 30 Min. vor dem Frühstück",
                imeAction = ImeAction.Done,
                onImeAction = onNameDone,
            )
        }
    }
}

/** Nur Ziffern und ein Komma (Punkt wird zu Komma), höchstens 8 Zeichen. */
private fun sanitizeStrength(input: String): String {
    val sb = StringBuilder()
    var comma = false
    for (ch in input) {
        when {
            ch.isDigit() -> sb.append(ch)
            (ch == ',' || ch == '.') && !comma && sb.isNotEmpty() -> { sb.append(','); comma = true }
        }
        if (sb.length >= 8) break
    }
    return sb.toString()
}
