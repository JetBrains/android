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
package com.android.tools.idea.gradle.project

import com.android.tools.adtui.swing.findAllDescendants
import com.android.tools.idea.gradle.jdk.GradleDefaultJvmCriteriaStore
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.openapi.options.ConfigurationException
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.impl.CollapsibleTitledSeparatorImpl
import com.intellij.util.ui.UIUtil
import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VendorItem
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView.VersionItem
import org.jetbrains.plugins.gradle.settings.GradleSettings

class AndroidDefaultGradleJvmCriteriaControlBuilderTest: LightPlatformTestCase() {

  private lateinit var controlBuilder: AndroidDefaultGradleJvmCriteriaControlBuilder
  private lateinit var gradleSettings: GradleSettings
  private lateinit var container: PaintAwarePanel

  override fun setUp() {
    super.setUp()
    container = PaintAwarePanel()
    gradleSettings = GradleSettings(project).apply {
      storeProjectFilesExternally = true
    }
    controlBuilder = AndroidDefaultGradleJvmCriteriaControlBuilder(gradleSettings, testRootDisposable)
    controlBuilder.fillUi(container, 0)
  }

  override fun tearDown() {
    GradleDefaultJvmCriteriaStore.daemonJvmCriteria = null
    controlBuilder.disposeUIResources()
    super.tearDown()
  }

  fun testComponentDefaults() {
    assertFalse(controlBuilder.isModified)
    assertTrue(controlBuilder.validate(gradleSettings))

    val criteriaTitle = container.findAllDescendants(JBLabel::class.java) { it.text == "Default Gradle JVM criteria:" }.single()
    assertTrue(criteriaTitle.isVisible)

    val criteriaView = container.findAllDescendants(GradleDaemonJvmCriteriaView::class.java).single()
    assertNotNull(criteriaView)
    assertTrue(criteriaView.isVisible)
    criteriaView.assertVersionDropdownItems()
    criteriaView.assertVendorDropdownItems()
    criteriaView.assertAdvancedSettingsIsVisible(true)
  }

  fun testValidationOfValidInput() {
    controlBuilder.defaultGradleDaemonJvmCriteriaView.selectedVersion = VersionItem.Default(11)

    assertTrue(controlBuilder.validate(gradleSettings))
  }

  fun testValidationOfInvalidInput() {
    controlBuilder.defaultGradleDaemonJvmCriteriaView.selectedVersion = VersionItem.Custom("invalid")

    assertThrows(ConfigurationException::class.java) {
      controlBuilder.validate(gradleSettings)
    }
  }

  fun testApplyInvalidInput() {
    controlBuilder.defaultGradleDaemonJvmCriteriaView.selectedVersion = VersionItem.Custom("invalid")

    assertThrows(ConfigurationException::class.java) {
      controlBuilder.apply(gradleSettings)
    }
  }

  fun testApplyWithoutModification() {
    controlBuilder.apply(gradleSettings)

    assertNull(GradleDefaultJvmCriteriaStore.daemonJvmCriteria)
  }

  fun testApplyValidInputIsStoredOnPropertyComponents() {
    controlBuilder.defaultGradleDaemonJvmCriteriaView.selectedVersion = VersionItem.Default(17)
    controlBuilder.defaultGradleDaemonJvmCriteriaView.selectedVendor = VendorItem.Default(JvmVendor.KnownJvmVendor.AZUL)

    controlBuilder.apply(gradleSettings)

    assertNotNull(GradleDefaultJvmCriteriaStore.daemonJvmCriteria)
    assertEquals("17", GradleDefaultJvmCriteriaStore.daemonJvmCriteria?.version)
    assertEquals("azul systems", GradleDefaultJvmCriteriaStore.daemonJvmCriteria?.vendor?.rawVendor)
  }

  private fun GradleDaemonJvmCriteriaView.assertAdvancedSettingsIsVisible(isVisible: Boolean) {
    val advancedSettingsComponent = UIUtil.findComponentOfType(this, CollapsibleTitledSeparatorImpl::class.java)
    assertEquals("Advanced Settings", advancedSettingsComponent?.text)
    assertEquals(isVisible, advancedSettingsComponent?.isVisible)
  }

  private fun GradleDaemonJvmCriteriaView.assertVersionDropdownItems() {
    val expectedVersionList = LanguageLevel.HIGHEST.toJavaVersion().feature.downTo(8)
    expectedVersionList.forEachIndexed { index, expectedVersion ->
      val actualVersion = when (val versionItem = versionModel.getElementAt(index)) {
        is VersionItem.Default -> versionItem.version.toString()
        is VersionItem.Custom -> throw AssertionError("Unexpected custom version item: " + versionItem.value)
      }
      assertEquals(expectedVersion.toString(), actualVersion)
    }
  }

  private fun GradleDaemonJvmCriteriaView.assertVendorDropdownItems() {
    val expectedVendorList = listOf("<ANY_VENDOR>", "<CUSTOM_VENDOR>", "ADOPTIUM", "ADOPTOPENJDK", "AMAZON", "APPLE", "AZUL", "BELLSOFT", "GRAAL_VM",
                                    "HEWLETT_PACKARD", "IBM", "JETBRAINS", "MICROSOFT", "ORACLE", "SAP", "TENCENT")
    expectedVendorList.forEachIndexed { index, expectedVendor ->
      val actualVendor = when (val vendorItem = vendorModel.getElementAt(index)) {
        VendorItem.Any -> "<ANY_VENDOR>"
        VendorItem.SelectCustom -> "<CUSTOM_VENDOR>"
        is VendorItem.Default -> vendorItem.vendor.name
        is VendorItem.Custom -> throw AssertionError("Unexpected custom vendor item: " + vendorItem.value)
      }
      assertEquals(expectedVendor, actualVendor)
    }
  }
}