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
package com.android.tools.idea.uibuilder.editor;

import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.ui.DesignSurfaceToolbarUI;
import com.android.tools.idea.uibuilder.analytics.AnimationToolbarAnalyticsManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.StudioIcons;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.ChangeListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Control that provides controls for animations (play, pause, stop and frame-by-frame steps).
 */
public class AnimationToolbar extends JPanel implements Disposable {
  private static final int TICKER_STEP = 1000 / 30; // 30 FPS
  private static final Font BUTTON_FONT = UIUtil.getLabelFont(UIUtil.FontSize.MINI);

  protected static final String DEFAULT_PLAY_TOOLTIP = "Play";
  protected static final String DEFAULT_PAUSE_TOOLTIP = "Pause";
  protected static final String DEFAULT_STOP_TOOLTIP = "Reset";
  protected static final String DEFAULT_FRAME_FORWARD_TOOLTIP = "Step forward";
  protected static final String DEFAULT_FRAME_BACK_TOOLTIP = "Step backward";

  @NotNull private final AnimationListener myListener;
  private final JButton myPlayButton;
  private final JButton myPauseButton;
  private final JButton myStopButton;
  private final JButton myFrameFwdButton;
  private final JButton myFrameBckButton;
  // TODO: Add speed selector button.

  private final long myTickStepMs;

  private final long myMinTimeMs;

  private final JPanel myControlBar;
  /**
   * Slider that allows stepping frame by frame at different speeds
   */
  private final JSlider myFrameControl;
  @Nullable private final DefaultBoundedRangeModel myTimeSliderModel;
  private final ChangeListener myTimeSliderChangeModel;
  private long myMaxTimeMs;
  private boolean myLoopEnabled = true;
  /**
   * Ticker to control "real-time" animations and the frame control animations (the slider that allows moving at different speeds)
   */
  private ScheduledFuture<?> myTicker;
  private long myFramePositionMs;
  private long myLastTickMs = 0L;
  protected final AnimationToolbarType myToolbarType;
  protected final AnimationToolbarAnalyticsManager myAnalyticsManager = new AnimationToolbarAnalyticsManager();

