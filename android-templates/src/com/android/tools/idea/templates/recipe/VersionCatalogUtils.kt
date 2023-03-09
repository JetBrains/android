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
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo

/**
 * Add the dependency using Gradle Version Catalogs.
 *
 * Checks if the existing matching dependency is present in the toml file and adds the entry if it's not present.
 *
 * @return the reference to the added dependency
 */
fun addDependencyToVersionCatalog(
  catalogModel: GradleVersionCatalogModel?,
  resolvedMavenCoordinate: String,
): ReferenceTo? {
  val splitCoordinate = resolvedMavenCoordinate.split(":")
  val groupName = splitCoordinate[0]
  val libraryName = splitCoordinate[1]
  // dependency using BOM may not have the version at the end. In that case, the resolved version is null
  val resolvedVersion = if (splitCoordinate.size < 3) null else splitCoordinate[2]
  catalogModel?.run {
    // Dereference the sections so that the order of the sections is expected in case the toml file is empty
    versions()
    libraries()
  }
  val libraryInToml = findLibraryInCatalog(catalogModel, groupName, libraryName)
  return if (libraryInToml != null) {
    ReferenceTo(libraryInToml)
  } else {
    val versions = catalogModel?.versions()
    val libraries = catalogModel?.libraries()
    val pickedNameForLib = pickNameInToml(libraries, groupName, libraryName, resolvedVersion)
    val pickedNameForVersion = pickNameInToml(versions, groupName, libraryName, resolvedVersion)
    val libProperty = libraries?.findProperty(pickedNameForLib)
    libProperty?.getMapValue("group")?.setValue(groupName)
    libProperty?.getMapValue("name")?.setValue(libraryName)

    if (resolvedVersion != null) {
      val versionProperty = versions?.findProperty(pickedNameForVersion)
      versionProperty?.setValue(resolvedVersion)

      if (versionProperty != null) {
        libProperty?.getMapValue("version")?.setValue(ReferenceTo(versionProperty))
      }
    }
    return if (libProperty != null) ReferenceTo(libProperty) else null
  }
}

/**
 * Checks if the matching dependency is present in the version catalog model
 */
fun findLibraryInCatalog(
  versionCatalogModel: GradleVersionCatalogModel?,
  groupName: String,
  libraryName: String,
): GradlePropertyModel? {
  val mavenModuleName = buildString {
    append(groupName)
    append(":")
    append(libraryName)
  }
  val libraries = versionCatalogModel?.libraries()
  return libraries?.properties?.firstOrNull {
    when (it.valueType) {
      GradlePropertyModel.ValueType.MAP -> {
        (it.getMapValue("module")?.getValue(GradlePropertyModel.STRING_TYPE)?.equals(mavenModuleName) == true ||
         (it.getMapValue("group")?.getValue(GradlePropertyModel.STRING_TYPE)?.equals(groupName) == true &&
          it.getMapValue("name")?.getValue(GradlePropertyModel.STRING_TYPE)?.equals(libraryName) == true))
      }
      else -> false
    }
  }
}

fun pickNameInToml(sectionInCatalog: ExtModel?,
                   groupName: String,
                   libraryName: String,
                   resolvedVersion: String?): String {

  val namesInCatalog: Set<String>? = sectionInCatalog?.properties?.map {it.name}?.toSet()
  // TODO: Use smarter logic to pick the name in toml

  var pickedName = libraryName.replace(".", "-")
  if (namesInCatalog == null || !namesInCatalog.contains(pickedName)) {
    return pickedName
  }

  pickedName = buildString {
    append(groupName.replace(".", "-"))
    append("-")
    append(libraryName.replace(".", "-"))
  }
  if (!namesInCatalog.contains(pickedName)) {
    return pickedName
  }

  if (resolvedVersion != null) {
    pickedName = buildString {
      append(groupName.replace(".", "-"))
      append("-")
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