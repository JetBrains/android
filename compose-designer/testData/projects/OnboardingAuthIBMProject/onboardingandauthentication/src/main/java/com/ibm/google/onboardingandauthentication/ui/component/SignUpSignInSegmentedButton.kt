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

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.tooling.preview.Preview
import com.ibm.google.onboardingandauthentication.R
import com.ibm.google.onboardingandauthentication.ui.theme.OnBoardingAndAuthenticationTheme

/**
 * A composable that displays a Material Design 3 Single-Choice Segmented Button Row.
 *
 * @param options An array of option labels to display as segmented buttons.
 * @param selectedIndex The index of the currently selected option. Default is 0.
 * @param onSelectionChange Callback triggered when a different option is selected, providing the
 *   new selected index.
 * @param modifier Modifier to be applied to the SegmentedButtonRow container.
 */
@Composable
fun SignUpSignInSegmentedButton(
  options: Array<String>,
  selectedIndex: Int,
  onSelectionChange: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier) {
    options.forEachIndexed { index, label ->
      SegmentedButton(
        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
        onClick = { onSelectionChange(index) },
        selected = index == selectedIndex,
        label = { Text(text = label) },
      )
    }
  }
}

@Preview(showBackground = true)
@Composable
fun SignUpSignInSegmentedButtonPreview() {
  var selectedIndex by remember { mutableIntStateOf(0) }

  OnBoardingAndAuthenticationTheme {
    SignUpSignInSegmentedButton(
      options = stringArrayResource(R.array.segmented_button_labels),
      selectedIndex = selectedIndex,
      onSelectionChange = { selectedIndex = it },
    )
  }
}
