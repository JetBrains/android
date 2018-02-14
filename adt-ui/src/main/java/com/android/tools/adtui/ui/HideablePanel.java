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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.TabularLayout;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBEmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * A panel which wraps an inner component and provides a small arrow button for toggling its
 * visibility.
 */
public class HideablePanel extends JPanel {
  private static final Border HIDEABLE_PANEL_BORDER = new JBEmptyBorder(0, 10, 0, 15);
  private static final Border HIDEABLE_CONTENT_BORDER = new JBEmptyBorder(0, 12, 0, 5);

  private boolean myExpanded;
  private final JComponent myChild;
  private final JLabel myLabel;
  private final EventListenerList myStateChangeListeners;
  private final JPanel myTitlePanel;

  public HideablePanel(@NotNull Builder builder) {
    super(new BorderLayout());
    myChild = builder.myContent;
    myStateChangeListeners = new EventListenerList();
    myTitlePanel = new JPanel(new TabularLayout("Fit,*,Fit"));
    myLabel = setupTitleBar(builder);
    myChild.setBorder(HIDEABLE_CONTENT_BORDER);
    setBorder(HIDEABLE_PANEL_BORDER);
    add(myChild, BorderLayout.CENTER);

    // Set expanded state to not initial state so we trigger
    // all state changes when we call setExpanded.
    myExpanded = !builder.myInitiallyExpanded;
    setExpanded(builder.myInitiallyExpanded);
  }

  /**
   * Creates the title bar this is loosely based on {@link com.intellij.ui.TitledSeparator}
   *
   * @param builder builder that contains settings for configuring the title bar.
   * @return label to update the expanded icon.
   */
  private JLabel setupTitleBar(@NotNull Builder builder) {
    JLabel label = new JLabel(String.format("<html><nobr>%s</nobr></html>", builder.myTitle));
    myTitlePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    myTitlePanel.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        setExpanded(!isExpanded());
      }
    });

    // Add Label as first element, should always be left aligned.
    myTitlePanel.add(label, new TabularLayout.Constraint(0, 0));
    // If we have a separator we want it to be centered vertically so we wrap it in a panel.
    if (builder.myShowSeparator) {
      JPanel verticalAlignPanel = new JPanel(new BorderLayout());
      verticalAlignPanel.add(new JSeparator(), BorderLayout.CENTER);
      verticalAlignPanel.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 3));
      myTitlePanel.add(verticalAlignPanel, new TabularLayout.Constraint(0, 1));
    }
    // If we have a north east component we add that last it will only take up as much space as it needs.
    if (builder.myNorthEastComponent != null) {
      myTitlePanel.add(builder.myNorthEastComponent,
                       new TabularLayout.Constraint(0, 2));
    }
    add(myTitlePanel, BorderLayout.NORTH);
    return label;
  }

  /**
   * @param expanded sets the internal state for animating the expanding/collapsing of the child component.
   */
  @VisibleForTesting
  void setExpanded(boolean expanded) {
    if (myExpanded != expanded) {
      myExpanded = expanded;
      myChild.setVisible(expanded);
      if (expanded) {
        myLabel.setIcon(AllIcons.General.SplitDown);
      }
      else {
        myLabel.setIcon(AllIcons.General.SplitRight);
      }
      if (getParent() != null) {
        getParent().revalidate();
      }
      ActionListener[] listeners = myStateChangeListeners.getListeners(ActionListener.class);
      if (listeners != null) {
        for (ActionListener listener : listeners) {
          listener.actionPerformed(null);
        }
      }
    }
  }

  public void addStateChangedListener(@NotNull ActionListener expandedListener) {
    myStateChangeListeners.add(ActionListener.class, expandedListener);
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);

    // Super calls init BasicPaneUI which in turn calls setBackgroundColor, as such this can be null.
    if (myTitlePanel != null) {
      myTitlePanel.setBackground(bg);
    }
  }

  public boolean isExpanded() {
    return myExpanded;
  }

  public static final class Builder {
    @NotNull String myTitle;
    @NotNull JComponent myContent;
    @Nullable JComponent myNorthEastComponent;
    @Nullable Consumer<Boolean> myOnStateChangedConsumer;
    boolean myShowSeparator = true;
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

    /**
     * @param initiallyExpanded default state of the control when created.
     */
    @NotNull
    public Builder setInitiallyExpanded(boolean initiallyExpanded) {
      myInitiallyExpanded = initiallyExpanded;
      return this;
    }

    /**
     * @param show true of the separator should be visible.
     */
    @NotNull
    public Builder setShowSeparator(boolean show) {
      myShowSeparator = show;
      return this;
    }

    @NotNull
    public HideablePanel build() {
      return new HideablePanel(this);
    }
  }
}
