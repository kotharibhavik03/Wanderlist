package com.rizz.wanderlist.views.place

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rizz.wanderlist.domain.model.MemberRole
import com.rizz.wanderlist.domain.usecase.MemberEmailProblem
import com.rizz.wanderlist.views.text.rememberNoAutofill

/**
 * Adds a person to the place, shown inline in the People section rather than in a dialog:
 * the system draws email suggestions (Android Autofill) in the wrong place for fields
 * inside a dialog window, and right under the field on the main screen.
 *
 * [problemFor] explains why an email can't be added (yourself, already added); while it
 * returns a problem, Add stays disabled.
 */
@Composable
internal fun AddPersonForm(
    problemFor: (email: String) -> MemberEmailProblem?,
    onCancel: () -> Unit,
    onAdd: (email: String, role: MemberRole) -> Unit
) {
    var email by rememberSaveable { mutableStateOf("") }
    var role by remember { mutableStateOf(MemberRole.VISITED_WITH) }
    val problem = problemFor(email)
    // An unfinished address isn't an error yet; anything else is explained under the field.
    val message = when (problem) {
        null, MemberEmailProblem.Invalid -> null
        MemberEmailProblem.Yourself -> "That's you. You already have this place."
        is MemberEmailProblem.AlreadyAdded -> "Already added as ${problem.role.title()}."
    }
    val canAdd = problem == null

    // The form opens because the user wants to type an email: put the cursor there.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Add person", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                singleLine = true,
                label = { Text("Google account email") },
                isError = email.isNotBlank() && problem != null,
                supportingText = message?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                    // It asks for someone else's email: don't offer the user's own (iOS).
                    platformImeOptions = rememberNoAutofill()
                ),
                keyboardActions = KeyboardActions(onDone = { if (canAdd) onAdd(email, role) })
            )

            Column(modifier = Modifier.selectableGroup()) {
                MemberRole.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = role == option,
                                onClick = { role = option },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = role == option, onClick = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(option.title(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (option == MemberRole.COLLABORATOR) "Can edit this place and add photos"
                                else "Can view this place and add their own photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Button(onClick = { onAdd(email, role) }, enabled = canAdd) { Text("Add") }
            }
        }
    }
}
