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
package com.android.tools.adtui.util;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;

public class SwingUtil {

  /**
   * @return a {@link MouseEvent} similar to the given {@param event} except that its {@link MouseEvent#getID()} will be the {@param id}.
   */
  @NotNull
  public static MouseEvent convertMouseEventID(@NotNull MouseEvent event, int id) {
    return new MouseEvent((Component)event.getSource(),
                          id, event.getWhen(), event.getModifiers(), event.getX(), event.getY(),
                          event.getClickCount(), event.isPopupTrigger(),
                          event.getButton());
  }

  /**
   * @return a new {@link MouseEvent} similar to the given event except that its point will be the given point.
   */
  @NotNull
  public static MouseEvent convertMouseEventPoint(@NotNull MouseEvent event, @NotNull Point newPoint) {
    return new MouseEvent(
      event.getComponent(),
      event.getID(),
      event.getWhen(),
      event.getModifiers(),
      newPoint.x,
      newPoint.y,
      event.getClickCount(),
      event.isPopupTrigger());
  }
}
