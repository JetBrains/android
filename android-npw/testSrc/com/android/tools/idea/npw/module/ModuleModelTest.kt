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

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.npw.java.NewLibraryModuleModel
import com.android.tools.idea.npw.model.MultiTemplateRenderer
import com.android.tools.idea.npw.model.ProjectSyncInvoker
import com.android.tools.idea.npw.multiplatform.NewKotlinMultiplatformLibraryModuleModel
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findModule
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import junit.framework.TestCase
import org.jetbrains.android.util.AndroidBundle

class ModuleModelTest : AndroidGradleTestCase() {
  private val projectSyncInvoker = ProjectSyncInvoker.DefaultProjectSyncInvoker()

  private val multiTemplateRenderer: MultiTemplateRenderer get() = MultiTemplateRenderer { renderer ->
    object : Task.Modal(project, AndroidBundle.message("android.compile.messages.generating.r.java.content.name"), false) {
      override fun run(indicator: ProgressIndicator) {
        renderer(project)
      }
    }.queue()
    projectSyncInvoker.syncProject(project)
  }

  fun testInitFillsAllTheDataForLibraryModule() {
    loadSimpleApplication()

    val libraryModuleModel = NewLibraryModuleModel(project, ":", projectSyncInvoker).apply {
      packageName.set("com.google.lib")
    }

    multiTemplateRenderer.requestRender(libraryModuleModel.renderer)

    val module = myFixture.project.findModule("lib")
    val modulesToCompile = arrayOf(module)

    val invocationResult = invokeGradle(project) {
      it.compileJava(modulesToCompile)
    }
    TestCase.assertTrue(invocationResult.isBuildSuccessful)
  }

  fun testKmpModuleCreationAndAssemble() {
    loadProject(TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM)

    val kmpModuleModel = NewKotlinMultiplatformLibraryModuleModel(project, ":", projectSyncInvoker).apply {
      packageName.set("com.example.kmplibrary")
      agpVersion.set(AgpVersion(8, 1, 0))
    }
    multiTemplateRenderer.requestRender(kmpModuleModel.renderer)

    val module = myFixture.project.findModule("kmplibrary")
    val invocationResult = invokeGradle(project) {
      it.assemble(arrayOf(module))
    }
    TestCase.assertTrue(invocationResult.isBuildSuccessful)
  }
}