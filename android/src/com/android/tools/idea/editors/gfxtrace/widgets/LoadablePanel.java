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
package com.android.tools.idea.editors.gfxtrace.widgets;

import com.android.tools.rpclib.futures.SingleInFlight;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.Alarm;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@JPanel} delegate that can show a loading indicator overlay.
 * Do not interact with this via the LayeredPane API, or unexpected things will happen.
 */
public class LoadablePanel extends JBLayeredPane implements SingleInFlight.Listener {
  private static final long DELAY_MS = 50;

  @NotNull private final JPanel myContents;
  @NotNull private final LoadingLayer myLoadingLayer;
  @NotNull private final AtomicBoolean myShouldShow = new AtomicBoolean();
  @NotNull private final Alarm myAlarm = new Alarm();

  public LoadablePanel(@NotNull LayoutManager layout) {
    this(new JPanel(layout), Style.TRANSPARENT);
  }

  public LoadablePanel(@NotNull JPanel contents) {
    this(contents, Style.TRANSPARENT);
  }

  public LoadablePanel(@NotNull JPanel contents, @NotNull Style style) {
    myContents = contents;
    myLoadingLayer = new LoadingLayer(style);

    add(myContents, JLayeredPane.DEFAULT_LAYER);
    add(myLoadingLayer, JLayeredPane.POPUP_LAYER);
  }

  @NotNull
  public JPanel getContentLayer() {
    return myContents;
  }

  public void setLoadingText(@NotNull String loadingText) {
    myLoadingLayer.setLoadingText(loadingText);
  }

  public void startLoading() {
    myShouldShow.set(true);
    myAlarm.addRequest(new Runnable() {
      @Override
      public void run() {
        if (myShouldShow.get()) {
          myLoadingLayer.startLoading();
        }
      }
    }, DELAY_MS);
  }

  public void stopLoading() {
    stopLoading(null, null);
  }

  public void showLoadingError(@NotNull String message) {
    stopLoading(message, null);
  }

  public void showLoadingError(@NotNull String message, @NotNull Component errorComponent) {
    stopLoading(message, errorComponent);
  }

  private void stopLoading(final String message, final Component errorComponent) {
    myShouldShow.set(false);
    myAlarm.cancelAllRequests();
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        if (!myShouldShow.get()) {
          myLoadingLayer.stopLoading(message, errorComponent);
        }
      }
    });
  }

  @Override
  public void onIdleToWorking() {
    startLoading();
  }

  @Override
  public void onWorkingToIdle() {
    stopLoading();
  }

  @Override
  public Dimension getMinimumSize() {
    return myContents.getMinimumSize();
  }

  @Override
  public Dimension getPreferredSize() {
    return myContents.getPreferredSize();
  }

  @Override
  public void doLayout() {
    myContents.setBounds(0, 0, getWidth(), getHeight());
    myLoadingLayer.setBounds(0, 0, getWidth(), getHeight());
  }

  public enum Style {
    TRANSPARENT() {
      @Override
      public JPanel stylePanel(JPanel panel) {
        panel.setOpaque(false);
        return panel;
      }
    }, OPAQUE() {
      @Override
      public JPanel stylePanel(JPanel panel) {
        panel.setOpaque(true);
        panel.setBackground(UIUtil.getPanelBackground());
        panel.setBorder(JBUI.Borders.merge(JBUI.Borders.customLine(new JBColor(0, 0xffffff), 1), JBUI.Borders.empty(5), false));
        return panel;
      }
    };

    public void styleLabel(JBLabel label) {
      final Font font = label.getFont();
      label.setFont(font.deriveFont(font.getSize() * 1.5f));
    }

    public abstract JPanel stylePanel(JPanel panel);
  }

  private static class LoadingLayer extends JPanel {
    @NotNull private final JPanel myCenteredChildPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    @NotNull private final AsyncProcessIcon mySpinner = new AsyncProcessIcon.Big("Loading");
    @NotNull private final JBLabel myLabel = new JBLabel();
    private boolean myShowingError = false;
    @NotNull private String myLoadingText = "Loading...";

    public LoadingLayer(Style style) {
      super(new GridBagLayout());
      style.styleLabel(myLabel);
      setOpaque(false);

      add(style.stylePanel(myCenteredChildPanel));
      setVisible(false);
    }

    public void setLoadingText(@NotNull String loadingText) {
      myLoadingText = loadingText;
    }

    public void startLoading() {
      reset(true, myLoadingText, null);
      myShowingError = false;
      setVisible(true);
    }

    public void stopLoading(@Nullable String error, @Nullable Component errorComponent) {
      if (error == null) {
        if (!myShowingError) {
          setVisible(false);
        }
      } else {
        reset(false, error, AllIcons.General.ErrorDialog);
        if (errorComponent != null) {
          myCenteredChildPanel.add(errorComponent);
        }
        myShowingError = true;
        setVisible(true);
      }
    }

    private void reset(boolean spinner, String text, Icon icon) {
      myCenteredChildPanel.removeAll();
      if (spinner) {
        myCenteredChildPanel.add(mySpinner);
      }
      myCenteredChildPanel.add(myLabel);
      myLabel.setText(text);
      myLabel.setIcon(icon);
    }
  }
}