  /**
   * Constructs a new AnimationToolbar
   *
   * @param parentDisposable Parent {@link Disposable}
   * @param listener         {@link AnimationListener} that will be called in every tick
   * @param tickStepMs       Number of milliseconds to advance in every animator tick
   * @param minTimeMs        Start milliseconds for the animation
   * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
   */
  protected AnimationToolbar(@NotNull Disposable parentDisposable, @NotNull AnimationListener listener, long tickStepMs,
                             long minTimeMs, long initialMaxTimeMs, AnimationToolbarType toolbarType) {
    Disposer.register(parentDisposable, this);

    myListener = listener;
    myTickStepMs = tickStepMs;
    myMinTimeMs = minTimeMs;
    myMaxTimeMs = initialMaxTimeMs;
    myToolbarType = toolbarType;

    myPlayButton = newControlButton(StudioIcons.LayoutEditor.Motion.PLAY, "Play", DEFAULT_PLAY_TOOLTIP,
                                    AnimationToolbarAction.PLAY, this::onPlay);
    myPlayButton.setEnabled(true);
    myPauseButton = newControlButton(StudioIcons.LayoutEditor.Motion.PAUSE, "Pause", DEFAULT_PAUSE_TOOLTIP,
                                     AnimationToolbarAction.PAUSE, this::onPause);
    myPauseButton.setEnabled(true);
    myStopButton = newControlButton(StudioIcons.LayoutEditor.Motion.END_CONSTRAINT, "Stop", DEFAULT_STOP_TOOLTIP,
                                    AnimationToolbarAction.STOP, this::onStop);
    myFrameFwdButton = newControlButton(StudioIcons.LayoutEditor.Motion.GO_TO_END, "Step forward", DEFAULT_FRAME_FORWARD_TOOLTIP,
                                        AnimationToolbarAction.FRAME_FORWARD, this::onFrameFwd);
    myFrameBckButton = newControlButton(StudioIcons.LayoutEditor.Motion.GO_TO_START, "Step backward", DEFAULT_FRAME_BACK_TOOLTIP,
                                        AnimationToolbarAction.FRAME_BACKWARD, this::onFrameBck);

    myControlBar = new JPanel(new FlowLayout()) {
      @Override
      public void updateUI() {
        setUI(new DesignSurfaceToolbarUI());
      }
    };

    Box buttonsPanel = Box.createHorizontalBox();
    buttonsPanel.add(myStopButton);
    buttonsPanel.add(myFrameBckButton);
    buttonsPanel.add(myPlayButton);
    buttonsPanel.add(myPauseButton);
    buttonsPanel.add(myFrameFwdButton);
    myControlBar.add(buttonsPanel);


    if (isUnlimitedAnimationToolbar()) {
      myTimeSliderModel = null;
      myTimeSliderChangeModel = null;
    }
    else {
      myTimeSliderModel = new DefaultBoundedRangeModel(0, 0, 0, 100);
      myTimeSliderChangeModel = e -> {
        myAnalyticsManager.trackAction(myToolbarType, AnimationToolbarAction.FRAME_CONTROL);
        long newPositionMs = (long)((myMaxTimeMs - myMinTimeMs) * (myTimeSliderModel.getValue() / 100f));
        seek(newPositionMs);
      };
      JSlider timeSlider = new JSlider(0, 100, 0) {
        @Override
        public void updateUI() {
          setUI(new AnimationToolbarSliderUI(this));
          updateLabelUIs();
        }
      };
      timeSlider.setOpaque(false);
      timeSlider.setBorder(JBUI.Borders.empty());
      myTimeSliderModel.addChangeListener(myTimeSliderChangeModel);
      timeSlider.setModel(myTimeSliderModel);
      buttonsPanel.add(new JSeparator(SwingConstants.VERTICAL));
      myControlBar.add(timeSlider);
    }

    myFrameControl = new JSlider(-5, 5, 0);
    myFrameControl.setSnapToTicks(true);
    add(myControlBar);

    myFrameControl.addChangeListener(e -> {
      stopFrameTicker();

      int value = myFrameControl.getValue();

      if (value == 0) {
        stopFrameTicker();
        return;
      }

      long frameChange = myTickStepMs * value;
      myTicker =
        EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(() -> onTick(frameChange),
                                                                                 0L, TICKER_STEP, TimeUnit.MILLISECONDS);
    });
    myFrameControl.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(MouseEvent e) {
        if (!myFrameControl.isEnabled()) {
          return;
        }

        stopFrameTicker();

        myFrameControl.setValue(0);
      }
    });

    onStop();
  }

  @NotNull
  protected JPanel getControlBar() {
    return myControlBar;
  }

  /**
   * Constructs a new AnimationToolbar
   *
   * @param parentDisposable Parent {@link Disposable}
   * @param listener         {@link AnimationListener} that will be called in every tick
   * @param tickStepMs       Number of milliseconds to advance in every animator tick
   * @param minTimeMs        Start milliseconds for the animation
   */
  @NotNull
  public static AnimationToolbar createUnlimitedAnimationToolbar(@NotNull Disposable parentDisposable,
                                                                 @NotNull AnimationListener listener,
                                                                 long tickStepMs,
                                                                 long minTimeMs) {
    return new AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, -1, AnimationToolbarType.UNLIMITED);
  }

  /**
   * Constructs a new AnimationToolbar
   *
   * @param parentDisposable Parent {@link Disposable}
   * @param listener         {@link AnimationListener} that will be called in every tick
   * @param tickStepMs       Number of milliseconds to advance in every animator tick
   * @param minTimeMs        Start milliseconds for the animation
   * @param initialMaxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
   */
  @NotNull
  public static AnimationToolbar createAnimationToolbar(@NotNull Disposable parentDisposable,
                                                        @NotNull AnimationListener listener,
                                                        long tickStepMs,
                                                        long minTimeMs,
                                                        long initialMaxTimeMs) {
    return new AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, initialMaxTimeMs, AnimationToolbarType.LIMITED);
  }

  /**
   * Creates a new toolbar control button
   */
  @NotNull
  private JButton newControlButton(@NotNull Icon baseIcon,
                                   @NotNull String label,
                                   @Nullable String tooltip,
                                   AnimationToolbarAction action,
                                   @NotNull Runnable callback) {
    JButton button = new CommonButton();
    button.setName(label);
    button.setIcon(baseIcon);
    button.addActionListener((e) -> {
      myAnalyticsManager.trackAction(myToolbarType, action);
      callback.run();
    });

    button.setMinimumSize(JBUI.size(22, 22));
    button.setBorderPainted(false);
    button.setFont(BUTTON_FONT);
    button.setEnabled(false);
    button.setToolTipText(tooltip);

    return button;
  }

  /**
   * Set the enabled states of all the toolbar controls
   */
  protected void setEnabledState(boolean play, boolean pause, boolean stop, boolean frame) {
    myPlayButton.setEnabled(play);
    myPauseButton.setEnabled(pause);
    myStopButton.setEnabled(stop);
    myFrameFwdButton.setEnabled(frame);
    myFrameBckButton.setEnabled(frame);
    myFrameControl.setEnabled(frame);
  }

  /**
   * Set the visibilities of all the toolbar controls
   */
  protected void setVisibilityState(boolean play, boolean pause, boolean stop, boolean frame) {
    myPlayButton.setVisible(play);
    myPauseButton.setVisible(pause);
    myStopButton.setVisible(stop);
    myFrameFwdButton.setVisible(frame);
    myFrameBckButton.setVisible(frame);
    myFrameControl.setVisible(frame);
  }

  /**
   * Set the tooltips of all the toolbar controls
   */
  protected void setTooltips(@Nullable String play,
                             @Nullable String pause,
                             @Nullable String stop,
                             @Nullable String frameForward,
                             @Nullable String frameback) {
    myPlayButton.setToolTipText(play);
    myPauseButton.setToolTipText(pause);
    myStopButton.setToolTipText(stop);
    myFrameFwdButton.setToolTipText(frameForward);
    myFrameBckButton.setToolTipText(frameback);
  }

  private void onPlay() {
    stopFrameTicker();

    setEnabledState(false, true, true, false);
    setVisibilityState(false, true, true, true);

    myLastTickMs = System.currentTimeMillis();
    myTicker = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(() -> {
      long now = System.currentTimeMillis();
      long elapsed = now - myLastTickMs;
      myLastTickMs = now;
      onTick(elapsed);
    }, 0L, TICKER_STEP, TimeUnit.MILLISECONDS);
  }

  private void onPause() {
    setEnabledState(true, false, true, true);
    setVisibilityState(true, false, true, true);

    stopFrameTicker();
  }

  private void stopFrameTicker() {
    if (myTicker != null) {
      myTicker.cancel(false);
      myTicker = null;
    }
  }

  private void onStop() {
    stopFrameTicker();

    setEnabledState(true, false, false, false);
    setVisibilityState(true, false, true, true);
    setFramePosition(myMinTimeMs, false);
  }

  private void doFrame() {
    myListener.animateTo(myFramePositionMs);
  }

  private void onFrameFwd() {
    onTick(myTickStepMs);
  }

  private void onFrameBck() {
    onTick(-myTickStepMs);
  }

  /**
   * Called after a new frame position has been set
   */
  private void onNewFramePosition(boolean setByUser) {
    if (isUnlimitedAnimationToolbar()) {
      return;
    }

    if (myFramePositionMs >= myMaxTimeMs) {
      if (!setByUser && !myLoopEnabled) {
        // We've reached the end. Stop.
        onPause();
      }
    }

    myStopButton.setEnabled(myFramePositionMs - myTickStepMs >= myMinTimeMs);
    myFrameFwdButton.setEnabled(myFramePositionMs + myTickStepMs <= myMaxTimeMs);
    myFrameBckButton.setEnabled(myFramePositionMs - myTickStepMs >= myMinTimeMs);

    if (myTimeSliderModel != null) {
      myTimeSliderModel.removeChangeListener(myTimeSliderChangeModel);
      myTimeSliderModel.setValue((int)(((myFramePositionMs - myMinTimeMs) / (float)(myMaxTimeMs - myMinTimeMs)) * 100));
      myTimeSliderModel.addChangeListener(myTimeSliderChangeModel);
    }
  }

  /**
   * Sets a new frame position. If newPositionMs is outside of the min and max values, the value will be truncated to be within the range.
   *
   * @param newPositionMs new position in ms
   * @param setByUser     true if this new position was set by the user. In those cases we might want to automatically loop
   */
  private void setFramePosition(long newPositionMs, boolean setByUser) {
    myFramePositionMs = newPositionMs;

    if (myFramePositionMs < myMinTimeMs) {
      myFramePositionMs = myLoopEnabled ? myMaxTimeMs : myMinTimeMs;
    }
    else if (!isUnlimitedAnimationToolbar() && myFramePositionMs > myMaxTimeMs) {
      myFramePositionMs = myLoopEnabled ? myMinTimeMs : myMaxTimeMs;
    }
    onNewFramePosition(setByUser);
    doFrame();
  }

  /**
   * User triggered new position in the animation
   *
   * @param newPositionMs
   */
  private void seek(long newPositionMs) {
    setFramePosition(myMinTimeMs + newPositionMs, true);
  }

  /**
   * Called for every automatic tick in the animation
   *
   * @param elapsed
   */
  private void onTick(long elapsed) {
    setFramePosition(myFramePositionMs + elapsed, false);
  }

  public void setMaxtimeMs(long maxTimeMs) {
    assert isUnlimitedAnimationToolbar() : "Max time can not be set for unlimited animations";
    myMaxTimeMs = maxTimeMs;
  }

  /**
   * Stop any running animations
   */
  public void stop() {
    onStop();
  }

  /**
   * True if this is an animation toolbar for an unlimited toolbar
   */
  private boolean isUnlimitedAnimationToolbar() {
    return myMaxTimeMs == -1;
  }

  public AnimationToolbarType getToolbarType() {
    return myToolbarType;
  }

  @Override
  public void dispose() {
    stopFrameTicker();
  }
}
