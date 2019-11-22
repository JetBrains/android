/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.parser.android.AbstractProductFlavorDslElement
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement
import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement.AAPT_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement.ADB_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID
import com.android.tools.idea.gradle.dsl.parser.android.BuildFeaturesDslElement.BUILD_FEATURES
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement.BUILD_TYPES
import com.android.tools.idea.gradle.dsl.parser.android.CompileOptionsDslElement.COMPILE_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement.DATA_BINDING
import com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement.DEX_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD
import com.android.tools.idea.gradle.dsl.parser.android.KotlinOptionsDslElement.KOTLIN_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement.LINT_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement.PACKAGING_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement.PRODUCT_FLAVORS
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement.SIGNING_CONFIGS
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement.SOURCE_SETS
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement.SPLITS
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement.TEST_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.ViewBindingDslElement.VIEW_BINDING
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypeDslElement
import com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement
import com.android.tools.idea.gradle.dsl.parser.android.DefaultConfigDslElement
import com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement.CMAKE
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement.NDK_BUILD
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement.EXTERNAL_NATIVE_BUILD_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.VectorDrawablesOptionsDslElement.VECTOR_DRAWABLES_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement.CMAKE_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement.NDK_BUILD_OPTIONS
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement.ABI
import com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement.DENSITY
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement.LANGUAGE
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement.UNIT_TESTS
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationsDslElement.CONFIGURATIONS
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.java.JavaDslElement.JAVA
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement
import com.android.tools.idea.gradle.dsl.parser.plugins.PluginsDslElement.PLUGINS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement.FLAT_DIR_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement.CREDENTIALS_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.JCENTER_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.MAVEN_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.intellij.psi.PsiElement
import java.util.ArrayList

/**
 * Set of classes whose properties should not be merged into each other.
 */
private val makeDistinctClassSet = setOf(MavenRepositoryDslElement::class.java, FlatDirRepositoryDslElement::class.java)

/**
 * Get the block element that is given be repeat
 */
