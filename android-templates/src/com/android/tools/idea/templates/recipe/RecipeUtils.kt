/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FN_ANDROID_MANIFEST_XML
import com.android.tools.idea.templates.TemplateUtils.hasExtension
import com.android.tools.idea.templates.mergeXml as mergeXmlUtil
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.RecipeExecutor
import com.android.tools.idea.wizard.template.withoutSkipLines
import java.io.File

enum class IconsGenerationStyle {
  ALL,
  MIPMAP_ONLY,
  MIPMAP_SQUARE_ONLY,
  NONE,
}

object RecipeUtils {
  fun mergeXml(
    context: RenderingContext,
    source: String,
    to: File,
    targetFile: File,
    readTargetText: (File) -> String?,
    save: (String, File) -> Unit,
    writeTargetFile: (String?, File) -> Unit,
  ) {
    val content = source.withoutSkipLines()
    require(hasExtension(targetFile, DOT_XML)) { "Only XML files can be merged at this point: $targetFile" }

    val targetText =
      readTargetText(targetFile)
        ?: run {
          save(content, to)
          return
        }

    val contents = mergeXmlUtil(context, content, targetText, targetFile)

    writeTargetFile(contents, targetFile)
  }

  fun generateCommonModuleFiles(
    executor: RecipeExecutor,
    data: ModuleTemplateData,
    appTitle: String?, // may be null only for libraries
    manifestXml: String,
    generateGenericLocalTests: Boolean = false,
    generateGenericInstrumentedTests: Boolean = false,
    iconsGenerationStyle: IconsGenerationStyle = IconsGenerationStyle.ALL,
    themesXml: String? = null,
    themesXmlNight: String? = null,
    colorsXml: String? = null,
    appTitleResName: String = "app_name",
    addLocalTests: (String, File, com.android.tools.idea.wizard.template.Language) -> Unit = { _, _, _ -> },
    addInstrumentedTests: (String, Boolean, Boolean, File, com.android.tools.idea.wizard.template.Language) -> Unit = { _, _, _, _, _ -> },
    copyIcons: (File, Int) -> Unit = { _, _ -> },
    copyMipmapFolder: (File) -> Unit = { _ -> },
    copyMipmapFile: (File, String) -> Unit = { _, _ -> },
    gitignore: () -> String = { "" },
    androidModuleStrings: (String, String) -> String = { _, _ -> "" },
  ) {
    val (projectData, srcOut, resOut, manifestOut, instrumentedTestOut, localTestOut, _, moduleOut) = data
    val (useAndroidX, _) = projectData
    val language = projectData.language
    val isLibraryProject = data.isLibrary
    val packageName = data.packageName
    val apis = data.apis
    val minApi = apis.minApi

    executor.createDirectory(srcOut)

    executor.save(manifestXml, manifestOut.resolve(FN_ANDROID_MANIFEST_XML))
    executor.save(gitignore(), moduleOut.resolve(".gitignore"))
    if (generateGenericLocalTests) {
      addLocalTests(packageName, localTestOut, language)
    }
    if (generateGenericInstrumentedTests) {
      addInstrumentedTests(packageName, useAndroidX, isLibraryProject, instrumentedTestOut, language)
    }

    if (!isLibraryProject) {
      when (iconsGenerationStyle) {
        IconsGenerationStyle.ALL -> copyIcons(resOut, minApi.apiLevel)
        IconsGenerationStyle.MIPMAP_ONLY -> copyMipmapFolder(resOut)
        IconsGenerationStyle.MIPMAP_SQUARE_ONLY -> copyMipmapFile(resOut, "ic_launcher.webp")
        IconsGenerationStyle.NONE -> Unit
      }
      with(resOut.resolve(SdkConstants.FD_RES_VALUES)) {
        if (appTitle != null) {
          executor.save(androidModuleStrings(appTitleResName, appTitle), resolve("strings.xml"))
        }
        // Common themes.xml isn't needed for Compose because theme is created in Composable.
        if (themesXml != null && !data.isCompose) {
          executor.save(themesXml, resolve("themes.xml"))
        }
        if (colorsXml != null) {
          executor.save(colorsXml, resolve("colors.xml"))
        }
      }
      themesXmlNight?.let {
        // Common themes.xml isn't needed for Compose because theme is created in Composable.
        if (!data.isCompose) {
          executor.save(it, resOut.resolve(SdkConstants.FD_RES_VALUES_NIGHT).resolve("themes.xml"))
        }
      }
    }
  }
}
