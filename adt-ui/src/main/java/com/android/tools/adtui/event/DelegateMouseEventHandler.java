/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.event;

import com.intellij.util.ui.MouseEventHandler;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;

/**
 * Delegates mouse events from a source component to the target component.
 * <p>
 * Use {@link #delegateTo(Component)} with chained calls to {@link #installListenerOn(Component)},
 * {@link #installMotionListenerOn(Component)} or {@link #installMouseWheelListenerOn(Component)}.
 */
public class DelegateMouseEventHandler extends MouseEventHandler {
  @NotNull private final Component myTargetComponent;

  private DelegateMouseEventHandler(@NotNull Component targetComponent) {
    myTargetComponent = targetComponent;
  }

  /**
   * Installs mouse events to the given {@param sourceComponent}.
   * All mouse events from {@param sourceComponent} will be dispatched to the {@link #myTargetComponent}.
   *
   * @see Component#addMouseListener(MouseListener)
   */
  @NotNull
  public DelegateMouseEventHandler installListenerOn(@NotNull Component sourceComponent) {
    sourceComponent.addMouseListener(this);
    return this;
  }

  /**
   * Installs mouse motion events to the given {@param sourceComponent}.
   * All mouse motion events from {@param sourceComponent} will be dispatched to the {@link #myTargetComponent}.
   *
   * @see Component#addMouseMotionListener(MouseMotionListener)
   */
  @NotNull
  public DelegateMouseEventHandler installMotionListenerOn(@NotNull Component sourceComponent) {
    sourceComponent.addMouseMotionListener(this);
    return this;
  }

  /**
   * Installs mouse wheel events to the given {@param sourceComponent}.
   * All mouse wheel events from {@param sourceComponent} will be dispatched to the {@link #myTargetComponent}.
   *
   * @see Component#addMouseWheelListener(MouseWheelListener)
   */
  @NotNull
  public DelegateMouseEventHandler installMouseWheelListenerOn(@NotNull Component sourceComponent) {
    sourceComponent.addMouseWheelListener(this);
    return this;
  }

  /**
   * @param targetComponent - the target component where mouse events need to be dispatched.
   */
  @NotNull
  public static DelegateMouseEventHandler delegateTo(@NotNull Component targetComponent) {
    return new DelegateMouseEventHandler(targetComponent);
  }

  @Override
  protected void handle(MouseEvent event) {
    myTargetComponent.dispatchEvent(SwingUtilities.convertMouseEvent(event.getComponent(), event, myTargetComponent));
  }
}
