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
package com.android.tools.idea.testartifacts.instrumented

import com.android.tools.idea.AndroidPsiUtils.getPsiParentsOfType
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.SourceProviderManager
import com.android.tools.idea.projectsystem.androidProjectType
import com.android.tools.idea.projectsystem.containsFile
import com.android.tools.idea.projectsystem.isContainedBy
import com.android.tools.idea.run.AndroidRunConfigurationType
import com.android.tools.idea.util.androidFacet
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.Location
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.execution.junit.JUnitUtil
import com.intellij.execution.junit.JavaRunConfigurationProducerBase
import com.intellij.execution.junit.JavaRuntimeConfigurationProducerBase
import com.intellij.execution.junit2.PsiMemberParameterizedLocation
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType

/**
 * A [com.intellij.execution.actions.RunConfigurationProducer] implementation for [AndroidTestRunConfiguration].
 */
class AndroidTestConfigurationProducer : JavaRunConfigurationProducerBase<AndroidTestRunConfiguration>() {

  override fun setupConfigurationFromContext(configuration: AndroidTestRunConfiguration,
                                             context: ConfigurationContext,
                                             sourceElementRef: Ref<PsiElement>): Boolean {
    val configurator = AndroidTestConfigurator.createFromContext(context) ?: return false
    if (!configurator.configure(configuration, sourceElementRef)) {
      return false
    }

    // Set context.module to the configuration. It may set non-context module such as
    // pre-defined module in configuration template.
    if (!setupConfigurationModule(context, configuration)) {
      return false
    }

    return true
  }

  override fun findModule(configuration: AndroidTestRunConfiguration, contextModule: Module?): Module? {
    // In the base class implementation, it assumes that configuration module is null, and if not so,
    // it returns false, which is not always the case with AndroidTestRunConfiguration when the producer
    // is invoked from test result panel.
    // So here we just use either the contextModule or the configuration module.
    return if (contextModule != null) {
      contextModule
    }
    else if (configuration.getConfigurationModule().getModule() != null) {
      configuration.getConfigurationModule().getModule()
    }
    else null
  }

