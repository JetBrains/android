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

package com.android.tools.adtui.model.updater;

import com.android.tools.adtui.model.StopwatchTimer;
import java.util.LinkedList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * An auxiliary object that synchronizes a group of {@link Updatable} via a simple update loop
 * running at a specific frame rate. This ensures all UI components and model classes are reading
 * and displaying consistent information at any given time.
 */
public class Updater implements StopwatchTimer.TickHandler {

  public static final float DEFAULT_LERP_FRACTION = 0.99f;
  public static final float DEFAULT_LERP_THRESHOLD_RATIO = 0.001f;

  private final List<Updatable> mComponents;
  private List<Updatable> mToRegister;
  private List<Updatable> mToUnregister;
  private final StopwatchTimer mTimer;
  private boolean mReset;

  private boolean mUpdating;

  public Updater(@NotNull StopwatchTimer timer) {
    mComponents = new LinkedList<>();
    mToRegister = new LinkedList<>();
    mToUnregister = new LinkedList<>();
    mUpdating = false;
    mTimer = timer;
    mTimer.setHandler(this);
    mTimer.start();
  }

  public StopwatchTimer getTimer() {
    return mTimer;
  }

  public void register(Updatable updatable) {
    if (mUpdating) {
      mToRegister.add(updatable);
    }
    else {
      mComponents.add(updatable);
    }
  }

  public void register(@NotNull List<Updatable> updatables) {
    for (Updatable updatable : updatables) {
      register(updatable);
    }
  }

  public void unregister(@NotNull Updatable updatable) {
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

  public boolean isRunning() {
    return mTimer.isRunning();
  }

  public void reset() {
    mReset = true;
  }

  @Override
  public void onTick(long elapsedNs) {
    mUpdating = true;
    if (mReset) {
      mComponents.forEach(Updatable::reset);
      mReset = false;
    }

    mComponents.forEach(component -> component.update(elapsedNs));
    mComponents.forEach(Updatable::postUpdate);
    mUpdating = false;

    mToUnregister.forEach(this::unregister);
    mToRegister.forEach(this::register);

    mToUnregister.clear();
    mToRegister.clear();
  }

  /**
   * A linear interpolation that accumulates over time. This gives an exponential effect where the
   * value {@code from} moves towards the value {@code to} at a rate of {@code fraction} per
   * second. The actual interpolated amount depends on the current frame length.
   *
   * @param from          the value to interpolate from.
   * @param to            the target value.
   * @param fraction      the interpolation fraction.
   * @param frameLengthNs the frame length in nanoseconds.
   * @param threshold     the difference threshold that will cause the method to jump to the target value without lerp.
   * @return the interpolated value.
   */
  public static float lerp(float from, float to, float fraction, long frameLengthNs, float threshold) {
    if (Math.abs(to - from) < threshold) {
      return to;
    }
    else {
      double length = frameLengthNs / 1000000000.0;
      float q = (float)Math.pow(1.0f - fraction, length);
      return from * q + to * (1.0f - q);
    }
  }

  public static double lerp(double from, double to, float fraction, long frameLengthNs, double threshold) {
    if (Math.abs(to - from) < threshold) {
      return to;
    }
    else {
      double length = frameLengthNs / 1000000000.0;
      double q = Math.pow(1.0f - fraction, length);
      return from * q + to * (1.0 - q);
    }
  }

  public static double lerp(double a, double b, float factor) {
    return a + (b - a) * factor;
  }
}
