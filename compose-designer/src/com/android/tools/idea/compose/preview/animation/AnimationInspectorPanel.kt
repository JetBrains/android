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
package com.android.tools.idea.compose.preview.animation

import com.android.tools.adtui.TabularLayout
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.animation.AnimationInspectorPanel.TransitionDurationTimeline
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.uibuilder.model.viewInfo
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSlider
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberFunctions

/**
 * Displays details about animations belonging to a Compose Preview. Allows users to see all the properties (e.g. `ColorPropKeys`) being
 * animated grouped by animation (e.g. `TransitionAnimation`, `AnimatedValue`). In addition, [TransitionDurationTimeline] is a timeline view
 * that can be controlled by scrubbing or through a set of controllers, such as play/pause and jump to end. The [AnimationInspectorPanel]
 * therefore allows a detailed inspection of Compose animations.
 *
 * TODO(b/157895086): set the main content to a tabbed pane.
 */
class AnimationInspectorPanel(surface: DesignSurface) : JPanel(TabularLayout("Fit,*", "Fit,*")) {
  private var composableTitle = JBLabel("Animations").apply { border = JBUI.Borders.empty(5, 0) }

  private var mainContent = JPanel(TabularLayout("Fit,*,Fit", "Fit,*")).apply {
    border = JBUI.Borders.customLine(JBColor.border(), 1, 0, 0, 0)
  }

  init {
    name = "Animation Inspector"
    add(composableTitle, TabularLayout.Constraint(0, 0))

    // Play controllers. TODO(b/157895086): implement actions and polish UI.
    val toolbar = JPanel(TabularLayout("Fit,Fit,Fit,Fit")).apply {
      add(toolbarAction(StudioIcons.LayoutEditor.Motion.GO_TO_START), TabularLayout.Constraint(0, 0))
      add(toolbarAction(StudioIcons.LayoutEditor.Motion.PLAY), TabularLayout.Constraint(0, 1))
      add(toolbarAction(StudioIcons.LayoutEditor.Motion.GO_TO_END), TabularLayout.Constraint(0, 2))
      add(toolbarAction(StudioIcons.LayoutEditor.Motion.PLAY_YOYO), TabularLayout.Constraint(0, 3))
    }

    // start/end states combo box.
    // TODO(b/157896171): states should be obtained from the TransitionAnimation object, not hard coded.
    val states = arrayOf("start", "end")
    val statesToolbar = JPanel(TabularLayout("Fit,Fit,Fit"))
    val startStateComboBox = ComboBox(DefaultComboBoxModel(states))
    val endStateComboBox = ComboBox(DefaultComboBoxModel(states))
    statesToolbar.add(startStateComboBox, TabularLayout.Constraint(0, 0))
    statesToolbar.add(JBLabel(message("animation.inspector.state.to.label")), TabularLayout.Constraint(0, 1))
    statesToolbar.add(endStateComboBox, TabularLayout.Constraint(0, 2))

    // Animated properties
    // TODO(b/157895086): this is a placeholder. This component should display the properties being animated.
    val animatedPropertiesPanel = JPanel().apply {
      preferredSize = JBDimension(200, 200)
      border = JBUI.Borders.customLine(JBColor.border(), 1)
    }

    mainContent.add(toolbar, TabularLayout.Constraint(0, 0))
    mainContent.add(statesToolbar, TabularLayout.Constraint(0, 2))
    mainContent.add(animatedPropertiesPanel, TabularLayout.Constraint(1, 0))
    mainContent.add(TransitionDurationTimeline(surface), TabularLayout.Constraint(1, 1, 2))
    add(mainContent, TabularLayout.Constraint(1, 0, 2))
  }

  private fun toolbarAction(icon: Icon) = JButton(icon).apply {
    preferredSize = JBDimension(24, 24)
    border = JBUI.Borders.empty()
  }

  /**
   *  Timeline panel ranging from 0 to the max duration of the animations being inspected, listing all the animations and their
   *  corresponding range as well. The timeline should respond to mouse commands, allowing users to jump to specific points, scrub it, etc.
   *
   *  TODO(b/157896171): duration should be obtained from the animation, not hard coded.
   *  TODO(b/157895086): The slider is a placeholder. The actual UI component is more complex and will be done in a future pass.
   */
  private class TransitionDurationTimeline(private val surface: DesignSurface) : JSlider(0, 10000, 0) {

    var cachedVal = -1

    var composeViewAdapter: Any? = null
    var setAnimationClockFunction: KFunction<*>? = null

    init {
      addChangeListener {
        if (this.value == cachedVal) return@addChangeListener // Ignore repeated values
        val newValue = this.value
        cachedVal = newValue

        if (composeViewAdapter != null && setAnimationClockFunction != null) {
          setClockTime(newValue)
          return@addChangeListener
        }

        surface.models.single().components[0]?.let { nlComponent ->
          composeViewAdapter = nlComponent.viewInfo?.viewObject ?: return@let
          setAnimationClockFunction = composeViewAdapter!!::class.memberFunctions.single {
            it.name == "setClockTime"
          }
          setClockTime(newValue)
        }
      }
    }

    private fun setClockTime(timeMs: Int) {
      surface.layoutlibSceneManagers.single().executeCallbacksAndRequestRender {
          setAnimationClockFunction!!.call(composeViewAdapter, timeMs.toLong())
      }
    }
  }
}