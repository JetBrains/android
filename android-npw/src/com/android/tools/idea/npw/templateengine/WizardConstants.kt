/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine

import androidx.compose.ui.unit.dp
import com.intellij.util.ui.JBUI
import java.awt.Dimension

object WizardConstants {
  object TemplateNames {
    const val EMPTY_ACTIVITY = "empty-activity"
  }

  // UI Titles & Buttons
  const val DIALOG_TITLE = "New Project Wizard (New Engine)"
  const val FINISH_BUTTON_TEXT = "Finish"
  const val TASK_TITLE = "Creating Project"

  // SDK Template Payload Relative Subpath
  const val RELATIVE_PATH_TEMPLATES_ZIP = "build/templates/android-project-templates.zip"

  // Project Parameter Defaults
  const val DEFAULT_APPLICATION_NAME = "My Application"
  const val DEFAULT_PACKAGE_PREFIX = "com.example."
  const val DEFAULT_MIN_SDK = "24"
  const val DEFAULT_COMPILE_SDK = "36"

  // Engine Replacement Parameter Keys
  const val KEY_SDK_PATH = "sdkPath"
  const val KEY_NAME = "name"
  const val KEY_APPLICATION_ID = "applicationId"
  const val KEY_NAMESPACE = "namespace"
  const val KEY_MIN_SDK = "minSdk"
  const val KEY_COMPILE_SDK = "compileSdk"
  const val KEY_COMPILE_SDK_MINOR = "compileSdkMinor"

  // Template Tags & Category Titles
  const val TAG_WEAR = "wear"
  const val TAG_TV = "tv"
  const val TAG_CAR = "car"

  val templateCellSize = 192.dp

  // Dialog Dimensions
  val PREFERRED_DIALOG_SIZE: Dimension
    get() = JBUI.size(900, 650)

  val MINIMUM_DIALOG_SIZE: Dimension
    get() = JBUI.size(600, 350)
}
