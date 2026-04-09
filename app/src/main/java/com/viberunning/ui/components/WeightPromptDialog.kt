package com.viberunning.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.viberunning.util.PreferencesManager
import java.util.Locale

@Composable
fun WeightPromptDialog(
    preferencesManager: PreferencesManager,
    useImperial: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val currentLbs = preferencesManager.weightLbs
    val displayWeight = if (useImperial) {
        currentLbs.toInt().toString()
    } else {
        (currentLbs * 0.453592f).toInt().toString()
    }

    var weightText by remember { mutableStateOf(displayWeight) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Your Weight") },
        text = {
            Column {
                Text(
                    text = "It's been a while since your last weight update. " +
                            "Keeping this current helps improve calorie estimates.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Current: ${
                        if (useImperial) String.format(Locale.US, "%.0f lbs", currentLbs)
                        else String.format(Locale.US, "%.0f kg", currentLbs * 0.453592f)
                    }",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                    label = { Text(if (useImperial) "Weight (lbs)" else "Weight (kg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val lbs = if (useImperial) {
                        weightText.toFloatOrNull() ?: currentLbs
                    } else {
                        val kg = weightText.toFloatOrNull() ?: (currentLbs * 0.453592f)
                        kg / 0.453592f
                    }
                    if (lbs > 0f) preferencesManager.weightLbs = lbs
                    onSave()
                },
                enabled = weightText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later")
            }
        }
    )
}
