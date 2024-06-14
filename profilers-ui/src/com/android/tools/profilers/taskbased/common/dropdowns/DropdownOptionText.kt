/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.taskbased.common.dropdowns

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.android.tools.profilers.taskbased.common.constants.colors.TaskBasedUxColors
import com.android.tools.profilers.taskbased.common.constants.dimensions.TaskBasedUxDimensions
import com.android.tools.profilers.taskbased.common.text.EllipsisText

@Composable
fun DropdownOptionText(modifier: Modifier = Modifier, primaryText: String, secondaryText: String? = null, isEnabled: Boolean) {
  Row (modifier = modifier.padding(horizontal = TaskBasedUxDimensions.DROPDOWN_HORIZONTAL_PADDING_DP)) {
    DropdownOptionText(text = primaryText)
    secondaryText?.let {
      Spacer(modifier = Modifier.width(5.dp))
      DropdownOptionText(text = "(${it})", color = if (isEnabled) TaskBasedUxColors.DROPDOWN_SEC_TEXT_COLOR else Color.Unspecified)
    }
  }
}


@Composable
private fun DropdownOptionText(text: String, color: Color = Color.Unspecified) {
  EllipsisText(text = text, color = color, fontSize = TextUnit(14f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp))
}