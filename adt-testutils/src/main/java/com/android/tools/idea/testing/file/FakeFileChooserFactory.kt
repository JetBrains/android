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
package com.android.tools.idea.testing.file

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDialog
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.FileTextField
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.replaceService
import java.awt.Component
import javax.swing.JTextField

/** Creates a fake [FileChooserFactory] such that the file choosers it creates would return the given files. */
fun Disposable.registerFakeFileChooserFactory(vararg toSelect: VirtualFile) {
  val factory = FakeFileChooserFactory(*toSelect)
  ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, this)
}

/** A fake [FileChooserFactory] such that the file choosers it creates would return the given files. */
class FakeFileChooserFactory(vararg val toSelect: VirtualFile) : FileChooserFactory() {

  override fun createFileChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): FileChooserDialog {
    return FileChooserDialog { _, _ -> toSelect }
  }

  override fun createPathChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): PathChooserDialog {
    return PathChooserDialog { _, callback -> callback.consume(toSelect.toList()) }
  }

  override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
    throw NotImplementedError("If you need this method, please implement it")
  }

  override fun createSaveFileDialog(descriptor: FileSaverDescriptor, parent: Component): FileSaverDialog {
    throw NotImplementedError("If you need this method, please implement it")
  }

  override fun createFileTextField(descriptor: FileChooserDescriptor, showHidden: Boolean, parent: Disposable?): FileTextField {
    throw NotImplementedError("If you need this method, please implement it")
  }

  override fun installFileCompletion(field: JTextField, descriptor: FileChooserDescriptor, showHidden: Boolean, parent: Disposable?) {
    throw NotImplementedError("If you need this method, please implement it")
  }
}
