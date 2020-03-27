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

import com.android.tools.idea.run.AndroidRunConfiguration
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.project.Project
import org.jdom.Element

private const val CONFIGURATION_ELEMENT_NAME = "compose-preview-run-configuration"
private const val COMPOSABLE_FQN_ATR_NAME = "composable-fqn"

/** A run configuration to launch the Compose tooling PreviewActivity to a device/emulator passing a @Composable via intent parameter. */
open class ComposePreviewRunConfiguration(project: Project, factory: ConfigurationFactory) : AndroidRunConfiguration(project, factory) {

  var composableMethodFqn: String? = null
    set(value) {
      field = value
      ACTIVITY_EXTRA_FLAGS = "--es composable ${value}"
    }

  init {
    // This class is open just to be inherited in the tests, and the derived class is available when it needs to be accessed
    // TODO(b/152183413): limit the search to the library scope. We currently use the global scope because SpecificActivityLaunch.State only
    //                    accepts either project or global scope.
    @Suppress("LeakingThis")
    setLaunchActivity("androidx.ui.tooling.preview.PreviewActivity", true)
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

  override fun getConfigurationEditor() = ComposePreviewSettingsEditor(project, this)
}
