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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension
import com.android.tools.idea.testartifacts.testsuite.GradleRunConfigurationExtension.BooleanOptions.SHOW_TEST_RESULT_IN_ANDROID_TEST_SUITE_VIEW
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.execution.test.runner.AllInPackageGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.TasksToRun

/**
 * A configuration producer for creating Gradle run configurations for screenshot tests
 * within a given package in screenshot test source set. This class extends {@link AllInPackageGradleConfigurationProducer}
 * to provide specialized configuration for screenshot testing.
 */
class ScreenshotTestAllInPackageGradleConfigurationProducer: AllInPackageGradleConfigurationProducer() {
  override fun suggestConfigurationName(context: ConfigurationContext, element: PsiPackage, chosenElements: List<PsiPackage>): String {
    return "Screenshot Tests in ${element.qualifiedName}"
  }

  override fun doIsConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
    val location = context.location ?: return false
    val psiPackage = AbstractJavaTestConfigurationProducer.checkPackage(location.psiElement) ?: return false

    val androidModule = AndroidUtils.getAndroidModule(context) ?: return false
    val androidFacet = AndroidFacet.getInstance(androidModule) ?: return false
    if (!isScreenshotTestSourceSet(location, androidFacet)) return false

    val configurationTaskNames = configuration.settings.taskNames
    return  configurationTaskNames == taskNamesWithFilter(context, psiPackage)
  }

  override fun getAllTestsTaskToRun(context: ConfigurationContext,
                                    element: PsiPackage,
                                    chosenElements: List<PsiPackage>): List<TestTasksToRun> {
    val tasksToRun = mutableListOf<TestTasksToRun>()
    val testFilter = "--tests \"${element.qualifiedName}*\""
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
    val psiPackage = AbstractJavaTestConfigurationProducer.checkPackage(location.psiElement)?: return false

    val myModule = AndroidUtils.getAndroidModule(context) ?: return false
    val facet = AndroidFacet.getInstance(myModule) ?: return false
    if (!isScreenshotTestSourceSet(location, facet)) {
      return false
    }

    val project = context.project ?: return false
    val packageName = psiPackage.qualifiedName
    if (packageName.isEmpty()) return false
    sourceElementRef.set(psiPackage)
    configuration.settings.externalProjectPath = project.basePath
    configuration.settings.taskNames = taskNamesWithFilter(context, psiPackage)
    configuration.name = suggestConfigurationName(context, psiPackage, emptyList())
    return true
  }

  private fun taskNamesWithFilter(context: ConfigurationContext, psiPackage: PsiPackage): List<String> {
    return getScreenshotTestTaskNames(context)!! + "--tests" + "\"${psiPackage.qualifiedName}.*\""
  }
}