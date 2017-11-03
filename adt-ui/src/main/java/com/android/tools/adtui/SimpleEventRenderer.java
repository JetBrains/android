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

import com.android.tools.adtui.model.event.EventAction;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;

/**
 * Interface to define how events should be rendered in the event timeline.
 */
public interface SimpleEventRenderer<E> {

  /**
   * Primary draw function for events. This function will get called only when an event is supposed to have something drawn.
   *
   * @param parent    The parent element to draw to.
   * @param g2d       The graphics object used to draw elements.
   * @param transform The coordinates on the screen where the event starts
   * @param length    The length of the event if the event has a unit of time associated with its rendering.
   * @param data      The EventAction data used to trigger this draw event. This data can contain some addtional information
   *                  used by the renderers such as the string passed via keyboard event. If this argument is null the renderer
   *                  is expected to ignore the additional data or is not expected to use it.
   */
  void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length, @Nullable EventAction<E> data);

  default void draw(Component parent, Graphics2D g2d, AffineTransform transform, double length) {
    draw(parent, g2d, transform, length, null);
  }
}
