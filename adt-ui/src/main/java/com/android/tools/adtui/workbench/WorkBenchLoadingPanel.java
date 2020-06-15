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
package com.android.tools.adtui.workbench;

import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

/**
 * Drop-in replacement for JBLoadingPanel with ability to display a message when loading
 * of the widget failed.
 */
public class WorkBenchLoadingPanel extends JPanel {
  private final JBLoadingPanel myLoadingPanel;
  private final MyMessagePanel myMessagePanel;
  private boolean myShowingMessagePanel;

  public WorkBenchLoadingPanel(@Nullable LayoutManager manager, @NotNull Disposable parent,
                        @SuppressWarnings("SameParameterValue") int startDelayMs) {
    super(new BorderLayout());
    myMessagePanel = new MyMessagePanel();
    myLoadingPanel = new JBLoadingPanel(manager, parent, startDelayMs);
    super.add(myLoadingPanel);
  }

  public void startLoading() {
    resumeLoading();
    myLoadingPanel.startLoading();
  }

  public void stopLoading() {
    resumeLoading();
    myLoadingPanel.stopLoading();
  }

  public void setLoadingText(String text) {
    myLoadingPanel.setLoadingText(text);
  }

  @VisibleForTesting
  public boolean isLoading() {
    return myLoadingPanel.isLoading();
  }

  @VisibleForTesting
  public boolean hasError() {
    return myMessagePanel.isShowing();
  }

  boolean isLoadingOrHasError() {
    return isLoading() || hasError();
  }

  @Override
  public Component add(Component comp) {
    return myLoadingPanel.add(comp);
  }

  @Override
  public Component add(Component comp, int index) {
    return myLoadingPanel.add(comp, index);
  }

  @Override
  public void add(Component comp, Object constraints) {
    myLoadingPanel.add(comp, constraints);
  }

  @Override
  public Dimension getPreferredSize() {
    return myLoadingPanel.getPreferredSize();
  }

  /**
   * Replaces loading animation with the given message.
   */
  public void abortLoading(String message, @SuppressWarnings("SameParameterValue") Icon icon) {
    myMessagePanel.setText(message);
    myMessagePanel.setIcon(icon);
    if (!myShowingMessagePanel) {
      super.remove(myLoadingPanel);
      super.add(myMessagePanel);
      myShowingMessagePanel = true;
    }
  }

  private void resumeLoading() {
    if (myShowingMessagePanel) {
      super.remove(myMessagePanel);
      super.add(myLoadingPanel);
      myShowingMessagePanel = false;
    }
  }

  private static class MyMessagePanel extends JPanel {
    private final JLabel myText = new JLabel("", SwingConstants.CENTER);
    private boolean myInitialized;

    MyMessagePanel() {
      super(new BorderLayout());
      setOpaque(false);
      add(myText);
      updateTextFont();
      myInitialized = true;
    }

    public void setText(String text) {
      myText.setText(text);
    }

    public void setIcon(Icon icon) {
      myText.setIcon(icon);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      if (myInitialized) {
        updateTextFont();
      }
    }

    private void updateTextFont() {
      // Similar to JBLoadingPanel.customizeStatusText but with a smaller font.
      Font font = UIUtil.getLabelFont();
      myText.setFont(font.deriveFont(font.getStyle(), font.getSize() + 4));
      myText.setForeground(ColorUtil.toAlpha(UIUtil.getLabelForeground(), 150));
    }
  }
}