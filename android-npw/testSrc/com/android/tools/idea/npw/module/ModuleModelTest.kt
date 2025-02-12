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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.java.NewLibraryModuleModel
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import org.jetbrains.android.util.AndroidBundle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ModuleModelTest {
  @get:Rule val projectRule = AndroidGradleProjectRule()
  private val projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()
  private val multiTemplateRenderer: MultiTemplateRenderer
    get() = MultiTemplateRenderer { renderer ->
      object :
          Task.Modal(
            projectRule.project,
            AndroidBundle.message("android.compile.messages.generating.r.java.content.name"),
            false,
          ) {
          override fun run(indicator: ProgressIndicator) {
            renderer(project)
          }
        }
        .queue()
      projectSyncInvoker.syncProject(projectRule.project)
    }

  @Test
  fun testInitFillsAllTheDataForLibraryModule() {
    projectRule.load(TestProjectPaths.SIMPLE_APPLICATION) {
      val libraryModuleModel =
        NewLibraryModuleModel(projectRule.project, ":", projectSyncInvoker).apply {
          packageName.set("com.google.lib")
        }
      multiTemplateRenderer.requestRender(libraryModuleModel.renderer)
    }
    projectRule.invokeTasks("compileDebugSources", ":lib:compileJava").apply {
      buildError?.printStackTrace()
      assertTrue(isBuildSuccessful)
    }
  }
}
