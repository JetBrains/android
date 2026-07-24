/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.producers

import com.google.idea.blaze.android.run.test.BlazeAndroidTestRunConfigurationState
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod

/**
 * Handles the specific case where the user creates a run configuration for android instrumentation tests by selecting test suites / classes
 * / methods from the test UI tree. This producer only handles android instrumentation tests run without using blaze test or mobile-install.
 */
class BlazeFilterAndroidTestRunConfigurationProducer :
  BlazeRunConfigurationProducer<BlazeCommandRunConfiguration>(BlazeCommandRunConfigurationType.getInstance()) {

  private class TestLocationName(val className: String, val methodName: String)

  override fun doSetupConfigFromContext(
    configuration: BlazeCommandRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>,
  ): Boolean {
    val handlerState = configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState::class.java) ?: return false
    val locationName = getTestLocationName(context) ?: return false

    handlerState.className = locationName.className
    handlerState.methodName = locationName.methodName
    handlerState.testingType =
      if (locationName.methodName.isEmpty()) BlazeAndroidTestRunConfigurationState.TEST_CLASS
      else BlazeAndroidTestRunConfigurationState.TEST_METHOD

    configuration.setGeneratedName()
    return true
  }

  override fun doIsConfigFromContext(configuration: BlazeCommandRunConfiguration, context: ConfigurationContext): Boolean {
    val handlerState = configuration.getHandlerStateIfType(BlazeAndroidTestRunConfigurationState::class.java) ?: return false
    val locationName = getTestLocationName(context) ?: return false

    if (locationName.className != handlerState.className) {
      return false
    }
    return locationName.methodName == handlerState.methodName
  }

  companion object {
    /**
     * @return name of the test location as a class#method name pair. Returns null if the test location is not a class or a method of a
     *   class.
     */
    private fun getTestLocationName(context: ConfigurationContext): TestLocationName? {
      val selectedElementLocations = SmRunnerUtils.getSelectedSmRunnerTreeElements(context)
      if (selectedElementLocations.isEmpty() || selectedElementLocations.size > 1) {
        return null // Don't support android instrumentation tests from more than one location.
      }
      val selectedElement = context.psiLocation
      if (selectedElement is PsiMethod) {
        val containingClass = selectedElement.containingClass ?: return null
        val qualifiedClassName = containingClass.qualifiedName ?: return null
        return TestLocationName(qualifiedClassName, selectedElement.name)
      } else if (selectedElement is PsiClass) {
        val qualifiedClassName = selectedElement.qualifiedName ?: return null
        return TestLocationName(qualifiedClassName, "")
      }
      return null
    }
  }
}
