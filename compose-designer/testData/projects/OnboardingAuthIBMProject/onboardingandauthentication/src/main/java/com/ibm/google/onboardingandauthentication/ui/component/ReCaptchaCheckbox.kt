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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * A composable that mimics a reCAPTCHA checkbox with dynamic width and end-aligned image.
 *
 * @param label The text displayed next to the checkbox.
 * @param checked The current checked state of the checkbox.
 * @param onCheckedChange Callback invoked when the checkbox checked state changes.
 * @param modifier Modifier for styling and layout customization.
 */
@Composable
fun ReCaptchaCheckbox(
  label: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier =
      modifier
        .border(1.dp, MaterialTheme.colorScheme.surfaceContainerLow)
        .background(MaterialTheme.colorScheme.onPrimary)
        .height(48.dp)
        .width(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(30.dp, Alignment.Start),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = CheckboxDefaults.colors(uncheckedColor = MaterialTheme.colorScheme.primary),
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
      )
    }
    Image(
      modifier = Modifier.padding(all = 4.dp),
      painter = painterResource(id = R.drawable.google_recaptcha),
      contentDescription = null, // Decorative Icon
      contentScale = ContentScale.Crop,
    )
  }
}

@Preview(showBackground = true)
@Composable
fun ReCaptchaCheckboxPreview() {
  var checked by remember { mutableStateOf(false) }

  OnBoardingAndAuthenticationTheme {
    ReCaptchaCheckbox(
      label = stringResource(id = R.string.i_am_not_robot),
      checked = checked,
      onCheckedChange = { checked = it },
    )
  }
}
