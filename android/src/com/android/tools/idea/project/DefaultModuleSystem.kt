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
package com.android.tools.idea.project

import com.android.SdkConstants
import com.android.ide.common.repository.GradleCoordinate
import com.android.tools.idea.model.MergedManifest
import com.android.tools.idea.projectsystem.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet

class DefaultModuleSystem(val module: Module) : AndroidModuleSystem {
  override fun registerDependency(coordinate: GradleCoordinate) {}

  override fun getRegisteredDependency(coordinate: GradleCoordinate): GradleCoordinate? = null

  override fun getResolvedDependency(coordinate: GradleCoordinate): GradleCoordinate? {
    // TODO(b/79883422): Replace the following code with the correct logic for detecting .aar dependencies.
    // The following if / else if chain maintains previous support for supportlib and appcompat until
    // we can determine it's safe to take away.
    if (SdkConstants.SUPPORT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
      val entries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in entries) {
        if (orderEntry is LibraryOrderEntry) {
          val classes = orderEntry.getRootFiles(OrderRootType.CLASSES)
          for (file in classes) {
            if (file.name == "android-support-v4.jar") {
              return GoogleMavenArtifactId.SUPPORT_V4.getCoordinate("+")
            }
          }
        }
      }
    }
    else if (SdkConstants.APPCOMPAT_LIB_ARTIFACT == "${coordinate.groupId}:${coordinate.artifactId}") {
      val entries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in entries) {
        if (orderEntry is ModuleOrderEntry) {
          val moduleForEntry = orderEntry.module
          if (moduleForEntry == null || moduleForEntry == module) {
            continue
          }
          AndroidFacet.getInstance(moduleForEntry) ?: continue
          val manifestInfo = MergedManifest.get(moduleForEntry)
          if ("android.support.v7.appcompat" == manifestInfo.`package`) {
            return GoogleMavenArtifactId.APP_COMPAT_V7.getCoordinate("+")
          }
        }
      }
    }

    return null
  }

  override fun getModuleTemplates(targetDirectory: VirtualFile?): List<NamedModuleTemplate> {
    return emptyList()
  }

  override fun canGeneratePngFromVectorGraphics(): CapabilityStatus {
    return CapabilityNotSupported()
  }

  override fun getInstantRunSupport(): CapabilityStatus {
    return CapabilityNotSupported()
  }
}