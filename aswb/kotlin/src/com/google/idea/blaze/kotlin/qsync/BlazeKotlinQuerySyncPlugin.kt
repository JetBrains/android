/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.kotlin.qsync

import com.google.idea.blaze.base.model.primitives.LanguageClass
import com.google.idea.blaze.base.projectview.ProjectViewSet
import com.google.idea.blaze.base.qsync.BlazeQuerySyncPlugin
import com.google.idea.blaze.base.sync.projectview.LanguageSupport
import com.google.idea.blaze.common.Context
import com.google.idea.blaze.java.projectview.JavaLanguageLevelSection
import com.intellij.openapi.project.Project
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.kotlin.cli.common.arguments.unfrozen
import org.jetbrains.kotlin.idea.compiler.configuration.Kotlin2JvmCompilerArgumentsHolder

/** Supports Kotlin.  */
class BlazeKotlinQuerySyncPlugin : BlazeQuerySyncPlugin {
  override fun updateProjectSettingsForQuerySync(project: Project, context: Context<*>, projectViewSet: ProjectViewSet) {
    if (!isKotlinProject(projectViewSet)) {
      return
    }

    // Set jvm-target from java language level
    val javaLanguageLevel =
      JavaLanguageLevelSection.getLanguageLevel(projectViewSet, LanguageLevel.JDK_21)
    setProjectJvmTarget(project, javaLanguageLevel)
  }

  companion object {
    private fun setProjectJvmTarget(project: Project, javaLanguageLevel: LanguageLevel) {
      val k2JVMCompilerArguments = Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings.unfrozen()

      val javaVersion = javaLanguageLevel.toJavaVersion().toString()
      k2JVMCompilerArguments.jvmTarget = javaVersion
      Kotlin2JvmCompilerArgumentsHolder.getInstance(project).settings = k2JVMCompilerArguments
    }

    private fun isKotlinProject(projectViewSet: ProjectViewSet): Boolean {
      val workspaceLanguageSettings =
        LanguageSupport.createWorkspaceLanguageSettings(projectViewSet)
      return workspaceLanguageSettings.isLanguageActive(LanguageClass.KOTLIN)
    }
  }
}
