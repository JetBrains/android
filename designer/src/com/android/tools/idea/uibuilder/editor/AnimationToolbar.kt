/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.adtui.common.primaryContentBackground
import com.android.tools.adtui.common.primaryPanelBackground
import com.android.tools.adtui.stdui.CommonButton
import com.android.tools.adtui.ui.DesignSurfaceToolbarUI
import com.android.tools.idea.uibuilder.analytics.AnimationToolbarAnalyticsManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.Box
import javax.swing.DefaultBoundedRangeModel
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.JSlider
import javax.swing.SwingConstants
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.roundToLong

internal const val DEFAULT_PLAY_TOOLTIP = "Play"
internal const val DEFAULT_PAUSE_TOOLTIP = "Pause"
internal const val DEFAULT_STOP_TOOLTIP = "Reset"
internal const val DEFAULT_SPEED_CONTROL_TOOLTIP = "Speed control"
internal const val NO_ANIMATION_TOOLTIP = "There is no animation to play"

/**
 * Control that provides controls for animations (play, pause, stop and frame-by-frame steps).
 *
 * @param parentDisposable Parent [Disposable]
 * @param listener         [AnimationListener] that will be called in every tick
 * @param tickStepMs       Number of milliseconds to advance in every animator tick
 * @param minTimeMs        Start milliseconds for the animation
 * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
 */
