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
import com.android.tools.idea.npw.model.NewAndroidModuleModel
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.platform.AndroidVersionsInfo
import com.android.tools.idea.npw.platform.AndroidVersionsInfo.VersionItem
import com.android.tools.idea.observable.BatchInvoker
import com.android.tools.idea.observable.TestInvokeStrategy
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.wizard.template.FormFactor
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.TemplatesUsage.TemplateComponent.WizardUiContext.NEW_MODULE
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.RunsInEdt
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@RunsInEdt
class ConfigureAndroidModuleStepTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels(
    JavaModuleModelBuilder.rootModuleBuilder,
    AndroidModuleModelBuilder(":app", "debug", AndroidProjectBuilder()),
    JavaModuleModelBuilder(":libs", buildable = false),
    AndroidModuleModelBuilder(":libs:lib", "debug", AndroidProjectBuilder()),
    AndroidModuleModelBuilder(":libs:lib2", "debug", AndroidProjectBuilder())
  ).onEdt()

  @Before
  fun setUp() {
    myInvokeStrategy = TestInvokeStrategy()
    BatchInvoker.setOverrideStrategy(myInvokeStrategy)
  }

  @After
  fun tearDown() {
    BatchInvoker.clearOverrideStrategy()
  }

  /**
   * When adding two libraries to a project, the second library package name should have a distinct value from the first one
   * (com.example.mylib vs com.example.mylib2). See http://b/68177735 for more details.
   */
  @Test
  fun packageNameDependsOnModuleName() {
    val disposable = projectRule.fixture.projectDisposable
    val project = projectRule.project

    val basePackage = "com.example"
    val newModuleModel = NewAndroidModuleModel.fromExistingProject(project, ":", ProjectSyncInvoker.DefaultProjectSyncInvoker(), FormFactor.Mobile)
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, 25, basePackage, "Test Title")

    Disposer.register(disposable, newModuleModel)
    Disposer.register(disposable, configureAndroidModuleStep)
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
  @Test
  fun moduleNamesWithParent() {
    val disposable = projectRule.fixture.projectDisposable
    val project = projectRule.project

    val basePackage = "com.example"
    val parentName = ":libs"
    val newModuleModel = NewAndroidModuleModel.fromExistingProject(project, parentName, ProjectSyncInvoker.DefaultProjectSyncInvoker(), FormFactor.Mobile)
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, 25, basePackage, "Test Title")

    Disposer.register(disposable, newModuleModel)
    Disposer.register(disposable, configureAndroidModuleStep)
    myInvokeStrategy.updateAllSteps()

    fun verify(applicationName: String, packageName: String, moduleGradlePath: String) {
      newModuleModel.applicationName.set(applicationName)
      myInvokeStrategy.updateAllSteps()
      assertThat(newModuleModel.moduleName.get()).isEqualTo(moduleGradlePath)
      assertThat(newModuleModel.packageName.get()).isEqualTo(packageName)
    }

    verify("My Application", "com.example.myapplication", ":libs:myapplication")
    verify("Some what Longer LibName", "com.example.somewhatlongerlibname", ":libs:somewhatlongerlibname")
    // Verify unique name generation.
    verify("lib", "com.example.lib3", ":libs:lib3")
  }

  /**
   * When adding a parent to a module name (eg :parent:module_name), the package name should ignore the parent, but the module name don't.
   */
  @Test
  fun moduleNamesWithoutParent() {
    val disposable = projectRule.fixture.projectDisposable
    val project = projectRule.project

    val basePackage = "com.example"
    val parentName = ""
    val newModuleModel = NewAndroidModuleModel.fromExistingProject(project, parentName, ProjectSyncInvoker.DefaultProjectSyncInvoker(), FormFactor.Mobile)
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, 25, basePackage, "Test Title")

    Disposer.register(disposable, newModuleModel)
    Disposer.register(disposable, configureAndroidModuleStep)
    myInvokeStrategy.updateAllSteps()

    fun verify(applicationName: String, packageName: String, moduleGradlePath: String) {
      newModuleModel.applicationName.set(applicationName)
      myInvokeStrategy.updateAllSteps()
      assertThat(newModuleModel.moduleName.get()).isEqualTo(moduleGradlePath)
      assertThat(newModuleModel.packageName.get()).isEqualTo(packageName)
    }

    verify("My Application", "com.example.myapplication", "myapplication")
    verify("Some what Longer LibName", "com.example.somewhatlongerlibname", "somewhatlongerlibname")
    // Verify unique name generation: no conflict with :libs:lib.
    verify("lib", "com.example.lib", "lib")
  }

  /**
   * This tests assumes Project without androidx configuration.
   * Selecting API <28 should allow the use of "Go Forward", and API >=28 should stop the user from "Go Forward"
   */
  @Test
  fun selectAndroid_Q_onNonAndroidxProjects() {
    val disposable = projectRule.fixture.projectDisposable
    val project = projectRule.project

    val newModuleModel = NewAndroidModuleModel.fromExistingProject(project, ":", ProjectSyncInvoker.DefaultProjectSyncInvoker(), FormFactor.Mobile)
    val configureAndroidModuleStep = ConfigureAndroidModuleStep(newModuleModel, 25, "com.example", "Test Title")

    Disposer.register(disposable, newModuleModel)
    Disposer.register(disposable, configureAndroidModuleStep)
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