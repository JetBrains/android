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

import com.android.tools.idea.AndroidPsiUtils.getPsiParentsOfType
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.fileClasses.javaFileFacadeFqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.TasksToRun

/**
 * A configuration producer for creating Gradle run configurations for screenshot tests for a given class in the screenshot test source set.
 * This class extends {@link TestClassGradleConfigurationProducer} to provide specialized configuration for screenshot testing. The
 * configuration is only produced if there is at least one method within the class that is annotated with a Preview or multi-Preview
 * annotation
 */
class ScreenshotTestClassGradleConfigurationProducer : TestClassGradleConfigurationProducer() {
  private val visitedAnnotation = mutableMapOf<String, Boolean>()

  override fun suggestConfigurationName(context: ConfigurationContext, element: PsiClass, chosenElements: List<PsiClass>): String {
    return "Screenshot Tests in ${element.qualifiedName}"
  }

  override fun doIsConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
    if (configuration.getUserData<Boolean>(IS_SCREENSHOT_TEST_CONFIGURATION) != true) {
      return false
    }

    val location = context.location ?: return false
    val myModule = AndroidUtils.getAndroidModule(context) ?: return false
    val facet = AndroidFacet.getInstance(myModule) ?: return false
    if (!isScreenshotTestSourceSet(location, facet)) return false
    // TODO: Enable screenshot tests for release variants as well.
    if (!myModule.getModuleSystem().isDebuggable) return false

    val expectedTasks: List<String>

    val psiClass =
      getPsiParentsOfType(location.psiElement, PsiClass::class.java, false).firstOrNull()
        ?: getPsiParentsOfType(location.psiElement, KtClassOrObject::class.java, false).firstOrNull()?.toLightClass()
    if (psiClass != null && isClassDeclarationWithPreviewTestAnnotatedMethods(psiClass, visitedAnnotation)) {
      expectedTasks = taskNamesWithFilter(context, listOfNotNull(psiClass.qualifiedName))
    } else {
      val containingFile = location.psiElement as? PsiClassOwner ?: location.psiElement.containingFile as? PsiClassOwner
      if (containingFile != null) {
        val qualifiedNames = mutableSetOf<String>()

        containingFile.classes.forEach { classElement ->
          if (isClassDeclarationWithPreviewTestAnnotatedMethods(classElement, visitedAnnotation)) {
            classElement.qualifiedName?.let { qualifiedNames.add(it) }
          }
        }
        if (containingFile is KtFile) {
          val hasTopLevelTests =
            containingFile.declarations.any { declaration ->
              (declaration as? KtNamedFunction)?.toLightMethods()?.any { method ->
                isMethodDeclarationPreviewTestAnnotated(method, visitedAnnotation)
              } == true
            }
          if (hasTopLevelTests) {
            qualifiedNames.add(containingFile.javaFileFacadeFqName.asString())
          }
        }

        if (qualifiedNames.isEmpty()) {
          return false
        }
        expectedTasks = taskNamesWithFilter(context, qualifiedNames.toList())
      } else {
        return false
      }
    }

    return configuration.settings.taskNames == expectedTasks
  }

  override fun getAllTestsTaskToRun(
    context: ConfigurationContext,
    element: PsiClass,
    chosenElements: List<PsiClass>,
  ): List<TestTasksToRun> {
    val tasksToRun = mutableListOf<TestTasksToRun>()
    val testFilter = "--tests \"${element.qualifiedName}\""
    val tasks = getScreenshotTestTaskNames(context) ?: return tasksToRun
    tasksToRun.add(TestTasksToRun(TasksToRun.Impl("screenshotTest", tasks), testFilter))
    return tasksToRun
  }

  override fun doSetupConfigurationFromContext(
    configuration: GradleRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>,
  ): Boolean {
    if (!StudioFlags.ENABLE_SCREENSHOT_TESTING.get()) {
      return false
    }
    val configured = configure(configuration, sourceElement, context)
    if (configured) {
      configuration.putUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey, true)
      configuration.putUserData<Boolean>(IS_SCREENSHOT_TEST_CONFIGURATION, true)
    }
    return configured
  }

  private fun configure(configuration: GradleRunConfiguration, sourceElementRef: Ref<PsiElement>, context: ConfigurationContext): Boolean {
    val location = context.location ?: return false

    val myModule = AndroidUtils.getAndroidModule(context) ?: return false
    val facet = AndroidFacet.getInstance(myModule) ?: return false
    if (!isScreenshotTestSourceSet(location, facet)) {
      return false
    }
    // TODO: Enable screenshot tests for release variants as well.
    if (!myModule.getModuleSystem().isDebuggable) return false

    val project = context.project ?: return false

    // Try to resolve the context to an enclosing class (e.g., when right-clicking inside a class in the editor)
    val psiClass =
      getPsiParentsOfType(location.psiElement, PsiClass::class.java, false).firstOrNull()
        ?: getPsiParentsOfType(location.psiElement, KtClassOrObject::class.java, false).firstOrNull()?.toLightClass()
    if (psiClass != null && isClassDeclarationWithPreviewTestAnnotatedMethods(psiClass, visitedAnnotation)) {
      sourceElementRef.set(psiClass)
      configuration.settings.externalProjectPath = project.basePath
      configuration.name = suggestConfigurationName(context, psiClass, emptyList())
      configuration.settings.taskNames = taskNamesWithFilter(context, listOfNotNull(psiClass.qualifiedName))
      return true
    }

    // Fallback to file level if no enclosing class is found (e.g., right-clicking outside any class in the editor, or on a file in the
    // Project view)
    val containingFile = location.psiElement as? PsiClassOwner ?: location.psiElement.containingFile as? PsiClassOwner
    if (containingFile != null) {
      val qualifiedNames = mutableSetOf<String>()

      // Collect all classes in the file that have screenshot tests.
      containingFile.classes.forEach { classElement ->
        if (isClassDeclarationWithPreviewTestAnnotatedMethods(classElement, visitedAnnotation)) {
          classElement.qualifiedName?.let { qualifiedNames.add(it) }
        }
      }

      // Also check for top-level functions.
      if (containingFile is KtFile) {
        val hasTopLevelTests =
          containingFile.declarations.any { declaration ->
            (declaration as? KtNamedFunction)?.toLightMethods()?.any { method ->
              isMethodDeclarationPreviewTestAnnotated(method, visitedAnnotation)
            } == true
          }
        if (hasTopLevelTests) {
          qualifiedNames.add(containingFile.javaFileFacadeFqName.asString())
        }
      }

      if (qualifiedNames.isEmpty()) {
        return false
      }

      // A representative PsiElement is required by the base producer.
      val representativeElement = containingFile.classes.firstOrNull() ?: containingFile
      sourceElementRef.set(representativeElement)

      configuration.settings.externalProjectPath = project.basePath
      configuration.name = "Screenshot Tests in ${containingFile.name}"
      configuration.settings.taskNames = taskNamesWithFilter(context, qualifiedNames.toList())
      return true
    }

    return false
  }

  private fun taskNamesWithFilter(context: ConfigurationContext, qualifiedNames: List<String>): List<String> {
    val baseTasks = getScreenshotTestTaskNames(context) ?: return emptyList()
    val testFilters = qualifiedNames.flatMap { listOf("--tests", "\"$it\"") }
    return baseTasks + testFilters
  }
}
