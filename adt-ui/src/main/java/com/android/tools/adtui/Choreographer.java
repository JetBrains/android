/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.tools.adtui;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.model.FpsTimer;
import com.android.tools.adtui.model.StopwatchTimer;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.model.updater.Updater;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.HierarchyListener;
import java.util.LinkedList;
import java.util.List;

/**
 * An auxiliary object that synchronizes a group of {@link Updatable} via a simple update loop
 * running at a specific frame rate. This ensures all UI components and model classes are reading
 * and displaying consistent information at any given time.
 *
 * Deprecated. Please use {@link Updater} instead.
 */
@Deprecated
public class Choreographer implements StopwatchTimer.TickHandler {

  public static final float DEFAULT_LERP_FRACTION = 0.99f;
  public static final float DEFAULT_LERP_THRESHOLD_PERCENTAGE = 0.001f;

  private final List<LegacyAnimatedComponent> mComponents;
  private List<LegacyAnimatedComponent> mToRegister;
  private List<LegacyAnimatedComponent> mToUnregister;
  private final StopwatchTimer mTimer;
  private boolean mReset;

  /**
   * At the end of each update loop, repaint is trigger on the parent container so that all its
   * children are redrawn, displaying the updated data. This avoids having to trigger repaint on
   * individual components registered to the Choreographer, which can result in redundant draw
   * calls between loops if they overlap.
   */
  @NotNull
  private final JComponent mParentContainer;
  private boolean mUpdating;

  /**
   * @param fps    The frame rate that this Choreographer should run at.
   * @param parent The parent component that contains all {@link LegacyAnimatedComponent} registered
   *               with the Choreographer.
   */
  public Choreographer(int fps, @NotNull JComponent parent) {
    this(new FpsTimer(fps), parent);
  }

  public Choreographer(@NotNull JComponent parent) {
    this(new FpsTimer(), parent);
  }

  @VisibleForTesting
  public Choreographer(@NotNull StopwatchTimer timer, @NotNull JComponent parent) {
    mParentContainer = parent;
    mComponents = new LinkedList<>();
    mToRegister = new LinkedList<>();
    mToUnregister = new LinkedList<>();
    mUpdating = false;
    mTimer = timer;
    mTimer.setHandler(this);
    mTimer.start();
  }

  @VisibleForTesting
  public Choreographer(@NotNull StopwatchTimer timer) {
    this(timer, new JPanel());
  }

  @VisibleForTesting
  public StopwatchTimer getTimer() {
    return mTimer;
  }

  public void register(LegacyAnimatedComponent updatable) {
    if (mUpdating) {
      mToRegister.add(updatable);
    }
    else {
      mComponents.add(updatable);
    }
  }

  public void register(@NotNull List<LegacyAnimatedComponent> updatables) {
    for (LegacyAnimatedComponent updatable : updatables) {
      register(updatable);
    }
  }

  public void unregister(@NotNull LegacyAnimatedComponent updatable) {
    if (mUpdating) {
      mToUnregister.add(updatable);
    }
    else {
      mComponents.remove(updatable);
    }
  }

  public void stop() {
    if (mTimer.isRunning()) {
      mTimer.stop();
    }
  }

  @Override
  public void onTick(long elapsed) {
    step(elapsed);
  }

  /**
   * @deprecated Legacy method to animate components. Each component animates on its own and has a
   * choreographer that is bound to that component's visibility. This can be safely removed once
   * all the legacy AnimatedComponents in Studio has been replaced by the new UI.
   */
  @Deprecated
  public static void animate(final LegacyAnimatedComponent component) {
    final Choreographer choreographer = new Choreographer(30, component);
    choreographer.register(component);
    HierarchyListener listener = event -> {
      if (choreographer.mTimer.isRunning() && !component.isShowing()) {
        choreographer.mTimer.stop();
      }
      else if (!choreographer.mTimer.isRunning() && component.isShowing()) {
        choreographer.mTimer.start();
      }
    };
    listener.hierarchyChanged(null);
    component.addHierarchyListener(listener);
  }

  public void reset() {
    mReset = true;
  }

  private void step(long frameLength) {
    mUpdating = true;
    if (mReset) {
      mComponents.forEach(Updatable::reset);
      mReset = false;
    }

    mComponents.forEach(component -> component.update(frameLength));
    mComponents.forEach(Updatable::postUpdate);
    mUpdating = false;

    mToUnregister.forEach(this::unregister);
    mToRegister.forEach(this::register);

    mToUnregister.clear();
    mToRegister.clear();

    mParentContainer.repaint();
  }

  /**
   * A linear interpolation that accumulates over time. This gives an exponential effect where the
   * value {@code from} moves towards the value {@code to} at a rate of {@code fraction} per
   * second. The actual interpolated amount depends on the current frame length.
   *
   * @param from        the value to interpolate from.
   * @param to          the target value.
   * @param fraction    the interpolation fraction.
   * @param frameLength the frame length in seconds.
   * @param threshold   the difference threshold that will cause the method to jump to the target value without lerp.
   * @return the interpolated value.
   */
  public static float lerp(float from, float to, float fraction, float frameLength, float threshold) {
    if (Math.abs(to - from) < threshold) {
      return to;
    }
    else {
      float q = (float)Math.pow(1.0f - fraction, frameLength);
      return from * q + to * (1.0f - q);
    }
  }

  public static double lerp(double from, double to, float fraction, float frameLength, float threshold) {
    if (Math.abs(to - from) < threshold) {
      return to;
    }
    else {
      double q = Math.pow(1.0f - fraction, frameLength);
      return from * q + to * (1.0 - q);
    }
  }

  public static float lerp(float from, float to, float fraction, float frameLength) {
    return lerp(from, to, fraction, frameLength, 0);
  }

  public static double lerp(double from, double to, float fraction, float frameLength) {
    return lerp(from, to, fraction, frameLength, 0);
  }
}
