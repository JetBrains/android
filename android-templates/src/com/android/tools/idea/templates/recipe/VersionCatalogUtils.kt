/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo

/**
 * Add the dependency or get the existing dependency using Gradle Version Catalogs.
 *
 * Checks if the existing matching dependency is present in the [catalogModel] and adds the entry if it's not present.
 *
 * @return the reference to the added dependency when the [catalogModel] doesn't have the matching dependency.
 * Return the existing dependency as a reference when the [catalogModel] has the matching dependency.
 */
fun getOrAddDependencyToVersionCatalog(
  catalogModel: GradleVersionCatalogModel?,
  resolvedMavenCoordinate: String,
): ReferenceTo? {
  catalogModel?.run {
    // Dereference the sections so that the order of the sections is expected in case the toml file is empty
    versions()
    libraries()
  } ?: return null

  val splitCoordinate = resolvedMavenCoordinate.split(":")
  val groupName = splitCoordinate[0]
  val libraryName = splitCoordinate[1]
  // dependency using BOM may not have the version at the end. In that case, the resolved version is null
  val resolvedVersion = if (splitCoordinate.size < 3) null else splitCoordinate[2]
  val libraryInToml = findLibraryInCatalog(catalogModel, groupName, libraryName)
  return if (libraryInToml != null) {
    ReferenceTo(libraryInToml)
  } else {
    val versions = catalogModel.versions()
    val libraries = catalogModel.libraries()
    val pickedNameForLib = pickNameInToml(
      sectionInCatalog = libraries,
      libraryName = libraryName,
      groupName = groupName,
      resolvedVersion = resolvedVersion)
    val pickedNameForVersion = pickNameInToml(
      sectionInCatalog = versions,
      libraryName = libraryName,
      groupName = groupName,
      resolvedVersion = resolvedVersion)
    val libProperty = libraries.findProperty(pickedNameForLib)
    libProperty.getMapValue("group")?.setValue(groupName)
    libProperty.getMapValue("name")?.setValue(libraryName)

    if (resolvedVersion != null) {
      val versionProperty = versions.findProperty(pickedNameForVersion)
      versionProperty.setValue(resolvedVersion)

      libProperty.getMapValue("version")?.setValue(ReferenceTo(versionProperty))
    }
    return ReferenceTo(libProperty)
  }
}

/**
 *  Add the plugin or get the existing plugin using Gradle Version Catalogs.
 *
 *  Checks if the existing matching plugin is present in the [catalogModel] and adds the entry if it's not present.
 *
 *  @return the reference to the added plugin when the [catalogModel] doesn't have the matching plugin.
 *  Return the existing plugin as a reference when the [catalogModel] has the matching plugin.
 */
fun getOrAddPluginToVersionCatalog(
  catalogModel: GradleVersionCatalogModel?,
  pluginName: String,
  resolvedVersion: String,
): ReferenceTo? {
  catalogModel?.run {
    // Dereference the sections so that the order of the sections is expected in case the toml file is empty
    versions()
    libraries()
    plugins()
  } ?: return null
  val pluginInToml = findPluginInCatalog(catalogModel, pluginName)
  return if (pluginInToml != null) {
    ReferenceTo(pluginInToml)
  } else {
    val plugins = catalogModel.plugins()
    val versions = catalogModel.versions()
    val pickedNameForPlugin = pickNameInToml(
      sectionInCatalog = plugins,
      libraryName = pluginName,
      resolvedVersion = resolvedVersion)
    val pickedNameForVersion = pickNameInToml(
      sectionInCatalog = versions,
      libraryName = pluginName,
      resolvedVersion = resolvedVersion)
    val pluginProperty = plugins.findProperty(pickedNameForPlugin)
    pluginProperty.getMapValue("id")?.setValue(pluginName)
    val versionProperty = versions.findProperty(pickedNameForVersion)
    versionProperty.setValue(resolvedVersion)
    pluginProperty.getMapValue("version")?.setValue(ReferenceTo(versionProperty))
    return ReferenceTo(pluginProperty)
  }
}

/**
 * Checks if the matching dependency is present in the version catalog model
 */
private fun findLibraryInCatalog(
  versionCatalogModel: GradleVersionCatalogModel,
  groupName: String,
  libraryName: String,
): GradlePropertyModel? {
  val mavenModuleName = buildString {
    append(groupName)
    append(":")
    append(libraryName)
  }
  val libraries = versionCatalogModel.libraries()
  return libraries.properties.firstOrNull {
    when (it.valueType) {
      ValueType.MAP -> {
        (it.getMapValue("module")?.getValue(STRING_TYPE) == mavenModuleName ||
         (it.getMapValue("group")?.getValue(STRING_TYPE) == groupName &&
          it.getMapValue("name")?.getValue(STRING_TYPE) == libraryName))
      }
      else -> false
    }
  }
}

/**
 * Checks if the matching plugin is present in the version catalog model
 */
private fun findPluginInCatalog(
  versionCatalogModel: GradleVersionCatalogModel?,
  pluginName: String,
): GradlePropertyModel? {
  return versionCatalogModel?.plugins()?.properties?.firstOrNull {
    when (it.valueType) {
      ValueType.MAP -> {
        it.getMapValue("id")?.getValue(STRING_TYPE) == pluginName
      }
      else -> false
    }
  }
}

private fun pickNameInToml(
  sectionInCatalog: ExtModel,
  libraryName: String,
  groupName: String? = null,
  resolvedVersion: String? = null): String {

  val namesInCatalog: Set<String> = sectionInCatalog.properties.map {it.name}.toSet()
  // TODO: Use smarter logic to pick the name in toml

  var pickedName = libraryName.replace(".", "-")
  if (!namesInCatalog.contains(pickedName)) {
    return pickedName
  }

  if (groupName != null) {
    pickedName = buildString {
      append(groupName.replace(".", "-"))
      append("-")
      append(libraryName.replace(".", "-"))
    }
    if (!namesInCatalog.contains(pickedName)) {
      return pickedName
    }
  }

  if (resolvedVersion != null) {
    pickedName = buildString {
      groupName?.let {
        append(groupName.replace(".", "-"))
        append("-")
      }
      append(libraryName.replace(".", "-"))
      append(resolvedVersion.replace(".", ""))
    }
    if (!namesInCatalog.contains(pickedName)) {
      return pickedName
    }
  }

  // Fallback to make sure unique name is created
  var id = 2
  while (true) {
    val name = "${pickedName}${id++}"
    if (!namesInCatalog.contains(name)) {
      return name
    }
  }
}