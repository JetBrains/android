/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.kmp

import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.testartifacts.instrumented.AndroidTestConfigurationProducer.Companion.OPTIONS_EP
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfigurationType
import com.android.tools.idea.testartifacts.instrumented.getOptions
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.Location
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.junit.JavaRunConfigurationProducerBase
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.asJava.LightClassUtil
import org.jetbrains.kotlin.asJava.classes.KtFakeLightMethod
import org.jetbrains.kotlin.asJava.elements.KtLightElement
import org.jetbrains.kotlin.asJava.toFakeLightClass
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.idea.base.facet.implementingModules
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.base.facet.isTestModule
import org.jetbrains.kotlin.idea.base.util.isAndroidModule
import org.jetbrains.kotlin.idea.gradleJava.extensions.KotlinMultiplatformCommonProducersProvider
import org.jetbrains.kotlin.idea.gradleJava.run.isProvidedByMultiplatformProducer
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * A [com.intellij.execution.actions.RunConfigurationProducer] implementation for [AndroidTestRunConfiguration] in multiplatform modules.
 */
class KotlinMultiplatformAndroidTestConfigurationProducer : JavaRunConfigurationProducerBase<AndroidTestRunConfiguration>(), KotlinMultiplatformCommonProducersProvider {

  val logger = Logger.getInstance(AndroidTestRunConfiguration::class.java)

  override fun setupConfigurationFromContext(configuration: AndroidTestRunConfiguration,
                                             context: ConfigurationContext,
                                             sourceElementRef: Ref<PsiElement>): Boolean {
    val configurator = KotlinMultiplatformAndroidTestConfigurator.createFromContext(context) ?: return false
    if (!configurator.configureKMP(configuration, context, sourceElementRef) ) {
      return false
    }

    // Set context.module to the configuration. It may set non-context module such as
    // pre-defined module in configuration template.
    if (!setupConfigurationModule(context, configuration)) {
      return false
    }

    configuration.EXTRA_OPTIONS = getOptions(configuration.EXTRA_OPTIONS, context, OPTIONS_EP.extensionList, logger)

    return true
  }

  override fun findModule(configuration: AndroidTestRunConfiguration, contextModule: Module?): Module? {
    // Check if the context module is a multiplatform one that has AndroidTest implementing modules.
    // If true, then we can set up a AndroidTestRunConfiguration using the context data to create the RunConfiguration and relying on the
    // AndroidTest implementing module to get the AndroidFacet information.
    return if (contextModule != null && contextModule.isMultiPlatformModule) {
      contextModule.implementingModules.find { it.isAndroidTestModule() }
    }
    else if (configuration.configurationModule.module != null) {
      configuration.configurationModule.module
    }
    else null
  }

  override fun isConfigurationFromContext(configuration: AndroidTestRunConfiguration, context: ConfigurationContext): Boolean {
    val expectedConfig = configurationFactory.createTemplateConfiguration(configuration.project) as AndroidTestRunConfiguration
    val configurator = KotlinMultiplatformAndroidTestConfigurator.createFromContext(context) ?: return false
    if (!configurator.configureKMP(expectedConfig, context, Ref())) {
      return false
    }
    if (configuration.TESTING_TYPE != expectedConfig.TESTING_TYPE) {
      return false
    }
    if (!configuration.EXTRA_OPTIONS.contains(getOptions("", context, OPTIONS_EP.extensionList, logger))) {
      return false
    }

    return when (configuration.TESTING_TYPE) {
      AndroidTestRunConfiguration.TEST_ALL_IN_MODULE -> configuration.TEST_NAME_REGEX == expectedConfig.TEST_NAME_REGEX
      AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE -> configuration.PACKAGE_NAME == expectedConfig.PACKAGE_NAME
      AndroidTestRunConfiguration.TEST_CLASS -> configuration.CLASS_NAME == expectedConfig.CLASS_NAME
      AndroidTestRunConfiguration.TEST_METHOD -> configuration.CLASS_NAME == expectedConfig.CLASS_NAME &&
                                                 configuration.METHOD_NAME == expectedConfig.METHOD_NAME
      else -> false
    }
  }

