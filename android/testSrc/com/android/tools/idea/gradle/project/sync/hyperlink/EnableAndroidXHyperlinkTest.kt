/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.refactoring.ENABLE_JETIFIER_PROPERTY
import org.jetbrains.android.refactoring.USE_ANDROIDX_PROPERTY
import org.junit.Test
import java.util.Properties

class EnableAndroidXHyperlinkTest {
  @Test
  fun `properties set`() {
    val quickFix = EnableAndroidXHyperlink()
    val properties = Properties()
    quickFix.setProperties(properties)
    assertThat(properties.getProperty(USE_ANDROIDX_PROPERTY)).isEqualTo("true")
    assertThat(properties.getProperty(ENABLE_JETIFIER_PROPERTY)).isEqualTo("true")
    assertThat(properties.keys).hasSize(2)
  }
}