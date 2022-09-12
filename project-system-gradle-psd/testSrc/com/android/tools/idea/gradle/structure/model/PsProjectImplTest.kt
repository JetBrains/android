/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.asParsed
import com.android.tools.idea.gradle.util.GradleWrapper
import com.android.tools.idea.testing.BuildEnvironment
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.gradle.util.PropertiesFiles.savePropertiesToFile
import com.intellij.openapi.application.runWriteAction
import org.gradle.wrapper.WrapperExecutor.DISTRIBUTION_URL_PROPERTY
import org.hamcrest.core.IsEqual.equalTo
import org.hamcrest.core.IsNull.nullValue
import org.junit.Assert.assertThat
import org.junit.Assume.assumeThat

class PsProjectImplTest : DependencyTestCase() {

  private val changedModules = mutableSetOf<String>()

  fun testModuleOrder() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val project = PsProjectImpl(myFixture.project)

    // settings.gradle does not list modules in the lexicographic order.
    assumeThat(project.parsedModel.projectSettingsModel?.modulePaths()?.toList(), equalTo(listOf(
      ":", ":app", ":lib", ":jav", ":nested1", ":nested2", ":nested1:deep", ":nested2:deep", ":nested2:trans:deep2", ":dyn_feature")))

    // Lexicographically ordered by gradlePath.
    // Includes not declared module ":nested2:trans".
    val modulesBeforeGradleModelsResolved = project.modules.map { it.gradlePath }
    assertThat<List<String?>>(modulesBeforeGradleModelsResolved, equalTo(listOf(
      ":app", ":dyn_feature", ":jav", ":lib", ":nested1", ":nested1:deep", ":nested2", ":nested2:deep", ":nested2:trans", ":nested2:trans:deep2")))

    project.testResolve()

    // Includes not declared module ":nested2:trans".
    val modulesAfterGradleModelsResolved = project.modules.map { it.gradlePath }
    assertThat<List<String?>>(modulesAfterGradleModelsResolved, equalTo(listOf(
      ":app", ":dyn_feature", ":jav", ":lib", ":nested1", ":nested1:deep", ":nested2", ":nested2:deep", ":nested2:trans", ":nested2:trans:deep2")))

