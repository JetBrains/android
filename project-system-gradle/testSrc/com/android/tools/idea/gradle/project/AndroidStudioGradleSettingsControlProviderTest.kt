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
import com.intellij.openapi.externalSystem.util.PaintAwarePanel
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.plugins.gradle.settings.GradleSettings

class AndroidStudioGradleSettingsControlProviderTest: LightPlatformTestCase() {

  fun testStoreExternallyCheckBoxIsNotDisplayed() {
    val gradleSettings = GradleSettings(project)
    val controlBuilder = AndroidStudioGradleSettingsControlProvider().getSystemSettingsControlBuilder(gradleSettings)

    val container = PaintAwarePanel()
    controlBuilder.fillUi(container, 0)
    val generateImlFiles = container.findAllDescendants(JBCheckBox::class.java) { it.text == "Generate *.iml files for modules imported from Gradle" }
    assertEmpty(generateImlFiles.toList())
  }
}