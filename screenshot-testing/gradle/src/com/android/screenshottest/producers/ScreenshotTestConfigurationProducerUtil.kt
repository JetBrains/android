/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.screenshottest.producers

import com.android.tools.idea.gradle.model.IdeTestSuite
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.model.gradleModuleModel
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.containsFile
import com.android.tools.idea.testartifacts.testsuite.runconfiguration.TestSuiteUtils
import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiUtilCore
import java.util.Locale
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.gradleIdentityPath

const val SCREENSHOT_TEST_ENGINE_ID = "preview-screenshot-test-engine"
const val DEFAULT_VERIFICATION_TARGET_NAME = "Default"

private const val PREVIEW_TEST_ANNOTATION = "com.android.tools.screenshot.PreviewTest"

val IS_SCREENSHOT_TEST_CONFIGURATION = Key.create<Boolean>("com.android.tools.idea.testartifacts.screenshot.isScreenshotTest")
val IS_SCREENSHOT_UPDATE_CONFIGURATION = Key.create<Boolean>("com.android.tools.idea.testartifacts.screenshot.isScreenshotUpdate")

/** Returns true if this [IdeTestSuite] uses the screenshot validation test engine. */
fun IdeTestSuite.isScreenshotTestSuite(): Boolean {
  return junitEngineInfo.includedEngines.contains(SCREENSHOT_TEST_ENGINE_ID)
}

private fun String.capitalize(): String = replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

/**
 * Checks if the given location belongs to screenshot test source set. It verifies if the virtual file associated with the location either
 * - is contained within the screenshot test or generated screenshot test source roots, or
 * - contains the screenshot test or generated screenshot test source sets
 *
 * @param location The location of the PSI element to check.
 * @param facet The Android facet associated with the project.
 * @return {@code true} if the location is within a screenshot test source set, {@code false} otherwise.
 */
fun isScreenshotTestSourceSet(location: Location<PsiElement>, facet: AndroidFacet): Boolean {
  val sourceProviders = SourceProviderManager.getInstance(facet)
  val virtualFile = PsiUtilCore.getVirtualFile(location.psiElement) ?: return false
  sourceProviders.hostTestSources[CommonTestType.SCREENSHOT_TEST]?.let {
    if (it.containsFile(virtualFile) || it.containedIn(virtualFile)) {
      return true
    }
  }
  sourceProviders.generatedHostTestSources[CommonTestType.SCREENSHOT_TEST]?.let {
    if (it.containsFile(virtualFile) || it.containedIn(virtualFile)) {
      return true
    }
  }
  return false
}

/** Checks if the module has the legacy standalone screenshot plugin applied. */
fun isLegacyScreenshotPluginApplied(module: Module): Boolean {
  val gradleModuleModel = module.gradleModuleModel ?: return false
  return gradleModuleModel.hasComposeScreenshotPlugin
}

/**
 * Retrieves the Gradle task name(s) for screenshot validation.
 *
 * For legacy screenshot plugin consumers, this resolves to the standard `:validate<Variant>ScreenshotTest` task. For AGP native screenshot
 * test suites, this maps the clicked file to its containing test suite and constructs the Gradle 9 test suite execution task for the
 * default verification target (e.g. `:test<SuiteName>Default<VariantName>TestSuite`).
 *
 * Note: Target resolution is currently hardcoded to the default verification target to prevent accidentally executing companion
 * recording/update tasks from IDE run configurations.
 *
 * @param context The configuration context.
 * @return A list of Strings containing the screenshot test task name(s), or `null` if required information is missing.
 */
