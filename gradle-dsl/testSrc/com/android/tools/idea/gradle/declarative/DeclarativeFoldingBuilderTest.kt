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
package com.android.tools.idea.gradle.declarative

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import org.junit.Rule
import org.junit.Test

class DeclarativeFoldingBuilderTest {
  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()

  private val myFixture: CodeInsightTestFixtureImpl by lazy { projectRule.fixture as CodeInsightTestFixtureImpl }

  @Test
  fun test() {
    myFixture.addFileToProject(
      "build.gradle.dcl",
      // language=Declarative
      """
      <fold text='/* ... */'>/*
      some long
      comment
      */</fold>
      plugins<fold text='{...}'> {
          id("org.gradle.experimental.android-application")
      }</fold>
      androidApplication<fold text='{...}'> {
          namespace = "com.example.myapplication"
          compileSdk = 31
      }</fold>
      declarativeDependencies<fold text='{...}'> {
          implementation("com.google.guava:guava:32.1.2-jre")
          implementation("org.apache.commons:commons-lang3:3.12.0")
          implementation("android.arch.core:common:1.1.1")
      }</fold>
    """.trimIndent()
    )
    myFixture.testFolding("${projectRule.project.basePath}/build.gradle.dcl")
  }
}

