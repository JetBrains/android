/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.npw.platform.Language.JAVA
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.util.AndroidBundle.message
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

import com.android.tools.idea.templates.TemplateMetadata
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.ANDROIDX
import com.android.tools.idea.templates.TemplateMetadata.TemplateConstraint.KOTLIN
import org.junit.Test
import java.util.EnumSet

/**
 * Tests for [ChooseActivityTypeStep].
 */
class ChooseActivityTypeStepTest {

  private val messageKeys = activityGalleryStepMessageKeys

  @Test
  fun testNoTemplateForExistingModule() {
    assertThat(validateTemplate(null, 5, 5,
                                isNewModule = false, isAndroidxProject = false, language = JAVA,
                                messageKeys = messageKeys)).isEqualTo("No activity template was selected")
  }

  @Test
  fun testNoTemplateForNewModule() {
    assertThat(validateTemplate(null, 5, 5,
                                isNewModule = true, isAndroidxProject = false, language = JAVA,
                                messageKeys = messageKeys)).isEqualTo("")
  }

  @Test
  fun testTemplateWithMinSdkHigherThanModule() {
    val template = mock(TemplateMetadata::class.java)
    `when`(template.minSdk).thenReturn(9)

    assertThat(validateTemplate(template, 5, 5,
                                isNewModule = true, isAndroidxProject = true, language = JAVA,
                                messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.min.sdk", 9))
  }

  @Test
  fun testTemplateWithMinBuildSdkHigherThanModule() {
    val template = mock(TemplateMetadata::class.java)
    `when`(template.minBuildApi).thenReturn(9)

    assertThat(validateTemplate(template, 5, 5,
                                isNewModule = true, isAndroidxProject = true, language = JAVA,
                                messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.min.build", 9))
  }

  @Test
  fun testTemplateRequiringAndroidX() {
    val template = mock(TemplateMetadata::class.java)
    `when`(template.constraints).thenReturn(EnumSet.of(ANDROIDX))

    assertThat(validateTemplate(template, 5, 5,
                                isNewModule = false, isAndroidxProject = false, language = JAVA,
                                messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.androidx"))
  }

  @Test
  fun testTemplateRequiringKotlin() {
    val template = mock(TemplateMetadata::class.java)
    `when`(template.constraints).thenReturn(EnumSet.of(KOTLIN))

    assertThat(validateTemplate(template, 5, 5,
                                isNewModule = false, isAndroidxProject = false, language = JAVA,
                                messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.needs.kotlin"))
  }
}
