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
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.REFERENCE_TO_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo

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
  val pluginInToml = findPluginInCatalog(catalogModel.plugins(), pluginName)
  return if (pluginInToml != null) {
    ReferenceTo(pluginInToml)
  } else {
    val plugins = catalogModel.plugins()
    val versions = catalogModel.versions()
    val pluginProperty = PluginUtils.createPluginProperty(
      plugins = plugins,
      pluginName = pluginName,
      resolvedVersion = resolvedVersion,
    )
    val versionProperty = PluginUtils.getOrCreateVersionProperty(
      versions = versions,
      plugins = plugins,
      pluginName = pluginName,
      resolvedVersion = resolvedVersion)
    pluginProperty.getMapValue("version")?.setValue(ReferenceTo(versionProperty))
    return ReferenceTo(pluginProperty)
  }
}

/**
 * Checks if the matching plugin is present in the version catalog model
 */
private fun findPluginInCatalog(
  plugins: ExtModel,
  pluginName: String,
): GradlePropertyModel? {
  return plugins.properties.firstOrNull {
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

object PluginUtils {

  private val agpPluginIds = AgpPlugin.values().map { it.id }.toSet()

  /**
   * Get existing version property or create a new one if the matching property doesn't exist in the [versions] model.
   *
   * Some plugins should have the common version property (e.g. plugins listed in the [AgpPlugin]) instead of creating own one.
   * This method first checks if the [pluginName] is part of the predefined plugins that should have the common version property,
   * then get the existing version property if it's present or create a new one if it isn't present
   */
  fun getOrCreateVersionProperty(versions: ExtModel, plugins: ExtModel, pluginName: String, resolvedVersion: String): GradlePropertyModel {
    return if (agpPluginIds.contains(pluginName)) {
      getAgpVersionProperty(versions, plugins, resolvedVersion)
    } else {
      // When the plugin doesn't depend on AGP, follow the regular rule to pick the name for the version
      val defaultVersionName = if (pluginName == KotlinPlugin.KOTLIN_ANDROID.id) {
        KotlinPlugin.KOTLIN_ANDROID.defaultVersionName
      } else {
        pluginName
      }
      val pickedNameForVersion = pickNameInToml(
        sectionInCatalog = versions,
        libraryName = defaultVersionName,
        resolvedVersion = resolvedVersion)
      val versionProperty = versions.findProperty(pickedNameForVersion)
      versionProperty.setValue(resolvedVersion)
      versionProperty
    }
  }

  private fun getAgpVersionProperty(versions: ExtModel,
                                    plugins: ExtModel,
                                    resolvedVersion: String): GradlePropertyModel {

    fun getExistingAgpPluginVersionReferencePropertyOrNull(): GradlePropertyModel? {
      // Look for the first plugin property where the plugin depends on AGP and
      // the plugin's version is a reference.
      AgpPlugin.values().forEach { agpPlugin ->
        val agpPluginProperty = findPluginInCatalog(plugins, agpPlugin.id)
        if (agpPluginProperty != null && agpPluginProperty.getMapValue("version")?.valueType == ValueType.REFERENCE) {
          return versions.properties.firstOrNull {
            it.name == agpPluginProperty.getMapValue("version")?.getValue(REFERENCE_TO_TYPE)?.referredElement?.name
          }
        }
      }
      return null
    }

    val agpPluginVersionReferenceProperty = getExistingAgpPluginVersionReferencePropertyOrNull()
    return if (agpPluginVersionReferenceProperty != null) {
      agpPluginVersionReferenceProperty
    } else {
      val pickedNameForVersion = pickNameInToml(versions, AgpPlugin.APPLICATION.defaultVersionName)
      val versionProperty = versions.findProperty(pickedNameForVersion)
      versionProperty.setValue(resolvedVersion)
      versionProperty
    }
  }

  /**
   * Create a new plugin property in the [plugins] model.
   *
   * This method first checks if the [pluginName] has the default suggested plugin name
   * (e.g. common plugins such as "com.android.application" has a default plugin name)
   * Then, pick a plugin name from the catalog and return the created plugin property.
   */
  fun createPluginProperty(plugins: ExtModel, pluginName: String, resolvedVersion: String): GradlePropertyModel {

    fun getDefaultPluginNameOrNull(pluginName: String): String? {
      val defaultNameForAgp = AgpPlugin.values().firstOrNull {
        it.id == pluginName
      }?.defaultPluginName
      if (defaultNameForAgp != null) { return defaultNameForAgp }

      return KotlinPlugin.values().firstOrNull {
        it.id == pluginName
      }?.defaultPluginName
    }

    val defaultPluginName = getDefaultPluginNameOrNull(pluginName)
    val pickedNameForPlugin = if (defaultPluginName != null) {
      pickNameInToml(
        sectionInCatalog = plugins,
        libraryName = defaultPluginName,
        resolvedVersion = resolvedVersion)
    } else {
      pickNameInToml(
        sectionInCatalog = plugins,
        libraryName = pluginName,
        resolvedVersion = resolvedVersion)
    }
    val pluginProperty = plugins.findProperty(pickedNameForPlugin)
    pluginProperty.getMapValue("id")?.setValue(pluginName)
    return pluginProperty
  }
}

/**
 * Enum class whose values represent each plugin that should have the common Android Gradle Plugin
 * version in the app.
 */
enum class AgpPlugin(val id: String, val defaultPluginName: String) {
  APPLICATION("com.android.application", "androidApplication"),
  LIBRARY("com.android.library", "androidLibrary"),
  TEST("com.android.test", "androidTest"),
  ASSET_PACK("com.android.asset-pack", "androidAssetPack"),
  ASSET_PACK_BUNDLE("com.android.asset-pack-bundle", "androidAssetPackBundle"),
  DYNAMIC_FEATURE("com.android.dynamic-feature", "androidDynamicFeature"),
  FUSED_LIBRARY("com.android.fused-library", "androidFusedLibrary"),
  INTERNAL_SETTINGS("com.android.internal.settings", "androidInternalSettings"),
  SETTINGS("com.android.settings", "androidSettings"),
  LINT("com.android.lint", "androidLint")
  ;

  val defaultVersionName = "agp"
}

/**
 * Enum class whose values represent each plugin that should have the common Kotlin
 * version in the app. At the moment, this doesn't have to be an enum class because
 * it only has one value. But defining it as an enum to be consistent with [AgpPlugin].
 */
enum class KotlinPlugin(val id: String, val defaultPluginName: String) {
  KOTLIN_ANDROID("org.jetbrains.kotlin.android", "kotlinAndroid")
  ;

  val defaultVersionName = "kotlin"
}