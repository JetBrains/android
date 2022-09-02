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
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.timeline.ElementState
import com.android.tools.idea.compose.preview.message
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.AnActionButton
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.MatteBorder

class AnimationCard(
  previewState: AnimationPreviewState,
  val surface: DesignSurface<*>,
  override val state: ElementState,
  private val tracker: ComposeAnimationEventTracker
) : JPanel(TabularLayout("*", "30px,30px")), Card {

  // Collapsed view:
  //   Expand button
  //   |    Transition name
  //   |   |                            Duration of the transition
  //   ↓   ↓                            ↓
  // ⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽
  // ⎹  ▶  transitionName                  100ms ⎹ ⬅ component
  // ⎹     ❄️  ↔️  [Start State]  to  [End State]⎹
  //  ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅
  //      ↑    ↑
  //      |    StateComboBox - AnimatedVisibilityComboBox or StartEndComboBox.
  //     Freeze / unfreeze toggle.
  //
  //
  // Expanded view:
  // ⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽
  // ⎹  ▼  transitionName                  100ms ⎹
  // ⎹     ❄️  ↔️  [Start State]  to  [End State]⎹
  // ⎹                                           ⎹
  // ⎹                                           ⎹
  // ⎹                                           ⎹
  // ⎹                                           ⎹
  //  ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅̅̅ ̅ ̅̅ ̅ ̅ ̅̅̅

  override val component: JPanel = this
  var openInTabListeners: MutableList<() -> Unit> = mutableListOf()
  override var expandedSize = InspectorLayout.TIMELINE_LINE_ROW_HEIGHT

  private val firstRow =
    JPanel(TabularLayout("30px,*,Fit", "30px")).apply { border = JBUI.Borders.empty(0, 0, 0, 8) }

  private val secondRow =
    JPanel(TabularLayout("30px,Fit,*", "30px")).apply { border = JBUI.Borders.empty(0, 25, 0, 8) }

  override fun getCurrentHeight() =
    if (state.expanded) expandedSize else InspectorLayout.TIMELINE_LINE_ROW_HEIGHT

  var durationLabel: Component? = null
  override fun setDuration(durationMillis: Int?) {
    durationLabel?.let { firstRow.remove(it) }
    durationLabel =
      JBLabel("${durationMillis ?: "_"}ms")
        .apply { foreground = UIUtil.getContextHelpForeground() }
        .also { firstRow.add(it, TabularLayout.Constraint(0, 2)) }
  }

  fun addOpenInTabListener(listener: () -> Unit) {
    openInTabListeners.add(listener)
  }

  fun addStateComponent(component: JComponent) {
    secondRow.add(component, TabularLayout.Constraint(0, 1))
  }

  init {
    val expandButton = SingleButtonToolbar(surface, "ExpandCollapseAnimationCard", ExpandAction())
    firstRow.add(expandButton, TabularLayout.Constraint(0, 0))
    firstRow.add(JBLabel(state.title ?: "_"), TabularLayout.Constraint(0, 1))

    val freezeToolbar =
      SingleButtonToolbar(
        surface,
        "FreezeAnimationCard",
        FreezeAction(previewState, state, tracker)
      )
    secondRow.add(freezeToolbar, TabularLayout.Constraint(0, 0))
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

  private inner class ExpandAction() :
    AnActionButton(message("animation.inspector.action.expand"), UIUtil.getTreeCollapsedIcon()) {

    override fun actionPerformed(e: AnActionEvent) {
      state.expanded = !state.expanded
      if (state.expanded) {
        tracker(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.EXPAND_ANIMATION_CARD)
      } else {
        tracker(
          ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.COLLAPSE_ANIMATION_CARD
        )
      }
    }

    override fun updateButton(e: AnActionEvent) {
      super.updateButton(e)
      e.presentation.isEnabled = true
      e.presentation.apply {
        if (state.expanded) {
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
