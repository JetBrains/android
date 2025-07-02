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
package com.android.tools.idea.templates.recipe

import com.android.tools.idea.templates.TemplateUtils
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.SourceSetType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import java.io.File

/** [RecipeExecutor] that collects references as a result of executing instructions in a recipe. */
class FindReferencesRecipeExecutor(private val context: RenderingContext) : RecipeExecutor {
  private val project: Project = context.project

  override fun save(source: String, to: File) {
    addTargetFile(to)
  }

  override fun append(source: String, to: File) {
    addTargetFile(to)
  }

  override fun mergeXml(source: String, to: File) {
    addTargetFile(to)
  }

  override fun open(file: File) {
    context.filesToOpen.add(resolveTargetFile(file))
  }

  override fun copy(from: File, to: File) {
    if (from.extension.isBlank() && !from.name.equals("gradlew") && !from.name.equals("project_ignore")) return

    addTargetFile(to)
  }

  override fun createDirectory(at: File) {}

  override fun applyPlugin(plugin: String, revision: String?, minRev: String?) {
    context.plugins.add(plugin)
  }

  override fun addPlugin(plugin: String, classpathModule: String, version: String) {
    context.plugins.add(plugin)
    context.classpathEntries.add("$classpathModule:$version")
  }

  override fun addPlugin(plugin: String, classpath: String) {
    context.plugins.add(plugin)
    context.classpathEntries.add(classpath)
  }

  override fun applyPluginInModule(
    plugin: String,
    module: Module,
    revision: String?,
    minRev: String?,
  ) {
    context.plugins.add(plugin)
  }

  override fun applyPluginWithClasspathInModule(pluginId: String, module: Module, classpathModule: String, version: String) {
    context.plugins.add(pluginId)
    context.classpathEntries.add("$classpathModule:$version")
  }

  override fun addClasspathDependency(
    mavenCoordinate: String,
    minRev: String?,
    forceAdding: Boolean,
  ) {
    context.classpathEntries.add(mavenCoordinate)
  }

  override fun addDependency(
    mavenCoordinate: String,
    configuration: String,
    minRev: String?,
    moduleDir: File?,
    toBase: Boolean,
    sourceSetName: String?,
  ) {
    context.dependencies.put(configuration, mavenCoordinate)
  }

  override fun addPlatformDependency(
    mavenCoordinate: String,
    configuration: String,
    enforced: Boolean,
  ) {
    context.dependencies.put(configuration, mavenCoordinate)
  }

  override fun addModuleDependency(configuration: String, moduleName: String, toModule: File) {}

  override fun addFileDependency(file: String, configuration: String) {}

  override fun addProjectGradleProperty(propertyName: String, textToAdd: String) {}

  fun addTargetFile(file: File) {
    context.targetFiles.add(resolveTargetFile(file))
  }

  private fun resolveTargetFile(file: File): File =
    if (file.isAbsolute) file else File(context.outputRoot, file.path)

  override fun addSourceSet(type: SourceSetType, name: String, dir: File) {}

  override fun setExtVar(name: String, value: String) {}

  override fun getExtVar(name: String, valueIfNotFound: String): String = valueIfNotFound

  override fun getClasspathDependencyVarName(mavenCoordinate: String, valueIfNotFound: String) =
    valueIfNotFound

  override fun getDependencyVarName(mavenCoordinate: String, valueIfNotFound: String) =
    valueIfNotFound

  override fun addIncludeToSettings(moduleName: String) {}

  override fun setBuildFeature(name: String, value: Boolean) {}

  override fun setViewBinding(value: Boolean) {}

  override fun setComposeOptions(kotlinCompilerExtensionVersion: String?) {}

  override fun setCppOptions(cppFlags: String, cppPath: String, cppVersion: String) {}

  override fun requireJavaVersion(version: String, kotlinSupport: Boolean) {}

  override fun setJavaKotlinCompileOptions(isKotlin: Boolean, moduleDir: File?) {}

  override fun addDynamicFeature(name: String, toModule: File) {}

  override fun getJavaVersion(defaultVersion: String): String {
    return TemplateUtils.getJavaVersion(project, defaultVersion)
  }

  override fun useLibrary(name: String) {}
}