fun GradleDslFile.getBlockElement(
  nameParts: List<String>,
  converter: GradleDslNameConverter,
  parentElement: GradlePropertiesDslElement,
  nameElement: GradleNameElement? = null
): GradlePropertiesDslElement? {
  return nameParts.map { namePart -> namePart.trim { it <= ' ' } }.fold(parentElement) { resultElement, nestedElementName ->
    val canonicalNestedElementName = converter.modelNameForParent(nestedElementName, resultElement)
    val elementName = nameElement ?: GradleNameElement.fake(canonicalNestedElementName)
    var element = resultElement.getElement(canonicalNestedElementName)

    if (element != null && makeDistinctClassSet.contains(element::class.java)) {
      element = null // Force recreation of the element
    }

    if (element is GradlePropertiesDslElement) {
      return@fold element
    }

    if (element != null) return null

    // Handle special cases based on the elements name
    when (nestedElementName) {
      "rootProject" -> return@fold context.rootProjectFile ?: this
      // Ext element is supported for any Gradle domain object that implements ExtensionAware. Here we get or create
      // such an element if needed.
      EXT.name -> {
        val newElement = EXT.constructor.construct(resultElement, elementName)
        resultElement.setParsedElement(newElement)
        return@fold newElement
      }
      APPLY_BLOCK_NAME -> {
        val newApplyElement = ApplyDslElement(resultElement)
        resultElement.setParsedElement(newApplyElement)
        return@fold newApplyElement
      }
    }

    // Handle the normal cases
    val newElement: GradlePropertiesDslElement = when (resultElement) {
      is GradleDslFile -> createNewElementForFileOrSubProject(resultElement, nestedElementName) ?: return null
      is SubProjectsDslElement -> createNewElementForFileOrSubProject(resultElement, nestedElementName) ?: return null
      is BuildScriptDslElement -> when (nestedElementName) {
        DEPENDENCIES.name -> DEPENDENCIES.constructor.construct(resultElement, elementName)
        REPOSITORIES.name -> REPOSITORIES.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is RepositoriesDslElement -> when (nestedElementName) {
        MAVEN_BLOCK_NAME -> MavenRepositoryDslElement(resultElement, elementName)
        JCENTER_BLOCK_NAME -> MavenRepositoryDslElement(resultElement, elementName)
        FLAT_DIR_BLOCK_NAME -> FlatDirRepositoryDslElement(resultElement)
        else -> return null
      }
      is MavenRepositoryDslElement -> when (nestedElementName) {
        CREDENTIALS_BLOCK_NAME -> MavenCredentialsDslElement(resultElement)
        else -> return null
      }
      is AndroidDslElement -> when (nestedElementName) {
        "defaultConfig" -> DefaultConfigDslElement(resultElement, elementName)
        // TODO(xof): these should be (resultElement.blockMap.get(nestedElementName) ?: return null).constructor.construct(resultElement, elementName)
        //  (think also about how to make the "defaultConfig" case fit into a map lookup given that the interface must be different).
        AAPT_OPTIONS.name -> AAPT_OPTIONS.constructor.construct(resultElement, elementName)
        ADB_OPTIONS.name -> ADB_OPTIONS.constructor.construct(resultElement, elementName)
        BUILD_FEATURES.name -> BUILD_FEATURES.constructor.construct(resultElement, elementName)
        BUILD_TYPES.name -> BUILD_TYPES.constructor.construct(resultElement, elementName)
        COMPILE_OPTIONS.name -> COMPILE_OPTIONS.constructor.construct(resultElement, elementName)
        DATA_BINDING.name -> DATA_BINDING.constructor.construct(resultElement, elementName)
        DEX_OPTIONS.name -> DEX_OPTIONS.constructor.construct(resultElement, elementName)
        EXTERNAL_NATIVE_BUILD.name -> EXTERNAL_NATIVE_BUILD.constructor.construct(resultElement, elementName)
        KOTLIN_OPTIONS.name -> KOTLIN_OPTIONS.constructor.construct(resultElement, elementName)
        LINT_OPTIONS.name -> LINT_OPTIONS.constructor.construct(resultElement, elementName)
        PACKAGING_OPTIONS.name -> PACKAGING_OPTIONS.constructor.construct(resultElement, elementName)
        PRODUCT_FLAVORS.name -> PRODUCT_FLAVORS.constructor.construct(resultElement, elementName)
        SIGNING_CONFIGS.name -> SIGNING_CONFIGS.constructor.construct(resultElement, elementName)
        SOURCE_SETS.name -> SOURCE_SETS.constructor.construct(resultElement, elementName)
        SPLITS.name -> SPLITS.constructor.construct(resultElement, elementName)
        TEST_OPTIONS.name -> TEST_OPTIONS.constructor.construct(resultElement, elementName)
        VIEW_BINDING.name -> VIEW_BINDING.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is ExternalNativeBuildDslElement -> when (nestedElementName) {
        CMAKE.name -> CMAKE.constructor.construct(resultElement, elementName)
        NDK_BUILD.name -> NDK_BUILD.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is ProductFlavorsDslElement -> ProductFlavorDslElement(resultElement, elementName)
      is AbstractProductFlavorDslElement -> when (nestedElementName) {
        "manifestPlaceholders" -> GradleDslExpressionMap(resultElement, elementName)
        "testInstrumentationRunnerArguments" -> GradleDslExpressionMap(resultElement, elementName)
        EXTERNAL_NATIVE_BUILD_OPTIONS.name -> EXTERNAL_NATIVE_BUILD_OPTIONS.constructor.construct(resultElement, elementName)
        NDK_OPTIONS.name -> NDK_OPTIONS.constructor.construct(resultElement, elementName)
        VECTOR_DRAWABLES_OPTIONS.name -> VECTOR_DRAWABLES_OPTIONS.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is BuildTypesDslElement -> BuildTypeDslElement(resultElement, elementName)
      is BuildTypeDslElement -> when (nestedElementName) {
        "manifestPlaceholders" -> GradleDslExpressionMap(resultElement, elementName)
        else -> return null
      }
      is SigningConfigsDslElement -> SigningConfigDslElement(resultElement, elementName)
      is SourceSetsDslElement -> SourceSetDslElement(resultElement, elementName)
      is SourceSetDslElement -> when (nestedElementName) {
        "manifest" -> SourceFileDslElement(resultElement, elementName)
        else -> SourceDirectoryDslElement(resultElement, elementName)
      }
      is ExternalNativeBuildOptionsDslElement -> when (nestedElementName) {
        CMAKE_OPTIONS.name -> CMAKE_OPTIONS.constructor.construct(resultElement, elementName)
        NDK_BUILD_OPTIONS.name -> NDK_BUILD_OPTIONS.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is SplitsDslElement -> when (nestedElementName) {
        ABI.name -> ABI.constructor.construct(resultElement, elementName)
        DENSITY.name-> DENSITY.constructor.construct(resultElement, elementName)
        LANGUAGE.name -> LANGUAGE.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is TestOptionsDslElement -> when (nestedElementName) {
        UNIT_TESTS.name -> UNIT_TESTS.constructor.construct(resultElement, elementName)
        else -> return null
      }
      is ConfigurationsDslElement -> ConfigurationDslElement(resultElement, elementName)
      // we're not going to be clever about the contents of a ConfigurationDslElement: but we do need
      // to record whether there's anything there or not.
      is ConfigurationDslElement -> GradleDslClosure(resultElement, null, elementName)
      else -> return null
    }

    resultElement.setParsedElement(newElement)
    return@fold newElement
  }
}

private fun createNewElementForFileOrSubProject(resultElement: GradlePropertiesDslElement,
                                                nestedElementName: String): GradlePropertiesDslElement? {
  val elementName = GradleNameElement.fake(nestedElementName)
  return when (nestedElementName) {
    ANDROID.name -> ANDROID.constructor.construct(resultElement, elementName)
    BUILDSCRIPT.name -> BUILDSCRIPT.constructor.construct(resultElement.dslFile, elementName)
    CONFIGURATIONS.name -> CONFIGURATIONS.constructor.construct(resultElement, elementName)
    DEPENDENCIES.name -> DEPENDENCIES.constructor.construct(resultElement, elementName)
    EXT.name -> EXT.constructor.construct(resultElement, elementName)
    JAVA.name -> JAVA.constructor.construct(resultElement, elementName)
    REPOSITORIES.name -> REPOSITORIES.constructor.construct(resultElement, elementName)
    SUBPROJECTS_BLOCK_NAME -> SubProjectsDslElement(resultElement)
    PLUGINS_BLOCK_NAME -> PluginsDslElement(resultElement)
    else -> {
      val projectKey = ProjectPropertiesDslElement.getStandardProjectKey(nestedElementName) ?: return null
      return ProjectPropertiesDslElement(resultElement, GradleNameElement.fake(projectKey))
    }
  }
}

/**
 * Get the parent dsl element with a valid psi
 */
internal fun getNextValidParent(element: GradleDslElement): GradleDslElement? {
  var element : GradleDslElement? = element
  var psi = element?.psiElement
  while (element != null && (psi == null || !psi.isValid)) {
    element = element.parent ?: return element

    psi = element.psiElement
  }

  return element
}

internal fun removePsiIfInvalid(element: GradleDslElement?) {
  if (element == null) return

  if (element.psiElement != null && !element.psiElement!!.isValid) {
    element.psiElement = null
  }

  if (element.parent != null) {
    removePsiIfInvalid(element.parent)
  }
}

/**
 * @param startElement starting element
 * @return the last non-null psi element in the tree starting at node startElement.
 */
internal fun findLastPsiElementIn(startElement: GradleDslElement): PsiElement? {
  val psiElement = startElement.psiElement
  if (psiElement != null) {
    return psiElement
  }

  for (element in Lists.reverse(ArrayList(startElement.children))) {
    if (element != null) {
      val psi = findLastPsiElementIn(element)
      if (psi != null) {
        return psi
      }
    }
  }
  return null
}

/**
 * Get the external name of a dsl element by trimming the parent's name parts and converting the name from model to external, if necessary,
 * returning a pair of the name and whether this is a method call an assignment or unknown (see
 * [GradleDslNameConverter.externalNameForParent])
 */
internal fun maybeTrimForParent(name: GradleNameElement,
                                parent: GradleDslElement?,
                                converter: GradleDslNameConverter): Pair<String, Boolean?> {
  // FIXME(xof): this case needs fixing too
  if (parent == null) return name.fullName() to null

  val parts = ArrayList(name.fullNameParts())
  if (parts.isEmpty()) {
    return name.fullName() to null
  }
  var lastNamePart = parts.removeAt(parts.size - 1)
  val parentParts = Splitter.on(".").splitToList(parent.qualifiedName)
  var i = 0
  while (i < parentParts.size && !parts.isEmpty() && parentParts[i] == parts[0]) {
    parts.removeAt(0)
    i++
  }

  val externalNameInfo = converter.externalNameForParent(lastNamePart, parent)

  lastNamePart = externalNameInfo.first
  parts.add(lastNamePart)
  return GradleNameElement.createNameFromParts(parts) to externalNameInfo.second
}
