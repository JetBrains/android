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
package com.android.tools.idea.welcome.wizard

import com.google.common.truth.Truth.assertThat
import com.intellij.ui.components.JBLabel
import org.junit.Test

import org.junit.Assert.assertNotNull

class FormFactorUtilsTest {
  @Test
  fun formFactorsImage() {
    val jLabel = JBLabel()
    val withEmulatorIcons = getFormFactorsImage(jLabel, true)
    val allIcons = getFormFactorsImage(jLabel, false)

    assertNotNull(withEmulatorIcons)
    assertNotNull(allIcons)

    // Note: When running unit tests, it will not load the real images, each icon uses a 1x1 pixel size
    assertThat(allIcons!!.iconWidth).isAtLeast(withEmulatorIcons!!.iconWidth)
  }
}
