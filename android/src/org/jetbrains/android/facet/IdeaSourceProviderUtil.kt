/*
 * Copyright (C) 2013 The Android Open Source Project
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
@file:JvmName("IdeaSourceProviderUtil")

package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.vfs.VirtualFile

/**
 * Returns true if this SourceProvider has one or more source folders contained by (or equal to)
 * the given folder.
 */
fun IdeaSourceProvider.containsFile(file: VirtualFile): Boolean {
  if (manifestFiles.contains(file) || manifestDirectories.contains(file)) {
    return true
  }

  for (container in allSourceFolders) {
    // Don't do ancestry checking if this file doesn't exist
    if (!container.exists()) {
      continue
    }

    if (isAncestor(container, file, false /* allow them to be the same */)) {
      return true
    }
  }
  return false
}

fun <T: IdeaSourceProvider> Iterable<T>.findByFile(file: VirtualFile): T? = firstOrNull { it.containsFile(file) }

fun isTestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
  return SourceProviderManager.getInstance(facet).unitTestSources.containsFile(candidate) ||
         SourceProviderManager.getInstance(facet).androidTestSources.containsFile(candidate)
}

/** Returns true if the given candidate file is a manifest file in the given module  */
fun isManifestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
  return SourceProviderManager.getInstance(facet).sources.manifestFiles.contains(candidate)
}

/** Returns the manifest files in the given module  */
fun getManifestFiles(facet: AndroidFacet): List<VirtualFile> {
  return SourceProviderManager.getInstance(facet).sources.manifestFiles.toList()
}

val IdeaSourceProvider.allSourceFolders: Sequence<VirtualFile>
  get() =
    arrayOf(
      javaDirectories,
      resDirectories,
      aidlDirectories,
      renderscriptDirectories,
      assetsDirectories,
      jniDirectories,
      jniLibsDirectories
    )
      .asSequence()
      .flatten()


val IdeaSourceProvider.allSourceFolderUrls: Sequence<String>
  get() =
    arrayOf(
      javaDirectoryUrls,
      resDirectoryUrls,
      aidlDirectoryUrls,
      renderscriptDirectoryUrls,
      assetsDirectoryUrls,
      jniDirectoryUrls,
      jniLibsDirectoryUrls
    )
      .asSequence()
      .flatten()


