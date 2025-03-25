/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidDeclarativeModel
import com.android.tools.idea.gradle.dsl.api.android.AndroidModel
import com.android.tools.idea.gradle.dsl.api.configurations.ConfigurationsModel
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel
import com.android.tools.idea.gradle.dsl.api.java.JavaModel
import com.android.tools.idea.gradle.dsl.api.kotlin.KotlinModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel
import com.android.tools.idea.gradle.dsl.model.android.AndroidDeclarativeModelImpl
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl
import com.android.tools.idea.gradle.dsl.model.build.BuildScriptModelImpl
import com.android.tools.idea.gradle.dsl.model.build.EmptyBuildScriptModel
import com.android.tools.idea.gradle.dsl.model.configurations.ConfigurationsModelImpl
import com.android.tools.idea.gradle.dsl.model.configurations.EmptyConfigurationsModelImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.EmptyDependenciesModelImpl
import com.android.tools.idea.gradle.dsl.model.dependencies.ScriptDependenciesModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.EmptyExtModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl
import com.android.tools.idea.gradle.dsl.model.java.EmptyJavaModelImpl
import com.android.tools.idea.gradle.dsl.model.java.JavaModelImpl
import com.android.tools.idea.gradle.dsl.model.kotlin.EmptyKotlinModelImpl
import com.android.tools.idea.gradle.dsl.model.kotlin.KotlinModelImpl
import com.android.tools.idea.gradle.dsl.model.repositories.EmptyRepositoriesModelImpl
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter
import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter.Kind.DECLARATIVE
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleBuildFile
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement
import com.android.tools.idea.gradle.dsl.parser.kotlin.KotlinDslElement
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription


class GradleDefaultBlockModels : BlockModelProvider<GradleBuildModel, GradleBuildFile> {

  override fun availableModels(kind: GradleDslNameConverter.Kind): List<BlockModelBuilder<*, GradleBuildFile>> {
    return when (kind) {
      DECLARATIVE -> DECLARATIVE_ROOT_AVAILABLE_MODELS
      else -> DEFAULT_ROOT_AVAILABLE_MODELS
    }
  }

  override val parentClass = GradleBuildModel::class.java
  override val parentDslClass = GradleBuildFile::class.java

  override fun elementsMap(kind: GradleDslNameConverter.Kind): Map<String, PropertiesElementDescription<*>> {
    return when (kind) {
      DECLARATIVE -> DECLARATIVE_ROOT_ELEMENTS_MAP
      else -> DEFAULT_ROOT_ELEMENTS_MAP
    }
  }

  companion object {
    private val DEFAULT_ROOT_ELEMENTS_MAP = mapOf(
      "android" to AndroidDslElement.ANDROID,
      "buildscript" to BuildScriptDslElement.BUILDSCRIPT,
      "configurations" to ConfigurationsDslElement.CONFIGURATIONS,
      "dependencies" to DependenciesDslElement.DEPENDENCIES,
      "ext" to ExtDslElement.EXT,
      "java" to JavaDslElement.JAVA,
      "kotlin" to KotlinDslElement.KOTLIN,
      "repositories" to RepositoriesDslElement.REPOSITORIES,
      "subprojects" to SubProjectsDslElement.SUBPROJECTS,
      "plugins" to PluginsDslElement.PLUGINS)

    private val DECLARATIVE_ROOT_ELEMENTS_MAP = mapOf(
      "androidApp" to AndroidDslElement.ANDROID_APP,
      "androidLibrary" to AndroidDslElement.ANDROID_LIBRARY
    )

    private fun declarativeBuilder(file: GradleBuildFile): AndroidDeclarativeModel {
      file.getPropertyElement(AndroidDslElement.ANDROID_APP)?.let { element ->
        return AndroidDeclarativeModelImpl(element)
      }
      file.getPropertyElement(AndroidDslElement.ANDROID_LIBRARY)?.let { element ->
        return AndroidDeclarativeModelImpl(element)
      }
      // TODO throw exception for now but need to create add element mechanism
      throw IllegalStateException("Cannot create android[App|Library] dsl element")
    }

    private val DEFAULT_ROOT_AVAILABLE_MODELS = listOf<BlockModelBuilder<*, GradleBuildFile>>(
      AndroidModel::class.java from {
        AndroidModelImpl(it.ensurePropertyElement(AndroidDslElement.ANDROID))
      },
      BuildScriptModel::class.java from {
        BuildScriptModelImpl(it.ensurePropertyElementAt(BuildScriptDslElement.BUILDSCRIPT, 0))
      },

      ConfigurationsModel::class.java from {
        ConfigurationsModelImpl(it.ensurePropertyElementBefore(ConfigurationsDslElement.CONFIGURATIONS, DependenciesDslElement::class.java))
      },

      DependenciesModel::class.java from { file ->
        val dependenciesDslElement: DependenciesDslElement = file.ensurePropertyElement(
          DependenciesDslElement.DEPENDENCIES)
        ScriptDependenciesModelImpl(dependenciesDslElement)
      },

      ExtModel::class.java from {
        var at = 0
        val elements: List<GradleDslElement> = it.currentElements
        for (element in elements) {
          when (element) {
            is ApplyDslElement, is PluginsDslElement, is BuildScriptDslElement -> at += 1
            else -> break
          }
        }
        val extDslElement: ExtDslElement = it.ensurePropertyElementAt(ExtDslElement.EXT, at)
        return@from ExtModelImpl(extDslElement)
      },

      JavaModel::class.java from {
        JavaModelImpl(it.ensurePropertyElement(JavaDslElement.JAVA))
      },

      KotlinModel::class.java from {
        KotlinModelImpl(it.ensurePropertyElement(KotlinDslElement.KOTLIN))
      },

      RepositoriesModel::class.java from {
        RepositoriesModelImpl(it.ensurePropertyElement(RepositoriesDslElement.REPOSITORIES))
      }
    )

    private val DECLARATIVE_ROOT_AVAILABLE_MODELS = listOf<BlockModelBuilder<*, GradleBuildFile>>(
      AndroidDeclarativeModel::class.java from { declarativeBuilder(it) },
      BuildScriptModel::class.java from { EmptyBuildScriptModel() },
      ConfigurationsModel::class.java from { EmptyConfigurationsModelImpl() },
      DependenciesModel::class.java from { EmptyDependenciesModelImpl() },
      ExtModel::class.java from { EmptyExtModelImpl() },
      JavaModel::class.java from { EmptyJavaModelImpl() },
      KotlinModel::class.java from { EmptyKotlinModelImpl() },
      RepositoriesModel::class.java from { EmptyRepositoriesModelImpl() }
    )
  }
}

private infix fun <M, P> Class<M>.from(action: (P) -> M): BlockModelBuilder<M, P> where M : GradleDslModel, P : GradlePropertiesDslElement {
  return object : BlockModelBuilder<M, P> {
    override fun modelClass() = this@from
    override fun create(p: P): M = action(p)
  }
}