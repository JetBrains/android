/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
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
package com.google.idea.bazel.java.run.producers

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.producers.BlazeRunConfigurationProducer
import com.google.idea.blaze.base.run.producers.RunConfigurationContext
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.junit.JUnitUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

/** Producer for abstract test classes/methods. */
class BlazeJavaAbstractTestCaseConfigurationProducer :
  BlazeRunConfigurationProducer<RunConfigurationContext>(BlazeCommandRunConfigurationType.getInstance()) {

  private class AbstractTestLocation(val abstractClass: PsiClass, val method: PsiMethod? = null)

  override fun findContext(context: ConfigurationContext): RunConfigurationContext? {
    val location = getAbstractLocation(context) ?: return null
    return object : RunConfigurationContext {
      override val sourceElement: PsiElement = location.method ?: location.abstractClass

      override fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
        config.name = "Choose subclass for ${configName(location.abstractClass, location.method)}"
        config.setNameChangedByUser(true)
        return true
      }

      override fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
        // this is an intermediate type -- when it's fully instantiated (via 'onFirstRun') it will be
        // recognized by a different producer.
        return false
      }
    }
  }

  override fun onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
    chooseSubclass(configuration, context, startRunnable)
  }

  companion object {
    private fun getAbstractLocation(context: ConfigurationContext): AbstractTestLocation? {
      if (SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isNotEmpty()) {
        // handled by a different producer
        return null
      }
      val method = getTestMethod(context)
      if (method != null) {
        val psiClass = method.containingClass
        return if (hasTestSubclasses(psiClass)) AbstractTestLocation(psiClass!!, method) else null
      }
      var location = context.location ?: return null
      location = JavaExecutionUtil.stepIntoSingleClass(location) ?: return null
      val psiClass = PsiTreeUtil.getParentOfType(location.psiElement, PsiClass::class.java, false)
      return if (hasTestSubclasses(psiClass)) AbstractTestLocation(psiClass!!, null) else null
    }

    private fun getTestMethod(context: ConfigurationContext): PsiMethod? {
      val psi = context.psiLocation
      if (psi is PsiMethod && AnnotationUtil.isAnnotated(psi, JUnitUtil.TEST_ANNOTATION, AnnotationUtil.CHECK_TYPE)) {
        return psi
      }
      val selectedMethods = TestMethodSelectionUtil.getSelectedMethods(context)
      return if (selectedMethods != null && selectedMethods.size == 1) selectedMethods[0] else null
    }

    private fun hasTestSubclasses(psiClass: PsiClass?): Boolean {
      return psiClass != null && SubclassTestChooser.findTestSubclasses(psiClass).isNotEmpty()
    }

    @JvmStatic
    @VisibleForTesting
    fun chooseSubclass(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
      val config = configuration.configuration
      if (config !is BlazeCommandRunConfiguration) {
        return
      }
      val location = locationFromConfiguration(configuration) ?: return
      SubclassTestChooser.chooseSubclass(context, location.abstractClass) { psiClass ->
        if (psiClass != null) {
          setupContext(config, psiClass, location.method)
        }
        startRunnable.run()
      }
    }

    private fun locationFromConfiguration(configuration: ConfigurationFromContext): AbstractTestLocation? {
      val element = configuration.sourceElement
      var method: PsiMethod? = null
      var psiClass: PsiClass? = null
      if (element is PsiMethod) {
        method = element
        psiClass = method.containingClass
      } else if (element is PsiClass) {
        psiClass = element
      }
      return if (hasTestSubclasses(psiClass)) AbstractTestLocation(psiClass!!, method) else null
    }

    private fun setupContext(configuration: BlazeCommandRunConfiguration, subClass: PsiClass, method: PsiMethod?) {
      val testContext = JavaTestContextProvider.fromClassAndMethod(subClass, method) ?: return
      testContext.setupRunConfiguration(configuration)
    }

    private fun configName(psiClass: PsiClass, method: PsiMethod?): String {
      val classPart = psiClass.name
      return if (method == null) classPart ?: "" else "$classPart.${method.name}"
    }
  }
}
