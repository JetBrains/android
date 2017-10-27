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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Control that provides controls for animations (play, pause, stop and frame-by-frame steps).
 */
public class AnimationToolbar extends JPanel implements Disposable {
  private static final int TICKER_STEP = 1000 / 30; // 30 FPS
  private static final Font BUTTON_FONT = UIUtil.getLabelFont(UIUtil.FontSize.SMALL);

  @NotNull private final AnimationListener myListener;
  private final JButton myPlayButton;
  private final JButton myPauseButton;
  private final JButton myStopButton;
  private final JButton myFrameFwdButton;
  private final JButton myFrameBckButton;

  private final long myTickStepMs;

  private final long myMinTimeMs;
  private final long myMaxTimeMs;

  /**
   * Slider that allows stepping frame by frame at different speeds
   */
  private final JSlider myFrameControl;
  @Nullable private final DefaultBoundedRangeModel myTimeSliderModel;
  private final ChangeListener myTimeSliderChangeModel;
  /**
   * Ticker to control "real-time" animations and the frame control animations (the slider that allows moving at different speeds)
   */
  private ScheduledFuture<?> myTicker;
  private long myFramePositionMs;
  private long myLastTickMs = 0L;

  /**
   * Constructs a new AnimationToolbar
   * @param parentDisposable Parent {@link Disposable}
   * @param listener {@link AnimationListener} that will be called in every tick
   * @param tickStepMs Number of milliseconds to advance in every animator tick
   * @param minTimeMs Start milliseconds for the animation
   * @param maxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
   */
  private AnimationToolbar(@NotNull Disposable parentDisposable, @NotNull AnimationListener listener, long tickStepMs,
                           long minTimeMs, long maxTimeMs) {
    super(new BorderLayout());
    setBorder(IdeBorderFactory.createEmptyBorder(0, 10, 0, 10));


    Disposer.register(parentDisposable, this);

    myListener = listener;
    myTickStepMs = tickStepMs;
    myMinTimeMs = minTimeMs;
    myMaxTimeMs = maxTimeMs;

    JPanel buttonsPanel = new JPanel();
    // TODO: Replace with icons
    myPlayButton = newControlButton(">", this::onPlay);
    myPauseButton = newControlButton("||", this::onPause);
    myStopButton = newControlButton("■", this::onStop);
    myFrameFwdButton = newControlButton(">|", this::onFrameFwd);
    myFrameBckButton = newControlButton("|<", this::onFrameBck);

    buttonsPanel.add(myPlayButton);
    buttonsPanel.add(myPauseButton);
    buttonsPanel.add(myStopButton);
    buttonsPanel.add(myFrameBckButton);
    buttonsPanel.add(myFrameFwdButton);

    myFrameControl = new JSlider(-5, 5, 0);
    myFrameControl.setSnapToTicks(true);

    if (isUnlimitedAnimationToolbar()) {
      myTimeSliderModel = null;
      myTimeSliderChangeModel = null;
    }
    else {
      myTimeSliderModel = new DefaultBoundedRangeModel(0, 0, 0, 100);
      myTimeSliderChangeModel = e -> {
        long newPositionMs = (long)(myMaxTimeMs * (myTimeSliderModel.getValue() / 100f));
        long elapsed = newPositionMs - myFramePositionMs;
        onTick(elapsed);
      };
      JSlider timeSlider = new JSlider(0, 100, 0);
      timeSlider.setBorder(IdeBorderFactory.createEmptyBorder(5, 0, 5, 0));
      timeSlider.setMajorTickSpacing(10);
      timeSlider.setPaintTicks(true);
      myTimeSliderModel.addChangeListener(myTimeSliderChangeModel);
      timeSlider.setModel(myTimeSliderModel);
      add(timeSlider, BorderLayout.NORTH);
    }

    add(buttonsPanel, BorderLayout.LINE_START);
    add(myFrameControl, BorderLayout.LINE_END);

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

  /**
   * Constructs a new AnimationToolbar
   * @param parentDisposable Parent {@link Disposable}
   * @param listener {@link AnimationListener} that will be called in every tick
   * @param tickStepMs Number of milliseconds to advance in every animator tick
   * @param minTimeMs Start milliseconds for the animation
   */
  @NotNull
  public static AnimationToolbar createUnlimitedAnimationToolbar(@NotNull Disposable parentDisposable,
                                                                 @NotNull AnimationListener listener,
                                                                 long tickStepMs,
                                                                 long minTimeMs) {
    return new AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, -1);
  }

