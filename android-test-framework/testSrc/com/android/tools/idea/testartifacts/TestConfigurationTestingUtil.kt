/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.testartifacts

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class TestConfigurationTestingUtil {
  companion object {
    @JvmOverloads
    @JvmStatic
    inline fun <reified T : RunConfiguration> PsiElement.createRunConfiguration(check: T.() -> Boolean = { true }): T? {
      val project = this.project
      val context = createContext(project, this)
      val settings = context.configuration ?: return null
      val runManager = RunManager.getInstance(project)
      runManager.addConfiguration(settings)
      val configuration = settings.configuration as? T ?: return null
      if (!configuration.check()) return null
      return configuration
    }

    @JvmStatic
    fun PsiElement.createGradleRunConfiguration() : GradleRunConfiguration? =
      // Having no tasks to run means that there shouldn't be a configuration created. This will be handled by Intellij in
      // https://youtrack.jetbrains.com/issue/IDEA-277826.
      createRunConfiguration { settings.taskNames.isNotEmpty() }
    @JvmStatic
    fun PsiElement.createAndroidTestRunConfiguration() : AndroidTestRunConfiguration? =
      createRunConfiguration()

    @JvmStatic
    fun PsiElement.createConfigurations() = createContext(this.project, this).configurationsFromContext

    @JvmStatic
    fun createContext(project: Project, psiElement: PsiElement) =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, project)
        .add(PlatformCoreDataKeys.MODULE, ModuleUtilCore.findModuleForPsiElement(psiElement))
        .add(Location.DATA_KEY, PsiLocation.fromPsiElement(psiElement))
        .build()
        .let { ConfigurationContext.getFromContext(it, ActionPlaces.UNKNOWN) }

    @JvmStatic
    fun getPsiElement(project: Project, file: String, isDirectory: Boolean): PsiElement {
      val virtualFile = VfsUtilCore.findRelativeFile(file, project.baseDir)
      assertThat(virtualFile).isNotNull()
      val element: PsiElement? = if (isDirectory) PsiManager.getInstance(project).findDirectory(virtualFile!!)
      else PsiManager.getInstance(project).findFile(virtualFile!!)
      assertThat(element).isNotNull()
      return element!!
    }

    @JvmStatic
    fun Project.getPsiElement(source: PsiElementSource): PsiElement =
      when (source) {
        is Class -> JavaPsiFacade.getInstance(this).findClass(source.qualifiedName, GlobalSearchScope.projectScope(this))!!
        is Method -> JavaPsiFacade.getInstance(this).findClass(source.qualifiedName, GlobalSearchScope.projectScope(this))!!
          .findMethodsByName(source.methodName)
          .first().sourceElement!!
        is FileSystemPsiElementSource -> getPsiElement(this, source.name, source.isDirectory)
      }
  }

  sealed interface PsiElementSource
  class Class(val qualifiedName: String): PsiElementSource
  class Method(val qualifiedName: String, val methodName: String): PsiElementSource
  abstract class FileSystemPsiElementSource(val name: String, val isDirectory: Boolean): PsiElementSource
  class File(name: String): FileSystemPsiElementSource(name, false)
  class Directory(name: String): FileSystemPsiElementSource(name, true)
}