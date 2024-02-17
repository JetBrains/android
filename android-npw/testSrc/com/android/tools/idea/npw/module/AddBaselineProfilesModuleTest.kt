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
package com.android.tools.idea.npw.module

import com.android.sdklib.SdkVersionInfo
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.npw.baselineprofiles.ConfigureBaselineProfilesModuleStep
import com.android.tools.idea.npw.baselineprofiles.NewBaselineProfilesModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(Parameterized::class)
class AddBaselineProfilesModuleTest(
  private val useGmdParam: Boolean,
) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "useGmdParam={0}")
    fun data(): List<Array<Any>> = listOf(
      arrayOf(true),
      arrayOf(false),
    )

    fun addNewBaselineProfilesModule(projectRule: AndroidGradleProjectRule, useGmdParam: Boolean, useGradleKtsParam: Boolean) {
      projectRule.load(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

      val project = projectRule.project
      val model = NewBaselineProfilesModuleModel(
        project = project,
        moduleParent = ":",
        projectSyncInvoker = emptyProjectSyncInvoker,
      ).apply {
        androidSdkInfo.value = AndroidVersionsInfo.VersionItem.fromStableVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API)
        targetModule.value = project.findAppModule()
        useGradleKts.set(useGradleKtsParam)
        useGmd.set(useGmdParam)
        agpVersion.set(GradleProjectSystemUtil.getAndroidGradleModelVersionInUse(project)!!)
      }

      model.handleFinished() // Generate module files

      projectRule.invokeTasks("assembleDebug").apply {
        buildError?.printStackTrace()
        Assert.assertTrue("Project didn't compile correctly", isBuildSuccessful)
      }
    }

    // Ignore project sync (to speed up test), if later we are going to perform a gradle build anyway.
    val emptyProjectSyncInvoker = object : ProjectSyncInvoker {
      override fun syncProject(project: Project) {}
    }
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Test
  fun addNewBaselineProfilesModuleTest() {
    addNewBaselineProfilesModule(projectRule, useGmdParam, false)
  }
}

class ConfigureBaselineProfilesModuleStepTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule().onEdt()

  private lateinit var disposable: Disposable

  @Before
  fun setup() {
    disposable = Disposer.newDisposable()
    BatchInvoker.setOverrideStrategy(BatchInvoker.INVOKE_IMMEDIATELY_STRATEGY)
  }

  @After
  fun tearDown() {
    BatchInvoker.clearOverrideStrategy()
    Disposer.dispose(disposable)
  }

  private suspend fun buildStepWithProject(targetProjectPath: String): Pair<ConfigureBaselineProfilesModuleStep, NewBaselineProfilesModuleModel> {
    return withContext(Dispatchers.EDT) {
      val model = NewBaselineProfilesModuleModel(
        project = projectRule.project,
        moduleParent = ":",
        projectSyncInvoker = AddBaselineProfilesModuleTest.emptyProjectSyncInvoker
      )
      projectRule.loadProject(targetProjectPath)
      ConfigureBaselineProfilesModuleStep(
        disposable = disposable,
        model = model
      ) to model
    }
  }

  @Test
  fun withAppProjectWithPhoneAndWearAndTvAndAutomotive() = runBlocking(Dispatchers.EDT) {
    val (step, model) = buildStepWithProject(TestProjectPaths.APP_WITH_WEAR_AND_TV_AND_AUTOMOTIVE)

    assertEquals(step.targetModuleCombo.selectedItem, projectRule.project.findAppModule())
    assertEquals(4, step.targetModuleCombo.itemCount)
    assertTrue(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())

    step.targetModuleCombo.selectedItem = projectRule.project.findModule("automotiveApp")
    assertFalse(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())

    step.targetModuleCombo.selectedItem = projectRule.project.findModule("wearApp")
    assertFalse(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())

    step.targetModuleCombo.selectedItem = projectRule.project.findModule("tvApp")
    assertFalse(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())

    step.targetModuleCombo.selectedItem = projectRule.project.findAppModule()
    assertTrue(step.useGmdCheck.isEnabled)
    // Can be true or false depending on GMD selection state.
    assertEquals(step.useGmdCheck.isSelected, model.useGmd.get())
  }

  @Test
  fun withLibProjectOnly() = runBlocking(Dispatchers.EDT) {
    val (step, model) = buildStepWithProject(TestProjectPaths.KOTLIN_LIB)

    assertNull(step.targetModuleCombo.selectedItem)
    assertEquals(0, step.targetModuleCombo.itemCount)
    assertFalse(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())
  }

  @Test
  fun withAppWithLibProject() = runBlocking(Dispatchers.EDT) {
    val (step, model) = buildStepWithProject(TestProjectPaths.ANDROIDX_WITH_LIB_MODULE)

    assertEquals(step.targetModuleCombo.selectedItem, projectRule.project.findAppModule())
    assertEquals(1, step.targetModuleCombo.itemCount)
    assertTrue(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())
  }

  @Test
  fun withWearAppProjectOnly() = runBlocking(Dispatchers.EDT) {
    val (step, model) = buildStepWithProject(TestProjectPaths.WEAR_WATCHFACE)

    assertEquals(step.targetModuleCombo.selectedItem, projectRule.project.findAppModule())
    assertEquals(1, step.targetModuleCombo.itemCount)
    assertFalse(step.useGmdCheck.isEnabled)
    assertFalse(step.useGmdCheck.isSelected)
    assertFalse(model.useGmd.get())
  }
}