/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues.processor

import com.android.tools.idea.gradle.project.sync.issues.SdkInManifestIssuesReporter.SdkProperty.MIN
import com.intellij.openapi.module.Module
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewBundle
import org.junit.Test
import org.mockito.Mockito.mock

class RemoveSdkFromManifestProcessorTest : HeavyPlatformTestCase() {
  @Test
  fun testUsageViewDescriptor() {
    val processor = RemoveSdkFromManifestProcessor(project, listOf(), MIN)
    val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
    assertEquals("Property to move/remove " + UsageViewBundle.getReferencesString(1, 1),
                 usageDescriptor.getCodeReferencesText(1, 1))
    assertEquals("Properties to move/remove " + UsageViewBundle.getReferencesString(2, 2),
                 usageDescriptor.getCodeReferencesText(2, 2))
    assertEquals("Remove minSdkVersion from manifest", usageDescriptor.processedElementsHeader)
  }

  @Test
  fun testMultipleModulesUsageViewDescriptor() {
    val processor = RemoveSdkFromManifestProcessor(project, listOf(mock(Module::class.java), mock(Module::class.java)), MIN)
    val usageDescriptor = processor.createUsageViewDescriptor(UsageInfo.EMPTY_ARRAY)
    assertEquals("Property to move/remove " + UsageViewBundle.getReferencesString(1, 1),
                 usageDescriptor.getCodeReferencesText(1, 1))
    assertEquals("Properties to move/remove " + UsageViewBundle.getReferencesString(2, 2),
                 usageDescriptor.getCodeReferencesText(2, 2))
    assertEquals("Remove minSdkVersion from manifests", usageDescriptor.processedElementsHeader)
  }
}