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

import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Class
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.createGradleRunConfiguration
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Companion.getPsiElement
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Directory
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.File
import com.android.tools.idea.testartifacts.TestConfigurationTestingUtil.Method
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

fun createAndroidGradleTestConfigurationFromMethod(project: Project, qualifiedName: String, methodName: String): GradleRunConfiguration? {
  return project.getPsiElement(Method(qualifiedName, methodName)).createGradleRunConfiguration()
}

fun createAndroidGradleTestConfigurationFromClass(project: Project, qualifiedName: String) : GradleRunConfiguration? {
  return project.getPsiElement(Class(qualifiedName)).createGradleRunConfiguration()
}

fun createAndroidGradleTestConfigurationFromDirectory(project: Project, directory: String) : GradleRunConfiguration? {
  return project.getPsiElement(Directory(directory)).createGradleRunConfiguration()
}

fun createAndroidGradleTestConfigurationFromFile(project: Project, file: String) : GradleRunConfiguration? {
  return project.getPsiElement(File(file)).createGradleRunConfiguration()
}
