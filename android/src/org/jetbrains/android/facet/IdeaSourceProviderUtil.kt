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
import com.intellij.util.containers.ContainerUtil.flatten

/**
 * Returns true if this SourceProvider has one or more source folders contained by (or equal to)
 * the given folder.
 */
fun containsFile(provider: IdeaSourceProvider, file: VirtualFile): Boolean {
  val srcDirectories = provider.allSourceFolders
  if (provider.manifestFile == file) {
    return true
  }

  for (container in srcDirectories) {
    // Check the flavor root directories
    val parent = container.parent
    if (parent != null && parent.isDirectory && parent == file) {
      return true
    }

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

fun isTestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
  return SourceProviderManager.getInstance(facet).currentTestSourceProviders.any { containsFile(it, candidate) }
}

/** Returns true if the given candidate file is a manifest file in the given module  */
fun isManifestFile(facet: AndroidFacet, candidate: VirtualFile): Boolean {
  return SourceProviderManager.getInstance(facet).currentSourceProviders.any { candidate == it.manifestFile }
}

/** Returns the manifest files in the given module  */
fun getManifestFiles(facet: AndroidFacet): List<VirtualFile> {
  return SourceProviderManager.getInstance(facet).currentSourceProviders.mapNotNull { it.manifestFile }
}

val IdeaSourceProvider.allSourceFolders: Collection<VirtualFile>
  get() =
    flatten(arrayOf(javaDirectories, resDirectories, aidlDirectories, renderscriptDirectories, assetsDirectories, jniDirectories,
                    jniLibsDirectories))

