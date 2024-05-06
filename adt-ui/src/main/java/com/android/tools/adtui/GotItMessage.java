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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.BrowserHyperlinkListener;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.event.HyperlinkListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Copied from com/intellij/ui/GotItMessage but visually customized for Studio based on design from UX
 */
public class GotItMessage {
  @NotNull private final String myTitle;
  @NotNull private final String myMessage;

  private Disposable myDisposable;
  private Runnable myCallback;
  private HyperlinkListener myHyperlinkListener = BrowserHyperlinkListener.INSTANCE;
  private boolean myShowCallout = true;

  private GotItMessage(@NotNull String title, @NotNull String message) {
    myTitle = title;
    myMessage =
      "<html><body><div style='font-family: " +
      StartupUiUtil.getLabelFont().getFontName() +
      "; font-size: " +
      JBUI.scale(12) +
      "pt; color: " +
      GotItPanel.TEXT_COLOR +
      ";'>" +
      StringUtil.replace(message, "\n", "<br>") +
      "</div></body></html>";
  }

  public static GotItMessage createMessage(@NotNull String title, @NotNull String message) {
    return new GotItMessage(title, message);
  }

  public GotItMessage setDisposable(Disposable disposable) {
    myDisposable = disposable;
    return this;
  }

  public GotItMessage setCallback(@Nullable Runnable callback) {
    myCallback = callback;
    return this;
  }

  public GotItMessage setHyperlinkListener(@Nullable HyperlinkListener hyperlinkListener) {
    myHyperlinkListener = hyperlinkListener;
    return this;
  }

  public GotItMessage setShowCallout(boolean showCallout) {
    myShowCallout = showCallout;
    return this;
  }

  public void show(RelativePoint point, Balloon.Position position) {
    if (myDisposable != null && Disposer.isDisposed(myDisposable)) {
      return;
    }
    final GotItPanel panel = new GotItPanel();
    panel.myTitle.setText(myTitle);

    panel.myMessage.setText(myMessage);
    if (myHyperlinkListener != null) {
      panel.myMessage.addHyperlinkListener(myHyperlinkListener);
    }
    panel.myRoot.setBackground(GotItPanel.BACKGROUND);
    panel.myMessagePanel.setOpaque(false);
    panel.myButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    panel.myButtonLabel.setText(StringUtil.toUpperCase(panel.myButtonLabel.getText()));
    final BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(panel.myRoot);
    if (myDisposable != null) {
      builder.setDisposable(myDisposable);
    }

    final Balloon balloon = builder
      .setFillColor(GotItPanel.BACKGROUND)
      .setHideOnClickOutside(false)
      .setHideOnAction(false)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setShowCallout(myShowCallout)
      .setBlockClicksThroughBalloon(true)
      .createBalloon();

    panel.myButton.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        balloon.hide();
        if (myCallback != null) {
          myCallback.run();
        }
      }
    });

    balloon.show(point, position);
  }
}
