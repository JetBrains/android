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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.gradle.structure.configurables.ui.properties.EditorExtensionAction
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditor
import com.android.tools.idea.gradle.structure.configurables.ui.properties.ModelPropertyEditorFactory
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.util.toVirtualFile
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import java.io.File
import javax.swing.Icon

class BrowseFilesExtension<T : Any, PropertyCoreT : ModelPropertyCore<T>>(
  private val project: PsProject,
  private val propertyContext: FileTypePropertyContext<T>
) : EditorExtensionAction<T, PropertyCoreT> {

  override val title: String = "Choose File"
  override val tooltip: String = "Choose File"
  override val icon: Icon = AllIcons.General.OpenDisk
  override val isMainAction: Boolean = false

  override fun isAvailableFor(property: PropertyCoreT, isPropertyContext: Boolean): Boolean = true

  override fun invoke(
    property: PropertyCoreT,
    editor: ModelPropertyEditor<T>,
    editorFactory: ModelPropertyEditorFactory<T, PropertyCoreT>) {

    val resolveRootVirtualFile = propertyContext.resolveRootDir
    val browseRootVirtualFile = propertyContext.browseRootDir?.toVirtualFile()

    fun normalizePath(path: String): String =
      if (resolveRootVirtualFile == null) path
      else File(path).relativeToOrSelf(resolveRootVirtualFile).path

    fun String.resolveAbsoluteFile() = let { path ->
      if (resolveRootVirtualFile == null) File(path).absoluteFile
      else resolveRootVirtualFile.resolve(File(path)).absoluteFile
    }

    editor.updateProperty()
    val descriptor = FileChooserDescriptor(true, false, false, true, false, false).apply {
      withTreeRootVisible(true)
      withShowHiddenFiles(false)
      propertyContext.filterPredicate?.let { predicate -> withFileFilter { predicate(File(it.path)) } }
      browseRootVirtualFile?.let { withRoots(it) }
    }
    val result =
      FileChooserFactory.getInstance().createFileChooser(descriptor, project.ideProject, editor.component)
        .choose(
          project.ideProject,
          property
            .getParsedValue()
            .value
            .maybeValue
            ?.let { propertyContext.format(it) }
            ?.resolveAbsoluteFile()
            ?.toVirtualFile()
        )
    val selectedPath = result.firstOrNull()?.path ?: return
    property.setParsedValue(propertyContext.parse(normalizePath(selectedPath)).value)
    editor.reload()
  }
}