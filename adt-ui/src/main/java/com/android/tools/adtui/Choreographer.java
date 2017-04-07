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

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyListener;
import java.util.LinkedList;
import java.util.List;

/**
 * An auxiliary object that synchronizes a group of {@link Animatable} via a simple update loop
 * running at a specific frame rate. This ensures all UI components and model classes are reading
 * and displaying consistent information at any given time.
 */
public class Choreographer implements ActionListener {

  private static final int DEFAULT_FPS = 60;
  private static final float NANOSECONDS_IN_SECOND = 1000000000.0f;
  private static final float DEFAULT_FRAME_LENGTH = 1.0f / DEFAULT_FPS;

  private final List<Animatable> mComponents;
  private List<Animatable> mToRegister;
  private List<Animatable> mToUnregister;
  private final Timer mTimer;
  private boolean mUpdate;
  private long mFrameTime;
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
   * @param parent The parent component that contains all {@link AnimatedComponent} registered
   *               with the Choreographer.
   */
  public Choreographer(int fps, @NotNull JComponent parent) {
    mParentContainer = parent;
    mComponents = new LinkedList<>();
    mToRegister = new LinkedList<>();
    mToUnregister = new LinkedList<>();
    mUpdate = true;
    mUpdating = false;
    mTimer = new Timer(1000 / fps, this);
    mTimer.start();
  }

  public Choreographer(@NotNull JComponent parent) {
    this(DEFAULT_FPS, parent);
  }

  public void register(Animatable animatable) {
    if (mUpdating) {
      mToRegister.add(animatable);
    } else {
      mComponents.add(animatable);
    }
  }

  public void register(@NotNull List<Animatable> animatables) {
    for (Animatable animatable : animatables) {
      register(animatable);
    }
  }

  public void unregister(@NotNull Animatable animatable) {
    if (mUpdating) {
      mToUnregister.add(animatable);
    } else {
      mComponents.remove(animatable);
    }
  }

  public void stop() {
    if (mTimer.isRunning()) {
      mTimer.stop();
    }
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    long now = System.nanoTime();
    float frame = (now - mFrameTime) / NANOSECONDS_IN_SECOND;
    mFrameTime = now;

    if (!mUpdate) {
      return;
    }
    step(frame);
  }

  /**
   * @deprecated Legacy method to animate components. Each component animates on its own and has a
   * choreographer that is bound to that component's visibility. This can be safely removed once
   * all the legacy AnimatedComponents in Studio has been replaced by the new UI.
   */
  @Deprecated
  public static void animate(final AnimatedComponent component) {
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

  public void setUpdate(boolean update) {
    mUpdate = update;
  }

  public void step() {
    step(DEFAULT_FRAME_LENGTH);
  }

  public void reset() {
    mReset = true;
  }

  private void step(float frameLength) {
    mUpdating = true;
    if (mReset) {
      mComponents.forEach(Animatable::reset);
      mReset = false;
    }

    mComponents.forEach(component -> component.animate(frameLength));
    mComponents.forEach(Animatable::postAnimate);
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
