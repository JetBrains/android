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

import com.android.tools.idea.gradle.project.model.GradleAndroidModelImpl
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import java.io.File
import java.nio.file.Files
import kotlin.io.path.Path
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

  @get:Rule val ruleChain: RuleChain = RuleChain.outerRule(projectRule).around(edtRule)

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
    val bundlePath = this.homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(projectPath)
  }

  @Test
  fun testInitialDestinationBundleNotSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = project.basePath
    // Set Apk to confirm it is not the same
    val apkPath = this.homePath + File.separator + "Apk"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.BUNDLE)).isEqualTo(projectPath)
  }

  @Test
  fun testInitialDestinationApkSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = this.homePath + File.separator + "Apk"
    val bundlePath = this.homePath + File.separator + "Bundle"
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.APK), apkPath)
    properties.setValue(gradleSignStep.getApkPathPropertyName(name, ExportSignedPackageWizard.BUNDLE), bundlePath)
    assertThat(gradleSignStep.getInitialPath(properties, name, ExportSignedPackageWizard.APK)).isEqualTo(apkPath)
  }

  @Test
  fun testInitialDestinationBundleSet() {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val apkPath = this.homePath + File.separator + "Apk"
    val bundlePath = this.homePath + File.separator + "Bundle"
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

  private fun verifyDestinationEndsWhiteSpace(targetType: TargetType) {
    val gradleSignStep = GradleSignStep(myWizard)
    val properties = PropertiesComponent.getInstance(project)
    val destinationPath = "${this.homePath}${File.separator}$targetType "
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
