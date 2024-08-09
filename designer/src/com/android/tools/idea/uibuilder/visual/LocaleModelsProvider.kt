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
package com.android.tools.idea.uibuilder.visual

import com.android.ide.common.resources.Locale
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.type.typeOf
import com.android.tools.idea.configurations.ConfigurationForFile
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.configurations.ConfigurationMatcher
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.res.StudioResourceRepositoryManager
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFile

private const val EFFECTIVE_FLAGS =
  ConfigurationListener.CFG_ADAPTIVE_SHAPE or
    ConfigurationListener.CFG_DEVICE or
    ConfigurationListener.CFG_DEVICE_STATE or
    ConfigurationListener.CFG_UI_MODE or
    ConfigurationListener.CFG_NIGHT_MODE or
    ConfigurationListener.CFG_THEME or
    ConfigurationListener.CFG_TARGET or
    ConfigurationListener.CFG_FONT_SCALE

/**
 * This class provides the [NlModel]s with projects locales for [VisualizationForm].<br> The
 * provided [NlModel]s are associated with [Locale.ANY] (which is the default locale) and all
 * specific [Locale] in the project. Note that even there is no particular layout file a project
 * locale, the corresponding [NlModel] is still created. In that case the file of [NlModel] is the
 * default layout file. This is same as Android runtime behaviour.
 */
object LocaleModelsProvider : VisualizationModelsProvider {

  override fun createNlModels(
    parentDisposable: Disposable,
    file: PsiFile,
    buildTarget: AndroidBuildTargetReference,
  ): List<NlModel> {
    if (file.typeOf() != LayoutFileType) {
      return emptyList()
    }

    // Steps to find all locales in the project:
    // 1) Find the file corresponds to Locale.ANY - which is actually the layout file in res/layout
    // folder.
    //    This layout file is used at runtime when there is no particular layout for the device
    // locale.
    // 2) Find the locales in this project and find the corresponding layout files.
    //    Note that the default layout file is used when there is no specific layout for a locale,
    // and it will apply the corresponding
    //    locale resources. This is same as runtime behaviour.

    val currentFile = file.virtualFile ?: return emptyList()
    val configurationManager = ConfigurationManager.getOrCreateInstance(buildTarget.module)
    // Note that the current file may not be default config. (e.g. the current file is in
    // layout-en-rGB file)
    val currentFileConfig = configurationManager.getConfiguration(currentFile)

    val models = mutableListOf<NlModel>()

    // The layout file used when there is no particular layout for device's locale.
    val defaultFile =
      ConfigurationMatcher.getBetterMatch(currentFileConfig, null, null, Locale.ANY, null)
        ?: currentFile
    val defaultLocaleConfig =
      ConfigurationForFile.create(currentFileConfig, defaultFile).apply { locale = Locale.ANY }

    run {
      val firstModel =
        NlModel.Builder(parentDisposable, buildTarget, defaultFile, defaultLocaleConfig)
          .withComponentRegistrar(NlComponentRegistrar)
          .build()
      firstModel.displaySettings.setTooltip(defaultLocaleConfig.toHtmlTooltip())
      firstModel.displaySettings.setDisplayName("Default (no locale)")
      models.add(firstModel)

      registerModelsProviderConfigurationListener(
        firstModel,
        currentFileConfig,
        defaultLocaleConfig,
        EFFECTIVE_FLAGS,
      )
    }

    val locales =
      StudioResourceRepositoryManager.getInstance(buildTarget.facet)
        .localesInProject
        .sortedWith(Locale.LANGUAGE_CODE_COMPARATOR)

    for (locale in locales) {
      val betterFile =
        ConfigurationMatcher.getBetterMatch(defaultLocaleConfig, null, null, locale, null)
          ?: defaultFile
      val config = ConfigurationForFile.create(defaultLocaleConfig, betterFile)
      config.locale = locale
      val label = Locale.getLocaleLabel(locale, false)
      val model =
        NlModel.Builder(parentDisposable, buildTarget, betterFile, config)
          .withComponentRegistrar(NlComponentRegistrar)
          .build()
      model.displaySettings.setTooltip(config.toHtmlTooltip())
      model.displaySettings.setDisplayName(label)
      models.add(model)

      registerModelsProviderConfigurationListener(model, currentFileConfig, config, EFFECTIVE_FLAGS)
    }
    return models
  }
}
