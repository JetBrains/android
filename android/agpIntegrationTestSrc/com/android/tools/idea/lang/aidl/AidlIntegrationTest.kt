/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.lang.aidl

import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class AidlIntegrationTest {
  private val projectRule = AndroidGradleProjectRule()

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule())

  private val fixture by lazy { projectRule.fixture }
  private val project by lazy { projectRule.project }

  @Before
  fun setUp() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_AIDL)
    projectRule.generateSources()
  }

  @Test
  fun kotlinImplementation() {
    fixture.openFileInEditor(
      project
        .guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/com/example/KotlinRemoteInterfaceImpl.kt")!!
    )

    // Checking highlighting ensures that the AIDL generated code was created and built correctly.
    // Otherwise, there would be errors (whether about a missing base class, un-matching override,
    // or whatever else).
    fixture.checkHighlighting()
  }

  @Test
  fun javaImplementation() {
    fixture.openFileInEditor(
      project
        .guessProjectDir()!!
        .findFileByRelativePath("app/src/main/java/com/example/JavaRemoteInterfaceImpl.java")!!
    )

    // Checking highlighting ensures that the AIDL generated code was created and built correctly.
    // Otherwise, there would be errors (whether about a missing base class, un-matching override,
    // or whatever else).
    fixture.checkHighlighting()
  }
}