    assertThat(project.findModuleByGradlePath(":nested2:trans")?.isDeclared, equalTo(false))
  }

  fun testRemoveModule() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))

    project.removeModule(gradlePath = ":nested2:deep")
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())

    project.applyChanges()  // applyChanges() discards resolved models.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())

    project.testResolve()  // A removed module should not reappear unless it is in the middle of a hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
  }

  fun testRemoveDynamicFeatureModule() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    assumeThat(project.findModuleByGradlePath(":dyn_feature")?.isDeclared, equalTo(true))
    assumeThat(
      project
        .findModuleByGradlePath(":app")
        ?.parsedModel
        ?.android()
        ?.dynamicFeatures()
        ?.getListValue(":dyn_feature")
        ?.getValue(STRING_TYPE),
      equalTo(":dyn_feature"))

    project.removeModule(gradlePath = ":dyn_feature")
    assertThat(project.findModuleByGradlePath(":dyn_feature")?.isDeclared, nullValue())

    assertThat(project.findModuleByGradlePath(":app")?.isModified, equalTo(true))

    project.applyChanges()  // applyChanges() discards resolved models.
    assertThat(project.findModuleByGradlePath(":dyn_feature")?.isDeclared, nullValue())
    assertThat(
      project
        .findModuleByGradlePath(":app")
        ?.parsedModel
        ?.android()
        ?.dynamicFeatures()
        ?.getListValue(":dyn_feature")
        ?.getValue(STRING_TYPE),
      nullValue())

    project.testResolve()  // A removed module should not reappear unless it is in the middle of a hierarchy.
    assertThat(project.findModuleByGradlePath(":dyn_feature")?.isDeclared, nullValue())
  }

  fun testRemoveMiddleModule() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    assumeThat(project.findModuleByGradlePath(":nested2")?.isDeclared, equalTo(true))

    project.removeModule(gradlePath = ":nested2")
    runWriteAction {
      project.ideProject.baseDir.findFileByRelativePath("/nested2/build.gradle")!!.delete("test")
    }
    assertThat(project.findModuleByGradlePath(":nested2")?.isDeclared, nullValue())

    project.applyChanges()  // applyChanges() discards resolved models.
    // A removed module should reappear because it is in the middle of the hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2")?.isDeclared, equalTo(false))

    project.testResolve()  // A removed module should still exist because it is in the middle of the hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2")?.isDeclared, equalTo(false))
  }

  fun testApplyRunAndReparse() {
    val newSuffix = "testApplyRunAndReparse"
    val newSuffix2 = "testApplyRunAndReparse2"
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

    // First remove :nested2:deep from the test project so that it can abe re-added later.
    run {
      val tempProjectInstance = PsProjectImpl(myFixture.project)
      assumeThat(tempProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
      tempProjectInstance.removeModule(gradlePath = ":nested2:deep")
      tempProjectInstance.applyChanges()
    }

    val project = PsProjectImpl(myFixture.project).also { it.testResolve() }
    // Subscribe to notifications from the very beginning to ensure that new modules are auto-subscribed.
    project.testSubscribeToNotifications()

    val appModule = project.findModuleByGradlePath(":app") as PsAndroidModule
    // Make sure the test module has been removed.
    assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
    // Make a random change.
    appModule.findBuildType("debug")!!.applicationIdSuffix = newSuffix.asParsed()

    // Make an independent change (add :nested2:deep back) to the configuration while in the "run" phase.
    project.applyRunAndReparse {
      val anotherProjectInstance = PsProjectImpl(myFixture.project)
      assumeThat(anotherProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
      // Any previously pending changes should be applied and visible at this point.
      assertThat(
        (anotherProjectInstance.findModuleByGradlePath(":app") as PsAndroidModule).findBuildType("debug")!!.applicationIdSuffix,
        equalTo(newSuffix.asParsed()))

      // Add :nested2:deep back to the project.
      anotherProjectInstance.parsedModel.projectSettingsModel!!.addModulePath(":nested2:deep")
      anotherProjectInstance.isModified = true
      anotherProjectInstance.applyChanges()

      // Different DSL models are independent.
      assumeThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, nullValue())
      // Validate that the module has re-appeared in the project.
      val validationProjectInstance = PsProjectImpl(myFixture.project)
      assumeThat(validationProjectInstance.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
      true
    }

    // The module collection should be refreshed and the removed module should disappear.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))
    project.testResolve()  // A removed module should not reappear unless it is in the middle of a hierarchy.
    assertThat(project.findModuleByGradlePath(":nested2:deep")?.isDeclared, equalTo(true))

    // Reset changed module set and assert that the change handler has been auto-subscribed to the notifications from the new module.
    changedModules.clear()
    val nested2DeepModule = project.findModuleByGradlePath(":nested2:deep") as PsAndroidModule
    nested2DeepModule.findBuildType("debug")!!.applicationIdSuffix = newSuffix2.asParsed()
    assertThat(changedModules.contains(":nested2:deep"), equalTo(true))
  }

  fun testApplyRunAndReparse_cancel() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)

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

  fun testAgpVersion() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    var project = PsProjectImpl(myFixture.project)

    assertThat(project.androidGradlePluginVersion, equalTo(BuildEnvironment.getInstance().gradlePluginVersion.asParsed()))

    project.androidGradlePluginVersion = "1.23".asParsed()
    project.applyChanges()

    project = PsProjectImpl(myFixture.project)
    assertThat(project.androidGradlePluginVersion, equalTo("1.23".asParsed()))
  }

  fun testAgpVersion_missing() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    var project = PsProjectImpl(myFixture.project)

    assertThat(project.androidGradlePluginVersion, equalTo(BuildEnvironment.getInstance().gradlePluginVersion.asParsed()))

    val existingAgpDependency =
      project
        .parsedModel
        .projectBuildModel
        ?.buildscript()
        ?.dependencies()
        ?.artifacts("classpath")
        ?.first { it.compactNotation().startsWith("com.android.tools.build:gradle:") }!!
    // Remove it and make sure the property can still be configured.
    project.parsedModel.projectBuildModel?.buildscript()?.dependencies()?.remove(existingAgpDependency)
    project.androidGradlePluginVersion = "1.23".asParsed()
    project.applyChanges()

    project = PsProjectImpl(myFixture.project)
    assertThat(project.androidGradlePluginVersion, equalTo("1.23".asParsed()))
  }

  fun testGradleVersion() {
    loadProject(TestProjectPaths.PSD_SAMPLE_GROOVY)
    var project = PsProjectImpl(myFixture.project)

    run {
      // Change file: to https: to workaround GradleWrapper not making changes to a local distribution.
      val wrapper = GradleWrapper.find(project.ideProject)!!
      val properties = wrapper.properties
      val property = properties.getProperty(DISTRIBUTION_URL_PROPERTY).orEmpty()
      properties.setProperty(DISTRIBUTION_URL_PROPERTY, property.replace("file:", "https:"))
      savePropertiesToFile(properties, wrapper.propertiesFilePath, null)
    }

    assertThat(
      project.gradleVersion,
      equalTo(GradleWrapper.find(project.ideProject)?.gradleVersion?.asParsed()))

    project.gradleVersion = "1.1".asParsed()
    project.applyChanges()

    project = PsProjectImpl(myFixture.project)
    assertThat(project.gradleVersion, equalTo("1.1".asParsed()))
    assertThat(GradleWrapper.find(project.ideProject)?.gradleVersion, equalTo("1.1"))
  }

  private fun PsProject.testSubscribeToNotifications() {
    this.onModuleChanged(testRootDisposable) { module -> changedModules.add(module.gradlePath.orEmpty()) }
  }
}