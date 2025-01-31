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
package com.android.tools.idea.testartifacts.screenshot

import com.android.tools.idea.AndroidPsiUtils.getPsiParentsOfType
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.execution.test.runner.TestClassGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.TasksToRun

/**
 * A configuration producer for creating Gradle run configurations for screenshot tests
 * for a given class in the screenshot test source set. This class extends {@link TestClassGradleConfigurationProducer}
 * to provide specialized configuration for screenshot testing. The configuration is only produced
 * if there is at least one method within the class that is annotated with a Preview or multi-Preview annotation
 */
class ScreenshotTestClassGradleConfigurationProducer: TestClassGradleConfigurationProducer() {
  private val visitedAnnotation = mutableMapOf<String, Boolean>()
  override fun suggestConfigurationName(context: ConfigurationContext, element: PsiClass, chosenElements: List<PsiClass>): String {
    return "Screenshot Tests in ${element.qualifiedName}"
  }

  override fun doIsConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
    val location = context.location ?: return false
    val psiClass = getPsiParentsOfType(location.psiElement, PsiClass::class.java, false).firstOrNull()?:
                   getPsiParentsOfType(location.psiElement, PsiClassOwner::class.java, false).firstOrNull()?.classes?.firstOrNull() ?: return false

    val androidFacet = AndroidFacet.getInstance(AndroidUtils.getAndroidModule(context)!!) ?: return false
    if (!isScreenshotTestSourceSet(location, androidFacet)) return false
    if (!isClassDeclarationWithPreviewAnnotatedMethods(psiClass, visitedAnnotation)) return false

    val configurationTaskNames = configuration.settings.taskNames
    return configurationTaskNames == taskNamesWithFilter(context, psiClass)
  }

  override fun getAllTestsTaskToRun(context: ConfigurationContext,
                                    element: PsiClass,
                                    chosenElements: List<PsiClass>): List<TestTasksToRun> {
    val tasksToRun = mutableListOf<TestTasksToRun>()
    val testFilter = "--tests \"${element.qualifiedName}*\""
    val tasks = getScreenshotTestTaskNames(context) ?: return tasksToRun
    tasksToRun.add(TestTasksToRun(TasksToRun.Impl("screenshotTest", tasks), testFilter))
    return tasksToRun
  }

  override fun doSetupConfigurationFromContext(configuration: GradleRunConfiguration,
                                               context: ConfigurationContext,
                                               sourceElement: Ref<PsiElement>): Boolean {
    if (!StudioFlags.ENABLE_SCREENSHOT_TESTING.get())
      return false
    return configure(configuration, sourceElement, context)
  }

  private fun configure(configuration: GradleRunConfiguration, sourceElementRef: Ref<PsiElement>, context: ConfigurationContext): Boolean {
    val location = context.location ?: return false

    val myModule = AndroidUtils.getAndroidModule(context) ?: return false
    val facet = AndroidFacet.getInstance(myModule) ?: return false
    if (!isScreenshotTestSourceSet(location, facet)) {
      return false
    }

    val project = context.project ?: return false
    val candidates = sequence<PsiClass> {
      // First, looks up parents of PsiClass from the context location.
      yieldAll(getPsiParentsOfType(location.psiElement, PsiClass::class.java, false))

      // If there are no PsiClass ancestors of the context location, find PsiClassOwner ancestors and
      // look up their classes. We don't search recursively so nested classes may be overlooked.
      // For instance, if there are two top-level classes A and B in a file, the class B has a nested
      // class C with @RunWith annotation, and the context location is at the class A, the class C is
      // not discovered.
      getPsiParentsOfType(location.psiElement, PsiClassOwner::class.java, false).forEach { classOwner ->
        yieldAll(classOwner.classes.iterator())
      }
    }
    return candidates.any { psiClass ->
      if (!isClassDeclarationWithPreviewAnnotatedMethods(psiClass, visitedAnnotation)) return false
      sourceElementRef.set(psiClass)
      configuration.settings.externalProjectPath = project.basePath
      configuration.name = suggestConfigurationName(context, psiClass, emptyList())
      configuration.settings.taskNames = taskNamesWithFilter(context, psiClass)
      return true
    }
  }

  private fun taskNamesWithFilter(context: ConfigurationContext, psiClass: PsiClass): List<String> {
    return getScreenshotTestTaskNames(context)!! + "--tests" + "\"${psiClass.qualifiedName}*\""
  }
}