  override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean =
    !other.isProvidedByMultiplatformProducer()

  override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean =
    !other.isProvidedByMultiplatformProducer() && super.shouldReplace(self, other)

  override fun isProducedByCommonProducer(configuration: ConfigurationFromContext): Boolean {
    return configuration.isProducedBy(this.javaClass)
  }

  override fun findExistingConfiguration(context: ConfigurationContext): RunnerAndConfigurationSettings? = null

  override fun getConfigurationFactory(): ConfigurationFactory = AndroidTestRunConfigurationType.getInstance().factory

  override fun onFirstRun(configurationFromcontext: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
    // This function is called right before the execution time and after the run configuration has been set up.
    // We want to make sure we don't end up unnecessarily adding duplicates Run Configuration to the "Run/Debug" window.
    // Initially these RCs get a unique name set up for them each time we create them, but when we execute them, we do not need to add
    // a new entry to the UI menu if it is the same name and type.
    val configuration = configurationFromcontext.configuration
    if (configuration is AndroidTestRunConfiguration) {
      configuration.setGeneratedName()
    }
    super.onFirstRun(configurationFromcontext, context, startRunnable)
  }
}

/**
 * A helper class responsible for configuring [AndroidTestRunConfiguration] properly based on given information.
 * This is a stateless class and you can call [configure] method as many times as you wish.
 */
