/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant

import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.android.tools.idea.assistant.view.FeaturesPanel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import org.apache.http.concurrent.FutureCallback
import java.awt.BorderLayout
import java.io.FileNotFoundException
import java.net.URL
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Panel for "assistant" flows such as tutorials, domain specific tools, etc.
 */
class AssistSidePanel(private val actionId: String,
                      private val project: Project,
                      private val titleCallback: FutureCallback<String>?) : JPanel(BorderLayout()) {
  var loadingPanel: JBLoadingPanel
  private var errorPanel: JPanel
  private var errorText: JBLabel

  private var bundleCreator: AssistantBundleCreator

  private val log: Logger
    get() = Logger.getInstance(AssistSidePanel::class.java)

  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    loadingPanel = JBLoadingPanel(BorderLayout(), project, 200)
    loadingPanel.add(this, BorderLayout.CENTER)
    loadingPanel.setLoadingText("Loading assistant content")
    loadingPanel.name = "assistantPanel"
    loadingPanel.startLoading()

    // Add an error message to show when there is an error while loading
    errorPanel = JPanel(BorderLayout())
    val message = "Error loading assistant panel. Please check idea.log for detailed error message."
    val htmlText = String.format("<html><div style='text-align: center;'>%s</div></html>", StringUtil.escapeXml(message))
    errorText = JBLabel(htmlText)
    errorText.horizontalAlignment = JBLabel.CENTER
    errorPanel.add(errorText, BorderLayout.CENTER)
    this.add(errorPanel, BorderLayout.CENTER)
    errorPanel.isVisible = false

    try {
      bundleCreator = AssistantBundleCreator.EP_NAME.extensions.first { it.bundleId == actionId }
    } catch (e: NoSuchElementException) {
      throw RuntimeException("Unable to find configuration for the selected action: $actionId")
    }

    // Instantiate the bundle from a configuration file using the default bundle mapping.
    // If null, creator must provide the bundle instance themselves.
    var config: URL? = null
    try {
      config = bundleCreator.config
    }
    catch (e: FileNotFoundException) {
      log.warn(e)
    }

    // Config provided, use that with the default bundle.
    if (config != null) {
      val task = AssistantGetBundleFromConfigTask(project, config, AssistantLoadingCallback(), bundleCreator.bundleId)
      task.queue()
    }
    else {
      val task = AssistantGetBundleTask(project, bundleCreator, AssistantLoadingCallback())
      task.queue()
    }
  }

  private fun createFeaturesPanel(bundle: TutorialBundleData?,
                                  actionId: String,
                                  bundleCreator: AssistantBundleCreator,
                                  project: Project) {
    if (bundle == null) {
      log.error("Unable to get Assistant configuration for action: $actionId")
      errorPanel.isVisible = true
    }
    else {
      // Provide the creator's class for classloading purposes.
      bundle.setResourceClass(bundleCreator.javaClass)
      for (feature in bundle.features) {
        feature.setResourceClass(bundleCreator.javaClass)
      }
      titleCallback?.completed(bundle.name)

      val analyticsProvider = bundleCreator.analyticsProvider
      analyticsProvider.trackPanelOpened(project)

      val featuresPanel = FeaturesPanel(bundle, project, analyticsProvider)
      add(featuresPanel)
    }
  }

  inner class AssistantLoadingCallback: FutureCallback<TutorialBundleData> {
    override fun cancelled() {
      loadingPanel.stopLoading()
    }

    override fun completed(bundle: TutorialBundleData?) {
      createFeaturesPanel(bundle, actionId, bundleCreator, project)
      loadingPanel.stopLoading()
    }

    override fun failed(ex: java.lang.Exception?) {
      log.error(ex)
      loadingPanel.stopLoading()
      errorPanel.isVisible = true
    }
  }
}
