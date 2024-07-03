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
package com.android.tools.idea.gradle.declarative.runsGradle

import com.android.SdkConstants.FN_BUILD_GRADLE_DECLARATIVE
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.declarative.DeclarativeUnresolvedReferenceInspection
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.onEdt
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class DeclarativeUnresolvedReferenceTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  @Before
  fun setUp() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.override(true)
    projectRule.fixture.enableInspections(DeclarativeUnresolvedReferenceInspection::class.java)
  }

  @After
  fun tearDown() {
    StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT.clearOverride()
  }

  @Test
  fun testWrongReference() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_MULTI_VERSION_CATALOG)
    val file = projectRule.fixture.createFile(FN_BUILD_GRADLE_DECLARATIVE, """
      dependencies {
        implementation(libs.<warning descr="Cannot resolve symbol 'libs.some-guava'">some.guava</warning>)
      }
    """.trimIndent())
    projectRule.fixture.openFileInEditor(file)
    projectRule.fixture.checkHighlighting()
  }
}