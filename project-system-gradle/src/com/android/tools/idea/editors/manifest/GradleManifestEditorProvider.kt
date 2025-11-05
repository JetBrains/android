/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.editors.manifest

import com.android.SdkConstants
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

class GradleManifestEditorProvider : AsyncFileEditorProvider, DumbAware {

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (SdkConstants.FN_ANDROID_MANIFEST_XML != file.name) return false
    val module = ModuleUtilCore.findModuleForFile(file, project) ?: return false
    return AndroidFacet.getInstance(module) != null
  }

  override fun acceptRequiresReadAction(): Boolean = true
  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    thisLogger().warn("GradleManifestEditorProvider.createEditor should not be called")
    return createEditorAsync(project, file).build() // Why do I need to implement this?
  }
  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder {
    val module: Module = ModuleUtilCore.findModuleForFile(file, project) ?: throw IllegalStateException("Unable to find module for file: ${file.path}")
    val facet: AndroidFacet = AndroidFacet.getInstance(module) ?: throw IllegalStateException("Unable to find Android Facet for module: ${module.name}")
    return ManifestEditorBuilder(facet, file)
  }

  override fun getEditorTypeId(): String = "android-manifest"

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR

  private class ManifestEditorBuilder(private val facet: AndroidFacet, private val file: VirtualFile) : AsyncFileEditorProvider.Builder() {
    override fun build(): FileEditor = ManifestEditor(facet, file)
  }
}
