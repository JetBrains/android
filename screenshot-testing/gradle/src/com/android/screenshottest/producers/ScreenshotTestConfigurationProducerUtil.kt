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

import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.IdeaSourceProvider
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.containsFile
import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.util.Key
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.util.GradleUtil
import org.jetbrains.plugins.gradle.util.gradleIdentityPath
import com.android.ide.common.gradle.Version
import com.android.ide.common.gradle.Module as GradleModule
import com.android.tools.idea.projectsystem.DependencyScopeType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem
import com.android.tools.idea.projectsystem.gradle.getScreenshotTestModule

private const val SCREENSHOT_VALIDATION_GROUP_ID = "com.android.tools.screenshot"
private const val SCREENSHOT_VALIDATION_ARTIFACT_ID = "screenshot-validation-api"
private const val PREVIEW_TEST_ANNOTATION = "com.android.tools.screenshot.PreviewTest"
private const val MIN_SCREENSHOT_PLUGIN_VERSION = "0.0.1-alpha12"

val IS_SCREENSHOT_TEST_CONFIGURATION = Key.create<Boolean>("com.android.tools.idea.testartifacts.screenshot.isScreenshotTest")

/**
 * Checks if the given location belongs to screenshot test source set.
 * It verifies if the virtual file associated with the location either
 * - is contained within the screenshot test or generated screenshot test source roots, or
 * - contains the screenshot test or generated screenshot test source sets
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

/**
 * Returns the version string of the screenshot plugin (e.g. "0.0.1-alpha12")
 * using the GradleModuleSystem to resolve dependencies.
 */
fun getScreenshotTestPluginVersion(module: Module): Version? {
  val moduleSystem = module.getModuleSystem() as? GradleModuleSystem ?: return null

  val gradleModule = GradleModule(
    SCREENSHOT_VALIDATION_GROUP_ID,
    SCREENSHOT_VALIDATION_ARTIFACT_ID
  )
  // Return the version string directly
  return moduleSystem.getResolvedDependency(gradleModule, DependencyScopeType.MAIN)?.version
}

fun isScreenshotPluginVersionValid(context: ConfigurationContext): Boolean {
  val module = context.module ?: return false

  val screenshotModule = module.getScreenshotTestModule() ?: return false
  return try {
    val current = getScreenshotTestPluginVersion(screenshotModule) ?: return false
    val required = Version.parse(MIN_SCREENSHOT_PLUGIN_VERSION)
    current >= required
  } catch (e: Throwable) {
    false
  }
}

/**
 * Retrieves the name of the Gradle task for screenshot validation .
 * It uses the Gradle Android model and module data to construct the task names.
 *
 * @param context The configuration context.
 * @return A list of String containing the screenshot test task name, or {@code null} if any required information is missing.
 */
fun getScreenshotTestTaskNames(context: ConfigurationContext): List<String>? {
  val myModule = AndroidUtils.getAndroidModule(context) ?: return null
  val facet = AndroidFacet.getInstance(myModule) ?: return null
  val androidModel = GradleAndroidModel.get(facet) ?: return null
  val moduleData = GradleUtil.findGradleModuleData(myModule)?.data ?: return null
  return listOf(
    moduleData.gradleIdentityPath.trimEnd(':') + ":" +
      androidModel.getGradleScreenshotTestTaskNameForSelectedVariant("validate")
  )
}


/**
 * Checks if a given class declaration contains any methods annotated with the Compose Preview annotation or compose multi preview annotation
 *
 * @param psiClass The PSI class to check.
 * @param visitedAnnotations A mutable map to track visited annotations to avoid infinite recursion.
 * @return {@code true} if the class has at least one preview-annotated method, {@code false} otherwise.
 */
fun isClassDeclarationWithPreviewTestAnnotatedMethods(psiClass: PsiClass, visitedAnnotations: MutableMap<String, Boolean> = mutableMapOf()): Boolean {
  return psiClass.methods.any {
    isMethodDeclarationPreviewTestAnnotated(it, visitedAnnotations)
  }
}

/**
 * Checks if a given method declaration is annotated with the Compose Preview annotation or any multi preview annotation.
 *
 * @param psiMethod The PSI method to check.
 * @param visitedAnnotations A mutable map to track visited annotations to avoid infinite recursion.
 * @return {@code true} if the method is preview annotated, {@code false} otherwise.
 */
fun isMethodDeclarationPreviewTestAnnotated(psiMethod: PsiMethod, visitedAnnotations: MutableMap<String, Boolean> = mutableMapOf()) : Boolean {
  return psiMethod.annotations.any{ it.qualifiedName == PREVIEW_TEST_ANNOTATION}
}

private fun IdeaSourceProvider.containedIn(targetFolder: VirtualFile): Boolean {
  return manifestFileUrls.any { manifestFileUrl -> VfsUtilCore.isEqualOrAncestor(targetFolder.url, manifestFileUrl) } ||
         allSourceFolderUrls().any { sourceFolderUrl -> VfsUtilCore.isEqualOrAncestor(targetFolder.url, sourceFolderUrl) }
}

private fun IdeaSourceProvider.allSourceFolderUrls() : Sequence<String> {
  return arrayOf(
    javaDirectoryUrls,
    resDirectoryUrls,
    aidlDirectoryUrls,
    renderscriptDirectoryUrls,
    assetsDirectoryUrls,
    jniLibsDirectoryUrls
  ).asSequence()
    .flatten()
}