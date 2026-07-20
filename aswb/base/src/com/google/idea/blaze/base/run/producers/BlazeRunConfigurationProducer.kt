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

import com.google.idea.blaze.base.settings.Blaze
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.ConfigurationFromContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement

/** Base class for Blaze run configuration producers. */
abstract class BlazeRunConfigurationProducer<T : RunConfiguration>(configurationType: ConfigurationType) :
  RunConfigurationProducer<T>(configurationType) {

  override fun isPreferredConfiguration(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
    return Blaze.isBlazeProject(self.configuration.project)
  }

  override fun shouldReplace(self: ConfigurationFromContext, other: ConfigurationFromContext): Boolean {
    return Blaze.isBlazeProject(self.configuration.project) && !other.isProducedBy(BlazeRunConfigurationProducer::class.java)
  }

  override fun setupConfigurationFromContext(configuration: T, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean {
    if (!validContext(context)) {
      return false
    }
    return doSetupConfigFromContext(configuration, context, sourceElement)
  }

  protected abstract fun doSetupConfigFromContext(configuration: T, context: ConfigurationContext, sourceElement: Ref<PsiElement>): Boolean

  override fun isConfigurationFromContext(configuration: T, context: ConfigurationContext): Boolean {
    if (!validContext(context)) {
      return false
    }
    return doIsConfigFromContext(configuration, context)
  }

  protected abstract fun doIsConfigFromContext(configuration: T, context: ConfigurationContext): Boolean

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
}
