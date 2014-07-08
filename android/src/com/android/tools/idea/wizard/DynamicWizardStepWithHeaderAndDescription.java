/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.WeakHashMap;

import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * Base class for wizard steps with standard design.
 *
 * Subclasses should call {@link #setBodyComponent(javax.swing.JComponent)} from the constructor.
 */
public abstract class DynamicWizardStepWithHeaderAndDescription extends DynamicWizardStep implements Disposable {
  protected static final Key<String> KEY_DESCRIPTION =
    createKey(DynamicWizardStepWithHeaderAndDescription.class + ".description", ScopedStateStore.Scope.STEP, String.class);
  protected static final Key<String> KEY_TITLE =
    createKey(DynamicWizardStepWithHeaderAndDescription.class + ".title", ScopedStateStore.Scope.STEP, String.class);
  protected static final Key<String> KEY_MESSAGE =
    createKey(DynamicWizardStepWithHeaderAndDescription.class + ".message", ScopedStateStore.Scope.STEP, String.class);

  private static final String PROPERTY_FOCUS_OWNER = "focusOwner";

  @NotNull private final String myTitle;
  @Nullable private final String myMessage;
  @Nullable private final Disposable myDisposable;
  private PropertyChangeListener myFocusListener;
  private JPanel myRootPane;
  private JBLabel myTitleLabel;
  private JBLabel myMessageLabel;
  private JBLabel myIcon;
  private JLabel myDescriptionText;
  private JBLabel myErrorWarningLabel;
  private JPanel myNorthPanel;
  private JPanel myCustomHeaderPanel;
  private JPanel myTitlePanel;
  private Map<Component, String> myControlDescriptions = new WeakHashMap<Component, String>();

  /**
   * @deprecated Use {@link #DynamicWizardStepWithHeaderAndDescription(String, String, javax.swing.Icon, com.intellij.openapi.Disposable)}
   * to properly deregister focus listener when this page is no longer needed
   */
  @Deprecated
  public DynamicWizardStepWithHeaderAndDescription(@NotNull String title, @Nullable String message, @Nullable Icon icon) {
    this(title, message, icon, Disposer.newDisposable());
  }

  public DynamicWizardStepWithHeaderAndDescription(@NotNull String title,
                                                   @Nullable String message,
                                                   @Nullable Icon icon,
                                                   @Nullable Disposable parentDisposable) {
    myDisposable = parentDisposable;
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
    myTitle = title;
    myMessage = message;
    myIcon.setIcon(icon);
    int fontHeight = myMessageLabel.getFont().getSize();
    myTitleLabel.setBorder(BorderFactory.createEmptyBorder(fontHeight, 0, fontHeight, 0));
    myMessageLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, fontHeight, 0));
    if (getTitleBackgroundColor() != null) {
      myTitlePanel.setBackground(getTitleBackgroundColor());
      myNorthPanel.setBackground(getTitleBackgroundColor());
    }
    if (getTitleTextColor() != null) {
      myTitleLabel.setForeground(getTitleTextColor());
      myMessageLabel.setForeground(getTitleTextColor());
    }

    JComponent header = getHeader();
    if (header != null) {
      myCustomHeaderPanel.add(header, BorderLayout.CENTER);
      header.setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
      myCustomHeaderPanel.setVisible(true);
      myCustomHeaderPanel.repaint();
      myTitlePanel.setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
    } else {
      Insets topSegmentInsets = new Insets(WizardConstants.STUDIO_WIZARD_TOP_INSET,
                                           WizardConstants.STUDIO_WIZARD_INSETS.left,
                                           WizardConstants.STUDIO_WIZARD_INSETS.bottom,
                                           WizardConstants.STUDIO_WIZARD_INSETS.right);
      myNorthPanel.setBorder(new EmptyBorder(topSegmentInsets));
    }

    Font font = myTitleLabel.getFont();
    if (font == null) {
      font = UIUtil.getLabelFont();
    }
    font = new Font(font.getName(), font.getStyle() | Font.BOLD, font.getSize() + 4);
    myTitleLabel.setFont(font);
    myErrorWarningLabel.setForeground(JBColor.red);
  }

  protected static CompoundBorder createBodyBorder() {
    int fontSize = UIUtil.getLabelFont().getSize();
    Border insetBorder = BorderFactory.createEmptyBorder(fontSize * 4, fontSize * 2, fontSize * 4, fontSize * 2);
    return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UIUtil.getBorderColor()), insetBorder);
  }

  protected void setControlDescription(Component control, @Nullable String description) {
    if (myFocusListener == null) {
      myFocusListener = new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          updateDescription((JComponent)evt.getNewValue());
        }
      };
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(PROPERTY_FOCUS_OWNER, myFocusListener);
    }
    if (StringUtil.isEmpty(description)) {
      myControlDescriptions.remove(control);
    }
    else {
      myControlDescriptions.put(control, description);
    }
  }

  private void updateDescription(JComponent focusedComponent) {
    myState.put(KEY_DESCRIPTION, myControlDescriptions.get(focusedComponent));
  }

  @Override
  public void dispose() {
    if (myFocusListener != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(PROPERTY_FOCUS_OWNER, myFocusListener);
    }
  }

  protected final void setBodyComponent(JComponent component) {
    component.setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
    myRootPane.add(component, BorderLayout.CENTER);
  }

  @NotNull
  protected String getTitle() {
    return myTitle;
  }

  @Override
  public void init() {
    myState.put(KEY_TITLE, myTitle);
    myState.put(KEY_MESSAGE, myMessage);
    register(KEY_DESCRIPTION, getDescriptionText(), new ComponentBinding<String, JLabel>() {
      @Override
      public void setValue(String newValue, @NotNull JLabel component) {
        setDescriptionText(newValue);
      }
    });
    register(KEY_TITLE, myTitleLabel, new ComponentBinding<String, JBLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JBLabel component) {
        component.setText(newValue);
      }
    });
    register(KEY_MESSAGE, myMessageLabel, new ComponentBinding<String, JLabel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull JLabel component) {
        component.setVisible(!StringUtil.isEmpty(newValue));
        component.setText(ImportUIUtil.makeHtmlString(newValue));
      }
    });
  }

  /**
   * Subclasses may override this method if they want to provide a custom description label.
   */
  protected JLabel getDescriptionText() {
    return myDescriptionText;
  }

  protected final void setDescriptionText(@Nullable String templateDescription) {
    getDescriptionText().setText(ImportUIUtil.makeHtmlString(templateDescription));
  }

  @Nullable
  protected JBColor getTitleBackgroundColor() {
    return null;
  }

  @Nullable
  protected JBColor getTitleTextColor() {
    return null;
  }

  @Nullable
  protected JComponent getHeader() {
    return null;
  }

  @NotNull
  @Override
  public final JComponent getComponent() {
    return myRootPane;
  }

  @NotNull
  @Override
  public final JBLabel getMessageLabel() {
    return myErrorWarningLabel;
  }

  @Nullable
  protected Disposable getDisposable() {
    return myDisposable;
  }
}
