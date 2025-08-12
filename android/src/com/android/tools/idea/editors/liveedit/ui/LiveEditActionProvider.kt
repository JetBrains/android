/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.liveedit.ui

import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.CommonAndroidUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

class LiveEditActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project: Project = editor.project ?: return null

    if (!IdeSdks.getInstance().hasConfiguredAndroidSdk()) return null
    if (!CommonAndroidUtil.getInstance().isAndroidProject(project)) return null

    val file = FileDocumentManager.getInstance().getFile(editor.document)
    return if (project.isDefault || file == null || !file.exists()) null else LiveEditNotificationGroup()
  }
}