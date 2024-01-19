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
package com.android.tools.idea.assistant.view

import com.android.tools.idea.assistant.AssistActionHandler
import com.android.tools.idea.assistant.AssistNavListener
import com.android.tools.idea.assistant.datamodel.AnalyticsProvider
import com.android.tools.idea.assistant.datamodel.TutorialBundleData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.JPanel

/**
 * Entry point for the complete set of services and tutorials associated with Firebase. Initializes
 * presentation data from xml and arranges into cards for navigation purposes.
 */
class FeaturesPanel(
  bundle: TutorialBundleData,
  val project: Project,
  private val myAnalyticsProvider: AnalyticsProvider,
  defaultCardId: String? = null,
) : JPanel(BorderLayout()), ItemListener, ActionListener {
  private val cardKeys = mutableSetOf<String>()
  private val cardsPanel: JPanel
  private val cardLayout: CardLayout

  /** If non-null, the key of the currently open tutorial. Used for analytics tracking purposes. */
  private var myOpenTutorial: TutorialMetadata? = null

  init {
    background = UIUtils.getBackgroundColor()
    cardLayout = CardLayout()
    cardLayout.vgap = 0
    cardsPanel = JPanel(cardLayout)
    cardsPanel.isOpaque = false

    // NOTE: the card labels cannot be from an enum since the views will be
    // built up from xml.
    val featureList = bundle.features
    // Note: Hides Tutorial Chooser panel if there is only one feature and one tutorial.
    var hideChooserAndNavigationalBar = false
    if (featureList.size == 1 && featureList[0].tutorials.size == 1) {
      hideChooserAndNavigationalBar = true
      log.debug(
        "Tutorial chooser and head/bottom navigation bars are hidden because the assistant panel contains only one tutorial."
      )
    } else {
      addCard(
        TutorialChooser(this, bundle, myAnalyticsProvider, project),
        TutorialChooser.NAVIGATION_KEY,
      )
    }

    // Add all tutorial cards.
    bundle.features.forEach { feature ->
      feature.tutorials.forEach { tutorialData ->
        addCard(
          TutorialCard(this, tutorialData, feature, hideChooserAndNavigationalBar, bundle),
          tutorialData.key,
        )
      }
    }

    add(cardsPanel)

    if (defaultCardId != null && cardKeys.contains(defaultCardId)) {
      showCard(defaultCardId)
    }
  }

  private fun addCard(c: Component, key: String) {
    cardsPanel.add(c, key)
    cardKeys.add(key)
  }

  // TODO: Determine if this should just throw instead, we're not navigating via controls that
  // surface this event.
  override fun itemStateChanged(e: ItemEvent) {
    val cl = cardsPanel.layout as CardLayout
    cl.show(cardsPanel, e.item as String)
  }

  override fun actionPerformed(e: ActionEvent) {
    // TODO: Refactor this code to avoid bloat. This should generally be a dispatcher to more
    // specific classes that manage a given action
    // type. Current thinking is to use extensions so that it's completely generic.
    when (val source = e.source) {
      is NavigationButton -> {
        // Track that user has navigated away from a tutorial. Note that the
        // "chooser" card is special cased in {@code #showCard} such that
        // no myOpenTutorial + myTimeTutorialOpened are not set.
        if (myOpenTutorial != null) {
          myAnalyticsProvider.trackTutorialClosed(
            myOpenTutorial!!.key,
            myOpenTutorial!!.readDuration,
            project,
          )
          myOpenTutorial = null
        }
        val key = source.key
        showCard(key)
        AssistNavListener.EP_NAME.extensions
          .filter { listener -> key.startsWith(listener.idPrefix) }
          .forEach { listener -> listener.onActionPerformed(key, e) }
      }
      is StatefulButton.ActionButton -> {
        val actionId = source.key
        val handler =
          AssistActionHandler.EP_NAME.extensions.first { handler -> handler.id == actionId }
        requireNotNull(handler) { "Unhandled action, no handler found for key \"$actionId\"." }
        handler.handleAction(source.actionData, source.project)
        source.updateState()
      }
      else -> {
        throw RuntimeException("Unhandled action, \"${e.actionCommand}\".")
      }
    }
  }

  /**
   * Shows the card matching the given key.
   *
   * @param key The key of the card to show.
   */
  private fun showCard(key: String) {
    require(cardKeys.contains(key)) { "No views exist with key: $key" }
    log.debug("Received request to navigate to view with key: $key")
    if (key != TutorialChooser.NAVIGATION_KEY) {
      myAnalyticsProvider.trackTutorialOpen(key, project)
      myOpenTutorial = TutorialMetadata(key)
    }
    cardLayout.show(cardsPanel, key)
  }

  private class TutorialMetadata(val key: String) {
    private val myTimeOpenedMs = System.currentTimeMillis()

    val readDuration: Long
      get() = System.currentTimeMillis() - myTimeOpenedMs
  }

  companion object {
    private val log: Logger
      get() = Logger.getInstance(FeaturesPanel::class.java)
  }
}
