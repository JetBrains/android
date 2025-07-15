/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.animation

import com.android.annotations.TestOnly
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Insets
import java.beans.PropertyChangeListener
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
import javax.swing.border.MatteBorder
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Component and its layout for `All animations` tab. */
class AllTabPanel
private constructor(parentDisposable: Disposable, private val onUserScaleChange: () -> Unit) :
  JPanel(TabularLayout("6px,*", "32px,*")), Disposable {

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
  //          ↑
  //    The panel of cards allows horizontal scrolling.

  private val splitter =
    OnePixelSplitter(false, .4f, .1f, .9f).apply {
      // Cards
      val firstPanel = JPanel(TabularLayout("*")).apply { this.border = getCardsBorder() }
      // Show a horizontal scrollbar on attributes subpanel if content exceeds width of subpanel
      firstComponent = createHorizontalScrollPane(firstPanel)

      // Timeline
      secondComponent =
        JPanel(BorderLayout()).apply { this.border = MatteBorder(0, 1, 0, 0, JBColor.border()) }

      setBlindZone { Insets(0, 1, 0, 1) }
    }

  private val scope = AndroidCoroutineScope(parentDisposable)

  private val userScaleChangeListener = PropertyChangeListener {
    cardsPanel.border = getCardsBorder()
    onUserScaleChange()
  }

  /** First panel on the left side of the splitter containing the list of cards panel */
  private val cardsPanel
    get() = (splitter.firstComponent as JBScrollPane).viewport.view as JPanel

  /** Cards panel layout */
  private val cardsPanelLayout
    get() = cardsPanel.layout as TabularLayout

  /** Second panel on the right side of the splitter containing the timeline panel */
  private val timelinePanel
    get() = splitter.secondComponent as JPanel

  private val scrollPane =
    JBScrollPane(VERTICAL_SCROLLBAR_ALWAYS, HORIZONTAL_SCROLLBAR_NEVER).apply {
      this.setViewportView(splitter)
    }

  private val cardsLock = ReentrantReadWriteLock()

  @VisibleForTesting @GuardedBy("cardsLock") val cards = mutableListOf<Card>()

  private val jobsByCard = mutableMapOf<Card, Job>()

  constructor(parentDisposable: Disposable) : this(parentDisposable, {})

  init {
    Disposer.register(parentDisposable, this)
    add(scrollPane, TabularLayout.Constraint(1, 0, 2))
    isFocusable = false
    focusTraversalPolicy = LayoutFocusTraversalPolicy()
    JBUIScale.addUserScaleChangeListener(userScaleChangeListener)
  }

  /**
   * Helper function to wrap a component in a horizontal scroll pane. The horizontal scrollbar is
   * shown when the component is scrollable.
   *
   * @param component The component to wrap in the scroll pane.
   * @param minWidth The minimum width of the scroll pane. Default value [MIN_PANEL_WIDTH_PX].
   * @param minHeight The minimum height of the scroll pane.
   */
  private fun createHorizontalScrollPane(
    component: Component,
    minWidth: Int = MIN_PANEL_WIDTH_PX,
    minHeight: Int = 0,
  ) =
    JBScrollPane(component, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_ALWAYS).apply {
      // Removes JBScrollPane border to avoid double borders with panel.
      border = null
      minimumSize = Dimension(minWidth, minHeight)
    }

  private fun updateDimension() {
    val sumOfCardHeights = cardsLock.read { cards.sumOf { it.getCurrentHeight() } }
    val preferredHeight =
      InspectorLayout.timelineHeaderHeightScaled() + JBUI.scale(sumOfCardHeights)
    splitter.firstComponent.preferredSize =
      Dimension(splitter.firstComponent.width, preferredHeight)
    splitter.secondComponent.preferredSize =
      Dimension(splitter.secondComponent.width, preferredHeight)
  }

  fun addTimeline(timeline: JComponent) {
    timelinePanel.add(timeline, BorderLayout.CENTER)
    timeline.revalidate()
    timeline.doLayout()
  }

  fun addPlayback(playback: JComponent) {
    add(
      playback.apply { border = MatteBorder(0, 0, 1, 0, JBColor.border()) },
      TabularLayout.Constraint(0, 1),
    )
  }

  fun addCard(card: Card) {
    cardsLock.read {
      if (cards.contains(card)) return
      cardsPanel.add(card.component, TabularLayout.Constraint(cards.size, 0))
      cardsLock.write { cards.add(card) }
      cardsPanelLayout.setRowSizing(
        cards.indexOf(card),
        TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, card.getCurrentHeight()),
      )
    }
    updateDimension()
    if (card is AnimationCard) {
      jobsByCard[card] =
        scope.launch { card.expanded.collect { withContext(uiThread) { updateCardSize(card) } } }
    }
  }

  fun updateCardSize(card: Card) {
    cardsPanelLayout.setRowSizing(
      cardsLock.read { cards.indexOf(card) },
      TabularLayout.SizingRule(TabularLayout.SizingRule.Type.FIXED, card.getCurrentHeight()),
    )
    updateDimension()
    card.component.revalidate()
  }

  fun removeCard(card: Card) {
    cardsLock.write { cards.remove(card) }
    cardsPanel.remove(card.component)
    updateDimension()
    cardsPanel.revalidate()
    jobsByCard[card]?.cancel()
    jobsByCard.remove(card)
  }

  private fun getCardsBorder() = JBUI.Borders.emptyTop(InspectorLayout.TIMELINE_HEADER_HEIGHT)

  override fun dispose() {
    JBUIScale.removeUserScaleChangeListener(userScaleChangeListener)
  }

  companion object {
    /** Cards panel minimum width to prevent elements becoming invisible without visual cues. */
    private const val MIN_PANEL_WIDTH_PX = 185

    @TestOnly
    fun createAllTabPanelForTest(parentDisposable: Disposable, onUserScaleChange: () -> Unit) =
      AllTabPanel(parentDisposable, onUserScaleChange)
  }
}
