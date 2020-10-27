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
package com.android.tools.idea.projectsystem

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.TruthJUnit.assume
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.idea.util.application.runWriteAction

class IdeaSourceProviderImplTest : AndroidTestCase() {

  fun testManifestDirectory() {
    val manifestFile = myFixture.addFileToProject("src/main/AndroidManifest.xml", "manifest").virtualFile
    val manifestDirectory = manifestFile.parent
    val manifestFileUrl = manifestFile.url
    assume().that(VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl)).isNotNull()

    val ideaSourceProvider = NamedIdeaSourceProviderBuilder.create("name", manifestFileUrl).build()
    assertThat(ideaSourceProvider.manifestDirectories).isEqualTo(listOf(manifestDirectory))
  }

  fun testManifestDirectory_manifestDoesNotExist() {
    val manifestFile = myFixture.addFileToProject("src/main/AndroidManifest.xml", "manifest").virtualFile
    val manifestDirectory = manifestFile.parent
    val manifestFileUrl = manifestFile.url
    assume().that(VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl)).isNotNull()

    runWriteAction { manifestFile.delete(this) }
    assume().that(VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl)).isNull()

    val ideaSourceProvider = NamedIdeaSourceProviderBuilder.create("name", manifestFileUrl).build()
    assertThat(ideaSourceProvider.manifestDirectories).isEqualTo(listOf(manifestDirectory))
  }

  fun testContainsFile_manifestDirectory() {
    val manifestFile = myFixture.addFileToProject("src/main/AndroidManifest.xml", "manifest").virtualFile
    val manifestDirectory = manifestFile.parent
    val manifestFileUrl = manifestFile.url
    assume().that(VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl)).isNotNull()

    runWriteAction { manifestFile.delete(this) }
    assume().that(VirtualFileManager.getInstance().findFileByUrl(manifestFileUrl)).isNull()

    val ideaSourceProvider = NamedIdeaSourceProviderBuilder.create("name", manifestFileUrl).build()
    assertThat(ideaSourceProvider.containsFile(manifestDirectory)).isTrue()
  }
}