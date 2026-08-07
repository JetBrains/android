/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.coroutines.CancellationException

/** Producer for abstract test classes/methods. */
class BlazeJavaAbstractTestCaseConfigurationProducer :
  BlazeRunConfigurationProducer<BlazeJavaAbstractTestCaseConfigurationProducer.AbstractTestContext>(
    BlazeCommandRunConfigurationType.getInstance()
  ) {

  class AbstractTestContext(override val sourceElement: PsiElement, val abstractClass: PsiClass, val method: PsiMethod?) :
    RunConfigurationContext {

    override fun setupRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
      config.name = "Choose subclass for " + configName(abstractClass, method)
      config.setNameChangedByUser(true)
      return true
    }

    override fun matchesRunConfiguration(config: BlazeCommandRunConfiguration): Boolean {
      // this is an intermediate type -- when it's fully instantiated (via 'onFirstRun') it will be
      // recognized by a different producer.
      return false
    }
  }

  override fun findContext(context: ConfigurationContext): AbstractTestContext? {
    if (!SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isEmpty()) {
      // handled by a different producer
      return null
    }
    val method = getTestMethod(context)
    if (method != null) {
      val psiClass = method.containingClass ?: return null
      return if (hasTestSubclasses(psiClass)) AbstractTestContext(method, psiClass, method) else null
    }
    var location = context.location ?: return null
    location = JavaExecutionUtil.stepIntoSingleClass(location) ?: return null
    val psiClass = PsiTreeUtil.getParentOfType(location.psiElement, PsiClass::class.java, false) ?: return null
    return if (hasTestSubclasses(psiClass)) AbstractTestContext(psiClass, psiClass, null) else null
  }

  override suspend fun refineContext(
    initialContext: AbstractTestContext,
    configuration: ConfigurationFromContext,
    context: ConfigurationContext,
  ): RunConfigurationContext {
    val psiClass =
      SubclassTestChooser.chooseSubclass(context, initialContext.abstractClass) ?: throw CancellationException("No subclass chosen")

    val concreteContext =
      readAction { JavaTestContextProvider.fromClassAndMethod(psiClass, initialContext.method) }
        ?: throw CancellationException("Could not create context for subclass")

    return concreteContext
  }

  companion object {
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

    private fun configName(psiClass: PsiClass, method: PsiMethod?): String {
      val classPart = psiClass.name
      return if (method == null) classPart ?: "" else "$classPart.${method.name}"
    }
  }
}
