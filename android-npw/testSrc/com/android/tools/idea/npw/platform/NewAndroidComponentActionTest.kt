/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.npw.platform

import com.android.AndroidProjectTypes
import com.android.AndroidProjectTypes.PROJECT_TYPE_APP
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SdkVersionInfo
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.idea.gradle.model.ARTIFACT_NAME_MAIN
import com.android.tools.idea.gradle.model.IdeSourceProvider
import com.android.tools.idea.gradle.model.impl.IdeJUnitEngineInfoImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteTargetImpl
import com.android.tools.idea.gradle.model.impl.IdeTestSuiteVariantTargetImpl
import com.android.tools.idea.model.AndroidModel
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.npw.actions.NewAndroidComponentAction
import com.android.tools.idea.testartifacts.testsuite.TestSuiteTestUtils.createAssetsTestSuiteSource
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.JavaModuleModelBuilder
import com.android.tools.idea.testing.onEdt
import com.android.tools.idea.wizard.model.ModelWizard
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.TemplateConstraint
import com.android.tools.module.AndroidModuleInfo
import com.google.common.collect.ImmutableSet
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.ui.UIUtil
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.android.facet.AndroidFacet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NewAndroidComponentActionTest {
  private lateinit var mySelectedAndroidFacet: AndroidFacet
  private lateinit var myActionEvent: AnActionEvent

  @get:Rule
  val projectRule =
    AndroidProjectRule.withAndroidModels(
        JavaModuleModelBuilder.rootModuleBuilder,
        AndroidModuleModelBuilder(
          gradlePath = ":app",
          selectedBuildVariant = "debug",
          projectBuilder =
            AndroidProjectBuilder(
              namespace = { "com.example.app" },
              mainSourceProvider = {
                IdeSourceProvider(
                  name = ARTIFACT_NAME_MAIN,
                  folder = moduleBasePath,
                  manifestFile = "AndroidManifest.xml",
                  javaDirectories = listOf("src/java"),
                  kotlinDirectories = listOf("src/kotlin"),
                  resourcesDirectories = emptyList(),
                  aidlDirectories = emptyList(),
                  renderscriptDirectories = emptyList(),
                  resDirectories = listOf("res"),
                  assetsDirectories = emptyList(),
                  jniLibsDirectories = emptyList(),
                  mlModelsDirectories = emptyList(),
                  shadersDirectories = emptyList(),
                  customSourceDirectories = emptyList(),
                  baselineProfileDirectories = emptyList(),
                )
              },
              testSuites = {
                listOf(
                  IdeTestSuiteImpl(
                    name = "journeysTest",
                    sources =
                      listOf(
                        createAssetsTestSuiteSource(
                          testSuitePath = moduleBasePath.resolve("src/journeysTest")
                        )
                      ),
                    junitEngineInfo =
                      IdeJUnitEngineInfoImpl(includedEngines = setOf("journeys-test-engine")),
                    targetedVariants = listOf("debug"),
                  )
                )
              },
              testSuiteArtifactsStub = { variant ->
                when (variant) {
                  "debug" ->
                    listOf(
                      IdeTestSuiteVariantTargetImpl(
                        suiteName = "journeysTest",
                        targetedVariantName = "debug",
                        targets =
                          listOf(
                            IdeTestSuiteTargetImpl(
                              targetName = "connectedTest",
                              testTaskName = "journeysTestTaskName",
                              targetedDevices = emptyList(),
                            )
                          ),
                      )
                    )
                  else -> emptyList()
                }
              },
            ),
        ),
      )
      .onEdt()

  @Before
  fun setUp() {
    val file = projectRule.fixture.addFileToProject("app/src/Test.kt", "fun a() {}").virtualFile
    val module = ModuleUtilCore.findModuleForFile(file, projectRule.project)!!

    mySelectedAndroidFacet = setupFacetForModule(module)
    myActionEvent = createTestActionEventForFile(file, module)

    val presentation = Presentation()
    presentation.setEnabled(false)
  }

  private fun setupFacetForModule(module: Module): AndroidFacet {
    val facet = AndroidFacet.getInstance(module)!!
    AndroidModel.setForTests(facet, mock<AndroidModel>())

    val mockAndroidModuleInfo = mock<AndroidModuleInfo>()
    whenever(mockAndroidModuleInfo.minSdkVersion).thenReturn(AndroidVersion(1, null))
    whenever(mockAndroidModuleInfo.buildSdkVersion).thenReturn(AndroidVersion(1, null))

    StudioAndroidModuleInfo.setInstanceForTest(facet, mockAndroidModuleInfo)

    return facet
  }

  private fun createTestActionEventForFile(
    virtualFile: VirtualFile,
    module: Module,
  ): AnActionEvent {
    val dataContext =
      SimpleDataContext.builder()
        .add<Module>(PlatformCoreDataKeys.MODULE, module)
        .add<VirtualFile?>(PlatformCoreDataKeys.VIRTUAL_FILE, virtualFile)
        .build()
    return TestActionEvent.createTestEvent(dataContext)
  }

  @Test
  fun noAndroidModulePresentationShouldBeDisabled() {
    val dataContext = SimpleDataContext.builder().build()
    val actionEvent = TestActionEvent.createTestEvent(dataContext)
    NewAndroidComponentAction(Category.Other, "templateName", 0).update(actionEvent)

    assertThat(actionEvent.presentation.isVisible).isFalse()
    assertThat(actionEvent.presentation.isEnabled).isFalse()
    assertThat(actionEvent.presentation.text)
      .isEqualTo("templateName (Disabled - No Android module found)")
  }

  @Test
  fun nonInstantAppPresentationShouldBeEnabled() {
    NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun lowMinSdkApiPresentationShouldBeDisabled() {
    NewAndroidComponentAction(Category.Other, "templateName", SdkVersionInfo.HIGHEST_KNOWN_API + 1)
      .update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isFalse()
    assertThat(myActionEvent.presentation.text).contains("Requires minSdk")
  }

  @Test
  fun noAndroidXSupportPresentationShouldBeDisabled() {
    val constraints = listOf(TemplateConstraint.AndroidX)
    NewAndroidComponentAction(Category.Other, "templateName", 0, constraints).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isFalse()
    assertThat(myActionEvent.presentation.text).contains("Requires AndroidX support")
  }

  @Test
  fun appTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_APP

    NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun instantTypePresentationShouldBeDisabledForIapp() {
    mySelectedAndroidFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_INSTANTAPP

    NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isFalse()
  }

  @Test
  fun libraryTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_LIBRARY

    NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun testTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_TEST

    NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun featureTypePresentationShouldBeEnabledForIapp() {
    mySelectedAndroidFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_FEATURE

    NewAndroidComponentAction(Category.Other, "templateName", 0).update(myActionEvent)

    assertThat(myActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun verifyTemplateDialog() {
    mySelectedAndroidFacet.configuration.projectType = AndroidProjectTypes.PROJECT_TYPE_APP

    val modelWizardReference = AtomicReference<ModelWizard?>(null)
    val action =
      NewAndroidComponentAction(Category.Other, "Empty Activity", 0, ImmutableSet.of()) {
        modelWizard: ModelWizard?,
        _: String?,
        _: Project? ->
        modelWizardReference.set(modelWizard)
      }
    action.update(myActionEvent)
    assertThat(myActionEvent.presentation.isEnabled).isTrue()

    ApplicationManager.getApplication().invokeAndWait { action.actionPerformed(myActionEvent) }
    val modelWizard = checkNotNull(modelWizardReference.get())

    UIUtil.invokeAndWaitIfNeeded {
      modelWizard.contentPanel.setSize(640, 480)
      val fakeUi = FakeUi(modelWizard.contentPanel, 1.0, false, projectRule.testRootDisposable)
      try {
        fakeUi.layoutAndDispatchEvents()
      } catch (_: InterruptedException) {}

      // There should only be 3 compatible templates (_main_, debug, release) since the file is in
      // the "app/src" directory and the templates without source roots  are filtered out.
      val comboBox =
        fakeUi.findComponent(ComboBox::class.java) { combo: ComboBox<*> ->
          "ModuleTemplateCombo" == combo.getName()
        }
      assertNotNull(comboBox)
      assertThat(comboBox.itemCount).isEqualTo(3)
    }

    Disposer.dispose(modelWizard)
  }

  @Test
  fun verifyTemplateDialog_journeys() {
    val testSuiteFile =
      projectRule.fixture.addFileToProject("app/src/journeysTest/test.journey.xml", "").virtualFile
    val testSuiteModule = ModuleUtilCore.findModuleForFile(testSuiteFile, projectRule.project)!!

    val facet = setupFacetForModule(testSuiteModule)
    facet.configuration.projectType = PROJECT_TYPE_APP

    val testEvent = createTestActionEventForFile(testSuiteFile, testSuiteModule)

    val modelWizardReference = AtomicReference<ModelWizard>(null)
    val action =
      NewAndroidComponentAction(
        Category.Other,
        "Journey File", // Template name for Journey
        0,
        emptyList(),
        { modelWizard, _, _ -> modelWizardReference.set(modelWizard) },
      )

    action.update(testEvent)
    assertThat(testEvent.presentation.isEnabled).isTrue()

    ApplicationManager.getApplication().invokeAndWait { action.actionPerformed(testEvent) }

    assertTrue(modelWizardReference.get() != null)
    Disposer.dispose(modelWizardReference.get())
  }
}
