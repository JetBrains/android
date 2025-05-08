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

import com.android.tools.idea.gradle.jdk.GradleDefaultJvmCriteriaStore
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUiUtil.INSETS
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import org.gradle.internal.jvm.inspection.JvmVendor
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.plugins.gradle.service.execution.GradleDaemonJvmCriteria
import org.jetbrains.plugins.gradle.service.settings.GradleDaemonJvmCriteriaView
import org.jetbrains.plugins.gradle.settings.GradleSettings
import java.awt.GridBagConstraints

/**
 * An [AndroidDefaultGradleSystemSettingsControlBuilder] exposing settings allowing to configure
 * the default Daemon JVM criteria used for gradle builds/sync process on new created projects.
 */
class AndroidDefaultGradleJvmCriteriaControlBuilder(
  initialSettings: GradleSettings,
  private val disposable: Disposable
) : AndroidDefaultGradleSystemSettingsControlBuilder(initialSettings, disposable) {

  @VisibleForTesting
  lateinit var defaultGradleDaemonJvmCriteriaView: GradleDaemonJvmCriteriaView

  override fun fillUi(canvas: PaintAwarePanel, indentLevel: Int) {
    super.fillUi(canvas, indentLevel)
    addDefaultJvmCriteriaView(canvas, indentLevel)
  }

  override fun apply(settings: GradleSettings) {
    validate(settings)
    if (defaultGradleDaemonJvmCriteriaView.isModified) {
      defaultGradleDaemonJvmCriteriaView.applySelection()

      GradleDefaultJvmCriteriaStore.daemonJvmCriteria = defaultGradleDaemonJvmCriteriaView.initialCriteria
    }

    super.apply(settings)
  }

  override fun validate(settings: GradleSettings): Boolean {
    defaultGradleDaemonJvmCriteriaView.validateSelection()
    return super.validate(settings)
  }

  override fun isModified(): Boolean {
    if (defaultGradleDaemonJvmCriteriaView.isModified) return true
    return super.isModified()
  }

  private fun addDefaultJvmCriteriaView(canvas: PaintAwarePanel, indentLevel: Int) {
    val defaultGradleJvmCriteriaLabel = JBLabel(AndroidBundle.message("gradle.settings.jvm.criteria.default.component.label.text"))
    val insets = JBUI.insets(INSETS + INSETS * indentLevel, INSETS + INSETS * indentLevel, 0, INSETS)
    val gradleJvmLabelConstraint = GridBag().anchor(GridBagConstraints.NORTHWEST).weightx(0.0).insets(insets)
    canvas.add(defaultGradleJvmCriteriaLabel, gradleJvmLabelConstraint)

    defaultGradleDaemonJvmCriteriaView = createDefaultGradleJvmCriteriaView()
    defaultGradleJvmCriteriaLabel.labelFor = defaultGradleDaemonJvmCriteriaView
    canvas.add(defaultGradleDaemonJvmCriteriaView, ExternalSystemUiUtil.getFillLineConstraints(indentLevel))
  }

  private fun createDefaultGradleJvmCriteriaView() = GradleDaemonJvmCriteriaView(
    criteria = GradleDaemonJvmCriteria(IdeSdks.DEFAULT_JDK_VERSION.maxLanguageLevel.feature().toString(), null),
      versionsDropdownList = LanguageLevel.JDK_1_8.toJavaVersion().feature..LanguageLevel.HIGHEST.toJavaVersion().feature,
      vendorDropdownList =  JvmVendor.KnownJvmVendor.entries.filter { it != JvmVendor.KnownJvmVendor.UNKNOWN },
      displayAdvancedSettings = true,
      disposable = disposable
    )
}