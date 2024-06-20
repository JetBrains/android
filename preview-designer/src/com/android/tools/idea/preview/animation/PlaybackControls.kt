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

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.util.ActionToolbarUtil
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.preview.PreviewBundle.message
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import java.awt.Component
import java.time.Duration
import javax.swing.JComponent

/** Playback controls toolbar. */
class PlaybackControls(
  val clockControl: SliderClockControl,
  val tracker: AnimationTracker,
  val rootComponent: JComponent,
  parentDisposable: Disposable,
) {

  enum class TimelineSpeed(val speedMultiplier: Float, val displayText: String) {
    X_0_1(0.1f, "0.1x"),
    X_0_25(0.25f, "0.25x"),
    X_0_5(0.5f, "0.5x"),
    X_0_75(0.75f, "0.75x"),
    X_1(1f, "1x"),
    X_2(2f, "2x"),
  }

  private val playPauseAction = PlayPauseAction(parentDisposable)
  private val loopAction = TimelineLoopAction()
  private val speedAction = TimelineSpeedAction()

  private val toolbars = mutableListOf<PlaybackToolbar>()

  private fun updateActionsImmediately() {
    toolbars.forEach { it.playbackControls.updateActionsImmediately() }
  }

  fun createToolbar(extraActions: List<AnAction> = emptyList()) =
    PlaybackToolbar(extraActions).also { toolbars.add(it) }.component

  fun pause() {
    playPauseAction.pause()
  }

  private inner class PlaybackToolbar(extraActions: List<AnAction>) {
    val component: JComponent
      get() = playbackControls.component

    /**
     * Create a toolbar panel with actions to control the animation, e.g. play, pause and jump to
     * start/end.
     *
     * TODO(b/157895086): Update action icons when we have the final Compose Animation tooling icons
     * TODO(b/157895086): Disable toolbar actions while build is in progress
     */
    val playbackControls =
      ActionManager.getInstance()
        .createActionToolbar(
          "Animation Preview",
          DefaultActionGroup(
            listOf(
              loopAction,
              GoToStartAction(),
              playPauseAction,
              GoToEndAction(),
              speedAction,
              Separator(),
            ) + extraActions
          ),
          true,
        )
        .apply {
          targetComponent = rootComponent
          ActionToolbarUtil.makeToolbarNavigable(this)
        }

    private val playPauseComponent: Component?
      get() = playbackControls.component.components?.elementAtOrNull(2)

    /** Snap the animation to the start state. */
    inner class GoToStartAction :
      DumbAwareAction(
        message("animation.inspector.action.go.to.start"),
        null,
        StudioIcons.LayoutEditor.Motion.GO_TO_START,
      ) {
      override fun actionPerformed(e: AnActionEvent) {
        clockControl.jumpToStart()
        tracker.triggerJumpToStartAction()
        // Switch focus to Play button if animation is not playing at the moment.
        // If animation is playing - no need to switch focus as GoToStart button will be enabled
        // again.
        if (!playPauseAction.isPlaying) playPauseComponent?.requestFocus()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !clockControl.isAtStart()
      }
    }

    /** Snap the animation to the end state. */
    inner class GoToEndAction :
      DumbAwareAction(
        message("animation.inspector.action.go.to.end"),
        null,
        StudioIcons.LayoutEditor.Motion.GO_TO_END,
      ) {
      override fun actionPerformed(e: AnActionEvent) {
        clockControl.jumpToEnd()
        tracker.triggerJumpToEndAction()
        // Switch focus to Play button if animation is not playing in the loop at the moment.
        // If animation is playing in the loop - no need to switch focus as GoToEnd button will be
        // enabled again.
        if (!playPauseAction.isPlaying || !clockControl.playInLoop)
          playPauseComponent?.requestFocus()
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = !clockControl.isAtEnd()
      }
    }
  }

  /**
   * Action to play and pause the animation. The icon and tooltip gets updated depending on the
   * playing state.
   */
  private inner class PlayPauseAction(parentDisposable: Disposable) :
    DumbAwareAction(
      message("animation.inspector.action.play"),
      null,
      StudioIcons.LayoutEditor.Motion.PLAY,
    ),
    Disposable {
    private val tickPeriod = Duration.ofMillis(30)

    /** Ticker that increment the animation timeline while it's playing. */
    private val ticker =
      ControllableTicker(
        {
          if (isPlaying) {
            UIUtil.invokeLaterIfNeeded {
              clockControl.incrementClockBy(tickPeriod.toMillis().toInt())
            }
            if (clockControl.isAtEnd()) {
              if (clockControl.playInLoop) {
                handleLoopEnd()
              } else {
                pause()
                UIUtil.invokeLaterIfNeeded { updateActionsImmediately() }
              }
            }
          }
        },
        tickPeriod,
      )

    var isPlaying = false
      private set

    override fun actionPerformed(e: AnActionEvent) =
      if (isPlaying) {
        pause()
        tracker.triggerPauseAction()
      } else {
        play()
        tracker.triggerPlayAction()
      }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = true
      e.presentation.apply {
        if (isPlaying) {
          icon = AllIcons.Actions.Pause
          text = message("animation.inspector.action.pause")
        } else {
          icon = StudioIcons.LayoutEditor.Motion.PLAY
          text = message("animation.inspector.action.play")
        }
      }
    }

    private fun play() {
      if (clockControl.isAtEnd()) {
        // If playing after reaching the timeline end, we should go back to start so the animation
        // can be actually played.
        clockControl.jumpToStart()
      }
      isPlaying = true
      ticker.start()
    }

    fun pause() {
      isPlaying = false
      ticker.stop()
    }

    private fun handleLoopEnd() {
      UIUtil.invokeLaterIfNeeded { clockControl.jumpToStart() }
    }

    override fun dispose() {
      Disposer.dispose(ticker)
    }

    init {
      Disposer.register(parentDisposable, this)
    }
  }

  /**
   * Action to speed up or slow down the timeline. The clock runs faster/slower depending on the
   * value selected.
   *
   * TODO(b/157895086): Add a proper icon for the action.
   */
  private inner class TimelineSpeedAction :
    DropDownAction(
      message("animation.inspector.action.speed"),
      message("animation.inspector.action.speed"),
      null,
    ) {

    init {
      enumValues<TimelineSpeed>().forEach { addAction(SpeedAction(it)) }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.text = clockControl.speed.displayText
    }

    override fun displayTextInToolbar() = true

    private inner class SpeedAction(private val speed: TimelineSpeed) :
      ToggleAction(speed.displayText, speed.displayText, null) {
      override fun isSelected(e: AnActionEvent) = clockControl.speed == speed

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        clockControl.speed = speed
        tracker.changeAnimationSpeed(speed.speedMultiplier)
      }
    }
  }

  /**
   * Action to keep the timeline playing in loop. When active, the timeline will keep playing
   * indefinitely instead of stopping at the end. When reaching the end of the window, the timeline
   * will increment the loop count until it reaches its limit. When that happens, the timelines
   * jumps back to start.
   *
   * TODO(b/157895086): Add a proper icon for the action.
   */
  private inner class TimelineLoopAction :
    ToggleAction(
      message("animation.inspector.action.loop"),
      message("animation.inspector.action.loop"),
      StudioIcons.LayoutEditor.Motion.LOOP,
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent) = clockControl.playInLoop

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      clockControl.playInLoop = state
      if (state) tracker.enableLoopAction() else tracker.disableLoopAction()
    }
  }
}
