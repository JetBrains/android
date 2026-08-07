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
package com.google.idea.blaze.base.run.producers

import com.android.tools.idea.concurrency.coroutineScope
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.settings.Blaze
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Base class for Blaze run configuration producers. */
abstract class BlazeRunConfigurationProducer<C : RunConfigurationContext>(configurationType: ConfigurationType) :
  RunConfigurationProducer<BlazeCommandRunConfiguration>(configurationType) {

  private val logger = Logger.getInstance(BlazeRunConfigurationProducer::class.java)

  override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
    return Blaze.isBlazeProject(self.configuration.project)
  }

  override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
    return Blaze.isBlazeProject(self.configuration.project) && !other.isProducedBy(BlazeRunConfigurationProducer::class.java)
  }

  override fun setupConfigurationFromContext(
    configuration: BlazeCommandRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>,
  ): Boolean {
    if (!validContext(context)) {
      return false
    }
    val runContext = findContext(context) ?: return false
    if (!runContext.setupRunConfiguration(configuration)) {
      return false
    }
    sourceElement.set(runContext.sourceElement)
    return true
  }

  override fun isConfigurationFromContext(configuration: BlazeCommandRunConfiguration, context: ConfigurationContext): Boolean {
    if (!validContext(context)) {
      return false
    }
    val runContext = findContext(context) ?: return false
    return runContext.matchesRunConfiguration(configuration)
  }

  protected abstract fun findContext(context: ConfigurationContext): C?

  /** Returns true if the producer should ignore contexts outside the project. Defaults to false. */
  protected open fun restrictedToProjectFiles(): Boolean {
    return false
  }

  private fun validContext(context: ConfigurationContext?): Boolean {
    if (context == null) return false
    if (restrictedToProjectFiles() && context.module == null) {
      return false
    }
    if (!isBlazeContext(context)) {
      return false
    }
    return true
  }

  private fun isBlazeContext(context: ConfigurationContext): Boolean {
    return Blaze.isBlazeProject(context.project)
  }

  final override fun onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
    val project = context.project
    project.coroutineScope.launch(Dispatchers.Default) {
      try {
        val initialContext = readAction { findContext(context) } ?: throw CancellationException("Context no longer valid")
        val refinedContext = refineContext(initialContext, configuration, context)
        val resolvedContext = resolveContext(refinedContext, configuration, context)
        withContext(Dispatchers.EDT) {
          resolvedContext.setupRunConfiguration(configuration.configuration as BlazeCommandRunConfiguration)
          startRunnable.run()
        }
      } catch (e: CancellationException) {
        logger.info("Run configuration preparation cancelled: ${e.message}")
      } catch (e: Throwable) {
        logger.error("Failed to prepare run configuration", e)
        withContext(Dispatchers.EDT) {
          Messages.showErrorDialog(project, "Failed to prepare run configuration: ${e.localizedMessage}", "Run Configuration Error")
        }
      }
    }
  }

  protected open suspend fun refineContext(
    initialContext: C,
    configuration: ConfigurationFromContext,
    context: ConfigurationContext,
  ): RunConfigurationContext = initialContext.refine(context)

  protected open suspend fun resolveContext(
    refinedContext: RunConfigurationContext,
    configuration: ConfigurationFromContext,
    context: ConfigurationContext,
  ): RunConfigurationContext = refinedContext.resolve(context.project)
}
