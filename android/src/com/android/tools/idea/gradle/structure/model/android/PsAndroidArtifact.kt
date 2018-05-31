/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.AndroidProject.*
import com.android.ide.common.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.*
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Lists
import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Modules.TestRoot
import com.intellij.icons.AllIcons.Nodes.Artifact
import com.intellij.openapi.util.text.StringUtil.capitalize
import icons.AndroidIcons.AndroidTestRoot
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class PsAndroidArtifact(override val parent: PsVariant, val resolvedName: String)
  : PsChildModel(), PsAndroidModel {

  override val name: String
  override val icon: Icon
  override var resolvedModel: IdeBaseArtifact? = null

  constructor(parent: PsVariant, resolvedName: String, resolvedModel: IdeBaseArtifact?): this(parent, resolvedName) {
    init(resolvedModel)
  }

  fun init(resolvedModel: IdeBaseArtifact?) {
    this.resolvedModel = resolvedModel
  }

  init {
    var icon = Artifact
    var name = ""
    when (resolvedName) {
      ARTIFACT_MAIN -> icon = AllIcons.Modules.SourceRoot
      ARTIFACT_ANDROID_TEST -> {
        name = "AndroidTest"
        icon = AndroidTestRoot
      }
      ARTIFACT_UNIT_TEST -> {
        name = "UnitTest"
        icon = TestRoot
      }
    }
    this.name = name
    this.icon = icon
  }

  private var myDependencies: PsAndroidArtifactDependencyCollection? = null

  override val gradleModel: AndroidModuleModel get() = parent.gradleModel

  override val isDeclared: Boolean = false

  val dependencies: PsAndroidArtifactDependencyCollection
    get() = myDependencies ?: PsAndroidArtifactDependencyCollection(this).also { myDependencies = it }

  val possibleConfigurationNames: List<String>
    @VisibleForTesting
    get() {
      val configurationNames = Lists.newArrayList<String>()
      when (resolvedName) {
        ARTIFACT_MAIN -> {
          configurationNames.add(COMPILE)
          configurationNames.add(API)
          configurationNames.add(IMPLEMENTATION)
        }
        ARTIFACT_UNIT_TEST -> {
          configurationNames.add(TEST_COMPILE)
          configurationNames.add(TEST_API)
          configurationNames.add(TEST_IMPLEMENTATION)
        }
        ARTIFACT_ANDROID_TEST -> {
          configurationNames.add(ANDROID_TEST_COMPILE)
          configurationNames.add(ANDROID_TEST_API)
          configurationNames.add(ANDROID_TEST_IMPLEMENTATION)
        }
      }

      val variant = parent

      val buildTypeName = variant.buildType.name
      when (resolvedName) {
        ARTIFACT_MAIN -> {
          configurationNames.add(buildTypeName + COMPILE_SUFFIX)
          configurationNames.add(buildTypeName + API_SUFFIX)
          configurationNames.add(buildTypeName + IMPLEMENTATION_SUFFIX)
        }
        ARTIFACT_UNIT_TEST -> {
          configurationNames.add("test" + capitalize(buildTypeName) + COMPILE_SUFFIX)
          configurationNames.add("test" + capitalize(buildTypeName) + API_SUFFIX)
          configurationNames.add("test" + capitalize(buildTypeName) + IMPLEMENTATION_SUFFIX)
        }
      }

      variant.forEachProductFlavor { productFlavor ->
        val productFlavorName = productFlavor.name
        when (resolvedName) {
          ARTIFACT_MAIN -> {
            configurationNames.add(productFlavorName + COMPILE_SUFFIX)
            configurationNames.add(productFlavorName + API_SUFFIX)
            configurationNames.add(productFlavorName + IMPLEMENTATION_SUFFIX)
          }
          ARTIFACT_UNIT_TEST -> {
            configurationNames.add("test" + capitalize(productFlavorName) + COMPILE_SUFFIX)
            configurationNames.add("test" + capitalize(productFlavorName) + API_SUFFIX)
            configurationNames.add("test" + capitalize(productFlavorName) + IMPLEMENTATION_SUFFIX)
          }
          ARTIFACT_ANDROID_TEST -> {
            configurationNames.add("androidTest" + capitalize(productFlavorName) + COMPILE_SUFFIX)
            configurationNames.add("androidTest" + capitalize(productFlavorName) + API_SUFFIX)
            configurationNames.add("androidTest" + capitalize(productFlavorName) + IMPLEMENTATION_SUFFIX)
          }
        }
      }
      return configurationNames
    }

  internal fun resetDependencies() {
    myDependencies = null
  }

  operator fun contains(parsedDependency: DependencyModel): Boolean {
    val configurationName = parsedDependency.configurationName()
    return containsConfigurationName(configurationName)
  }

  fun containsConfigurationName(configurationName: String): Boolean {
    return possibleConfigurationNames.contains(configurationName)
  }

  companion object {
    @NonNls
    private val API_SUFFIX = "Api"
    @NonNls
    private val COMPILE_SUFFIX = "Compile"
    @NonNls
    private val IMPLEMENTATION_SUFFIX = "Implementation"
  }
}
