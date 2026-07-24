/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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

import com.google.common.annotations.VisibleForTesting
import com.google.idea.blaze.base.command.BlazeCommandName
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.run.BlazeCommandRunConfiguration
import com.google.idea.blaze.base.run.BlazeCommandRunConfigurationType
import com.google.idea.blaze.base.run.producers.BinaryContextProvider.BinaryRunContext
import com.google.idea.blaze.base.run.smrunner.SmRunnerUtils
import com.google.idea.blaze.base.run.state.BlazeCommandRunConfigurationCommonState
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.psi.util.PsiModificationTracker

/** Produces run configurations via [BinaryContextProvider]. */
class BinaryContextRunConfigurationProducer :
  BlazeRunConfigurationProducer<BlazeCommandRunConfiguration>(BlazeCommandRunConfigurationType.getInstance()) {

  companion object {
    private val cacheKey =
      Key.create<ParameterizedCachedValue<BinaryRunContext, ConfigurationContext>>(BinaryContextRunConfigurationProducer::class.java.name)
    private val PROVIDER =
      ParameterizedCachedValueProvider<BinaryRunContext, ConfigurationContext> { context ->
        val runContext = BinaryContextProvider.EP_NAME.extensions.firstNotNullOfOrNull { it.getRunContext(context) }
        CachedValueProvider.Result.create(
          runContext,
          PsiModificationTracker.MODIFICATION_COUNT,
          QuerySyncManager.getInstance(context.project).projectModificationTracker,
        )
      }
  }

  private fun findRunContext(context: ConfigurationContext): BinaryRunContext? {
    if (SmRunnerUtils.getSelectedSmRunnerTreeElements(context).isNotEmpty()) {
      // not a binary run context
      return null
    }
    val psi = context.psiLocation ?: return null
    return CachedValuesManager.getManager(context.project).getParameterizedCachedValue(psi, cacheKey, PROVIDER, false, context)
  }

  override fun doSetupConfigFromContext(
    configuration: BlazeCommandRunConfiguration,
    context: ConfigurationContext,
    sourceElement: Ref<PsiElement>,
  ): Boolean {
    val runContext = findRunContext(context) ?: return false
    sourceElement.set(runContext.sourceElement)
    configuration.setTargetInfo(runContext.target)
    val handlerState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    handlerState.commandState.command = BlazeCommandName.RUN
    configuration.setGeneratedName()
    return true
  }

  @VisibleForTesting
  public override fun doIsConfigFromContext(configuration: BlazeCommandRunConfiguration, context: ConfigurationContext): Boolean {
    val commonState = configuration.getHandlerStateIfType(BlazeCommandRunConfigurationCommonState::class.java) ?: return false
    if (commonState.commandState.command != BlazeCommandName.RUN) {
      return false
    }
    val runContext = findRunContext(context) ?: return false
    val targets = configuration.targetPatterns
    return targets.size == 1 && runContext.target.label().toString() == targets[0]
  }
}
