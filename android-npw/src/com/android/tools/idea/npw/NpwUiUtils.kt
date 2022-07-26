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
package com.android.tools.idea.npw

import com.android.tools.idea.gradle.plugin.AndroidPluginInfo
import com.android.tools.idea.wizard.template.Category
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.Cell
import com.intellij.ui.layout.LayoutBuilder
import java.awt.Component
import java.net.MalformedURLException
import java.net.URL
import javax.swing.JLabel
import javax.swing.SwingConstants

// TODO: Move this to `intellij.android.common`.
internal fun invokeLater(modalityState: ModalityState = ModalityState.any(), f: () -> Unit) =
  ApplicationManager.getApplication().invokeLater(f, modalityState)

/**
 * Creates a [JLabel], sets [JLabel.labelFor] and an optional [ContextHelpLabel].
 * It is recommended to create it inside of a cell if context help is used.
 */
@Deprecated("Use contextLabel method and Kotlin UI DSL")
internal fun Cell.labelFor(text: String, forComponent: Component, contextHelpText: String? = null): JLabel {
  val label = if (contextHelpText == null) {
    JBLabel(text)
  }
  else {
    ContextHelpLabel.create(contextHelpText).apply {
      setText(text)
      horizontalTextPosition = SwingConstants.LEFT
    }
  }.apply {
    labelFor = forComponent
  }

  label()
  return label
}

internal fun contextLabel(text: String, contextHelpText: String): JLabel {
  return ContextHelpLabel.create(contextHelpText).apply {
    setText(text)
    horizontalTextPosition = SwingConstants.LEFT
  }
}

@Deprecated("Use Kotlin UI DSL Version 2")
internal fun LayoutBuilder.verticalGap() {
  row {
    label("")
  }
}

// TODO: parentej needs to be updated to 4.0.0 when released
internal const val COMPOSE_MIN_AGP_VERSION = "4.0.0-alpha02"

internal fun hasComposeMinAgpVersion(project: Project?, category: Category): Boolean {
  if (project == null || Category.Compose != category) {
    return true
  }
  val androidPluginInfo = AndroidPluginInfo.findFromModel(project) ?: return true
  val agpVersion = androidPluginInfo.pluginVersion ?: return true
  return agpVersion >= COMPOSE_MIN_AGP_VERSION
}

/**
 * Utility method used to create a URL from its String representation without throwing a [MalformedURLException].
 * Callers should use this if they're absolutely certain their URL is well formatted.
 */
internal fun toUrl(urlAsString: String): URL {
  val url: URL = try {
    URL(urlAsString)
  }
  catch (e: MalformedURLException) {
    // Caller should guarantee this will never happen!
    throw RuntimeException(e)
  }
  return url
}
