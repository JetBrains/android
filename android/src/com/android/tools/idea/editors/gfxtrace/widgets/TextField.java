/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.intellij.ui.components.JBTextField;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * A {@link JBTextField} with some extra features.
 */
public class TextField extends JBTextField {
  private final ChangeEvent myChangeEvent = new ChangeEvent(this);
  private final AtomicBoolean fireEvents = new AtomicBoolean(true);

  public TextField() {
    init();
  }

  public TextField(int columns) {
    super(columns);
    init();
  }

  public TextField(String text) {
    super(text);
    init();
  }

  public TextField(String text, int columns) {
    super(text, columns);
    init();
  }

  private void init() {
    getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        fireChange();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        fireChange();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        fireChange();
      }
    });
  }

  @Override
  public void setText(String text) {
    setText(text, true);
  }

  public void setText(String text, boolean fireEvent) {
    boolean currentValue = fireEvents.getAndSet(fireEvent);
    try {
      super.setText(text);
    } finally {
      fireEvents.set(currentValue);
    }
  }

  protected void fireChange() {
    if (fireEvents.get()) {
      Object[] listeners = listenerList.getListenerList();
      for (int i = listeners.length - 2; i >= 0; i -= 2) {
        if (listeners[i] == ChangeListener.class) {
          ((ChangeListener)listeners[i + 1]).stateChanged(myChangeEvent);
        }
      }
    }
  }

  public void addChangedListener(final ChangeListener listener) {
    listenerList.add(ChangeListener.class, listener);
  }
}
