// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel
import com.android.tools.idea.gradle.dsl.api.crashlytics.CrashlyticsModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.dsl.api.java.JavaModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap.BlockModelBuilder
import com.android.tools.idea.gradle.dsl.model.GradleBlockModelMap.BlockModelProvider
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl
import com.android.tools.idea.gradle.dsl.model.build.BuildScriptModelImpl
import com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationsModelImpl
import com.android.tools.idea.gradle.dsl.model.crashlytics.CrashlyticsModelImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.DeclarativeDependenciesModelImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.ScriptDependenciesModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl
import com.android.tools.idea.gradle.dsl.model.java.JavaModelImpl
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement
import com.android.tools.idea.gradle.dsl.parser.crashlytics.CrashlyticsDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription
import com.android.tools.idea.gradle.dsl.utils.EXT_DECLARATIVE_TOML

class GradleDefaultBlockModels : BlockModelProvider<GradleBuildModel, GradleDslFile> {

  override fun availableModels(): List<BlockModelBuilder<*, GradleDslFile>> {
    return DEFAULT_ROOT_AVAILABLE_MODELS;
  }

  override fun getParentClass() = GradleBuildModel::class.java

  override fun elementsMap(): Map<String, PropertiesElementDescription<*>> {
    return DEFAULT_ROOT_ELEMENTS_MAP
  }


  companion object {
    private val DEFAULT_ROOT_ELEMENTS_MAP = mapOf(
      "android" to AndroidDslElement.ANDROID,
      "buildscript" to BuildScriptDslElement.BUILDSCRIPT,
      "configurations" to ConfigurationsDslElement.CONFIGURATIONS,
      "crashlytics" to CrashlyticsDslElement.CRASHLYTICS,
      "dependencies" to DependenciesDslElement.DEPENDENCIES,
      "ext" to ExtDslElement.EXT,
      "java" to JavaDslElement.JAVA,
      "repositories" to RepositoriesDslElement.REPOSITORIES,
      "subprojects" to SubProjectsDslElement.SUBPROJECTS,
      "plugins" to PluginsDslElement.PLUGINS)

    private val DEFAULT_ROOT_AVAILABLE_MODELS = listOf<BlockModelBuilder<*, GradleDslFile>>(
      AndroidModel::class.java from {
        AndroidModelImpl(it.ensurePropertyElement(AndroidDslElement.ANDROID))
      },

      BuildScriptModel::class.java from {
        BuildScriptModelImpl(it.ensurePropertyElementAt(BuildScriptDslElement.BUILDSCRIPT, 0))
      },

      ConfigurationsModel::class.java from {
        ConfigurationsModelImpl(it.ensurePropertyElementBefore(ConfigurationsDslElement.CONFIGURATIONS, DependenciesDslElement::class.java))
      },

      CrashlyticsModel::class.java from {
        CrashlyticsModelImpl(it.ensurePropertyElement(CrashlyticsDslElement.CRASHLYTICS))
      },
      DependenciesModel::class.java from {
        val dependenciesDslElement: DependenciesDslElement = it.ensurePropertyElement(DependenciesDslElement.DEPENDENCIES)
        if (it.file.name.endsWith(EXT_DECLARATIVE_TOML)) {
          DeclarativeDependenciesModelImpl(dependenciesDslElement)
        }
        else {
          ScriptDependenciesModelImpl(dependenciesDslElement)
        }
      },

      ExtModel::class.java from {
        var at = 0
        val elements: List<GradleDslElement> = it.getCurrentElements()
        for (element in elements) {
          if (!(element is ApplyDslElement || element is PluginsDslElement || element is BuildScriptDslElement)) {
            break
          }
          at += 1
        }
        val extDslElement: ExtDslElement = it.ensurePropertyElementAt(ExtDslElement.EXT, at)
        return@from ExtModelImpl(extDslElement)
      },

      JavaModel::class.java from {
        JavaModelImpl(it.ensurePropertyElement(JavaDslElement.JAVA))
      },

      RepositoriesModel::class.java from {
        RepositoriesModelImpl(it.ensurePropertyElement(RepositoriesDslElement.REPOSITORIES))
      }
    )
  }


}

private infix fun <M, P> Class<M>.from(action: (P) -> M): BlockModelBuilder<M, P> where M : GradleDslModel, P : GradlePropertiesDslElement {
  return object : BlockModelBuilder<M, P> {
    override fun modelClass() = this@from
    override fun create(p: P): M = action(p)
  }
}