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

import com.android.tools.idea.wizard.template.Language.Java
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.util.AndroidBundle.message
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Tests for [ChooseActivityTypeStep].
 */
class ChooseActivityTypeStepTest {
  private val messageKeys = activityGalleryStepMessageKeys

  @Test
  fun testNoTemplateForExistingModule() {
    assertThat(Template.NoActivity.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = false, isAndroidxProject = false, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo("No activity template was selected")
  }

  @Test
  fun testNoTemplateForNewModule() {
    assertThat(mock(Template::class.java).validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = true, isAndroidxProject = false, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo("")
  }

  @Test
  fun testTemplateWithMinSdkHigherThanModule() {
    val template = mock(Template::class.java)
    `when`(template.minSdk).thenReturn(9)

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = true, isAndroidxProject = true, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.min.sdk", 9))
  }

  @Test
  fun testTemplateWithMinBuildSdkHigherThanModule() {
    val template = mock(Template::class.java)
    `when`(template.minCompileSdk).thenReturn(9)

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = true, isAndroidxProject = true, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.min.build", 9))
  }

  @Test
  fun testTemplateRequiringAndroidX() {
    val template = mock(Template::class.java)
    `when`(template.constraints).thenReturn(listOf(TemplateConstraint.AndroidX))

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = false, isAndroidxProject = false, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.androidx"))
  }

  @Test
  fun testTemplateRequiringJetifier() {
    val template = mock(Template::class.java)
    `when`(template.constraints).thenReturn(listOf(TemplateConstraint.Jetifier))

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = false, isAndroidxProject = true, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.jetifier"))
  }

  @Test
  fun testTemplateRequiringJetifierOnNonAndroidXProject() {
    val template = mock(Template::class.java)
    `when`(template.constraints).thenReturn(listOf(TemplateConstraint.Jetifier))

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = false, isAndroidxProject = false, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo("")
  }

  @Test
  fun testTemplateRequiringKotlinForNewModule() {
    val template = mock(Template::class.java)
    `when`(template.constraints).thenReturn(listOf(TemplateConstraint.Kotlin))

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = true, isAndroidxProject = false, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEqualTo(message("android.wizard.activity.invalid.needs.kotlin"))
  }

  @Test
  fun testTemplateRequiringKotlinForExistingModule() {
    val template = mock(Template::class.java)
    `when`(template.constraints).thenReturn(listOf(TemplateConstraint.Kotlin))

    assertThat(template.validate(
      moduleApiLevel = 5, moduleBuildApiLevel = 5,
      isNewModule = false, isAndroidxProject = false, isJetifierEnabled = false, language = Java,
      messageKeys = messageKeys))
      .isEmpty()
  }
}
