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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * Composable function that displays a row with a message and an action button.
 *
 * @param actionLabel The label text for the action button.
 * @param onActionClick Callback invoked when the action button is clicked.
 * @param modifier Modifier applied to the root composable for layout or styling.
 * @param message The text message to display before the action button.
 */
@Composable
fun PromptTextButton(
  actionLabel: String,
  onActionClick: () -> Unit,
  modifier: Modifier = Modifier,
  message: String? = null,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    message?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
      )
    }
    TextButton(onClick = onActionClick) {
      Text(
        text = actionLabel,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
      )
    }
  }
}

/**
 * Provides multiple preview states for the [PromptTextButton] composable using [Pair]s of message
 * and action label.
 *
 * Each pair represents a different UI configuration:
 * - Pair(message, actionLabel)
 *
 * Used by [@PreviewParameter] to generate multiple Compose previews automatically.
 */
private class PromptTextButtonPreviewProvider :
  PreviewParameterProvider<Pair<String /* message */, String /* actionLabel */>> {
  override val values =
    sequenceOf(
      "Already have an account?" to "Login", // Case 1: both value
      "Forgot password?" to "", // Case 2: only message
      "" to "Not now", // Case 3: only action label
    )
}

@Preview(name = "PromptTextButton Variations", showBackground = true, backgroundColor = 0xFFF9FFE9)
@Composable
fun PromptTextButtonPreview(
  @PreviewParameter(PromptTextButtonPreviewProvider::class) data: Pair<String, String>
) {
  OnBoardingAndAuthenticationTheme {
    PromptTextButton(
      modifier = Modifier.fillMaxWidth(),
      message = data.first,
      actionLabel = data.second,
      onActionClick = {},
    )
  }
}
