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

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * A customizable RoundedFilledButton composable following Material 3 guidelines.
 *
 * @param label The text label to display inside the button.
 * @param onClick Lambda function triggered when the button is clicked.
 * @param modifier Modifier to be applied to the button layout.
 * @param textStyle The text style applied to the label text.
 * @param shouldButtonEnabled Boolean indicating whether the button should be enabled or disabled.
 */
@Composable
fun RoundedFilledButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  textStyle: TextStyle = MaterialTheme.typography.labelLarge,
  shouldButtonEnabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    shape = MaterialTheme.shapes.extraLarge,
    enabled = shouldButtonEnabled,
    modifier = modifier,
  ) {
    Text(text = label, style = textStyle)
  }
}

@Preview(showBackground = true)
@Composable
fun RoundedFilledButtonPreview() {
  OnBoardingAndAuthenticationTheme { RoundedFilledButton(label = "Button", onClick = {}) }
}
