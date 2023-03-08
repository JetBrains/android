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
package com.android.tools.idea.gradle.actions

import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker.Companion.getInstance
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import icons.StudioIcons

class MakeGradleModuleAction : AndroidStudioGradleAction("Make Module(s)", "Build Selected Module(s)", StudioIcons.Shell.Toolbar.BUILD_MODULE) {
  override fun doUpdate(e: AnActionEvent, project: Project) {
    updatePresentation(e, project)
  }

  public override fun doPerform(e: AnActionEvent, project: Project) {
    val modules = GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(e.dataContext)
    getInstance(project).assemble(modules, TestCompileType.ALL)
  }

  companion object {
    @JvmStatic
    fun updatePresentation(e: AnActionEvent, project: Project) {
      val dataContext = e.dataContext
      val modules = GradleProjectInfo.getInstance(project).getModulesToBuildFromSelection(dataContext)
      val moduleCount = modules.size
      val presentation = e.presentation
      val isCompilationActive = CompilerManager.getInstance(project).isCompilationActive
      presentation.isEnabled = moduleCount > 0 && !isCompilationActive
      val presentationText: String
      if (moduleCount > 0) {
        var text = StringBuilder("Make Module")
        if (moduleCount > 1) {
          text.append("s")
        }
        for (i in 0 until moduleCount) {
          if (text.length > 30) {
            text = StringBuilder("Make Selected Modules")
            break
          }
          val toMake = modules[i]
          if (i != 0) {
            text.append(",")
          }
          text.append(" '").append(toMake.name).append("'")
        }
        presentationText = text.toString()
      } else {
        presentationText = "Make"
      }
      presentation.text = presentationText
      presentation.isVisible = moduleCount > 0 || ActionPlaces.PROJECT_VIEW_POPUP != e.place
    }
  }
}
