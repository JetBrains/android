/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.panels.htmlTextLabel
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

/**
 * This class defines a text label on build attribution pages showing html text with a single web link for help documentation.
 *
 * Same pattern appeared in multiple places so this class abstracts it out.
 */
class DescriptionWithHelpLinkLabel(
  text: String,
  learnMoreTarget: String,
  analytics: BuildAttributionUiAnalytics
) : JBPanel<JBPanel<*>>(GridBagLayout()) {

  init {
    val descriptionTextLabel = htmlTextLabel(text)

    val descriptionConstraints = GridBagConstraints().apply {
      fill = GridBagConstraints.HORIZONTAL
      gridx = 0
      weightx = 1.0
      gridy = 0
      insets = JBUI.insetsLeft(2)
      anchor = GridBagConstraints.FIRST_LINE_START
    }

    val learnMoreLink = HyperlinkLabel("Learn more").apply {
      addHyperlinkListener { analytics.helpLinkClicked() }
      setHyperlinkTarget(learnMoreTarget)
    }
    val linkConstraints = GridBagConstraints().apply {
      fill = GridBagConstraints.HORIZONTAL
      gridx = 0
      gridy = 1
      anchor = GridBagConstraints.LINE_START
    }

    add(descriptionTextLabel, descriptionConstraints)
    add(learnMoreLink, linkConstraints)
  }
}