/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.model

import com.android.tools.idea.testing.ProjectFiles.createFolderInProjectRoot
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PlatformTestCase
import org.junit.Test


class MergedManifestRefreshListenerTest : PlatformTestCase() {
  @Test
  fun testTopLevelDependents() {
    // Build up modules with dependencies,
    // myModule -> app0, app1, app2
    // app0 -> app1, app2
    // app1 -> app2
    val modules = mutableListOf(myModule)
    for (i in 0..2) {
      val path = createFolderInProjectRoot(myModule.project, "app$i").toNioPath()
      val dependencyModule = createModuleAt("app$i", myModule.project, JavaModuleType.getModuleType(), path)

      for (module in modules) {
        ModuleRootModificationUtil.addDependency(module, dependencyModule)
      }

      modules.add(dependencyModule)
    }

    // Check the top-level dependents of module "app2" only contain "myModule".
    val topLevelDependents = modules[modules.size - 1].getTopLevelResourceDependents().toList()
    assertThat(topLevelDependents).containsExactly(myModule)
  }
}