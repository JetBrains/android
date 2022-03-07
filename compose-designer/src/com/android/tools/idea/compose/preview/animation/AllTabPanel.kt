/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.TabularLayout
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.border.MatteBorder

/** Component and its layout for `All animations` tab. */
class AllTabPanel : JPanel(TabularLayout("*", "Fit,*")) {

  //   ________________________________________________
  //   | [Playback control]                            |
  //   |              |                                |
  //   |              |                                |
  //   |              |                                |
  //   |    List      |        Timeline                |
  //   |    of        |                                |
  //   |    cards     |                                |
  //   |              |                                |
  //   |              |                                |
  //   |    ...       |             ...                | <- Cards and Timeline are scrollable.
  //   |______________|________________________________|

  private val splitter = JBSplitter(0.4f).apply {
    // Cards
    firstComponent = JPanel(TabularLayout("*")).apply {
      this.border = MatteBorder(InspectorLayout.TIMELINE_TOP_OFFSET, 0, 0, 0, InspectorColors.TIMELINE_BACKGROUND_COLOR)
    }
    // Timeline
    secondComponent = JPanel(BorderLayout()).apply {
      this.border = MatteBorder(0, 1, 0, 0, JBColor.border())
    }
    dividerWidth = 1
  }

  private fun updateDimension() {
    splitter.firstComponent.preferredSize =
      Dimension(width, InspectorLayout.TIMELINE_TOP_OFFSET + cards.sumOf { it.getCurrentHeight() })
  }

  private val cardsLayout
    get() = splitter.firstComponent.layout as TabularLayout

  private val scrollPane = JBScrollPane(VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER).apply {
    this.setViewportView(splitter)
  }

  private val cards = mutableListOf<AnimationCard>()

  fun addTimeline(timeline: JComponent) {
    splitter.secondComponent.add(timeline, BorderLayout.CENTER)
    timeline.revalidate()
    timeline.doLayout()
  }

  fun addPlayback(playback: JComponent) {
    add(playback.apply { border = MatteBorder(0, 0, 1, 0, JBColor.border()) },
        TabularLayout.Constraint(0, 0))
  }

  fun addCard(card: AnimationCard) {
    if (cards.contains(card)) return
    splitter.firstComponent.add(card, TabularLayout.Constraint(cards.size, 0))
    cards.add(card)
    cardsLayout.setRowSizing(
      cards.indexOf(card), TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, card.getCurrentHeight()))
    card.state.addExpandedListener {
      cardsLayout.setRowSizing(
        cards.indexOf(card), TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, card.getCurrentHeight()))
      updateDimension()
      card.revalidate()
    }
    updateDimension()
  }

  fun removeCard(card: AnimationCard) {
    cards.remove(card)
    splitter.firstComponent.remove(card)
    updateDimension()
    splitter.firstComponent.revalidate()
  }

  init {
    add(scrollPane, TabularLayout.Constraint(1, 0))
    isFocusable = false
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
  }
}