open class AnimationToolbar protected constructor(parentDisposable: Disposable,
                                                  listener: AnimationListener,
                                                  tickStepMs: Long,
                                                  minTimeMs: Long,
                                                  initialMaxTimeMs: Long,
                                                  toolbarType: AnimationToolbarType)
  : JPanel(), AnimationController, Disposable {
  private val myAnimationListener: AnimationListener
  private val myPlayButton: JButton
  private val myPauseButton: JButton
  private val myStopButton: JButton

  private var playStatus: PlayStatus
  private val myTickStepMs: Long
  private val myMinTimeMs: Long
  protected val controlBar: JPanel

  /**
   * Slider that allows stepping frame by frame at different speeds
   */
  private val myFrameControl: JSlider
  private var myTimeSliderModel: DefaultBoundedRangeModel? = null
  private val speedControlButton: ActionButton

  /**
   * The progress bar to indicate the current progress of animation. User can also click/drag the indicator to set the progress.
   */
  protected var myTimeSlider: JSlider? = null
  private var myTimeSliderChangeModel: ChangeListener? = null
  protected var timeSliderSeparator: JSeparator? = null
  private var myMaxTimeMs: Long
  private var currentSpeedFactor: Double = PlaySpeed.x1.speedFactor
  private var myLoopEnabled = true
  override var forceElapsedReset: Boolean
    get() = _forceElapsedReset
    set(value) {
      _forceElapsedReset = value
    }
  private var _forceElapsedReset: Boolean = false

  /**
   * Ticker to control "real-time" animations and the frame control animations (the slider that allows moving at different speeds)
   */
  private var myTicker: ScheduledFuture<*>? = null
  private var myFramePositionMs: Long = 0
  private var myLastTickMs = 0L
  val toolbarType: AnimationToolbarType
  protected val myAnalyticsManager = AnimationToolbarAnalyticsManager()

  private val controllerListeners = mutableListOf<AnimationControllerListener>()

  /**
   * Creates a new toolbar control button
   */
  private fun newControlButton(baseIcon: Icon,
                               label: String,
                               tooltip: String?,
                               action: AnimationToolbarAction,
                               callback: Runnable)
  : JButton {
    val button: JButton = CommonButton()
    button.name = label
    button.icon = baseIcon
    button.addActionListener { e: ActionEvent? ->
      myAnalyticsManager.trackAction(toolbarType, action)
      // When action is performed, some buttons are disabled or become invisible, which may make the focus move to the next component in the
      // editor. We move the focus to toolbar here, so the next traversed component is still in the toolbar after action is performed.
      // In practice, when user presses tab after action performed, the first enabled button in the toolbar will gain the focus.
      this@AnimationToolbar.requestFocusInWindow()
      callback.run()
    }
    button.minimumSize = JBUI.size(22, 22)
    button.isBorderPainted = false
    button.font = BUTTON_FONT
    button.isEnabled = false
    button.toolTipText = tooltip
    return button
  }

  /**
   * Set the enabled states of all the toolbar controls
   */
  protected fun setEnabledState(play: Boolean, pause: Boolean, stop: Boolean, frame: Boolean, speed: Boolean) {
    myPlayButton.isEnabled = play
    myPauseButton.isEnabled = pause
    myStopButton.isEnabled = stop
    myFrameControl.isEnabled = frame
    speedControlButton.isEnabled = speed
  }

  /**
   * Set the visibility of play and pause buttons. Note that this doesn't affect the enabled states of them.
   * @see setEnabledState
   */
  protected fun setVisibilityOfPlayAndPauseButtons(playing: Boolean) {
    myPlayButton.isVisible = !playing
    myPauseButton.isVisible = playing
  }

  /**
   * Set the tooltips of all the toolbar controls
   */
  protected fun setTooltips(play: String?,
                            pause: String?,
                            stop: String?) {
    myPlayButton.toolTipText = play
    myPauseButton.toolTipText = pause
    myStopButton.toolTipText = stop
  }

  final override fun play() {
    stopFrameTicker()
    myLastTickMs = System.currentTimeMillis()
    myTicker = EdtExecutorService.getScheduledExecutorInstance()
      .scheduleWithFixedDelay({
        val now = System.currentTimeMillis()
        val elapsed = now - myLastTickMs
        myLastTickMs = now
        onTick((elapsed * currentSpeedFactor).roundToLong())
        if (myMaxTimeMs != -1L && myFramePositionMs >= myMaxTimeMs) {
          myTicker?.cancel(false)
          myTicker = null
          controllerListeners.forEach { it.onPlayStatusChanged(PlayStatus.COMPLETE) }
        }}, 0L, TICKER_STEP.toLong(), TimeUnit.MILLISECONDS)
    controllerListeners.forEach { it.onPlayStatusChanged(PlayStatus.PLAY) }
  }

  final override fun pause() {
    stopFrameTicker()
    controllerListeners.forEach { it.onPlayStatusChanged(PlayStatus.PAUSE) }
  }

  private fun stopFrameTicker() {
    if (myTicker != null) {
      myTicker!!.cancel(false)
      myTicker = null
    }
  }

  final override fun stop() {
    stopFrameTicker()
    setFrameMs(myMinTimeMs)
    controllerListeners.forEach { it.onPlayStatusChanged(PlayStatus.STOP) }
  }

  final override fun setFrameMs(frameMs: Long) {
    val calibratedFramePosition = when {
      frameMs < myMinTimeMs -> if (myLoopEnabled) myMaxTimeMs else myMinTimeMs
      !isUnlimitedAnimationToolbar && frameMs > myMaxTimeMs -> if (myLoopEnabled) myMinTimeMs else myMaxTimeMs
      else -> frameMs
    }
    myFramePositionMs = calibratedFramePosition
    controllerListeners.forEach { it.onCurrentFrameMsChanged(calibratedFramePosition) }
    myAnimationListener.animateTo(this, myFramePositionMs)
  }

  /**
   * User triggered new position in the animation
   *
   * @param newPositionMs
   */
  private fun seek(newPositionMs: Long) {
    setFrameMs(myMinTimeMs + newPositionMs)
  }

  /**
   * Called for every automatic tick in the animation
   *
   * @param elapsed
   */
  private fun onTick(elapsed: Long) {
    setFrameMs(myFramePositionMs + elapsed)
  }

  final override fun getPlayStatus(): PlayStatus = playStatus

  final override fun getFrameMs(): Long = myFramePositionMs

  final override fun setMaxTimeMs(maxTimeMs: Long) {
    myMaxTimeMs = maxTimeMs
    controllerListeners.forEach { it.onMaxTimeMsChanged(myMaxTimeMs) }
  }

  final override fun getMaxTimeMs(): Long = myMaxTimeMs

  final override fun setLooping(enabled: Boolean) {
    myLoopEnabled = enabled
    controllerListeners.forEach { it.onLoopingChanged(enabled) }
  }

  final override fun isLooping(): Boolean = myLoopEnabled

  final override fun registerAnimationControllerListener(listener: AnimationControllerListener) {
    controllerListeners.add(listener)
  }

  /**
   * True if this is an animation toolbar for an unlimited toolbar
   */
  private val isUnlimitedAnimationToolbar: Boolean
    get() = myMaxTimeMs == -1L

  override fun dispose() {
    controllerListeners.clear()
    stopFrameTicker()
  }

  companion object {
    private const val TICKER_STEP = 1000 / 30 // 30 FPS
    private val BUTTON_FONT = UIUtil.getLabelFont(UIUtil.FontSize.MINI)

    /**
     * Constructs a new AnimationToolbar
     *
     * @param parentDisposable Parent [Disposable]
     * @param listener         [AnimationListener] that will be called in every tick
     * @param tickStepMs       Number of milliseconds to advance in every animator tick
     * @param minTimeMs        Start milliseconds for the animation
     */
    fun createUnlimitedAnimationToolbar(parentDisposable: Disposable,
                                        listener: AnimationListener,
                                        tickStepMs: Long,
                                        minTimeMs: Long): AnimationToolbar {
      return AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, -1, AnimationToolbarType.UNLIMITED)
    }

    /**
     * Constructs a new AnimationToolbar
     *
     * @param parentDisposable Parent [Disposable]
     * @param listener         [AnimationListener] that will be called in every tick
     * @param tickStepMs       Number of milliseconds to advance in every animator tick
     * @param minTimeMs        Start milliseconds for the animation
     * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
     */
    fun createAnimationToolbar(parentDisposable: Disposable,
                               listener: AnimationListener,
                               tickStepMs: Long,
                               minTimeMs: Long,
                               initialMaxTimeMs: Long): AnimationToolbar {
      return AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, initialMaxTimeMs, AnimationToolbarType.LIMITED)
    }
  }

  init {
    Disposer.register(parentDisposable, this)
    myAnimationListener = listener
    myTickStepMs = tickStepMs
    myMinTimeMs = minTimeMs
    myMaxTimeMs = initialMaxTimeMs
    this.toolbarType = toolbarType
    myPlayButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.PLAY, "Play", DEFAULT_PLAY_TOOLTIP,
      AnimationToolbarAction.PLAY
    ) { play() }
    myPlayButton.isEnabled = true
    myPauseButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.PAUSE, "Pause", DEFAULT_PAUSE_TOOLTIP,
      AnimationToolbarAction.PAUSE
    ) { pause() }
    myPauseButton.isEnabled = false
    myPauseButton.isVisible = false
    // TODO(b/176806183): Before having a reset icon, use refresh icon instead.
    myStopButton = newControlButton(
      StudioIcons.LayoutEditor.Motion.GO_TO_START, "Stop", DEFAULT_STOP_TOOLTIP,
      AnimationToolbarAction.STOP
    ) { stop() }
    controlBar = object : JPanel(FlowLayout()) {
      override fun updateUI() {
        setUI(DesignSurfaceToolbarUI())
      }
    }
    speedControlButton = createPlaySpeedActionButton { currentSpeedFactor = it }
    val buttonsPanel = Box.createHorizontalBox()
    buttonsPanel.add(myStopButton)
    buttonsPanel.add(myPlayButton)
    buttonsPanel.add(myPauseButton)
    buttonsPanel.add(speedControlButton)
    controlBar.add(buttonsPanel)
    if (isUnlimitedAnimationToolbar) {
      myTimeSlider = null
      myTimeSliderModel = null
      myTimeSliderChangeModel = null
    } else {
      myTimeSliderModel = DefaultBoundedRangeModel(0, 0, 0, 100)
      myTimeSliderChangeModel = ChangeListener { e: ChangeEvent? ->
        myAnalyticsManager.trackAction(this.toolbarType, AnimationToolbarAction.FRAME_CONTROL)
        val sliderValue = myTimeSliderModel!!.value
        val newPositionMs = ((myMaxTimeMs - myMinTimeMs) * (sliderValue / 100f)).toLong()
        myStopButton.isEnabled = sliderValue != 0
        seek(newPositionMs)
        myTimeSlider!!.repaint()
      }
      myTimeSlider = object : JSlider(0, 100, 0) {
        override fun updateUI() {
          setUI(AnimationToolbarSliderUI(this))
          updateLabelUIs()
        }
      }
      myTimeSlider!!.isOpaque = false
      myTimeSlider!!.border = JBUI.Borders.empty()
      myTimeSliderModel!!.addChangeListener(myTimeSliderChangeModel)
      myTimeSlider!!.model = myTimeSliderModel
      timeSliderSeparator = JSeparator(SwingConstants.VERTICAL)
      buttonsPanel.add(timeSliderSeparator)
      controlBar.add(myTimeSlider)
    }
    myFrameControl = JSlider(-5, 5, 0)
    myFrameControl.snapToTicks = true
    add(controlBar)
    controlBar.background = primaryContentBackground
    background = primaryPanelBackground
    myFrameControl.addChangeListener { e: ChangeEvent? ->
      stopFrameTicker()
      val value = myFrameControl.value
      if (value == 0) {
        stopFrameTicker()
        return@addChangeListener
      }
      val frameChange = myTickStepMs * value
      myTicker = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(
        { onTick(frameChange) },
        0L, TICKER_STEP.toLong(), TimeUnit.MILLISECONDS
      )
    }

    myFrameControl.addMouseListener(object : MouseAdapter() {
      override fun mouseReleased(e: MouseEvent) {
        if (!myFrameControl.isEnabled) {
          return
        }
        stopFrameTicker()
        myFrameControl.value = 0
      }
    })
    stop()
    playStatus = PlayStatus.STOP
    registerAnimationControllerListener(MyControllerListener())
  }

  private inner class MyControllerListener: AnimationControllerListener {
    override fun onPlayStatusChanged(newStatus: PlayStatus) {
      playStatus = newStatus
      when (newStatus) {
        PlayStatus.PLAY -> {
          setEnabledState(play = false, pause = true, stop = true, frame = false, speed = true)
          setVisibilityOfPlayAndPauseButtons(playing = true)
        }
        PlayStatus.PAUSE -> {
          setEnabledState(play = true, pause = false, stop = true, frame = true, speed = true)
          setVisibilityOfPlayAndPauseButtons(playing = false)
        }
        PlayStatus.STOP -> {
          setEnabledState(play = true, pause = false, stop = false, frame = false, speed = true)
          setVisibilityOfPlayAndPauseButtons(playing = false)
        }
        PlayStatus.COMPLETE -> {
          setEnabledState(play = false, pause = false, stop = true, frame = true, speed = true)
          setVisibilityOfPlayAndPauseButtons(playing = true)
        }
      }
    }

    override fun onCurrentFrameMsChanged(newFrameMs: Long) {
      if (!isUnlimitedAnimationToolbar) {
        val timeSliderModel = myTimeSliderModel
        if (timeSliderModel != null) {
          timeSliderModel.removeChangeListener(myTimeSliderChangeModel)
          timeSliderModel.value = ((newFrameMs - myMinTimeMs) / (myMaxTimeMs - myMinTimeMs).toFloat() * 100).toInt()
          timeSliderModel.addChangeListener(myTimeSliderChangeModel)
        }
      }
    }
  }
}

private fun createPlaySpeedActionButton(callback: (Double) -> Unit): ActionButton {
  val action = AnimationSpeedActionGroup(callback)
  val presentation = PresentationFactory().getPresentation(action)
  val button = ActionButton(action, presentation, ActionPlaces.TOOLBAR, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  button.addPropertyChangeListener("enabled") {
    presentation.description = if (button.isEnabled) DEFAULT_SPEED_CONTROL_TOOLTIP else NO_ANIMATION_TOOLTIP
    button.update()
  }
  // The button has a down arrow in the bottom-right corner, which is close to the right bounds of button.
  // Add a right border to make the visual effect balanced.
  button.border = JBUI.Borders.emptyRight(4)
  return button
}
