/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.google.onboardingandauthentication.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Composable function that displays a row with a text label and a switch with a custom icon.
 *
 * @param label The text displayed to the left of the switch.
 * @param isChecked Whether the switch is currently checked.
 * @param onCheckedChange Callback invoked when the switch state changes, receiving the new checked
 *   state.
 * @param modifier Modifier applied to the row layout for styling or positioning.
 */
@Composable
fun TextSwitchRow(
  label: String,
  isChecked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style =
        MaterialTheme.typography.bodyMedium.copy(
          color = MaterialTheme.colorScheme.onSurface,
          fontWeight = FontWeight.W400,
        ),
    )
    Switch(
      checked = isChecked,
      onCheckedChange = { onCheckedChange(it) },
      thumbContent = {
        Icon(
          imageVector = if (isChecked) Icons.Default.Done else Icons.Default.Close,
          contentDescription = null, // Decorative Icon
          modifier = Modifier.size(SwitchDefaults.IconSize),
        )
      },
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun TextSwitchRowOffStatePreview() {
  OnBoardingAndAuthenticationTheme {
    TextSwitchRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      label = "I would like daily mindfulness reminders",
      isChecked = false,
      onCheckedChange = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun TextSwitchRowOnStatePreview() {
  OnBoardingAndAuthenticationTheme {
    TextSwitchRow(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
      label = "I would like daily mindfulness reminders",
      isChecked = true,
      onCheckedChange = {},
    )
  }
}
