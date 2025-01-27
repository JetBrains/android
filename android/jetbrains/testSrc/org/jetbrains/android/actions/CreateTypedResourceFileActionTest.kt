/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.actions

import com.android.ide.common.repository.GoogleMavenArtifactId
import com.android.resources.ResourceFolderType
import com.android.tools.idea.projectsystem.DependencyType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Path
import kotlin.jvm.java
import kotlin.test.assertNotNull
import org.jetbrains.android.actions.CreateTypedResourceFileAction.Companion.doIsAvailable
import org.jetbrains.android.actions.CreateTypedResourceFileAction.Companion.getDefaultRootTagByResourceType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class CreateTypedResourceFileActionTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }
  val module by lazy { projectRule.projectRule.module }

  @Test
  fun doIsAvailableForTypedResourceDirectory() {
    val dataContext = SimpleDataContext.builder()
    for (folderType in ResourceFolderType.entries) {
      val filePath = "res/" + folderType.getName() + "/my_" + folderType.getName() + ".xml"
      dataContext.add(CommonDataKeys.PSI_ELEMENT, fixture.addFileToProject(filePath, ""))
      assertWithMessage("Failed for ${folderType.getName()}")
        .that(doIsAvailable(dataContext.build(), folderType))
        .isTrue()
    }

    val resDir = fixture.findFileInTempDir("res")
    val psiResDir = fixture.psiManager.findDirectory(resDir)
    dataContext.add(CommonDataKeys.PSI_ELEMENT, psiResDir)
    // Should fail when the directory is not a type specific resource directory (e.g: res/drawable).
    assertThat(doIsAvailable(dataContext.build(), ResourceFolderType.DRAWABLE)).isFalse()
  }

  @Test
  fun addPreferencesScreenAndroidxPreferenceLibraryHandling() {
    // First check without having androidx.preference as a library
    assertThat(getDefaultRootTagByResourceType(module, ResourceFolderType.XML))
      .isEqualTo("PreferenceScreen")

    module
      .getModuleSystem()
      .registerDependency(GoogleMavenArtifactId.ANDROIDX_PREFERENCE.getCoordinate("+"), DependencyType.IMPLEMENTATION)

    // Now with the dependency, the handler should return "androidx.preference.PreferenceScreen"
    assertThat(getDefaultRootTagByResourceType(module, ResourceFolderType.XML))
      .isEqualTo("androidx.preference.PreferenceScreen")
  }

  @RunsInEdt
  @Test
  fun doCreateAndNavigate_rawResource_navigateTrue() {
    val filename = "new_resource_file.json"
    val action =
      CreateTypedResourceFileAction("Unimportant string", ResourceFolderType.RAW, false, false)
    val virtualFileDir =
      assertNotNull(
        VirtualFileManager.getInstance().findFileByNioPath(Path.of(fixture.tempDirPath))
      )
    val psiDirectory = assertNotNull(PsiManager.getInstance(project).findDirectory(virtualFileDir))
    val elements =
      action.doCreateAndNavigate(
        filename,
        psiDirectory,
        rootTagName = "",
        chooseTagName = false,
        navigate = true,
      )
    assertThat(elements).hasLength(1)
    val addedFile = elements.single()
    assertThat(addedFile).isInstanceOf(PsiFile::class.java)
    addedFile as PsiFile
    val editor = assertNotNull(FileEditorManager.getInstance(project).selectedTextEditor)
    assertThat(editor.virtualFile).isEqualTo(addedFile.virtualFile)
    assertThat(editor.virtualFile.name).isEqualTo(filename)
    assertThat(editor.document.text).isEmpty()
  }

  @RunsInEdt
  @Test
  fun doCreateAndNavigate_rawResource_navigateFalse() {
    val filename = "new_resource_file.json"
    val action =
      CreateTypedResourceFileAction("Unimportant string", ResourceFolderType.RAW, false, false)
    val virtualFileDir =
      assertNotNull(
        VirtualFileManager.getInstance().findFileByNioPath(Path.of(fixture.tempDirPath))
      )
    val psiDirectory = assertNotNull(PsiManager.getInstance(project).findDirectory(virtualFileDir))
    val elements =
      action.doCreateAndNavigate(
        filename,
        psiDirectory,
        rootTagName = "",
        chooseTagName = false,
        navigate = false,
      )
    assertThat(elements).hasLength(1)
    val addedFile = elements.single()
    assertThat(addedFile).isInstanceOf(PsiFile::class.java)
    addedFile as PsiFile
    assertThat(addedFile.virtualFile.name).isEqualTo(filename)
    assertThat(FileEditorManager.getInstance(project).selectedTextEditor).isNull()
  }
}
