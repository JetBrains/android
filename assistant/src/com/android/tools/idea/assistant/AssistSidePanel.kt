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
import java.awt.BorderLayout
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.xml.bind.JAXBException

/**
 * Panel for "assistant" flows such as tutorials, domain specific tools, etc.
 */
class AssistSidePanel(actionId: String, project: Project) : JPanel(BorderLayout()) {

  private val log: Logger
    get() = Logger.getInstance(AssistSidePanel::class.java)

  var title: String? = null
    private set

  init {
    border = BorderFactory.createEmptyBorder()
    isOpaque = false

    val assistContents = AssistantBundleCreator.EP_NAME.extensions.filter { it.bundleId == actionId }.map {
      // Instantiate the bundle from a configuration file using the default bundle mapping.
      // If null, creator must provide the bundle instance themselves.
      var config: URL? = null
      try {
        config = it.config
      }
      catch (e: FileNotFoundException) {
        log.warn(e)
      }

      // Config provided, use that with the default bundle.
      val bundle = if (config != null) {
        getBundle(config)
      }
      else {
        it.getBundle(project)
      }

      if (bundle == null) {
        log.error("Unable to get Assistant configuration for action: $actionId")
        null
      } else {
        // Provide the creator's class for classloading purposes.
        bundle!!.setResourceClass(it.javaClass)
        for (feature in bundle.features) {
          feature.setResourceClass(it.javaClass)
        }
        title = bundle.name

        val analyticsProvider = it.analyticsProvider
          analyticsProvider.trackPanelOpened(project)

        FeaturesPanel(bundle, project, analyticsProvider)
      }
    }

    if (assistContents.isEmpty()) {
      throw RuntimeException("Unable to find configuration for the selected action: $actionId")
    }
    add(assistContents.first())
  }

  private fun getBundle(config: URL): TutorialBundleData {
    var inputStream: InputStream? = null
    val bundle: TutorialBundleData
    try {
      inputStream = config.openStream()
      bundle = DefaultTutorialBundle.parse(inputStream!!)
    }
    catch (e: IOException) {
      throw RuntimeException("Unable to parse " + config.toString() +
                             " to read services configuration.", e)
    }
    catch (e: JAXBException) {
      throw RuntimeException(e)
    }
    finally {
      try {
        if (inputStream != null) inputStream.close()
      }
      catch (e: Exception) {
        log.warn(e)
      }

    }
    return bundle
  }
}
