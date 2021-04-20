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

import com.android.tools.compose.PREVIEW_ANNOTATION_FQNS
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.run.AndroidRunConfiguration
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.stats.RunStats
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposeDeployEvent
import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jdom.Element

private const val CONFIGURATION_ELEMENT_NAME = "compose-preview-run-configuration"
private const val COMPOSABLE_FQN_ATR_NAME = "composable-fqn"

/** A run configuration to launch the Compose tooling PreviewActivity to a device/emulator passing a @Composable via intent parameter. */
open class ComposePreviewRunConfiguration(project: Project, factory: ConfigurationFactory) : AndroidRunConfiguration(project, factory) {

  /**
   * Represents where this Run Configuration was triggered from.
   */
  enum class TriggerSource(val eventType: ComposeDeployEvent.ComposeDeployEventType) {
    UNKNOWN(ComposeDeployEvent.ComposeDeployEventType.UNKNOWN_EVENT_TYPE),
    TOOLBAR(ComposeDeployEvent.ComposeDeployEventType.DEPLOY_FROM_TOOLBAR),
    GUTTER(ComposeDeployEvent.ComposeDeployEventType.DEPLOY_FROM_GUTTER)
  }

  var triggerSource = TriggerSource.UNKNOWN

  var composableMethodFqn: String? = null
    set(value) {
      field = value
      updateActivityExtraFlags()
    }

  var providerClassFqn: String? = null
    set(value) {
      field = value
      updateActivityExtraFlags()
    }

  var providerIndex: Int = -1
    set(value) {
      field = value
      updateActivityExtraFlags()
    }

  init {
    // This class is open just to be inherited in the tests, and the derived class is available when it needs to be accessed
    // TODO(b/152183413): limit the search to the library scope. We currently use the global scope because SpecificActivityLaunch.State only
    //                    accepts either project or global scope.
    @Suppress("LeakingThis")
    setLaunchActivity("androidx.compose.ui.tooling.preview.PreviewActivity", true)
  }

  override fun updateExtraRunStats(runStats: RunStats) {
    super.updateExtraRunStats(runStats)
    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.COMPOSE_DEPLOY)
      .setComposeDeployEvent(ComposeDeployEvent.newBuilder().setType(triggerSource.eventType))
    runStats.addAdditionalOnCommitEvent(studioEvent)
  }

  private fun updateActivityExtraFlags() {
    ACTIVITY_EXTRA_FLAGS =
      (composableMethodFqn?.let { "--es composable $it" } ?: "") +
      (providerClassFqn?.let { " --es parameterProviderClassName $it" } ?: "") +
      (providerIndex.takeIf { it >= 0 }?.let { " --ei parameterProviderIndex $it" } ?: "")
  }

  override fun isProfilable() = false

  override fun readExternal(element: Element) {
    super.readExternal(element)

    element.getChild(CONFIGURATION_ELEMENT_NAME)?.let {
      it.getAttribute(COMPOSABLE_FQN_ATR_NAME)?.let { attr ->
        composableMethodFqn = attr.value
      }
    }
  }

  override fun writeExternal(element: Element) {
    super.writeExternal(element)

    composableMethodFqn?.let {
      val configurationElement = Element(CONFIGURATION_ELEMENT_NAME)
      configurationElement.setAttribute(COMPOSABLE_FQN_ATR_NAME, it)
      element.addContent(configurationElement)
    }
  }

  override fun validate(executor: Executor?): MutableList<ValidationError> {
    val errors = super.validate(executor)
    if (!isValidComposableSet()) {
      errors.add(ValidationError.fatal(message("run.configuration.no.valid.composable.set", composableMethodFqn ?: "")))
    }
    return errors
  }

  override fun getConfigurationEditor() = ComposePreviewSettingsEditor(project, this)

  /**
   * Returns whether [composableMethodFqn] points to a `@Composable` function annotated with `@Preview`.
   */
  private fun isValidComposableSet(): Boolean {
    val composableFqn = composableMethodFqn ?: return false

    JavaPsiFacade.getInstance(project).findClass(composableFqn.substringBeforeLast("."), GlobalSearchScope.projectScope(project))
      ?.findMethodsByName(composableFqn.substringAfterLast("."))?.forEach { method ->
        if (method.annotations.any { PREVIEW_ANNOTATION_FQNS.contains(it.qualifiedName) }) return@isValidComposableSet true
      }

    return false
  }
}
