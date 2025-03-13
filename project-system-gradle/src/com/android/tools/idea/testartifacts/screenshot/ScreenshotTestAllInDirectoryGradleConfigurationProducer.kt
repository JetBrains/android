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
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.execution.test.runner.AllInDirectoryGradleConfigurationProducer
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration
import org.jetbrains.plugins.gradle.util.TasksToRun

/**
 * A configuration producer for creating Gradle run configurations for screenshot tests
 * within a given directory.  This class extends {@link AllInDirectoryGradleConfigurationProducer}
 * to leverage its directory-based configuration creation capabilities. The configuration is produced as long
 * as there is a screenshot test source set present in the directory
 */
class ScreenshotTestAllInDirectoryGradleConfigurationProducer: AllInDirectoryGradleConfigurationProducer() {
  override fun suggestConfigurationName(context: ConfigurationContext, element: PsiElement, chosenElements: List<PsiElement>): String {
    return "Screenshot Tests in ${context.module!!.name}"
  }

  override fun doIsConfigurationFromContext(configuration: GradleRunConfiguration, context: ConfigurationContext): Boolean {
    val location = context.location ?: return false
    if (location.psiElement !is PsiDirectory) return false

    val androidModule = AndroidUtils.getAndroidModule(context) ?: return false
    val androidFacet = AndroidFacet.getInstance(androidModule) ?: return false
    if (!isScreenshotTestSourceSet(location, androidFacet)) return false

    val taskNames = getScreenshotTestTaskNames(context) ?: return false
    val configurationTaskNames = configuration.settings.taskNames
    return  configurationTaskNames == taskNames
  }

  override fun getAllTestsTaskToRun(context: ConfigurationContext,
                                    element: PsiElement,
                                    chosenElements: List<PsiElement>): List<TestTasksToRun> {
    val tasksToRun = mutableListOf<TestTasksToRun>()
    val tasks = getScreenshotTestTaskNames(context) ?: return tasksToRun
    tasksToRun.add(TestTasksToRun(TasksToRun.Impl("screenshotTest", tasks), ""))
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
    if (location.psiElement !is PsiDirectory) return false

    val myModule = AndroidUtils.getAndroidModule(context) ?: return false
    val facet = AndroidFacet.getInstance(myModule) ?: return false
    if (!isScreenshotTestSourceSet(location, facet)) {
      return false
    }

    val project = context.project ?: return false
    configuration.settings.externalProjectPath = project.basePath
    sourceElementRef.set(location.psiElement)
    configuration.settings.taskNames = getScreenshotTestTaskNames(context)!!
    configuration.name = suggestConfigurationName(context, location.psiElement, emptyList())
    return true
  }
}