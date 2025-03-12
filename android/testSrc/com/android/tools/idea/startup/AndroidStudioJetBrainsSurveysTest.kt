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
package com.android.tools.idea.startup

import com.android.tools.idea.IdeInfo
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryKeyBean
import com.intellij.testFramework.ApplicationRule
import org.junit.Assume
import org.junit.Rule
import org.junit.Test

/**
 * Ensures JetBrains feedback surveys are suppressed in Android Studio (b/402895189).
 * See [com.intellij.platform.feedback.FeedbackSurvey] and related classes for context.
 */
class AndroidStudioJetBrainsSurveysTest {
  @get:Rule
  val appRule = ApplicationRule()

  @Test
  fun testSurveysAreDisabled() {
    Assume.assumeTrue(IdeInfo.getInstance().isAndroidStudio)
    // JetBrains feedback surveys can be suppressed with the "platform.feedback" registry flag,
    // whose value we override in the Android plugin. The platform feedback machinery is largely
    // internal and infeasible to test on our side, so instead we only assert that the registry
    // flag exists and was successfully overridden.
    val feedbackKey = "platform.feedback"
    assertThat(Registry.`is`(feedbackKey)).named("Registry value '$feedbackKey'").isFalse()
    var foundOriginalRegistryKey = false
    for (registryKey in ExtensionPointName<RegistryKeyBean>("com.intellij.registryKey").extensionList) {
      if (registryKey.key == feedbackKey && !registryKey.overrides) {
        assertThat(foundOriginalRegistryKey).isFalse()
        assertThat(registryKey.defaultValue.toBoolean()).isTrue()
        foundOriginalRegistryKey = true
      }
    }
    assertWithMessage("Expected to find registry key '$feedbackKey' declared in the platform").that(foundOriginalRegistryKey).isTrue()
  }
}
