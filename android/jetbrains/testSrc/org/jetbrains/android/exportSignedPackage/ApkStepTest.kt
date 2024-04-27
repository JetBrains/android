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

import com.android.test.testutils.MockitoAwareLightPlatformTestCase
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.help.AndroidWebHelpProvider
import com.google.common.truth.Truth
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.android.exportSignedPackage.ApkStep.RUN_PROGUARD_PROPERTY
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetConfiguration
import org.jetbrains.android.facet.AndroidFacetProperties
import org.mockito.Mockito
import java.io.File
import org.mockito.Mockito.verify
import java.nio.file.Files.isSameFile
import kotlin.io.path.Path

class ApkStepTest : MockitoAwareLightPlatformTestCase() {
  private val myWizard = Mockito.mock(ExportSignedPackageWizard::class.java)

  override fun setUp() {
    super.setUp()
    whenever(myWizard.project).thenReturn(project)
    whenever(myWizard.targetType).thenReturn(ExportSignedPackageWizard.BUNDLE)
  }

  fun testGetHelpId() {
    val apkStep = ApkStep(myWizard)
    Truth.assertThat(apkStep.helpId).startsWith(AndroidWebHelpProvider.HELP_PREFIX + "studio/publish/app-signing")
  }

  fun testInitialDestinationNotSet() {
    val apkStep = ApkStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val projectPath = ModuleRootManager.getInstance(module).contentRoots[0].path + File.separator + module.name + ".apk"
    Truth.assertThat(apkStep.getInitialPath(properties, module)).isEqualTo(projectPath)
  }

  fun testInitialDestinationSet() {
    val apkStep = ApkStep(myWizard)
    val properties = PropertiesComponent.getInstance()
    val testPath = this.homePath + File.separator + "custom.apk"
    val apkPathPropertyName = apkStep.getApkPathPropertyName(module.name)
    properties.setValue(apkPathPropertyName, testPath)
    Truth.assertThat(apkStep.getInitialPath(properties, module)).isEqualTo(testPath)

    // Clean up properties after tests
    properties.setValue(apkPathPropertyName, null)
  }

  fun testApkDestinationEndsWhiteSpace() {
    val apkStep = ApkStep(myWizard)
    val properties = PropertiesComponent.getInstance(project)
    val destinationPath = "${this.homePath}${File.separator}Apk "
    val testFacet = Mockito.mock(AndroidFacet::class.java)
    whenever(testFacet.module).thenReturn(module)
    val facetConfiguration = Mockito.mock(AndroidFacetConfiguration::class.java)
    val facetProperties = AndroidFacetProperties()
    facetProperties.RUN_PROGUARD = false
    whenever(facetConfiguration.state).thenReturn(facetProperties)
    whenever(testFacet.configuration).thenReturn(facetConfiguration)
    whenever(myWizard.facet).thenReturn(testFacet)
    properties.setValue(apkStep.getApkPathPropertyName(module.name), destinationPath)
    properties.setValue(RUN_PROGUARD_PROPERTY, "false")

    val apkDir = java.io.File(destinationPath)
    if (!apkDir.exists()) {
      Truth.assertThat(apkDir.mkdirs()).isTrue()
    }

    apkStep._init(testFacet)
    val captor = argumentCaptor<String>()
    apkStep._commit(false)
    verify(myWizard).setApkPath(captor.capture())
    Truth.assertThat(isSameFile(Path(captor.value), Path(destinationPath))).isTrue()
  }
}