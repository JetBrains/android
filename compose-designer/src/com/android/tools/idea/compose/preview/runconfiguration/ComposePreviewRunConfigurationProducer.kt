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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.tools.idea.compose.preview.PREVIEW_ANNOTATION_FQN
import com.android.tools.idea.compose.preview.isValidPreviewLocation
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.kotlin.getClassName
import com.android.tools.idea.kotlin.getQualifiedName
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

/**
 * Producer of [ComposePreviewRunConfiguration] for `@Composable` functions annotated with [PREVIEW_ANNOTATION_FQN]. The configuration
 * created is initially named after the `@Composable` function, and its fully qualified name is properly set in the configuration.
 *
 * The [ConfigurationContext] where the [ComposePreviewRunConfiguration] is created from can be any descendant of the `@Composable` function
 * in the PSI tree, such as its annotations, function name or even the keyword "fun".
 */
open class ComposePreviewRunConfigurationProducer : LazyRunConfigurationProducer<ComposePreviewRunConfiguration>() {
  final override fun getConfigurationFactory(): ConfigurationFactory {
    if (StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION.get()) {
      // When the flag is enabled, ComposePreviewRunConfigurationType should be registered in compose-designer.xml, so actually try to
      // locate the configuration type.
      return runConfigurationType<ComposePreviewRunConfigurationType>().configurationFactories[0]
    }
    else {
      // When the flag is disabled, return a new instance of ComposePreviewRunConfigurationType, so this method returns a valid
      // configuration type instance when IntelliJ is iterating through the list of producers.
      return ComposePreviewRunConfigurationType()
    }
  }

  public final override fun setupConfigurationFromContext(configuration: ComposePreviewRunConfiguration,
                                                   context: ConfigurationContext,
                                                   sourceElement: Ref<PsiElement>): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION.get()) {
      return false
    }
    context.containingComposePreviewFunction()?.let {
      configuration.name = it.name!!
      configuration.composableMethodFqn = it.composePreviewFunctionFqn()
      configuration.setModule(context.module)
      return true
    }
    return false
  }

  final override fun isConfigurationFromContext(configuration: ComposePreviewRunConfiguration, context: ConfigurationContext): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION.get()) {
      return false
    }
    context.containingComposePreviewFunction()?.let {
      return configuration.name == it.name && configuration.composableMethodFqn == it.composePreviewFunctionFqn()
    }
    return false
  }
}

private fun KtNamedFunction.composePreviewFunctionFqn() = "${getClassName()}.${name}"

private fun ConfigurationContext.containingComposePreviewFunction(): KtNamedFunction? {
  return psiLocation?.let { location ->
    location.getNonStrictParentOfType<KtNamedFunction>()?.takeIf {
      it.isValidPreviewLocation() && it.annotationEntries.any { annotation -> annotation.getQualifiedName() == PREVIEW_ANNOTATION_FQN }
    }
  }
}