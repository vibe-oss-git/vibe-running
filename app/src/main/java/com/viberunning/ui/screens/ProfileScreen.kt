package com.viberunning.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.viberunning.util.PreferencesManager
import java.util.Locale

@Composable
fun ProfileScreen(
    preferencesManager: PreferencesManager,
    useImperial: Boolean,
    onProfileSaved: () -> Unit
) {
    val currentHeightInches = preferencesManager.heightInches
    val currentWeightLbs = preferencesManager.weightLbs

    // Height fields (imperial: feet + inches, metric: cm)
    var heightFeet by remember {
        mutableStateOf(if (currentHeightInches > 0) (currentHeightInches / 12).toInt().toString() else "")
    }
    var heightInches by remember {
        mutableStateOf(if (currentHeightInches > 0) (currentHeightInches % 12).toInt().toString() else "")
    }
    var heightCm by remember {
        mutableStateOf(if (currentHeightInches > 0) (currentHeightInches * 2.54f).toInt().toString() else "")
    }

    // Weight field
    var weightText by remember {
        mutableStateOf(
            if (currentWeightLbs > 0) {
                if (useImperial) currentWeightLbs.toInt().toString()
                else (currentWeightLbs * 0.453592f).toInt().toString()
            } else ""
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Your Profile",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Used to estimate calories burned during activities",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Height
        Text(
            text = "Height",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (useImperial) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = heightFeet,
                    onValueChange = { heightFeet = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Feet") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = heightInches,
                    onValueChange = { heightInches = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("Inches") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
        } else {
            OutlinedTextField(
                value = heightCm,
                onValueChange = { heightCm = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Centimeters") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Weight
        Text(
            text = "Weight",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = weightText,
            onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
            label = { Text(if (useImperial) "Pounds (lbs)" else "Kilograms (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(32.dp))

        // BMI display (if profile already exists)
        if (preferencesManager.hasProfile) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Current BMI",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", preferencesManager.bmi),
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = preferencesManager.bmiCategory,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "BMI is a general indicator. It does not account for muscle mass, " +
                                "bone density, or body composition.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Save button
        Button(
            onClick = {
                val totalInches = if (useImperial) {
                    val ft = heightFeet.toIntOrNull() ?: 0
                    val inc = heightInches.toIntOrNull() ?: 0
                    (ft * 12 + inc).toFloat()
                } else {
                    val cm = heightCm.toFloatOrNull() ?: 0f
                    cm / 2.54f
                }

                val lbs = if (useImperial) {
                    weightText.toFloatOrNull() ?: 0f
                } else {
                    val kg = weightText.toFloatOrNull() ?: 0f
                    kg / 0.453592f
                }

                if (totalInches > 0f) preferencesManager.heightInches = totalInches
                if (lbs > 0f) preferencesManager.weightLbs = lbs

                onProfileSaved()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = weightText.isNotBlank() && (
                (useImperial && heightFeet.isNotBlank()) ||
                (!useImperial && heightCm.isNotBlank())
            )
        ) {
            Text(
                text = if (preferencesManager.hasProfile) "Update Profile" else "Save Profile",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
