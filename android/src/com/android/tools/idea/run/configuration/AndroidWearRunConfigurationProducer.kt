/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.projectsystem.getHolderModule
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement

/**
 * Producer of [AndroidWearConfiguration] for classes that extend `Wearable Services`. The configuration created is
 * initially named after the name of the Service class name and its fully qualified name is properly set in the configuration.
 */
abstract class AndroidWearRunConfigurationProducer<T : AndroidWearConfiguration>(val type: Class<out ConfigurationType>)
  : LazyRunConfigurationProducer<T>() {

  abstract fun isValidService(psiClass: PsiClass): Boolean

  override fun getConfigurationFactory(): ConfigurationFactory =
    ConfigurationTypeUtil.findConfigurationType(type).configurationFactories[0]

  override fun isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean {
    val serviceName = context.psiLocation.getPsiClass()?.qualifiedName
    return configuration.componentLaunchOptions.componentName == serviceName
  }

  public override fun setupConfigurationFromContext(configuration: T,
                                                    context: ConfigurationContext,
                                                    sourceElement: Ref<PsiElement>): Boolean {
    val psiClass = context.psiLocation.getPsiClass()
    if (psiClass == null || !isValidService(psiClass)) {
      return false
    }
    val serviceName = psiClass.qualifiedName ?: return false

    configuration.name = JavaExecutionUtil.getPresentableClassName(serviceName)!!
    configuration.configurationModule.module = context.module.getHolderModule()
    configuration.componentLaunchOptions.componentName = serviceName

    return true
  }
}
