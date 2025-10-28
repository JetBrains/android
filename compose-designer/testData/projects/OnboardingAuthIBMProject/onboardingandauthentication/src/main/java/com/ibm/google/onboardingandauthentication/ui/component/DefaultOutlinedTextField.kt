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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Composable function that displays a customizable outlined text field following Material 3
 * guidelines.
 *
 * @param value The current value of the text field.
 * @param onValueChange Callback invoked when the text field value changes.
 * @param modifier Modifier applied to the text field for layout or styling.
 * @param label The text label for the text field.
 * @param readOnly Whether the text field is read-only.
 * @param isError Whether the text field is in an error state.
 * @param trailingIcon Optional icon to display at the end of the text field.
 * @param trailingIconContentDescription Optional content description for the trailing icon (for
 *   accessibility).
 * @param supportingText Optional supporting text displayed below the text field.
 * @param supportingTextColor Color of the supporting text.
 * @param visualTransformation Visual transformation to apply to the input (e.g., password masking).
 * @param keyboardOptions Keyboard options to configure the software keyboard.
 * @param keyboardActions Keyboard actions to handle IME actions.
 * @param onTrailingClick Callback invoked when the trailing icon is clicked.
 */
@Composable
fun DefaultOutlinedTextField(
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  label: String = "",
  readOnly: Boolean = false,
  isError: Boolean = false,
  trailingIcon: ImageVector? = null,
  trailingIconContentDescription: String? = null,
  supportingText: String? = null,
  supportingTextColor: Color = MaterialTheme.colorScheme.error,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  keyboardOptions: KeyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Unspecified),
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  onTrailingClick: () -> Unit = {},
) {
  OutlinedTextField(
    value = value,
    onValueChange = { onValueChange(it) },
    label = {
      Text(
        text = label,
        style =
          MaterialTheme.typography.bodySmall.copy(
            color =
              if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
          ),
      )
    },
    readOnly = readOnly,
    visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions,
    keyboardActions = keyboardActions,
    singleLine = true,
    modifier = modifier,
    isError = isError,
    trailingIcon = {
      if (trailingIcon != null) {
        IconButton(onClick = onTrailingClick) {
          Icon(imageVector = trailingIcon, contentDescription = trailingIconContentDescription)
        }
      }
    },
    supportingText = { supportingText?.let { Text(text = it, color = supportingTextColor) } },
  )
}

@Preview(showBackground = true)
@Composable
private fun DefaultOutlinedTextFieldNormalStatePreview() {
  OnBoardingAndAuthenticationTheme {
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      label = "Email",
      isError = false,
      value = "johndoe@example.com",
      trailingIcon = Icons.Outlined.Cancel,
      onValueChange = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun DefaultOutlinedTextFieldErrorStatePreview() {
  OnBoardingAndAuthenticationTheme {
    DefaultOutlinedTextField(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      label = "Email",
      isError = true,
      value = "jdoe@gmail",
      trailingIcon = Icons.Outlined.Error,
      supportingText = "Invalid email address",
      onValueChange = {},
    )
  }
}
