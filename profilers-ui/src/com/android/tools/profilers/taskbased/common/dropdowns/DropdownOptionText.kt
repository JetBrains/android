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

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.android.tools.profilers.taskbased.common.constants.TaskBasedUxDimensions
import org.jetbrains.jewel.ui.component.Text

@Composable
fun DropdownOptionText(text: String, modifier: Modifier = Modifier) {
  Text(text = text, fontSize = TextUnit(14f, TextUnitType.Sp), lineHeight = TextUnit(18f, TextUnitType.Sp), maxLines = 1,
       overflow = TextOverflow.Ellipsis,
       modifier = modifier.padding(horizontal = TaskBasedUxDimensions.DROPDOWN_HORIZONTAL_PADDING_DP))
}