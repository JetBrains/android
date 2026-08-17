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
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.getBestPopupPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.VisibleForTesting

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
    sourceElement.set(runContext.sourceElement)
    runContext.setupConfigurationName(configuration)
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

  /**
   * Refines, resolves, and applies the context to the given run configuration without launching or showing UI dialogs.
   *
   * @return true if the configuration was successfully resolved and configured, false otherwise.
   */
  @VisibleForTesting
  suspend fun prepareAndSetupRunConfiguration(config: BlazeCommandRunConfiguration, context: ConfigurationContext): Boolean {
    val initialContext = readAction { findContext(context) } ?: return false
    val popupPosition = getPopupPosition(context.dataContext)
    val refinedContext = refineContext(initialContext, popupPosition)
    val resolvedContext = resolveContext(refinedContext, context.project, popupPosition)
    return resolvedContext.setupRunConfiguration(config)
  }

  private fun getPopupPosition(dataContext: DataContext): RelativePoint? =
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(dataContext) != null || CommonDataKeys.EDITOR.getData(dataContext) != null) {
      getBestPopupPosition(dataContext)
    } else {
      null
    }

  final override fun onFirstRun(configuration: ConfigurationFromContext, context: ConfigurationContext, startRunnable: Runnable) {
    val project = context.project
    val config = configuration.configuration as? BlazeCommandRunConfiguration ?: return
    project.coroutineScope.launch(Dispatchers.Default) {
      try {
        if (!prepareAndSetupRunConfiguration(config, context)) {
          throw CancellationException("Failed to configure run configuration")
        }
        withContext(Dispatchers.EDT) { startRunnable.run() }
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

  protected suspend fun refineContext(initialContext: RunConfigurationContext, popupPosition: RelativePoint?): RunConfigurationContext =
    initialContext.refine(popupPosition)

  protected suspend fun resolveContext(
    refinedContext: RunConfigurationContext,
    project: Project,
    popupPosition: RelativePoint?,
  ): RunConfigurationContext = refinedContext.resolve(project, popupPosition)
}