fun getScreenshotTestTaskNames(context: ConfigurationContext): List<String>? {
  val myModule = AndroidUtils.getAndroidModule(context) ?: return null
  val facet = AndroidFacet.getInstance(myModule) ?: return null
  val androidModel = GradleAndroidModel.get(facet) ?: return null
  val moduleData = GradleUtil.findGradleModuleData(myModule)?.data ?: return null
  val modulePath = moduleData.gradleIdentityPath.trimEnd(':')

  if (isLegacyScreenshotPluginApplied(myModule)) {
    val taskName = androidModel.getGradleScreenshotTestTaskNameForSelectedVariant("validate")
    return listOf("$modulePath:$taskName")
  } else {
    val variantName = androidModel.selectedVariantName.capitalize()
    val screenshotSuites = androidModel.testSuites.filter { it.isScreenshotTestSuite() }

    val virtualFile = context.location?.psiElement?.let { PsiUtilCore.getVirtualFile(it) }

    val matchingSuite =
      if (virtualFile != null) {
        TestSuiteUtils.getTestSuiteAtRoot(screenshotSuites, virtualFile)
          ?: TestSuiteUtils.getTestSuiteContainingFile(screenshotSuites, virtualFile)
      } else {
        null
      }

    val suiteNames =
      if (matchingSuite != null) {
        listOf(matchingSuite.name)
      } else if (screenshotSuites.isNotEmpty()) {
        screenshotSuites.map { it.name }
      } else {
        listOf("screenshotTest")
      }

    // TODO(b/530362351): The target name is currently hardcoded to "Default" to ensure we only execute
    // verification tasks and never accidentally trigger companion recording/update targets (e.g. "defaultUpdate",
    // which overwrites reference screenshots). Once the AGP test suite model exposes whether a target is a
    // recording vs. verification target, dynamically resolve the target name from IdeTestSuiteVariantTarget
    // while filtering out recording targets.
    return suiteNames.map { suiteName ->
      val capitalizedSuiteName = suiteName.capitalize()
      "$modulePath:test$capitalizedSuiteName$DEFAULT_VERIFICATION_TARGET_NAME${variantName}TestSuite"
    }
  }
}

/**
 * Checks if a given class declaration contains any methods annotated with the Compose Preview annotation or compose multi preview
 * annotation
 *
 * @param psiClass The PSI class to check.
 * @param visitedAnnotations A mutable map to track visited annotations to avoid infinite recursion.
 * @return {@code true} if the class has at least one preview-annotated method, {@code false} otherwise.
 */
fun isClassDeclarationWithPreviewTestAnnotatedMethods(
  psiClass: PsiClass,
  visitedAnnotations: MutableMap<String, Boolean> = mutableMapOf(),
): Boolean {
  return psiClass.methods.any { isMethodDeclarationPreviewTestAnnotated(it, visitedAnnotations) }
}

/**
 * Checks if a given method declaration is annotated with the Compose Preview annotation or any multi preview annotation.
 *
 * @param psiMethod The PSI method to check.
 * @param visitedAnnotations A mutable map to track visited annotations to avoid infinite recursion.
 * @return {@code true} if the method is preview annotated, {@code false} otherwise.
 */
fun isMethodDeclarationPreviewTestAnnotated(
  psiMethod: PsiMethod,
  visitedAnnotations: MutableMap<String, Boolean> = mutableMapOf(),
): Boolean {
  return psiMethod.annotations.any { it.qualifiedName == PREVIEW_TEST_ANNOTATION }
}

private fun IdeaSourceProvider.containedIn(targetFolder: VirtualFile): Boolean {
  return manifestFileUrls.any { manifestFileUrl -> VfsUtilCore.isEqualOrAncestor(targetFolder.url, manifestFileUrl) } ||
    allSourceFolderUrls().any { sourceFolderUrl -> VfsUtilCore.isEqualOrAncestor(targetFolder.url, sourceFolderUrl) }
}

private fun IdeaSourceProvider.allSourceFolderUrls(): Sequence<String> {
  return arrayOf(
      javaDirectoryUrls,
      resDirectoryUrls,
      aidlDirectoryUrls,
      renderscriptDirectoryUrls,
      assetsDirectoryUrls,
      jniLibsDirectoryUrls,
    )
    .asSequence()
    .flatten()
}
