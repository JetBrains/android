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
package com.android.tools.idea.wizard.dynamic;

import static com.android.tools.idea.wizard.WizardConstants.STUDIO_WIZARD_INSETS;
import static com.android.tools.idea.wizard.WizardConstants.STUDIO_WIZARD_INSET_SIZE;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.createKey;

import com.android.tools.adtui.util.FormScalingUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.beans.PropertyChangeListener;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Base class for wizard steps that have a description label in the bottom.
 * One of the facilities provided by this class is tracking currently focused
 * component and displaying its description.</p>
 * <p>Subclasses should call {@link #setBodyComponent(JComponent)}
 * from the constructor.</p>
 */
public abstract class DynamicWizardStepWithDescription extends DynamicWizardStep implements Disposable {
  protected static final ScopedStateStore.Key<String> KEY_DESCRIPTION =
    createKey(DynamicWizardStepWithDescription.class + ".description", ScopedStateStore.Scope.STEP, String.class);

  private static final String PROPERTY_FOCUS_OWNER = "focusOwner";

  @Nullable private final Disposable myDisposable;
  private PropertyChangeListener myFocusListener;
  private JPanel myRootPane;
  private JLabel myDescriptionLabel;
  private JBLabel myErrorWarningLabel;
  private JPanel mySouthPanel;
  private Map<Component, String> myControlDescriptions = new WeakHashMap<Component, String>();

  public DynamicWizardStepWithDescription(@Nullable Disposable parentDisposable) {
    setupUI();
    myDisposable = parentDisposable;
    if (parentDisposable != null) {
      Disposer.register(parentDisposable, this);
    }
    mySouthPanel.setBorder(new EmptyBorder(STUDIO_WIZARD_INSETS));
    myErrorWarningLabel.setForeground(JBColor.red);
    // Set to BLANK. If completely empty the height calculation is off and window resizing results.
    myDescriptionLabel.setText(DynamicWizardStep.BLANK);
  }

  private void setupUI() {
    myRootPane = new JPanel();
    myRootPane.setLayout(new BorderLayout(0, 0));
    mySouthPanel = new JPanel();
    mySouthPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    myRootPane.add(mySouthPanel, BorderLayout.SOUTH);
    myDescriptionLabel = new JLabel();
    myDescriptionLabel.setText("");
    mySouthPanel.add(myDescriptionLabel, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myErrorWarningLabel = new JBLabel();
    myErrorWarningLabel.setText("");
    mySouthPanel.add(myErrorWarningLabel, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                              GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                              null, null, 0, false));
  }

  @NotNull
  @Override
  protected JPanel createStepBody() {
    return myRootPane;
  }

  @Override
  public void dispose() {
    if (myFocusListener != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(PROPERTY_FOCUS_OWNER, myFocusListener);
    }
  }

  protected final void setBodyComponent(JComponent component) {
    FormScalingUtil.scaleComponentTree(this.getClass(), component);
    component.setBorder(
      new EmptyBorder(new Insets(STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE, STUDIO_WIZARD_INSET_SIZE)));
    myRootPane.add(component, BorderLayout.CENTER);
  }

  @Override
  public void init() {
    register(KEY_DESCRIPTION, getDescriptionLabel(), new ComponentBinding<String, JLabel>() {
      @Override
      public void setValue(String newValue, @NotNull JLabel label) {
        label.setText(toHtml(newValue));
      }
    });
  }

  /**
   * Subclasses may override this method if they want to provide a custom description label.
   */
  protected JLabel getDescriptionLabel() {
    return myDescriptionLabel;
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
