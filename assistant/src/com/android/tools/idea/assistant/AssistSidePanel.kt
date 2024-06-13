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
import javax.swing.BorderFactory
import javax.swing.JPanel

/** Panel for "assistant" flows such as tutorials, domain specific tools, etc. */
class AssistSidePanel(private val project: Project) : JPanel(BorderLayout()) {
  val loadingPanel: JBLoadingPanel
  private val errorPanel: JPanel
  private val errorText: JBLabel

  private val log: Logger
    get() = Logger.getInstance(AssistSidePanel::class.java)

  private var featuresPanel: FeaturesPanel? = null

  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    loadingPanel = JBLoadingPanel(BorderLayout(), project, 200)
    loadingPanel.add(this, BorderLayout.CENTER)
    loadingPanel.setLoadingText("Loading assistant content")
    loadingPanel.name = "assistantPanel"

    // Add an error message to show when there is an error while loading
    errorPanel = JPanel(BorderLayout())
    val message = "Error loading assistant panel. Please check idea.log for detailed error message."
    val htmlText =
      "<html><div style='text-align: center;'>${StringUtil.escapeXmlEntities(message)}</div></html>"
    errorText = JBLabel(htmlText)
    errorText.horizontalAlignment = JBLabel.CENTER
    errorPanel.add(errorText, BorderLayout.CENTER)
    this.add(errorPanel, BorderLayout.CENTER)
    errorPanel.isVisible = false
  }

  fun showBundle(
    bundleId: String,
    defaultTutorialId: String? = null,
    onBundleCreated: ((TutorialBundleData) -> Unit)? = null,
  ) {
    featuresPanel?.let { remove(it) }
    loadingPanel.startLoading()
    errorPanel.isVisible = false

    val bundleCreator =
      try {
        AssistantBundleCreator.EP_NAME.extensions.first { it.bundleId == bundleId }
      } catch (e: NoSuchElementException) {
        log.warn("Unable to find configuration for the selected action: $bundleId")
        return
      }

    // Instantiate the bundle from a configuration file using the default bundle mapping.
    // If null, creator must provide the bundle instance themselves.
    val config =
      try {
        bundleCreator.config
      } catch (e: FileNotFoundException) {
        log.warn(e)
        null
      }

    // Config provided, use that with the default bundle.
    if (config != null) {
      AssistantGetBundleFromConfigTask(
          project,
          config,
          AssistantLoadingCallback(bundleId, bundleCreator, defaultTutorialId, onBundleCreated),
          bundleCreator.bundleId,
        )
        .queue()
    } else {
      AssistantGetBundleTask(
          project,
          bundleCreator,
          AssistantLoadingCallback(bundleId, bundleCreator, defaultTutorialId, onBundleCreated),
        )
        .queue()
    }
  }

  private inner class AssistantLoadingCallback(
    private val bundleId: String,
    private val bundleCreator: AssistantBundleCreator,
    private val defaultTutorialId: String?,
    private val onBundleCreated: ((TutorialBundleData) -> Unit)?,
  ) : FutureCallback<TutorialBundleData> {
    private fun createFeaturesPanel(
      bundle: TutorialBundleData?,
      actionId: String,
      bundleCreator: AssistantBundleCreator,
      project: Project,
    ) {
      if (bundle == null) {
        log.error("Unable to get Assistant configuration for action: $actionId")
        errorPanel.isVisible = true
      } else {
        // Provide the creator's class for classloading purposes.
        bundle.setResourceClass(bundleCreator.javaClass)
        for (feature in bundle.features) {
          feature.setResourceClass(bundleCreator.javaClass)
        }
        onBundleCreated?.invoke(bundle)

        val analyticsProvider = bundleCreator.analyticsProvider
        analyticsProvider.trackPanelOpened(project)

        featuresPanel = FeaturesPanel(bundle, project, analyticsProvider, defaultTutorialId)
        add(featuresPanel)
      }
    }

    override fun cancelled() {
      loadingPanel.stopLoading()
    }

    override fun completed(bundle: TutorialBundleData) {
      createFeaturesPanel(bundle, bundleId, bundleCreator, project)
      loadingPanel.stopLoading()
    }

    override fun failed(e: Exception) {
      log.error(e)
      loadingPanel.stopLoading()
      errorPanel.isVisible = true
    }
  }
}
