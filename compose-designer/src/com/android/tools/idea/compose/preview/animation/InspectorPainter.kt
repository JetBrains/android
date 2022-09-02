/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.compose.preview.message
import com.google.wireless.android.sdk.stats.ComposeAnimationToolingEvent
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.AnActionButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSlider

/** [ActionToolbarImpl] with enabled navigation. */
open class DefaultToolbarImpl(surface: DesignSurface<*>, place: String, action: AnAction) :
  ActionToolbarImpl(place, DefaultActionGroup(action), true) {
  init {
    targetComponent = surface
    ActionToolbarUtil.makeToolbarNavigable(this)
    layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    setMinimumButtonSize(ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  }
}

internal class SingleButtonToolbar(surface: DesignSurface<*>, place: String, action: AnAction) :
  DefaultToolbarImpl(surface, place, action) {
  // From ActionToolbar#setMinimumButtonSize, all the toolbar buttons have 25x25 pixels by default.
  // Set the preferred size of the
  // toolbar to be 5 pixels more in both height and width, so it fits exactly one button plus a
  // margin
  override fun getPreferredSize() = JBUI.size(30, 30)
}

/** Graphics elements corresponding to painting the inspector in [AnimationPreview]. */
object InspectorPainter {

  object Slider {

    /** Minimum distance between major ticks in the timeline. */
    private const val MINIMUM_TICK_DISTANCE = 150

    private val TICK_INCREMENTS =
      arrayOf(
        1_000_000_000,
        100_000_000,
        10_000_000,
        1_000_000,
        100_000,
        10_000,
        10_000,
        1_000,
        200,
        50,
        10,
        5,
        2
      )

    /**
     * Get the dynamic tick increment for horizontal slider:
     * * its width should be bigger than [MINIMUM_TICK_DISTANCE]
     * * tick increment is rounded to nearest [TICK_INCREMENTS]x
     */
    fun getTickIncrement(slider: JSlider, minimumTickSize: Int = MINIMUM_TICK_DISTANCE): Int {
      if (slider.maximum == 0 || slider.width == 0) return slider.maximum
      val increment =
        (minimumTickSize.toFloat() / slider.width * (slider.maximum - slider.minimum)).toInt()
      TICK_INCREMENTS.forEach {
        if (increment >= it) return@getTickIncrement (increment / (it - 1)) * it
      }
      return 1
    }
  }

  /** Thumb displayed in animation timeline. */
  object Thumb {
    private val THUMB_COLOR = JBColor(0x4A81FF, 0xB4D7FF)

    /** Half width of the shape used as the handle of the timeline scrubber. */
    private const val HANDLE_HALF_WIDTH = 5

    /** Half height of the shape used as the handle of the timeline scrubber. */
    private const val HANDLE_HALF_HEIGHT = 5

    /**
     * Paint a thumb for horizontal slider.
     * @param x bottom position of the scrubber
     * @param y bottom position of the scrubber
     */
    fun paintThumbForHorizSlider(g: Graphics2D, x: Int, y: Int, height: Int) {
      g.color = THUMB_COLOR
      g.stroke = InspectorLayout.simpleStroke
      g.drawLine(x, y, x, y + height)
      // The scrubber handle should have the following shape:
      //         ___
      //        |   |
      //         \ /
      // We add 5 points with the following coordinates:
      // (x, y): bottom of the scrubber handle
      // (x - halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one
      // (left side)
      // (x - halfWidth, y - Height): top-left point of the scrubber, where there is a right angle
      // (x + halfWidth, y - Height): top-right point of the scrubber, where there is a right angle
      // (x + halfWidth, y - halfHeight): where the scrubber angled part meets the vertical one
      // (right side)
      val handleHeight = HANDLE_HALF_HEIGHT * 2
      val xPoints =
        intArrayOf(
          x,
          x - HANDLE_HALF_WIDTH,
          x - HANDLE_HALF_WIDTH,
          x + HANDLE_HALF_WIDTH,
          x + HANDLE_HALF_WIDTH
        )
      val yPoints =
        intArrayOf(
          y,
          y - HANDLE_HALF_HEIGHT,
          y - handleHeight,
          y - handleHeight,
          y - HANDLE_HALF_HEIGHT
        )
      g.fillPolygon(xPoints, yPoints, xPoints.size)
    }
  }

  /**
   * Diamond shape displayed at the start and the end of an animation for each animation curve.
   * @param x coordinate of the center of the diamond
   * @param y coordinate of the center of the diamond
   * @param colorIndex index of the color from [GRAPH_COLORS]
   */
  class Diamond(val x: Int, val y: Int, private val colorIndex: Int) {
    // The diamond should have the following shape:
    //         /\
    //         \/
    // We add 4 points with the following coordinates:
    // (x, y - size): top point
    // (x + size, y): right point
    // (x, y + size): bottom point
    // (x - size, y): left point
    // where (x, y) is the center of the diamond
    private fun xArray(size: Int) = intArrayOf(x, x + size, x, x - size)
    private fun yArray(size: Int) = intArrayOf(y - size, y, y + size, y)
    private val diamond = Polygon(xArray(diamondSize()), yArray(diamondSize()), 4)
    private val diamondOutline = Polygon(xArray(diamondSize() + 1), yArray(diamondSize() + 1), 4)

    companion object {
      /** Size of the diamond shape used as the graph size limiter. */
      fun diamondSize() = JBUI.scale(6)
    }

    /** Paint diamond shape. */
    fun paint(g: Graphics2D, hover: Boolean) {
      g.color =
        if (hover) InspectorColors.LINE_OUTLINE_COLOR_ACTIVE
        else JBColor(Color.white, JBColor.border().darker())
      g.fillPolygon(diamondOutline)
      g.color = InspectorColors.GRAPH_COLORS[colorIndex % InspectorColors.GRAPH_COLORS.size]
      g.fillPolygon(diamond)
    }

    fun paintOutline(g: Graphics2D) {
      g.color = InspectorColors.GRAPH_COLORS[colorIndex % InspectorColors.GRAPH_COLORS.size]
      g.stroke = InspectorLayout.simpleStroke
      g.drawPolygon(diamondOutline)
    }

    fun contains(x: Int, y: Int) = diamondOutline.contains(x, y)
  }

  /** UI Component to display transition state. */
  interface StateComboBox {
    /** Root component. */
    val component: JComponent

    /** Set list of states for comboBoxes. */
    fun updateStates(states: Set<Any>)

    /** Set models for all comboBoxes in this component. */
    fun setModels(models: List<DefaultComboBoxModel<Any>>)

    /** Setup all listeners. */
    fun setupListeners()

    /** Hash code of selected state. */
    fun stateHashCode(): Int

    /** Get selected state for the [index]. */
    fun getState(index: Int = 0): Any

    /** Set a start state. */
    fun setStartState(state: Any?)

    /** Create models for comboBoxes in this component. */
    fun createModels(states: Set<Any>): List<DefaultComboBoxModel<Any>>

    /**
     * Update the given combo box width to be as wide as the longest model value that can be set.
     */
    fun updatePreferredWidth(comboBox: ComboBox<Any>, model: DefaultComboBoxModel<Any>) {
      val longestTextWidth =
        (0 until model.size).maxOfOrNull {
          comboBox.getFontMetrics(component.font).stringWidth(model.getElementAt(it).toString())
        }
          ?: return
      comboBox.setMinimumAndPreferredWidth(
        JBUI.scale(longestTextWidth + 35)
      ) // longest width + margin (that includes the dropdown arrow)
    }
  }

  /** Wrapper around multiple StateComboBox with shared state. */
  class StateComboBoxes(private val boxes: List<StateComboBox>) {
    // ComboBox models are shared for all [StateComboBox] so only boxes.first() can be used where
    // needed.
    fun stateHashCode() = boxes.first().stateHashCode()
    fun setupListeners() {
      boxes.first().setupListeners()
    }

    fun updateStates(states: Set<Any>) {
      val models = boxes.first().createModels(states)
      boxes.forEach { it.setModels(models) }
    }

    fun getState(index: Int = 0): Any = boxes.first().getState(index)
    fun setStartState(state: Any?) {
      boxes.first().setStartState(state)
    }
  }

  class EmptyComboBox() : StateComboBox, JPanel() {
    override val component = this
    override fun stateHashCode() = 0
    override fun setupListeners() {}
    override fun updateStates(states: Set<Any>) {}
    override fun getState(index: Int): Any = 0
    override fun setStartState(state: Any?) {}
    override fun setModels(models: List<DefaultComboBoxModel<Any>>) {}
    override fun createModels(states: Set<Any>): List<DefaultComboBoxModel<Any>> = emptyList()
  }

  /**
   * UI Component to display comboBox for AnimatedVisibility.
   * @params logger usage tracker for animation tooling
   * @params callback when state has changed
   */
  class AnimatedVisibilityComboBox(
    private val logger: ComposeAnimationEventTracker,
    private val callback: (stateComboBox: StateComboBox) -> Unit
  ) : StateComboBox, ComboBox<Any>(DefaultComboBoxModel(arrayOf<Any>())) {
    override val component: JComponent = this

    // AnimatedVisibilityCombobox component displays:
    //      ComboBox with the state.
    //      ↓
    //   [State]  ⬅ component
    init {
      model =
        DefaultComboBoxModel(
          arrayOf(message("animation.inspector.animated.visibility.combobox.placeholder.message"))
        )
    }
    override fun updateStates(states: Set<Any>) {
      model = DefaultComboBoxModel(states.toTypedArray())
    }

    override fun setModels(models: List<DefaultComboBoxModel<Any>>) {
      val onlyModel = models.single()
      model = onlyModel
      updatePreferredWidth(this, onlyModel)
    }

    override fun createModels(states: Set<Any>): List<DefaultComboBoxModel<Any>> =
      listOf(DefaultComboBoxModel(states.toTypedArray()))

    override fun setStartState(state: Any?) {
      (state as? String).let {
        for (i in 0 until itemCount) {
          val item = getItemAt(i)
          if (item.toString() == it) {
            selectedItem = item
          }
        }
      }
    }

    override fun setupListeners() {
      addActionListener {
        logger(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_END_STATE)
        callback(this)
      }
    }

    override fun stateHashCode() = selectedItem.hashCode()
    override fun getState(index: Int): Any = selectedItem
  }

  /**
   * UI Component to display comboBoxes for transition.
   * @params surface [DesignSurface] of the component
   * @params logger usage tracker for animation tooling
   * @params callback when state has changed
   */
  class StartEndComboBox(
    private val surface: DesignSurface<*>,
    private val logger: ComposeAnimationEventTracker,
    private val callback: (stateComboBox: StateComboBox) -> Unit
  ) : StateComboBox, JPanel(TabularLayout("Fit,Fit-,Fit,Fit-")) {
    //  StartEndComboBox component displays:
    //
    //   Swap button to switch states.
    //   |         ComboBox with start state.
    //   |         |        "to" label
    //   |         |         |       ComboBox with end state.
    //   ↓         ↓         ↓       ↓
    // ⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽⎽
    // ⎹  ↔️  [Start State]  to  [End State]  ⎹ ⬅ component
    //  ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅ ̅̅
    private val startStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))
    private val endStateComboBox = ComboBox(DefaultComboBoxModel(arrayOf<Any>()))
    override val component = this

    override fun stateHashCode() =
      Pair(startStateComboBox.selectedItem?.hashCode(), endStateComboBox.selectedItem?.hashCode())
        .hashCode()

    /**
     * Flag to be used when the [SwapStartEndStatesAction] is triggered, in order to prevent the
     * listener to be executed twice.
     */
    private var isSwappingStates = false

    init {
      val states = arrayOf(message("animation.inspector.states.combobox.placeholder.message"))
      startStateComboBox.model = DefaultComboBoxModel(states)
      endStateComboBox.model = DefaultComboBoxModel(states)

      val swapStatesActionToolbar =
        SingleButtonToolbar(surface, "Swap States", SwapStartEndStatesAction())
      add(swapStatesActionToolbar, TabularLayout.Constraint(0, 0))
      add(startStateComboBox, TabularLayout.Constraint(0, 1))
      add(JBLabel(message("animation.inspector.state.to.label")), TabularLayout.Constraint(0, 2))
      add(endStateComboBox, TabularLayout.Constraint(0, 3))
    }

    /** Sets up change listeners for [startStateComboBox] and [endStateComboBox]. */
    override fun setupListeners() {
      startStateComboBox.addActionListener(
        ActionListener {
          if (isSwappingStates) {
            // The is no need to trigger the callback, since we're going to make a follow up call to
            // update the end state.
            // Also, we only log start state changes if not swapping states, which has its own
            // tracking. Therefore, we can early return here.
            return@ActionListener
          }
          logger(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_START_STATE)
          callback(this)
        }
      )
      endStateComboBox.addActionListener(
        ActionListener {
          if (!isSwappingStates) {
            // Only log end state changes if not swapping states, which has its own tracking.
            logger(ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.CHANGE_END_STATE)
          }
          callback(this)
        }
      )
    }

    override fun setModels(models: List<DefaultComboBoxModel<Any>>) {
      startStateComboBox.model = models[0]
      updatePreferredWidth(startStateComboBox, models[0])
      endStateComboBox.model = models[1]
      updatePreferredWidth(endStateComboBox, models[1])
    }

    override fun createModels(states: Set<Any>): List<DefaultComboBoxModel<Any>> =
      listOf(
        DefaultComboBoxModel(states.toTypedArray()),
        DefaultComboBoxModel(states.toTypedArray())
      )

    override fun updateStates(states: Set<Any>) {
      startStateComboBox.model = DefaultComboBoxModel(states.toTypedArray())
      endStateComboBox.model = DefaultComboBoxModel(states.toTypedArray())
    }

    override fun getState(index: Int): Any =
      when (index) {
        0 -> startStateComboBox.selectedItem
        1 -> endStateComboBox.selectedItem
        else -> 0
      }

    override fun setStartState(state: Any?) {
      startStateComboBox.selectedItem = state
      // Try to select an end state different than the start state.
      if (startStateComboBox.selectedIndex == endStateComboBox.selectedIndex &&
          endStateComboBox.itemCount > 1
      ) {
        endStateComboBox.selectedIndex =
          (startStateComboBox.selectedIndex + 1) % endStateComboBox.itemCount
      }
    }

    private inner class SwapStartEndStatesAction() :
      AnActionButton(
        message("animation.inspector.action.swap.states"),
        StudioIcons.LayoutEditor.Motion.PLAY_YOYO
      ) {
      override fun actionPerformed(e: AnActionEvent) {
        isSwappingStates = true
        val startState = startStateComboBox.selectedItem
        startStateComboBox.selectedItem = endStateComboBox.selectedItem
        endStateComboBox.selectedItem = startState
        isSwappingStates = false
        logger(
          ComposeAnimationToolingEvent.ComposeAnimationToolingEventType.TRIGGER_SWAP_STATES_ACTION
        )
      }

      override fun updateButton(e: AnActionEvent) {
        super.updateButton(e)
        e.presentation.isEnabled = true
      }
    }
  }
}
