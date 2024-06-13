/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.importer

import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.android.facet.AndroidFacet
import java.io.File

class CreateDefaultResDirectoryGradleToken : CreateDefaultResDirectoryToken<GradleProjectSystem>, GradleToken {
  override fun createDefaultResDirectory(projectSystem: GradleProjectSystem, facet: AndroidFacet): File? {
    val module = facet.module
    return module.rootManager.contentRoots.singleOrNull()?.let {
      when {
        // TODO(b/322150460): Investigate whether this is enough, as it doesn't actually modify the project.  We might also
        //  need to turn resources on.  (If we do modify the project, we should probably also trigger a sync.)
        it.exists() && it.isDirectory -> VfsUtil.virtualToIoFile(it).resolve("src/main/res").also { dir -> dir.mkdirs() }
        else -> null
      }
    }
  }
}