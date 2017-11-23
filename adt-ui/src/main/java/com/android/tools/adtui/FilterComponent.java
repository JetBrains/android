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
package com.android.tools.adtui;


import com.intellij.openapi.application.ModalityState;
import com.intellij.ui.SearchTextFieldWithStoredHistory;
import com.intellij.util.Alarm;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * A modified version of IJ's FilterComponent that allows users to specify a custom delay between typing and
 * the filter box triggering a change event.
 */
public abstract class FilterComponent extends JPanel {
  private final SearchTextFieldWithStoredHistory myFilter;
  private final Alarm myUpdateAlarm = new Alarm();
  private final int myDelayMs;

  public FilterComponent(@NonNls String propertyName, int historySize, int delayMs) {
    super(new BorderLayout());
    myDelayMs = delayMs;
    myFilter = new SearchTextFieldWithStoredHistory(propertyName) {
      @Override
      protected Runnable createItemChosenCallback(JList list) {
        final Runnable callback = super.createItemChosenCallback(list);
        return () -> {
          callback.run();
          filter();
        };
      }

      @Override
      protected Component getPopupLocationComponent() {
        return FilterComponent.this.getPopupLocationComponent();
      }

      @Override
      protected void onFocusLost() {
        addCurrentTextToHistory();
        super.onFocusLost();
      }
    };
    myFilter.getTextEditor().addKeyListener(new KeyAdapter() {
      //to consume enter in combo box - do not process this event by default button from DialogWrapper
      @Override
      public void keyPressed(final KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          e.consume();
          myFilter.addCurrentTextToHistory();
          filter();
        }
        else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
          onEscape(e);
        }
      }
    });

    myFilter.addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        onChange();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        onChange();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        onChange();
      }
    });

    myFilter.setHistorySize(historySize);
    AccessibleContextUtil.setName(myFilter.getTextEditor(), "Message text filter");
    add(myFilter, BorderLayout.CENTER);
  }

  protected JComponent getPopupLocationComponent() {
    return myFilter;
  }

  public JTextField getTextEditor() {
    return myFilter.getTextEditor();
  }

  private void onChange() {
    myUpdateAlarm.cancelAllRequests();
    myUpdateAlarm.addRequest(() -> onlineFilter(), myDelayMs, ModalityState.stateForComponent(myFilter));
  }

  public void setHistorySize(int historySize) {
    myFilter.setHistorySize(historySize);
  }

  public void reset() {
    myFilter.reset();
  }

  protected void onEscape(KeyEvent e) {
  }

  public String getFilter() {
    return myFilter.getText();
  }

  public void setSelectedItem(final String filter) {
    myFilter.setSelectedItem(filter);
  }

  public void setFilter(final String filter) {
    myFilter.setText(filter);
  }

  public void selectText() {
    myFilter.selectText();
  }

  @Override
  public boolean requestFocusInWindow() {
    return myFilter.requestFocusInWindow();
  }

  public abstract void filter();

  protected void onlineFilter() {
    filter();
  }

  public void dispose() {
    myUpdateAlarm.cancelAllRequests();
  }

  protected void setHistory(java.util.List<String> strings) {
    myFilter.setHistory(strings);
  }
}
