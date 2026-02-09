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
package org.jetbrains.android.exportSignedPackage

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.model.IdeBasicVariant
import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import kotlin.io.path.Path
import kotlinx.coroutines.test.runTest
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard.TargetType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever

@RunsInEdt
class GradleSignStepTest {
  private val projectRule = ProjectRule()
  private val edtRule = EdtRule()

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(FlagRule(StudioFlags.SIGNED_BUILD_ADV_FEATURE, true)).around(projectRule).around(edtRule)

  @get:Rule val testName = TestName()

  private var myWizard: ExportSignedPackageWizard = mock()

  private val project
    get() = projectRule.project

  private val name
    get() = testName.methodName

  private val homePath
    get() = project.basePath!!

  @Before
  fun setUp() {
    whenever(myWizard.project).thenReturn(project)
    whenever(myWizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
    whenever(myWizard.disposable).thenReturn(projectRule.disposable)
  }

  @Test
  fun testGetHelpId() {
    val gradleSignStep = GradleSignStep(myWizard)
    assertThat(gradleSignStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }

  @Test
  fun testInitialDestinationApkNotSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = project.basePath
    // Set Bundle to confirm it is not the same
    val bundlePath = homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(projectPath)
  }

  @Test
  fun testInitialDestinationBundleNotSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = project.basePath
    // Set Apk to confirm it is not the same
    val apkPath = homePath + File.separator + "Apk"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(projectPath)
  }

  @Test
  fun testInitialDestinationApkSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = homePath + File.separator + "Apk"
    val bundlePath = homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(apkPath)
  }

  @Test
  fun testInitialDestinationBundleSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = homePath + File.separator + "Apk"
    val bundlePath = homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(bundlePath)
  }

  @Test
  fun testApkDestinationEndsWhiteSpace() {
    verifyDestinationEndsWhiteSpace(ExportSignedPackageWizard.APK)
  }

  @Test
  fun testBundleDestinationEndsWhiteSpace() {
    verifyDestinationEndsWhiteSpace(ExportSignedPackageWizard.BUNDLE)
  }

  @Test
  fun testAdiRegistered() = runTest {
    val client: AdiClient = mock()
    whenever(client.checkPackageRegistrationStatusAsync("com.example.app", null))
      .thenReturn(CompletableFuture.completedFuture(RegistrationState.REGISTERED))
    val gradleSignStep = GradleSignStep(myWizard, client)

    val properties = PropertiesComponent.getInstance(project)
    properties.setList(GradleSignStep.PROPERTY_BUILD_VARIANTS, listOf("release"))
    val bundlePath = homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)

    val testAndroidModel: GradleAndroidModelImpl = mock()
    whenever(testAndroidModel.moduleName).thenReturn(name)
    whenever(testAndroidModel.filteredVariantNames).thenReturn(listOf("release"))
    val variant: IdeBasicVariant = mock()
    whenever(variant.applicationId).thenReturn("com.example.app")
    whenever(testAndroidModel.findBasicVariantByName("release")).thenReturn(variant)

    gradleSignStep._init(testAndroidModel)

    UIUtil.dispatchAllInvocationEvents()

    val icon = getIconAt(gradleSignStep, 0)
    assertThat(icon).isEqualTo(StudioIcons.Common.SUCCESS_INLINE)
  }

  @Test
  fun testAdiNotRegistered() = runTest {
    val client: AdiClient = mock()
    whenever(client.checkPackageRegistrationStatusAsync("com.example.app", null))
      .thenReturn(CompletableFuture.completedFuture(RegistrationState.NOT_REGISTERED))
    val gradleSignStep = GradleSignStep(myWizard, client)

    val properties = PropertiesComponent.getInstance(project)
    properties.setList(GradleSignStep.PROPERTY_BUILD_VARIANTS, listOf("release"))
    val bundlePath = homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)

    val testAndroidModel: GradleAndroidModelImpl = mock()
    whenever(testAndroidModel.moduleName).thenReturn(name)
    whenever(testAndroidModel.filteredVariantNames).thenReturn(listOf("release"))
    val variant: IdeBasicVariant = mock()
    whenever(variant.applicationId).thenReturn("com.example.app")
    whenever(testAndroidModel.findBasicVariantByName("release")).thenReturn(variant)

    gradleSignStep._init(testAndroidModel)

    UIUtil.dispatchAllInvocationEvents()

    val icon = getIconAt(gradleSignStep, 0)
    assertThat(icon).isEqualTo(AllIcons.General.Note)
  }

  private fun getIconAt(step: GradleSignStep, index: Int): Icon? {
    val element = step.myBuildVariantsList.model.getElementAt(index) as GradleSignStep.VariantItem
    return element.icon
  }

  private fun verifyDestinationEndsWhiteSpace(targetType: TargetType) {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance(project)
    val destinationPath = "${homePath}${File.separator}$targetType "
    whenever(myWizard.targetType).thenReturn(targetType)
    val testAndroidModel: GradleAndroidModelImpl = mock()
    whenever(testAndroidModel.moduleName).thenReturn(name)
    whenever(testAndroidModel.filteredVariantNames).thenReturn(listOf("debug", "release"))
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, targetType), destinationPath)
    properties.setList(GradleSignStep.PROPERTY_BUILD_VARIANTS, listOf("release"))

    val apkDir = File(destinationPath)
    if (!apkDir.exists()) {
      assertThat(apkDir.mkdirs()).isTrue()
    }
    gradleSignStep._init(testAndroidModel)
    val captor = argumentCaptor<String>()
    gradleSignStep.commitForNext()
    verify(myWizard).setApkPath(captor.capture())
    assertThat(Files.isSameFile(Path(captor.firstValue), Path(destinationPath))).isTrue()
  }
}
