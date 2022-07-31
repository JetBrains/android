/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.ndk

import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet
import com.android.tools.idea.navigator.nodes.AndroidViewNodeProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class AndroidViewNodeNdkProvider : AndroidViewNodeProvider {
  override fun projectContainsExternalFile(project: Project, file: VirtualFile): Boolean {
    // Include files may be out-of-project so check for them.
    for (module in ModuleManager.getInstance(project).modules) {
      val ndkFacet = NdkFacet.getInstance(module!!)
      val ndkModuleModel = ndkFacet?.ndkModuleModel
      if (ndkModuleModel != null) {
        return containedByNativeNodes(project, ndkModuleModel, file)
      }
    }
    return false
  }
}