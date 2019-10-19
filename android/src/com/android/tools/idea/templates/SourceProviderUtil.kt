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
package com.android.tools.idea.templates

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.IdeaSourceProvider
import org.jetbrains.android.facet.SourceProviderManager
import org.jetbrains.android.facet.allSourceFolders
import org.jetbrains.android.facet.containsFile

/**
 * Returns a list of all source providers that contain, or are contained by, the given file.
 * For example, with the file structure:
 *
 * ```
 * src
 *   main
 *     aidl
 *       myfile.aidl
 *   free
 *     aidl
 *       myoverlay.aidl
 * ```
 *
 * With target file == "myoverlay.aidl" the returned list would be ['free'], but if target file == "src",
 * the returned list would be ['main', 'free'] since both of those source providers have source folders which
 * are descendants of "src."
 */
fun getSourceProvidersForFile(
  facet: AndroidFacet,
  targetFolder: VirtualFile?,
  defaultSourceProvider: IdeaSourceProvider?
): List<IdeaSourceProvider> {
  val sourceProviderList =
    if (targetFolder != null) {
      // Add source providers that contain the file (if any) and any that have files under the given folder
      SourceProviderManager.getInstance(facet).allSourceProviders
        .filter { provider ->
          containsFile(provider, targetFolder) || provider.isContainedBy(targetFolder)
        }
        .takeUnless { it.isEmpty() }
    }
    else null

  return sourceProviderList ?: listOfNotNull(defaultSourceProvider)
}

@VisibleForTesting
fun IdeaSourceProvider.isContainedBy(targetFolder: VirtualFile): Boolean {
  return allSourceFolders.any { VfsUtilCore.isAncestor(targetFolder, it, false) }
}

