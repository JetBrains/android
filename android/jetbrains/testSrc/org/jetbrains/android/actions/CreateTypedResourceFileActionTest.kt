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
import com.android.testutils.TestUtils
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.getSyncManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.util.toIoFile
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.android.actions.CreateTypedResourceFileAction.Companion.doIsAvailable
import org.jetbrains.android.actions.CreateTypedResourceFileAction.Companion.getDefaultRootTagByResourceType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@RunWith(JUnit4::class)
class CreateTypedResourceFileActionTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()
  val project by lazy { projectRule.project }
  val fixture by lazy { projectRule.fixture }
  val module by lazy { projectRule.module }

  @Test
  fun doIsAvailableForTypedResourceDirectory() {
    val dataContext = SimpleDataContext.builder()
    for (folderType in ResourceFolderType.entries) {
      val filePath = "res/" + folderType.name + "/my_" + folderType.name + ".xml"
      dataContext.add(CommonDataKeys.PSI_ELEMENT, fixture.addFileToProject(filePath, "").parent)
      assertWithMessage("Failed for ${folderType.name}").that(doIsAvailable(dataContext.build(), folderType.name)).isTrue()
    }

    val resDir = fixture.findFileInTempDir("res")
    val psiResDir = fixture.psiManager.findDirectory(resDir)
    dataContext.add(CommonDataKeys.PSI_ELEMENT, psiResDir)
    // Should fail when the directory is not a type specific resource directory (e.g: res/drawable).
    assertThat(doIsAvailable(dataContext.build(), ResourceFolderType.DRAWABLE.name)).isFalse()
  }

  @Test
  fun addPreferencesScreenAndroidxPreferenceLibraryHandling() {
    // First check without having androidx.preference as a library
    assertThat(getDefaultRootTagByResourceType(module, ResourceFolderType.XML)).isEqualTo("PreferenceScreen")

    module.getModuleSystem().registerDependency(GoogleMavenArtifactId.ANDROIDX_PREFERENCE.getCoordinate("+"))

    // Now with the dependency, the handler should return "androidx.preference.PreferenceScreen"
    assertThat(getDefaultRootTagByResourceType(module, ResourceFolderType.XML)).isEqualTo("androidx.preference.PreferenceScreen")
  }
}