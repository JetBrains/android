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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.DeclarativeAndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.withDeclarative
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeIdeSupport
import org.junit.After

class PsProjectImplDeclarativeTest {
  @get:Rule
  val projectRule: DeclarativeAndroidGradleProjectRule = AndroidGradleProjectRule().withDeclarative()

  @Before
  fun before() {
    DeclarativeIdeSupport.override(true)
  }

  @After
  fun after(){
    DeclarativeIdeSupport.clearOverride()
  }

  @Test
  fun testAgpVersionDeclarative() {
    projectRule.loadProject(TestProjectPaths.SIMPLE_APPLICATION_DECLARATIVE)
    var project = PsProjectImpl(projectRule.project)

    assertThat(project.androidGradlePluginVersion, equalTo(BuildEnvironment.getInstance().gradlePluginVersion.asParsed()))

    project.androidGradlePluginVersion = "8.8.0".asParsed()
    project.applyChanges()

    project = PsProjectImpl(projectRule.project)
    assertThat(project.androidGradlePluginVersion, equalTo("8.8.0".asParsed()))
  }

}