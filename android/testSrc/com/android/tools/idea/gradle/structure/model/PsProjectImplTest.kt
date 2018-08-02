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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.structure.model.android.testResolve
import com.android.tools.idea.testing.TestProjectPaths
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.nullValue
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat

class PsProjectImplTest : DependencyTestCase() {

  fun testModuleOrder() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val project = PsProjectImpl(myFixture.project)

    // settings.gradle does not list modules in the lexicographic order.
    assumeThat(project.parsedModel.projectSettingsModel?.modulePaths(), equalTo(listOf(
      ":", ":app", ":lib", ":jav", ":nested1", ":nested2", ":nested1:deep", ":nested2:deep", ":nested2:trans:deep2")))

    // Lexicographically ordered by gradlePath.
    val modulesBeforeGradleModelsResolved = project.modules.map { it.gradlePath }
    assertThat<List<String?>>(modulesBeforeGradleModelsResolved, equalTo(listOf(
      ":app", ":jav", ":lib", ":nested1", ":nested1:deep", ":nested2", ":nested2:deep", ":nested2:trans:deep2")))

    project.testResolve()

    // Includes not declared module ":nested2:trans".
    val modulesAfterGradleModelsResolved = project.modules.map { it.gradlePath }
    assertThat<List<String?>>(modulesAfterGradleModelsResolved, equalTo(listOf(
      ":app", ":jav", ":lib", ":nested1", ":nested1:deep", ":nested2", ":nested2:deep", ":nested2:trans", ":nested2:trans:deep2")))

    assertThat(project.findModuleByGradlePath(":nested2:trans")?.isDeclared, equalTo(false))
  }

  fun testRemoveModule() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))

    project.removeModule(gradlePath = ":nested2:deep")
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(false))

    project.applyChanges()  // applyChanges() discards resolved models.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())

    project.testResolve()  // A removed module should not reappear unless it is in the middle of a hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
  }

  fun testRemoveMiddleModule() {
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    assumeThat(project.findModuleByGradlePath(":nested2")?.isDeclared, equalTo(true))

    project.removeModule(gradlePath = ":nested2")
    assertThat(project.findModuleByGradlePath(":nested2")?.isDeclared, equalTo(false))

    project.applyChanges()  // applyChanges() discards resolved models.
    assertThat(project.findModuleByGradlePath(":nested2")?.isDeclared, nullValue())

    project.testResolve()  // A removed module should reappear because it is in the middle of the hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2")?.isDeclared, equalTo(false))
  }

  fun testApplyRunAndReparse() {
    val newSuffix = "testApplyRunAndReparse"
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    val appModule = project.findModuleByGradlePath(":app") as PsAndroidModule
    // Make a random change.
    appModule.findBuildType("debug")!!.applicationIdSuffix = newSuffix.asParsed()

    // Make an independent change to the configuration while in the "run" phase.
    project.applyRunAndReparse {
      val anotherProjectInstance = PsProjectImpl(myFixture.project)
      assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
      assumeThat(anotherProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
      // Any previously pending changes should be applied and visible at this point.
      assertThat(
        (anotherProjectInstance.findModuleByGradlePath(":app") as PsAndroidModule).findBuildType("debug")!!.applicationIdSuffix,
        equalTo(newSuffix.asParsed()))

      anotherProjectInstance.removeModule(gradlePath = ":nested2:deep")
      anotherProjectInstance.applyChanges()

      // Different DSL models are independent.
      assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
      // The removed module should disappear from the project which does not have a resolved Gradle model.
      assumeThat(anotherProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
      true
    }

    // The module collection should be refreshed and the removed module should disappear.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
    project.testResolve()  // A removed module should not reappear unless it is in the middle of a hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
  }

  fun testApplyRunAndReparse_cancel() {
    val newSuffix = "testApplyRunAndReparse"
    loadProject(TestProjectPaths.PSD_SAMPLE)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }

    // Make an independent change to the configuration while in the "run" phase.
    project.applyRunAndReparse {
      val anotherProjectInstance = PsProjectImpl(myFixture.project)
      assumeThat(anotherProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))

      anotherProjectInstance.removeModule(gradlePath = ":nested2:deep")
      anotherProjectInstance.applyChanges()

      // Different DSL models are independent.
      assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
      // The removed module should disappear from the project which does not have a resolved Gradle model.
      assumeThat(anotherProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
      false
    }
    // The model is not reloaded if change cancelled, even when some real changes have been made.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
  }
}