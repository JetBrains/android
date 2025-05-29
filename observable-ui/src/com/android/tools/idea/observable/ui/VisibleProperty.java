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
package com.android.tools.idea.observable.ui;

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.core.BoolProperty;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * {@link AbstractProperty} that wraps a Swing component and exposes its visibility state.
 */
public final class VisibleProperty extends BoolProperty implements ComponentListener {
  @NotNull private final JComponent myComponent;

  public VisibleProperty(@NotNull JComponent component) {
    myComponent = component;
    myComponent.addComponentListener(this);
  }

  @Override
  public void componentResized(ComponentEvent componentEvent) {
    // Don't care
  }

  @Override
  public void componentMoved(ComponentEvent componentEvent) {
    // Don't care
  }

  @Override
  public void componentShown(ComponentEvent componentEvent) {
    notifyInvalidated();
  }

  @Override
  public void componentHidden(ComponentEvent componentEvent) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Boolean get() {
    return myComponent.isVisible();
  }

  @Override
  protected void setDirectly(@NotNull Boolean value) {
    myComponent.setVisible(value);
  }
}
