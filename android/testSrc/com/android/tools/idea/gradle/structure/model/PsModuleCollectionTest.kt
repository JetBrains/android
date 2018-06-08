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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.model.android.DependencyTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestCase.synchronizeTempDirVfs
import java.io.File

/**
 * Tests for [PsModuleCollection].
 */
class PsModuleCollectionTest : DependencyTestCase() {

  private var patchProject: ((VirtualFile) -> Unit)? = null

  override fun prepareProjectForImport(srcRoot: File, projectRoot: File): File {
    val result = super.prepareProjectForImport(srcRoot, projectRoot)
    synchronizeTempDirVfs(project.baseDir)
    patchProject?.run {
      ApplicationManager.getApplication().runWriteAction {
        invoke(project.baseDir)
      }
      ApplicationManager.getApplication().saveAll()
    }
    return result
  }

  fun loadProject(path: String, patch: (VirtualFile) -> Unit) {
    patchProject = patch
    return try {
      super.loadProject(path)
    }
    finally {
      patchProject = null
    }
  }

  fun testNotSyncedModules() {
    loadProject(TestProjectPaths.PSD_SAMPLE) {
      it.findFileByRelativePath("settings.gradle")!!.let {
        it.setBinaryContent("include ':app', ':lib' ".toByteArray(it.charset))
      }
    }

    val resolvedProject = myFixture.project
    var project = PsProjectImpl(resolvedProject)
    assertThat(project.findModuleByName("jav") ).isNull()

    // Edit the settings file, but do not sync.
    val virtualFile = this.project.baseDir.findFileByRelativePath("settings.gradle")!!
    myFixture.openFileInEditor(virtualFile)
    myFixture.editor.selectionModel.selectLineAtCaret()
    myFixture.type("include ':app', ':lib', ':jav' ")
    PsiDocumentManager.getInstance(this.project).commitAllDocuments()

    project = PsProjectImpl(resolvedProject)

    assertThat(moduleWithSyncedModel(project, "app").projectType).isEqualTo(PsModuleType.ANDROID_APP)
    assertThat(moduleWithSyncedModel(project, "lib").projectType).isEqualTo(PsModuleType.ANDROID_LIBRARY)
    assertThat(moduleWithSyncedModel(project, "jav").projectType).isEqualTo(PsModuleType.JAVA)
  }
}

private fun moduleWithSyncedModel(project: PsProject, name: String): PsModule = project.findModuleByName(name) as PsModule
