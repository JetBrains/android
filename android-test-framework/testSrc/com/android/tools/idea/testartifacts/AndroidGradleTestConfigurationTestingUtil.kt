/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.intellij.execution.RunManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.junit.Assert


fun createAndroidGradleTestConfigurationFromClass(project: Project, qualifiedName: String) : GradleRunConfiguration? {
  val element = JavaPsiFacade.getInstance(project).findClass(qualifiedName, GlobalSearchScope.projectScope(project))
  Assert.assertNotNull(element)
  return createGradleConfigurationFromPsiElement(project, element!!)
}

fun createAndroidGradleConfigurationFromDirectory(project: Project, directory: String) : GradleRunConfiguration? {
  val element = getPsiElement(project, directory, true)
  return createGradleConfigurationFromPsiElement(project, element)
}

fun createAndroidGradleConfigurationFromFile(project: Project, file: String) : GradleRunConfiguration? {
  val element = getPsiElement(project, file, false)
  return createGradleConfigurationFromPsiElement(project, element)
}

fun createGradleConfigurationFromPsiElement(project: Project, psiElement: PsiElement) : GradleRunConfiguration? {
  val context = TestConfigurationTesting.createContext(project, psiElement)
  val settings = context.configuration ?: return null
  // Save run configuration in the project.
  val runManager = RunManager.getInstance(project)
  runManager.addConfiguration(settings)

  val configuration = settings.configuration
  if (configuration is GradleRunConfiguration) return configuration else return null
}

fun getPsiElement(project: Project, file: String, isDirectory: Boolean): PsiElement {
  val virtualFile = VfsUtilCore.findRelativeFile(file, project.baseDir)
  Assert.assertNotNull(virtualFile)
  val element: PsiElement? = if (isDirectory) PsiManager.getInstance(project).findDirectory(virtualFile!!)
  else PsiManager.getInstance(project).findFile(virtualFile!!)
  Assert.assertNotNull(element)
  return element!!
}