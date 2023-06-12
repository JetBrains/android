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
import com.google.common.annotations.VisibleForTesting
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.border.MatteBorder

/** Component and its layout for `All animations` tab. */
class AllTabPanel : JPanel(TabularLayout("2px,*", "31px,*")) {

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

  private val splitter =
    JBSplitter(false, .4f, .1f, .9f).apply {
      // Cards
      firstComponent = JPanel(TabularLayout("*")).apply { this.border = getCardsBorder() }
      // Timeline
      secondComponent =
        JPanel(BorderLayout()).apply { this.border = MatteBorder(0, 1, 0, 0, JBColor.border()) }
      dividerWidth = 3
    }

  private fun updateDimension() {
    val preferredHeight =
      InspectorLayout.timelineHeaderHeightScaled() +
        JBUI.scale(cards.sumOf { it.getCurrentHeight() })
    splitter.firstComponent.preferredSize =
      Dimension(splitter.firstComponent.width, preferredHeight)
    splitter.secondComponent.preferredSize =
      Dimension(splitter.secondComponent.width, preferredHeight)
  }

  private val cardsLayout
    get() = splitter.firstComponent.layout as TabularLayout

  private val scrollPane =
    JBScrollPane(VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER).apply {
      this.setViewportView(splitter)
    }

  @VisibleForTesting val cards = mutableListOf<Card>()

  fun addTimeline(timeline: JComponent) {
    splitter.secondComponent.add(timeline, BorderLayout.CENTER)
    timeline.revalidate()
    timeline.doLayout()
  }

  fun addPlayback(playback: JComponent) {
    add(
      playback.apply { border = MatteBorder(0, 0, 1, 0, JBColor.border()) },
      TabularLayout.Constraint(0, 1)
    )
  }

  fun addCard(card: Card) {
    if (cards.contains(card)) return
    splitter.firstComponent.add(card.component, TabularLayout.Constraint(cards.size, 0))
    cards.add(card)
    cardsLayout.setRowSizing(
      cards.indexOf(card),
      TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, card.getCurrentHeight())
    )
    card.state.addExpandedListener { updateCardSize(card) }
    updateDimension()
  }

  fun updateCardSize(card: Card) {
    cardsLayout.setRowSizing(
      cards.indexOf(card),
      TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, card.getCurrentHeight())
    )
    updateDimension()
    card.component.revalidate()
  }

  fun removeCard(card: Card) {
    cards.remove(card)
    splitter.firstComponent.remove(card.component)
    updateDimension()
    splitter.firstComponent.revalidate()
  }

  private fun getCardsBorder() = JBUI.Borders.emptyTop(InspectorLayout.TIMELINE_HEADER_HEIGHT)

  init {
    add(scrollPane, TabularLayout.Constraint(1, 0, 2))
    isFocusable = false
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    JBUIScale.addUserScaleChangeListener { splitter.firstComponent.border = getCardsBorder() }
  }
}
