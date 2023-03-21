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

import com.android.tools.compose.COMPOSE_PREVIEW_ACTIVITY_FQN
import com.android.tools.compose.COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN
import com.android.tools.idea.compose.preview.isValidComposePreview
import com.android.tools.idea.kotlin.fqNameMatches
import com.android.tools.idea.kotlin.getClassName
import com.android.tools.idea.projectsystem.getHolderModule
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.runConfigurationType
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.editor.EditorGutter
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

/**
 * Producer of [ComposePreviewRunConfiguration] for `@Composable` functions annotated with
 * [PREVIEW_ANNOTATION_FQN]. The configuration created is initially named after the `@Composable`
 * function, and its fully qualified name is properly set in the configuration.
 *
 * The [ConfigurationContext] where the [ComposePreviewRunConfiguration] is created from can be any
 * descendant of the `@Composable` function in the PSI tree, such as its annotations, function name
 * or even the keyword "fun".
 */
open class ComposePreviewRunConfigurationProducer :
  LazyRunConfigurationProducer<ComposePreviewRunConfiguration>() {
  final override fun getConfigurationFactory() =
    runConfigurationType<ComposePreviewRunConfigurationType>().configurationFactories[0]

  public final override fun setupConfigurationFromContext(
    configuration: ComposePreviewRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>
  ): Boolean {
    val module = context.module ?: context.location?.module ?: return false
    configuration.setLaunchActivity(COMPOSE_PREVIEW_ACTIVITY_FQN, true)
    context.containingComposePreviewFunction()?.let {
      configuration.name = it.name!!
      configuration.composableMethodFqn = it.composePreviewFunctionFqn()
      // We don't want to be able to create a run configuration from individual source set modules
      // so we use their container module instead
      configuration.setModule(module.getHolderModule())
      updateConfigurationTriggerToGutterIfNeeded(configuration, context)

      it.valueParameters.forEach { parameter ->
        parameter.annotationEntries
          .firstOrNull { annotation ->
            annotation.fqNameMatches(COMPOSE_PREVIEW_PARAMETER_ANNOTATION_FQN)
          }
          ?.let { previewParameter ->
            previewParameter.providerClassName()?.let { providerClass ->
              configuration.providerClassFqn = providerClass
              return@forEach
            }
          }
      }
      return true
    }
    return false
  }

  final override fun isConfigurationFromContext(
    configuration: ComposePreviewRunConfiguration,
    context: ConfigurationContext
  ): Boolean {
    context.containingComposePreviewFunction()?.let {
      val createdFromContext = configuration.composableMethodFqn == it.composePreviewFunctionFqn()
      if (createdFromContext) {
        // Handle configurations that already exist (e.g. that could have been created from the
        // Preview toolbar).
        updateConfigurationTriggerToGutterIfNeeded(configuration, context)
      }
      return createdFromContext
    }
    return false
  }
}

/**
 * When producing the configuration from the gutter icon, update its
 * [ComposePreviewRunConfiguration.TriggerSource] so we can keep track.
 */
private fun updateConfigurationTriggerToGutterIfNeeded(
  configuration: ComposePreviewRunConfiguration,
  context: ConfigurationContext
) {
  if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context.dataContext) is EditorGutter) {
    configuration.triggerSource = ComposePreviewRunConfiguration.TriggerSource.GUTTER
  }
}

/** Get the provider fully qualified class name of a `@PreviewParameter` annotated parameter. */
private fun KtAnnotationEntry.providerClassName(): String? {
  val annotationDescriptor =
    analyze(BodyResolveMode.PARTIAL).get(BindingContext.ANNOTATION, this) ?: return null
  val argument =
    annotationDescriptor.allValueArguments.entries
      .firstOrNull { it.key.asString() == "provider" }
      ?.value
      ?: return null
  return (argument.value as? KClassValue.Value.NormalClass)?.classId?.asSingleFqName()?.asString()
}

private fun KtNamedFunction.composePreviewFunctionFqn() = "${getClassName()}.${name}"

private fun ConfigurationContext.containingComposePreviewFunction() =
  psiLocation?.let { location ->
    location.getNonStrictParentOfType<KtNamedFunction>()?.takeIf { it.isValidComposePreview() }
  }
