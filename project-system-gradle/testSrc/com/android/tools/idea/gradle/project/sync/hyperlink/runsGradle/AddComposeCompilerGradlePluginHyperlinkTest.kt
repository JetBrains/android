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
package com.android.tools.idea.gradle.project.sync.hyperlink.runsGradle

import com.android.tools.idea.gradle.project.sync.hyperlink.AddComposeCompilerGradlePluginHyperlink
import com.android.tools.idea.gradle.project.sync.issues.processor.AddComposeCompilerGradlePluginProcessor
import com.android.tools.idea.testing.AndroidGradleTestCase
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions

/**
 * Tests for [AddComposeCompilerGradlePluginHyperlink]
 */
class AddComposeCompilerGradlePluginHyperlinkTest: AndroidGradleTestCase() {
  @Test
  fun testQuickFixRunsProcessor() {
    loadSimpleApplication()
    val mockProcessor = mock(AddComposeCompilerGradlePluginProcessor::class.java)
    val module = getModule("app")
    val quickfix = AddComposeCompilerGradlePluginHyperlink(project, listOf(module), "2.0.0")
    quickfix.applyFix(project, mockProcessor)
    verify(mockProcessor).run()
  }

  @Test
  fun testProcessorDoesNothingIfNoAffectedModules() {
    loadSimpleApplication()
    val mockProcessor = mock(AddComposeCompilerGradlePluginProcessor::class.java)
    val quickfix = AddComposeCompilerGradlePluginHyperlink(project, listOf(), "2.0.0")
    quickfix.applyFix(project, mockProcessor)
    verifyNoInteractions(mockProcessor)
  }
}