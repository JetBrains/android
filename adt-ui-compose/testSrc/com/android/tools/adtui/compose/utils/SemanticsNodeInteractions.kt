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
package com.android.tools.adtui.compose.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InjectionScope
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performMouseInput

/**
 * Hovers the mouse over the composable and waits for a specified duration.
 *
 * This is useful for testing tooltips and other hover-based interactions.
 *
 * @param rule The [StudioComposeTestRule] to advance the clock on.
 * @param durationMillis The duration to linger, in milliseconds.
 * @param positionProvider A function that returns the position to hover over. Defaults to the
 *   center of the node.
 */
@OptIn(ExperimentalTestApi::class)
fun SemanticsNodeInteraction.lingerMouseHover(
  rule: StudioComposeTestRule,
  durationMillis: Long = 1000,
  positionProvider: InjectionScope.() -> Offset = { center },
) {
  performMouseInput { moveTo(positionProvider()) }
  rule.mainClock.advanceTimeBy(durationMillis)
}