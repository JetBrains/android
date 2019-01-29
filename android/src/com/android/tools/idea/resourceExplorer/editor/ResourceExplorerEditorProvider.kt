/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.editor

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ResourceExplorerEditorProvider : FileEditorProvider {
  override fun accept(project: Project, file: VirtualFile): Boolean {
    return StudioFlags.RESOURCE_MANAGER_ENABLED.get() && file is ResourceExplorerFile
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (file is ResourceExplorerFile) {
      return ResourceExplorerEditor(file.facet)
    }
    Logger.getInstance(this::class.java).error("Resource Explorer can only accept instances of ResourceExplorerFile.\n" +
        "Fallback to a simple text editor")
    return TextEditorProvider.getInstance().createEditor(project, file)
  }

  override fun getEditorTypeId(): String = "Resource Explorer"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

}