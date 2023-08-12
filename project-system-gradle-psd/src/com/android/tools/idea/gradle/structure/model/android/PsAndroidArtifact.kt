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

import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.ANDROID_TEST_IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_FIXTURES_API
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_FIXTURES_COMPILE
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_FIXTURES_IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.dependencies.CommonConfigurationNames.TEST_IMPLEMENTATION
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.model.IdeBaseArtifact
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.icons.AllIcons
import com.intellij.icons.AllIcons.Modules.TestRoot
import com.intellij.icons.AllIcons.Nodes.Artifact
import com.intellij.openapi.util.text.StringUtil.capitalize
import icons.StudioIcons.Shell.Filetree.ANDROID_TEST_ROOT
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

class PsAndroidArtifact(override val parent: PsVariant, val resolvedName: IdeArtifactName)
  : PsChildModel() {

  override val name: String
  override val icon: Icon
  var resolvedModel: IdeBaseArtifact? = null

  constructor(parent: PsVariant, resolvedName: IdeArtifactName, resolvedModel: IdeBaseArtifact?): this(parent, resolvedName) {
    init(resolvedModel)
  }

  fun init(resolvedModel: IdeBaseArtifact?) {
    this.resolvedModel = resolvedModel
    myDependencies = null
  }

  init {
    var icon = Artifact
    var name = ""
    when (resolvedName) {
      IdeArtifactName.MAIN -> icon = AllIcons.Modules.SourceRoot
      IdeArtifactName.TEST_FIXTURES -> {
        name = "TestFixtures"
        icon = AllIcons.Modules.SourceRoot
      }
      IdeArtifactName.ANDROID_TEST -> {
        name = "AndroidTest"
        icon = ANDROID_TEST_ROOT
      }
      IdeArtifactName.UNIT_TEST -> {
        name = "UnitTest"
        icon = TestRoot
      }
    }
    this.name = name
    this.icon = icon
  }

  private var myDependencies: PsAndroidArtifactDependencyCollection? = null

  override val isDeclared: Boolean = false

  val dependencies: PsAndroidArtifactDependencyCollection
    get() = myDependencies ?: PsAndroidArtifactDependencyCollection(this).also { myDependencies = it }

  private val possibleConfigurationNames: List<String>
    get() {
      val variant = parent
      val buildTypeName = variant.buildType.name
      val productFlavorNames = variant.productFlavorNames
      return getPossibleConfigurationNames(resolvedName, buildTypeName, productFlavorNames)
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
}

@NonNls
private const val API_SUFFIX = "Api"
@NonNls
private const val COMPILE_SUFFIX = "Compile"
@NonNls
private const val IMPLEMENTATION_SUFFIX = "Implementation"

@VisibleForTesting
fun getPossibleConfigurationNames(
  resolvedName: IdeArtifactName,
  buildTypeName: String,
  productFlavorNames: List<String>
): List<String> {
  val configurationNames = mutableListOf<String>()
  when (resolvedName) {
    IdeArtifactName.MAIN -> {
      configurationNames.add(COMPILE)
      configurationNames.add(API)
      configurationNames.add(IMPLEMENTATION)
    }
    IdeArtifactName.UNIT_TEST -> {
      configurationNames.add(TEST_COMPILE)
      configurationNames.add(TEST_API)
      configurationNames.add(TEST_IMPLEMENTATION)
    }
    IdeArtifactName.ANDROID_TEST -> {
      configurationNames.add(ANDROID_TEST_COMPILE)
      configurationNames.add(ANDROID_TEST_API)
      configurationNames.add(ANDROID_TEST_IMPLEMENTATION)
    }
    IdeArtifactName.TEST_FIXTURES -> {
      configurationNames.add(TEST_FIXTURES_COMPILE)
      configurationNames.add(TEST_FIXTURES_API)
      configurationNames.add(TEST_FIXTURES_IMPLEMENTATION)
    }
  }

  when (resolvedName) {
    IdeArtifactName.MAIN -> {
      configurationNames.add(buildTypeName + COMPILE_SUFFIX)
      configurationNames.add(buildTypeName + API_SUFFIX)
      configurationNames.add(buildTypeName + IMPLEMENTATION_SUFFIX)
    }
    IdeArtifactName.UNIT_TEST -> {
      configurationNames.add("test" + capitalize(buildTypeName) + COMPILE_SUFFIX)
      configurationNames.add("test" + capitalize(buildTypeName) + API_SUFFIX)
      configurationNames.add("test" + capitalize(buildTypeName) + IMPLEMENTATION_SUFFIX)
    }
    else -> { }
  }

  productFlavorNames.forEach { productFlavorName ->
    when (resolvedName) {
      IdeArtifactName.MAIN -> {
        configurationNames.add(productFlavorName + COMPILE_SUFFIX)
        configurationNames.add(productFlavorName + API_SUFFIX)
        configurationNames.add(productFlavorName + IMPLEMENTATION_SUFFIX)
      }
      IdeArtifactName.UNIT_TEST -> {
        configurationNames.add("test" + capitalize(productFlavorName) + COMPILE_SUFFIX)
        configurationNames.add("test" + capitalize(productFlavorName) + API_SUFFIX)
        configurationNames.add("test" + capitalize(productFlavorName) + IMPLEMENTATION_SUFFIX)
      }
      IdeArtifactName.ANDROID_TEST -> {
        configurationNames.add("androidTest" + capitalize(productFlavorName) + COMPILE_SUFFIX)
        configurationNames.add("androidTest" + capitalize(productFlavorName) + API_SUFFIX)
        configurationNames.add("androidTest" + capitalize(productFlavorName) + IMPLEMENTATION_SUFFIX)
      }
      IdeArtifactName.TEST_FIXTURES -> {
        configurationNames.add("testFixtures" + capitalize(productFlavorName) + COMPILE_SUFFIX)
        configurationNames.add("testFixtures" + capitalize(productFlavorName) + API_SUFFIX)
        configurationNames.add("testFixtures" + capitalize(productFlavorName) + IMPLEMENTATION_SUFFIX)
      }
    }
  }
  return configurationNames
}