  /**
   * Constructs a new AnimationToolbar
   * @param parentDisposable Parent {@link Disposable}
   * @param listener {@link AnimationListener} that will be called in every tick
   * @param tickStepMs Number of milliseconds to advance in every animator tick
   * @param minTimeMs Start milliseconds for the animation
   * @param maxTimeMs Maximum number of milliseconds for the animation or -1 if there is no time limit
   */
  @NotNull
  public static AnimationToolbar createAnimationToolbar(@NotNull Disposable parentDisposable,
                                                        @NotNull AnimationListener listener,
                                                        long tickStepMs,
                                                        long minTimeMs,
                                                        long maxTimeMs) {
    return new AnimationToolbar(parentDisposable, listener, tickStepMs, minTimeMs, maxTimeMs);
  }

  /**
   * Creates a new toolbar control button
   */
  @NotNull
  private static JButton newControlButton(@NotNull String label, @NotNull Runnable callback) {
    JButton button = new JButton(label);
    button.addActionListener((e) -> callback.run());

    button.setFont(BUTTON_FONT);
    button.setEnabled(false);

    return button;
  }

  /**
   * Set the enabled state of all the toolbar controls
   */
  private void setEnabledState(boolean play, boolean pause, boolean stop, boolean frame) {
    myPlayButton.setEnabled(play);
    myPauseButton.setEnabled(pause);
    myStopButton.setEnabled(stop);
    myFrameFwdButton.setEnabled(frame);
    myFrameBckButton.setEnabled(frame);
    myFrameControl.setEnabled(frame);
  }

  private void onPlay() {
    stopFrameTicker();

    setEnabledState(false, true, true, false);

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

    setEnabledState(true, false, false, true);
    myFramePositionMs = myMinTimeMs;
    onNewFramePosition();
    doFrame();
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

  private void onTick(long elapsed) {
    myFramePositionMs += elapsed;
    if (myFramePositionMs < myMinTimeMs) {
      myFramePositionMs = myMinTimeMs;
    }
    else if (!isUnlimitedAnimationToolbar() && myFramePositionMs > myMaxTimeMs) {
      myFramePositionMs = myMaxTimeMs;
    }
    onNewFramePosition();
    doFrame();
  }

  private void onNewFramePosition() {
    if (isUnlimitedAnimationToolbar()) {
      return;
    }

    if (myFramePositionMs >= myMaxTimeMs) {
      // We've reached the end. Stop.
      onPause();
    }

    myStopButton.setEnabled(myFramePositionMs - myTickStepMs >= myMinTimeMs);
    myFrameFwdButton.setEnabled(myFramePositionMs + myTickStepMs <= myMaxTimeMs);
    myFrameBckButton.setEnabled(myFramePositionMs - myTickStepMs >= myMinTimeMs);

    if (myTimeSliderModel != null) {
      myTimeSliderModel.removeChangeListener(myTimeSliderChangeModel);
      myTimeSliderModel.setValue((int)(((myFramePositionMs - myMinTimeMs) / (float)(myMaxTimeMs - myMinTimeMs))  * 100));
      myTimeSliderModel.addChangeListener(myTimeSliderChangeModel);
    }
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

  @Override
  public void dispose() {
    stopFrameTicker();
  }

  public interface AnimationListener {
    void animateTo(long framePositionMs);
  }
}
