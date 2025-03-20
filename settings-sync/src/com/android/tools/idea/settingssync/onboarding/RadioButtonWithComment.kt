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
package com.android.tools.idea.settingssync.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.RadioButton
import org.jetbrains.jewel.ui.component.Text

/** A radio button with a primary label and additional explanatory text below. */
@Composable
internal fun RadioButtonWithComment(
  annotatedText: AnnotatedString,
  comment: String,
  selected: Boolean,
  onSelect: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }

  Row(
    modifier =
      Modifier.clickable(
        onClick = { onSelect.invoke() },
        enabled = true,
        role = Role.RadioButton,
        interactionSource = interactionSource,
        indication = null, // Disable highlighting of the row on hover
      ),
    verticalAlignment = Alignment.Top,
  ) {
    RadioButton(selected = selected, onClick = {}, interactionSource = interactionSource)

    Spacer(modifier = Modifier.width(4.dp))

    Column(Modifier.padding(top = 2.dp)) {
      Text(text = annotatedText, textAlign = TextAlign.Start)
      Spacer(modifier = Modifier.height(6.dp))
      Text(text = comment, color = JewelTheme.globalColors.text.info)
    }
  }
}
