/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow.splittingtabs.actions

import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import org.junit.Test

/**
 * Tests for [SplitAction]
 *
 * Tests of [SplitAction.actionPerformed] are in [com.android.tools.adtui.toolwindow.splittingtabs.SplittingPanelTest] for convenience
 */
class SplitActionTest {
  @Test
  fun presentation() {
    assertThat(SplitAction.Vertical().templatePresentation.text).isEqualTo("Split Right")
    assertThat(SplitAction.Vertical().templatePresentation.icon).isEqualTo(AllIcons.Actions.SplitVertically)
    assertThat(SplitAction.Horizontal().templatePresentation.text).isEqualTo("Split Down")
    assertThat(SplitAction.Horizontal().templatePresentation.icon).isEqualTo(AllIcons.Actions.SplitHorizontally)
  }
}