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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.common.AdtUiUtils;
import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.event.EventListenerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A panel which wraps an inner component and provides a small arrow button for toggling its
 * visibility.
 */
public class HideablePanel extends JPanel {
  /**
   * Enum that defines which component to register the toggle click listener on.
   */
  public enum ClickableComponent {
    /**
     * The panel title and arrow are the only parts that trigger the panel to toggle.
     */
    TITLE,
    /**
     * The full title bar including triggers the panel to toggle.
     */
    TITLE_BAR,
  }

  private static final Border HIDEABLE_PANEL_BORDER = new JBEmptyBorder(0, 10, 0, 15);
  private static final Border HIDEABLE_CONTENT_BORDER = new JBEmptyBorder(0, 12, 0, 5);
  private static final int TITLE_RIGHT_PADDING = 3;

  private boolean myExpanded;
  private final JComponent myChild;
  private final JLabel myLabel;
  private final EventListenerList myStateChangeListeners;
  private final JPanel myTitlePanel;

  private HideablePanel(@NotNull Builder builder) {
    super(new BorderLayout());
    myChild = builder.myContent;
    myStateChangeListeners = new EventListenerList();
    myTitlePanel = new JPanel(new TabularLayout("Fit,*,Fit-"));
    myLabel = setupTitleBar(builder);
    setTitle(builder.myTitle, builder.myIsTitleBold);
    myChild.setBorder(builder.myContentBorder == null ? HIDEABLE_CONTENT_BORDER : builder.myContentBorder);
    setBorder(builder.myPanelBorder == null ? HIDEABLE_PANEL_BORDER : builder.myPanelBorder);
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
    JLabel label = new JLabel();
    JComponent clickableComponent;
    MouseAdapter toggleAdapter = new MouseAdapter() {
      @Override
      public void mouseReleased(@NotNull MouseEvent e) {
        setExpanded(!isExpanded());
      }
    };
    switch (builder.myClickableComponent) {
      case TITLE:
        clickableComponent = label;
        break;
      case TITLE_BAR:
      default:
        clickableComponent = myTitlePanel;
        break;
    }
    clickableComponent.addMouseListener(toggleAdapter);
    clickableComponent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    // Add Label as first element, should always be left aligned.
    myTitlePanel.add(label, new TabularLayout.Constraint(0, 0));
    if (builder.myShowSeparator) {
      JComponent separatorComponent = AdtUiUtils.createHorizontalSeparator();
      separatorComponent.setBorder(new JBEmptyBorder(0, 10, 0, builder.myTitleRightPadding));
      myTitlePanel.add(separatorComponent, new TabularLayout.Constraint(0, 1));
    }
    // If we have a north east component we add that last it will only take up as much space as it needs.
    if (builder.myNorthEastComponent != null) {
      myTitlePanel.add(builder.myNorthEastComponent,
                       new TabularLayout.Constraint(0, 2));
    }
    add(myTitlePanel, BorderLayout.NORTH);
    if (builder.myTitleLeftPadding != null) {
      label.setBorder(JBUI.Borders.empty(0, builder.myTitleLeftPadding, 0, 0));
    }
    if (builder.myIconTextGap != null) {
      label.setIconTextGap(builder.myIconTextGap);
    }
    return label;
  }

  public void setTitle(String title, boolean isTitleBold) {
    if (isTitleBold) {
      title = String.format("<b>%s</b>", title);
    }
    myLabel.setText(String.format("<html><nobr>%s</nobr></html>", title));
  }

  public void setTitle(String title) {
    setTitle(title, false);
  }

  /**
   * @param expanded sets the internal state for animating the expanding/collapsing of the child component.
   */
  public void setExpanded(boolean expanded) {
    if (myExpanded != expanded) {
      myExpanded = expanded;
      myChild.setVisible(expanded);
      if (expanded) {
        myLabel.setIcon(AllIcons.General.ArrowDown);
      }
      else {
        myLabel.setIcon(AllIcons.General.ArrowRight);
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
    @NotNull ClickableComponent myClickableComponent = ClickableComponent.TITLE_BAR;
    @Nullable JComponent myNorthEastComponent;
    @Nullable Consumer<Boolean> myOnStateChangedConsumer;
    @Nullable Border myContentBorder;
    @Nullable Border myPanelBorder;
    boolean myShowSeparator = true;
    boolean myInitiallyExpanded = true;
    int myTitleRightPadding = TITLE_RIGHT_PADDING;
    @Nullable Integer myTitleLeftPadding;
    @Nullable Integer myIconTextGap;
    boolean myIsTitleBold = false;

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

    /**
     * Sets the content child component's border inside the hideable panel.
     */
    @NotNull
    public Builder setContentBorder(@NotNull Border border) {
      myContentBorder = border;
      return this;
    }

    /**
     * Sets this hideable panel's border.
     */
    @NotNull
    public Builder setPanelBorder(@NotNull Border border) {
      myPanelBorder = border;
      return this;
    }

    /**
     * Sets the title bar padding on the left.
     */
    @NotNull
    public Builder setTitleLeftPadding(int leftPadding) {
      myTitleLeftPadding = leftPadding;
      return this;
    }

    /**
     * Sets the title bar padding on the right.
     */
    @NotNull
    public Builder setTitleRightPadding(int rightPadding) {
      myTitleRightPadding = rightPadding;
      return this;
    }

    /**
     * Sets icon text gap which is the space between the icon and the text.
     */
    @NotNull
    public Builder setIconTextGap(int gap) {
      myIconTextGap = gap;
      return this;
    }

    /**
     * Sets which parts of the hideable panel toggle the component.
     */
    @NotNull
    public Builder setClickableComponent(@NotNull ClickableComponent clickableComponent) {
      myClickableComponent = clickableComponent;
      return this;
    }

    /**
     * Sets the title to be bold
     */
    @NotNull
    public Builder setIsTitleBold(boolean isTitleBold) {
      myIsTitleBold = isTitleBold;
      return this;
    }

    @NotNull
    public HideablePanel build() {
      return new HideablePanel(this);
    }
  }
}