  override fun isConfigurationFromContext(configuration: AndroidTestRunConfiguration, context: ConfigurationContext): Boolean {
    val expectedConfig = configurationFactory.createTemplateConfiguration(configuration.project) as AndroidTestRunConfiguration
    val configurator = AndroidTestConfigurator.createFromContext(context) ?: return false
    if (!configurator.configure(expectedConfig, Ref())) {
      return false
    }
    if (configuration.TESTING_TYPE != expectedConfig.TESTING_TYPE) {
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

  override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean = when {
    // This configuration producer works best for Gradle based project. If the configuration is generated
    // for non-Gradle project and other configuration is available, prefer the other one.
    !GradleProjectInfo.getInstance(self.configuration.project).isBuildWithGradle -> false

    // If the other configuration type is JUnitConfigurationType or GradleExternalTaskConfigurationType, prefer our configuration.
    // Although those tests may be able to run on both environment if they are written with the unified-api (androidx.test, Espresso),
    // here we prioritize instrumentation.
    other.configurationType is JUnitConfigurationType -> true
    other.configurationType is GradleExternalTaskConfigurationType -> true

    // Otherwise, we don't have preference. Let the IDE to decide which one to use.
    else -> false
  }

  override fun getConfigurationFactory(): ConfigurationFactory = AndroidTestRunConfigurationType.getInstance().factory
}

/**
 * A helper class responsible for configuring [AndroidTestRunConfiguration] properly based on given information.
 * This is a stateless class and you can call [configure] method as many times as you wish.
 */
private class AndroidTestConfigurator(private val facet: AndroidFacet,
                                      private val location: Location<PsiElement>,
                                      private val virtualFile: VirtualFile) {
  companion object {
    /**
     * Creates [AndroidTestConfigurator] from a given context.
     * Returns null if the context is not applicable for android test.
     */
    fun createFromContext(context: ConfigurationContext): AndroidTestConfigurator? {
      val location = context.location ?: return null
      val module = AndroidUtils.getAndroidModule(context) ?: return null
      val facet = module.androidFacet ?: return null
      val virtualFile = PsiUtilCore.getVirtualFile(location.psiElement) ?: return null
      return AndroidTestConfigurator(facet, location, virtualFile)
    }

    /**
     * Returns true if a given [method] is a test method otherwise false.
     */
    private fun isTestMethod(method: PsiMethod): Boolean {
      val testClass = method.containingClass ?: return false
      return JUnitUtil.isTestClass(testClass) && JUnitUtil.TestMethodFilter(testClass).value(method)
    }
  }

  /**
   * Configures a given configuration. If success, it returns true otherwise false.
   * When the configuration fails, the given configuration object may be configured in a halfway so you should dispose the
   * configuration.
   *
   * @param configuration a configuration instance to be configured
   * @param sourceElementRef the most relevant [PsiElement] such as test method, test class, or package is set back to the caller
   * for reference
   */
  fun configure(configuration: AndroidTestRunConfiguration,
                sourceElementRef: Ref<PsiElement>): Boolean {
    val sourceProviders = SourceProviderManager.getInstance(facet)
    val (androidTestSources, generatedAndroidTestSources) =
      if (facet.module.androidProjectType() == AndroidModuleSystem.Type.TYPE_TEST) {
        sourceProviders.sources to sourceProviders.generatedSources
      }
      else {
        sourceProviders.androidTestSources to sourceProviders.generatedAndroidTestSources
    }
    if (
      !androidTestSources.containsFile(virtualFile) && !androidTestSources.isContainedBy(virtualFile) &&
      !generatedAndroidTestSources.containsFile(virtualFile) && !generatedAndroidTestSources.isContainedBy(virtualFile)
    ) {
      return false
    }

    val androidTestModule =
      (if (facet.module.androidProjectType() == (AndroidModuleSystem.Type.TYPE_TEST)) facet.mainModule else facet.androidTestModule)
      ?: return false
    val targetSelectionMode = AndroidUtils.getDefaultTargetSelectionMode(
      androidTestModule, AndroidTestRunConfigurationType.getInstance(), AndroidRunConfigurationType.getInstance())
    if (targetSelectionMode != null) {
      configuration.deployTargetContext.targetSelectionMode = targetSelectionMode
    }

    // Try to create run configuration from the most specific one to the broader.
    return when {
      tryMethodTestConfiguration(configuration, sourceElementRef) -> true
      trySingleClassTestConfiguration(configuration, sourceElementRef) -> true
      tryAllInPackageTestConfiguration(configuration, sourceElementRef) -> true
      tryAllInDirectoryTestConfiguration(configuration, sourceElementRef) -> true
      else -> false
    }
  }

  /**
   * Tries to configure for a single method test. Returns true if success otherwise false.
   */
  private fun tryMethodTestConfiguration(configuration: AndroidTestRunConfiguration, sourceElementRef: Ref<PsiElement>): Boolean {
    getPsiParentsOfType(location.psiElement, PsiMethod::class.java, false).forEach { elementMethod ->
      if (isTestMethod(elementMethod)) {
        sourceElementRef.set(elementMethod)

        val className = JavaExecutionUtil.getRuntimeQualifiedName(elementMethod.containingClass!!) ?: ""
        val methodName = elementMethod.name
        val parameterizedLocation = PsiMemberParameterizedLocation.getParameterizedLocation(elementMethod.containingClass!!, null)

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
    }
    return false
  }

  /**
   * Tries to configure for a single class test. Returns true if success otherwise false.
   * If there are multiple test classes in a file, use the first one to configure.
   */
  private fun trySingleClassTestConfiguration(configuration: AndroidTestRunConfiguration, sourceElementRef: Ref<PsiElement>): Boolean {
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
      if (!JUnitUtil.isTestClass(psiClass)) {
        return false
      }
      sourceElementRef.set(psiClass)
      configuration.TESTING_TYPE = AndroidTestRunConfiguration.TEST_CLASS
      configuration.CLASS_NAME = JavaExecutionUtil.getRuntimeQualifiedName(psiClass) ?: ""
      configuration.setGeneratedName()
      return true
    }
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