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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.getHolderModule
import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass

/**
 * Producer of [AndroidWatchFaceConfiguration] for classes that extend `android.support.wearable.watchface.WatchFaceService`. The
 * configuration created is initially named after the name of the WatchFaceService class name and its fully qualified name is properly
 * set in the configuration.
 */
class AndroidWatchFaceRunConfigurationProducer : LazyRunConfigurationProducer<AndroidWatchFaceConfiguration>() {
  override fun getConfigurationFactory(): ConfigurationFactory =
    runConfigurationType<AndroidWatchFaceConfigurationType>().configurationFactories[0]

  override fun isConfigurationFromContext(configuration: AndroidWatchFaceConfiguration, context: ConfigurationContext): Boolean {
    if (!StudioFlags.ALLOW_RUN_WEAR_CONFIGURATIONS_FROM_GUTTER.get()) {
      return false
    }
    val serviceName = context.psiLocation.getPsiClass()?.qualifiedName
    return configuration.componentName == serviceName
  }

  public override fun setupConfigurationFromContext(configuration: AndroidWatchFaceConfiguration,
                                                    context: ConfigurationContext,
                                                    sourceElement: Ref<PsiElement>): Boolean {
    if (!StudioFlags.ALLOW_RUN_WEAR_CONFIGURATIONS_FROM_GUTTER.get()) {
      return false
    }
    val psiClass = context.psiLocation.getPsiClass()
    if (psiClass == null || !psiClass.isValidWatchFaceService()) {
      return false
    }
    val serviceName = psiClass.qualifiedName ?: return false

    configuration.name = JavaExecutionUtil.getPresentableClassName(serviceName)!!
    configuration.configurationModule.module = context.module.getHolderModule()
    configuration.componentName = serviceName

    return true
  }
}

internal fun PsiClass.isValidWatchFaceService(): Boolean {
  return WearBaseClasses.WATCH_FACES.any { wearBase -> InheritanceUtil.isInheritor(this, wearBase) }
}

internal fun PsiElement?.getPsiClass(): PsiClass? {
  return when (val parent = this?.parent) {
    is KtClass -> parent.toLightClass()
    is PsiClass -> parent
    else -> null
  }
}
