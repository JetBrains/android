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
package com.android.tools.idea.avd

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText

internal fun SemanticsNodeInteractionsProvider.onNodeWithEditableText(
  text: String,
  substring: Boolean = false,
  ignoreCase: Boolean = false,
  useUnmergedTree: Boolean = false,
): SemanticsNodeInteraction =
  onNode(hasSetTextAction() and hasText(text, substring, ignoreCase), useUnmergedTree)

internal fun SemanticsNodeInteractionsProvider.onNodeWithClickableText(
  text: String,
  substring: Boolean = false,
  ignoreCase: Boolean = false,
  useUnmergedTree: Boolean = false,
): SemanticsNodeInteraction =
  onNode(hasClickAction() and hasText(text, substring, ignoreCase), useUnmergedTree)
