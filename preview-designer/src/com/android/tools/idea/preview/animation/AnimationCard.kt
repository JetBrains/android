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

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.animation.actions.FreezeAction
import com.android.tools.idea.preview.animation.timeline.ElementState
import com.android.tools.idea.preview.util.createToolbarWithNavigation
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.MatteBorder
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow

class AnimationCard(
  timelinePanel: TimelinePanel,
  rootComponent: JComponent,
  val state: MutableStateFlow<ElementState>,
  override val title: String,
  extraActions: List<AnAction> = emptyList(),
  private val tracker: AnimationTracker,
) : JPanel(TabularLayout("*", "30px,40px")), Card {

  // Collapsed view:
  //   Expand button
  //   |    Transition name
  //   |   |                            Duration of the transition
  //   ↓   ↓                            ↓
  // ⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽
  // ⎹  ▶  transitionName                  100ms ⎹ ⬅ component
  // ⎹     ❄️  [extra actions]                   ⎹
  //  ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅
  //      ↑
  //      |
  //     Freeze / unfreeze toggle.
  //
  //
  // Expanded view:
  // ⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽
  // ⎹  ▼  transitionName                  100ms ⎹
  // ⎹     ❄️  [extra actions]                   ⎹
  // ⎹                                           ⎹
  // ⎹                                           ⎹
  // ⎹                                           ⎹
  // ⎹                                           ⎹
  //  ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅ ̅̅ ̅ ̅ ̅̅̅

  override val component: JPanel = this
  var openInTabListeners: MutableList<() -> Unit> = mutableListOf()
  override var expandedSize = InspectorLayout.TIMELINE_LINE_ROW_HEIGHT

  private val firstRow =
    JPanel(TabularLayout("30px,*,Fit", "30px")).apply { border = JBUI.Borders.emptyRight(8) }

  private val secondRow = JPanel(BorderLayout()).apply { border = JBUI.Borders.empty(0, 25, 0, 8) }

  override fun getCurrentHeight() =
    // Card has a minimum height of TIMELINE_LINE_ROW_HEIGHT.
    if (state.value.expanded) max(expandedSize, InspectorLayout.TIMELINE_LINE_ROW_HEIGHT)
    else InspectorLayout.TIMELINE_LINE_ROW_HEIGHT

  private var durationLabel: Component? = null

  override fun setDuration(durationMillis: Int) {
    durationLabel?.let { firstRow.remove(it) }
    durationLabel =
      JBLabel("${durationMillis}ms")
        .apply { foreground = UIUtil.getContextHelpForeground() }
        .also { firstRow.add(it, TabularLayout.Constraint(0, 2)) }
  }

  fun addOpenInTabListener(listener: () -> Unit) {
    openInTabListeners.add(listener)
  }

  init {
    val expandButton =
      createToolbarWithNavigation(
        rootComponent,
        "ExpandCollapseAnimationCard",
        listOf(ExpandAction()),
      )
    firstRow.add(expandButton.component, TabularLayout.Constraint(0, 0))
    firstRow.add(JBLabel(title), TabularLayout.Constraint(0, 1))

    val secondRowToolbar =
      createToolbarWithNavigation(
        rootComponent,
        "AnimationCard",
        listOf(FreezeAction(timelinePanel, state, tracker)) + extraActions,
      )
    secondRow.add(secondRowToolbar.component, BorderLayout.CENTER)
    add(firstRow, TabularLayout.Constraint(0, 0))
    add(secondRow, TabularLayout.Constraint(1, 0))
    OpenInNewTab().installOn(this)
    border = MatteBorder(1, 0, 0, 0, JBColor.border())
  }

  private inner class OpenInNewTab : DoubleClickListener() {
    override fun onDoubleClick(e: MouseEvent): Boolean {
      openInTabListeners.forEach { it() }
      return true
    }
  }

  private inner class ExpandAction :
    DumbAwareAction(
      message("animation.inspector.action.expand"),
      null,
      UIUtil.getTreeCollapsedIcon(),
    ) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
      state.value = state.value.copy(expanded = !state.value.expanded)
      if (state.value.expanded) {
        tracker.expandAnimationCard()
      } else {
        tracker.collapseAnimationCard()
      }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = true
      e.presentation.apply {
        if (state.value.expanded) {
          icon = UIUtil.getTreeExpandedIcon()
          text = message("animation.inspector.action.collapse")
        } else {
          icon = UIUtil.getTreeCollapsedIcon()
          text = message("animation.inspector.action.expand")
        }
      }
    }
  }
}
