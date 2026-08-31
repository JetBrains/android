/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.template

import com.android.tools.idea.wizard.template.FormFactor
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class KmpPluginPromotionProviderTest {
  @Test
  fun testGetTemplates() {
    val provider = KmpPluginPlaceholderProvider()
    val templates = provider.getTemplates()

    assertThat(templates).hasSize(2)

    val mobileTemplate = templates.first { it.formFactor == FormFactor.Mobile }
    assertThat(mobileTemplate.name).isEqualTo("Kotlin Multiplatform")
    assertThat(mobileTemplate.pluginId).isEqualTo("com.jetbrains.kmm")
    assertThat(mobileTemplate.thumb().path()).isNotNull()

    val genericTemplate = templates.first { it.formFactor == FormFactor.Generic }
    assertThat(genericTemplate.name).isEqualTo("Kotlin Multiplatform")
    assertThat(genericTemplate.pluginId).isEqualTo("com.jetbrains.kmm")
  }
}
