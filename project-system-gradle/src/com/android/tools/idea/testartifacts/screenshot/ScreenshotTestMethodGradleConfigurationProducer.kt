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
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.execution.test.runner.TestMethodGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.TasksToRun

/**
 * A configuration producer for creating Gradle run configurations for screenshot tests
 * for a given method in the screenshot test source set. This class extends {@link TestMethodGradleConfigurationProducer}
 * to provide specialized configuration for screenshot testing. The configuration is only produced
 * if the method is annotated with either a Preview or multi-Preview annotation
 */
class ScreenshotTestMethodGradleConfigurationProducer: TestMethodGradleConfigurationProducer() {
  private val visitedAnnotations = mutableMapOf<String, Boolean>()
  override fun suggestConfigurationName(context: ConfigurationContext, element: PsiMethod, chosenElements: List<PsiClass>): String {
    return "Screenshot Tests for ${element.name}"
  }

  override fun doIsConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
    val location = context.location ?: return false
    val psiMethod = getPsiParentsOfType(location.psiElement, PsiMethod::class.java, false).firstOrNull()?: return false

    val androidModule = AndroidUtils.getAndroidModule(context) ?: return false
    val androidFacet = AndroidFacet.getInstance(androidModule) ?: return false
    if (!isScreenshotTestSourceSet(location, androidFacet)) return false
    if (!isMethodDeclarationPreviewannotated(psiMethod, visitedAnnotations)) return false

    val configurationTaskNames = configuration.settings.taskNames
    return configurationTaskNames == taskNamesWithFilter(context, psiMethod)
  }

  override fun getAllTestsTaskToRun(context: ConfigurationContext,
                                    element: PsiMethod,
                                    chosenElements: List<PsiClass>): List<TestTasksToRun> {
    val tasksToRun = mutableListOf<TestTasksToRun>()
    // TODO: Handle case with top level methods b/393230437
    val className = JavaExecutionUtil.getRuntimeQualifiedName(element.containingClass!!) ?: return tasksToRun
    val methodName = element.name
    val testFilter = "--tests \"$className.$methodName\""
    val tasks = getScreenshotTestTaskNames(context) ?: return tasksToRun
    tasksToRun.add(TestTasksToRun(TasksToRun.Impl("screenshotTest", tasks), testFilter))
    return tasksToRun
  }

  override fun doSetupConfigurationFromContext(configuration: GradleRunConfiguration,
                                               context: ConfigurationContext,
                                               sourceElement: Ref<PsiElement>): Boolean {
    if (!StudioFlags.ENABLE_SCREENSHOT_TESTING.get()) {
      return false
    }
    configuration.putUserData<Boolean>(SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW.userDataKey, true)
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
    getPsiParentsOfType(location.psiElement, PsiMethod::class.java, false).forEach { elementMethod ->
      if (!isMethodDeclarationPreviewannotated(elementMethod, visitedAnnotations)) return false
      sourceElementRef.set(elementMethod)
      configuration.settings.externalProjectPath = project.basePath
      configuration.name = suggestConfigurationName(context, elementMethod, emptyList())
      configuration.settings.taskNames = taskNamesWithFilter(context, elementMethod)
      configuration.isDebugServerProcess = false
      configuration.isDebugAllEnabled = false

      return true
    }
    return false
  }

  private fun taskNamesWithFilter(context: ConfigurationContext, psiMethod: PsiMethod): List<String> {
    // TODO: Handle case with top level methods b/393230437
    val className = psiMethod.containingClass?.qualifiedName?: return emptyList()
    val methodName = psiMethod.name
    return getScreenshotTestTaskNames(context)!! + "--tests" + "\"$className.$methodName\""
  }

}