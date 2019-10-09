/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.sdklib.internal.androidTarget.MockPlatformTarget
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate.createDummyTemplate
import com.android.tools.idea.npw.FormFactor
import com.android.tools.idea.npw.model.NewModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ConfigureAndroidModuleStepTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    myInvokeStrategy = TestInvokeStrategy()
    BatchInvoker.setOverrideStrategy(myInvokeStrategy)
  }

  public override fun tearDown() {
    try {
      BatchInvoker.clearOverrideStrategy()
    }
    finally {
      super.tearDown()
    }
  }

  /**
   * When adding two libraries to a project, the second library package name should have a distinct value from the first one
   * (com.example.mylib vs com.example.mylib2). See http://b/68177735 for more details.
   */
  fun testPackageNameDependsOnModuleName() {
    val project = mock(Project::class.java)
    val moduleManager = mock(ModuleManager::class.java)

    `when`(project.getComponent(ModuleManager::class.java)).thenReturn(moduleManager)
    `when`<String>(project.basePath).thenReturn("/")
    `when`(moduleManager.modules).thenReturn(Module.EMPTY_ARRAY)

    val basePackage = "com.example"
    val newModuleModel = NewModuleModel(project, null, ProjectSyncInvoker.DefaultProjectSyncInvoker(), createDummyTemplate())
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, FormFactor.MOBILE, 25, basePackage, "Test Title")

    Disposer.register(testRootDisposable, newModuleModel)
    Disposer.register(testRootDisposable, configureAndroidModuleStep)
    myInvokeStrategy.updateAllSteps()

    fun assertPackageNameIsCorrectAfterSettingModuleName(moduleName: String) {
      newModuleModel.moduleName.set(moduleName)
      myInvokeStrategy.updateAllSteps()
      assertThat(newModuleModel.packageName.get()).isEqualTo("$basePackage.${moduleName.toLowerCase()}")
    }

    listOf("myLib1", "somewhatLongerLibName", "lib").forEach { assertPackageNameIsCorrectAfterSettingModuleName(it) }
  }

  /**
   * When adding a parent to a module name (eg :parent:module_name), the package name should ignore the parent, but the module name don't.
   */
  fun testModuleNamesWithParent() {
    val project = mock(Project::class.java)
    val moduleManager = mock(ModuleManager::class.java)

    `when`(project.getComponent(ModuleManager::class.java)).thenReturn(moduleManager)
    `when`<String>(project.basePath).thenReturn("/")
    `when`(moduleManager.modules).thenReturn(Module.EMPTY_ARRAY)

    val basePackage = "com.example"
    val parentName = "parent"
    val newModuleModel = NewModuleModel(project, parentName, ProjectSyncInvoker.DefaultProjectSyncInvoker(), createDummyTemplate())
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, FormFactor.MOBILE, 25, basePackage, "Test Title")

    Disposer.register(testRootDisposable, newModuleModel)
    Disposer.register(testRootDisposable, configureAndroidModuleStep)
    myInvokeStrategy.updateAllSteps()

    fun assertModuleNameIsCorrectAfterSettingApplicationName(applicationName: String) {
      newModuleModel.applicationName.set(applicationName)
      myInvokeStrategy.updateAllSteps()
      val moduleNameWithoutParent = applicationName.replace(" ", "").toLowerCase()
      assertThat(newModuleModel.moduleName.get()).isEqualTo(":$parentName:$moduleNameWithoutParent")
      assertThat(newModuleModel.packageName.get()).isEqualTo("$basePackage.$moduleNameWithoutParent")
    }

    listOf("My Application", "Some what Longer LibName", "lib").forEach { assertModuleNameIsCorrectAfterSettingApplicationName(it) }
  }

  /**
   * This tests assumes Project without androidx configuration.
   * Selecting API <28 should allow the use of "Go Forward", and API >=28 should stop the user from "Go Forward"
   */
  fun testSelectAndroid_Q_onNonAndroidxProjects() {
    val newModuleModel = NewModuleModel(project, null, ProjectSyncInvoker.DefaultProjectSyncInvoker(), createDummyTemplate())
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, FormFactor.MOBILE, 25, "com.example", "Test Title")

    Disposer.register(testRootDisposable, newModuleModel)
    Disposer.register(testRootDisposable, configureAndroidModuleStep)
    myInvokeStrategy.updateAllSteps()

    val androidTarget_P = createMockAndroidVersion(VersionCodes.P)
    newModuleModel.androidSdkInfo.value = androidTarget_P
    myInvokeStrategy.updateAllSteps()
    assertThat(configureAndroidModuleStep.canGoForward().get()).isTrue()

    val androidTarget_Q = createMockAndroidVersion(VersionCodes.Q)
    newModuleModel.androidSdkInfo.value = androidTarget_Q
    myInvokeStrategy.updateAllSteps()
    assertThat(configureAndroidModuleStep.canGoForward().get()).isFalse()
  }

  companion object {
    private lateinit var myInvokeStrategy: TestInvokeStrategy

    private fun createMockAndroidVersion(apiLevel: Int): VersionItem =
      mock(AndroidVersionsInfo::class.java).VersionItem(object : MockPlatformTarget(apiLevel, 0) {
        override fun getVersion(): AndroidVersion = AndroidVersion(apiLevel - 1, "TEST_PLATFORM")
      })
  }
}