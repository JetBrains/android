/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
private const val PREFERENCE_KEY = "layout.editor.validator.a11y"

/**
 * UI Controller for editor.
 */
interface LayoutScanningEditor{

  /**
   * Force refresh the design surface.
   */
  fun forceRefreshDesignSurface()
}

/**
 * Returns true if layout scanner should always be enabled. False otherwise.
 */
var alwaysEnableLayoutScanner
  get() = PropertiesComponent.getInstance().getBoolean(PREFERENCE_KEY, false)
  set(value) {
    if (value != alwaysEnableLayoutScanner) {
      PropertiesComponent.getInstance().setValue(PREFERENCE_KEY, value)

      for (project in ProjectManager.getInstance().openProjects) {
        val editorManager = FileEditorManager.getInstance(project)
        val editor = editorManager.selectedEditor

        if (editor is LayoutScanningEditor) {
          editor.forceRefreshDesignSurface()
        }
      }
      // TODO: b/158119426 Add metrics here
    }
  }