private class KotlinMultiplatformAndroidTestConfigurator(private val facet: AndroidFacet,
                                                         private val location: Location<PsiElement>,
                                                         private val virtualFile: VirtualFile) {
  companion object {
    /**
     * Creates [KotlinMultiplatformAndroidTestConfigurator] from a given context.
     * Returns null if the context is not applicable for android test.
     */
    fun createFromContext(context: ConfigurationContext): KotlinMultiplatformAndroidTestConfigurator? {
      val location = context.location ?: return null
      val module = AndroidUtils.getAndroidModule(context) ?: context.module ?: return null
      // If the given context module is not a multiplatform, then this producer is not responsible for setting this configuration.
      if (!module.isMultiPlatformModule)  return null
      // We set up the AndroidFacet by looking for a AndroidTest module that depends on the multiplatform context module.
      val androidFacet = if (module.isTestModule) module.implementingModules.find { it.isAndroidModule()}?.androidFacet else null
      if (androidFacet == null) return null
      val virtualFile = PsiUtilCore.getVirtualFile(location.psiElement) ?: return null
      return KotlinMultiplatformAndroidTestConfigurator(androidFacet, location, virtualFile)
    }
  }

  /**
   * Configures a given configuration. If success, it returns true otherwise false.
   * When the configuration fails, the given configuration object may be configured in a halfway so you should dispose the
   * configuration.
   *
   * @param configuration a configuration instance to be configured
   * @param context the context for the configuration we are trying to create
   * @param sourceElementRef the most relevant [PsiElement] such as test method, test class, or package is set back to the caller
   * for reference
   */
  fun configureKMP(configuration: AndroidTestRunConfiguration,  context: ConfigurationContext, sourceElementRef: Ref<PsiElement>): Boolean {

    val contextModule = context.module ?: return false
    val androidTestModule = contextModule.implementingModules.find { it.isAndroidTestModule() } ?: return false

    val targetSelectionMode = AndroidUtils.getDefaultTargetSelectionMode(
      androidTestModule, AndroidTestRunConfigurationType.getInstance(), AndroidRunConfigurationType.getInstance())
    if (targetSelectionMode != null) {
      configuration.deployTargetContext.targetSelectionMode = targetSelectionMode
    }

    // Try to create run configuration from the most specific one to the broader.
    return when {
      tryKotlinMultiplatformMethodTestConfiguration(configuration, sourceElementRef) -> true
      trySingleKotlinMultiplatformClassTestConfiguration(configuration, sourceElementRef) -> true
      tryAllInPackageTestConfiguration(configuration, sourceElementRef) -> true
      tryAllInDirectoryTestConfiguration(configuration, sourceElementRef) -> true
      else -> false
    }
  }

  private fun tryKotlinMultiplatformMethodTestConfiguration(configuration: AndroidTestRunConfiguration, sourceElementRef: Ref<PsiElement>): Boolean {
    val candidateMethod = getTestMethodForKotlinTest(location) ?: return false
    sourceElementRef.set(candidateMethod)
    sourceElementRef.set(candidateMethod)

    val className = JavaExecutionUtil.getRuntimeQualifiedName(candidateMethod.containingClass!!) ?: ""
    val methodName = candidateMethod.name
    val parameterizedLocation = PsiMemberParameterizedLocation.getParameterizedLocation(candidateMethod.containingClass!!, null)

    if (parameterizedLocation != null) {
      configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_MODULE
      configuration.CLASS_NAME = className
      configuration.METHOD_NAME = methodName
      configuration.TEST_NAME_REGEX = "${className}.${methodName}\\[.*\\]"
    } else {
      configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_METHOD
      configuration.CLASS_NAME = className
      configuration.METHOD_NAME = methodName
    }

    configuration.setGeneratedName()
    return true
  }

  private fun trySingleKotlinMultiplatformClassTestConfiguration(configuration: AndroidTestRunConfiguration, sourceElementRef: Ref<PsiElement>): Boolean {
    val classCandidate = getTestClassForKotlinTest(location) ?: return false
    sourceElementRef.set(classCandidate)
    configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_CLASS
    configuration.CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(classCandidate) ?: ""
    configuration.setGeneratedName()
    return true
  }

  private fun getTestMethodForKotlinTest(location: Location<*>): PsiMethod? {
    val leaf = when (val psi = location.psiElement) {
      is KtLightElement<*, *> -> psi.kotlinOrigin
      else -> psi
    }
    val function = leaf?.getParentOfType<KtNamedFunction>(false) ?: return null
    return LightClassUtil.getLightClassMethod(function) ?: KtFakeLightMethod.get(function)
  }

  private fun getTestClassForKotlinTest(location: Location<*>): PsiClass? {
    val leaf = when (val psi = location.psiElement) {
      is KtLightElement<*, *> -> psi.kotlinOrigin
      else -> psi
    }
    val owner = leaf?.getParentOfType<KtDeclaration>(false) as? KtClassOrObject ?: return null
    return owner.toLightClass() ?: owner.toFakeLightClass()
  }

  /**
   * Tries to configure for a directory test scope. Returns true if success otherwise false.
   * This means that we execute the tests in a ALL_IN_MODULE test scope.
   */
  private fun tryAllInDirectoryTestConfiguration(configuration: AndroidTestRunConfiguration, sourceElementRef: Ref<PsiElement>): Boolean {
    if (location.psiElement !is PsiDirectory) return false
    sourceElementRef.set(location.psiElement)
    configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_ALL_IN_MODULE
    configuration.setGeneratedName()

    return true
  }

  /**
   * Tries to configure for a all-in-package test. Returns true if success otherwise false.
   * If package name is unknown, it fallbacks to all-in-module test.
   */
  private fun tryAllInPackageTestConfiguration(configuration: AndroidTestRunConfiguration, sourceElementRef: Ref<PsiElement>): Boolean {
    val psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(location.psiElement) ?: return false
    if (psiPackage.qualifiedName.isEmpty()) return false
    sourceElementRef.set(psiPackage)

    val packageName = psiPackage.qualifiedName
    configuration.PACKAGE_NAME = packageName
    configuration.TESTING_TYPE = when {
      packageName.isNotEmpty() -> AndroidTestRunConfiguration.TEST_ALL_IN_PACKAGE
      else -> AndroidTestRunConfiguration.TEST_ALL_IN_MODULE
    }
    configuration.setGeneratedName()

    return true
  }
}