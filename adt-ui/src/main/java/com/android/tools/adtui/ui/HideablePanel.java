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
package com.android.tools.adtui.ui;

import com.intellij.ui.HideableDecorator;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * A panel which wraps an inner component and provides a small arrow button for toggling its
 * visibility.
 *
 * Note: This wraps IntelliJ's {@link HideableDecorator}, providing a convenient control for
 * modules that don't want to depend on platform-impl while also simplifying the API by offering
 * a significantly restricted subset of the original panel's functionality.
 */
public class HideablePanel extends JPanel {
  private static final Border HIDEABLE_PANEL_BORDER = new JBEmptyBorder(0, 10, 0, 5);
  private static final Border HIDEABLE_CONTENT_BORDER = new JBEmptyBorder(0, 12, 0, 5);

  private final HideableDecorator myHideableDecorator;

  public HideablePanel(@NotNull Builder builder) {
    super(new BorderLayout());
    myHideableDecorator = new HideableDecorator(this, builder.myTitle, false, builder.myNorthEastComponent) {
      @Override
      protected void on() {
        super.on();
        handleToggled();
      }

      @Override
      protected void off() {
        super.off();
        handleToggled();
      }

      private void handleToggled() {
        // For some reason, Swing doesn't consistently redo the layout when this panel's content
        // is hidden. The UI leaves a big blank space there until something else triggers a layout
        // (like a resize). Therefore, we force a layout, which really should be happening anyway.
        Container parent = HideablePanel.this.getParent();
        if (parent != null) {
          parent.doLayout();
        }
      }
    };

    builder.myContent.setBorder(HIDEABLE_CONTENT_BORDER);
    myHideableDecorator.setContentComponent(builder.myContent);
    setBorder(HIDEABLE_PANEL_BORDER);

    setExpanded(builder.myInitiallyExpanded);
  }

  public void setExpanded(boolean expanded) {
    myHideableDecorator.setOn(expanded);
  }

  public boolean isExpanded() {
    return myHideableDecorator.isExpanded();
  }

  public static final class Builder {
    @NotNull String myTitle;
    @NotNull JComponent myContent;
    @Nullable JComponent myNorthEastComponent;
    boolean myInitiallyExpanded = true;

    public Builder(@NotNull String title, @NotNull JComponent content) {
      myTitle = title;
      myContent = content;
    }

    /**
     * A component which, if set, will appear on the far right side of the header bar
     */
    @NotNull
    public Builder setNorthEastComponent(@Nullable JComponent northEastComponent) {
      myNorthEastComponent = northEastComponent;
      return this;
    }

    @NotNull
    public Builder setInitiallyExpanded(boolean initiallyExpanded) {
      myInitiallyExpanded = initiallyExpanded;
      return this;
    }

    @NotNull
    public HideablePanel build() {
      return new HideablePanel(this);
    }
  }
}
