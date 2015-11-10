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

import com.android.tools.idea.ui.ImageComponent;
import com.android.tools.idea.ui.wizard.StudioWizardLayout;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Wizard header component
 *
 * @deprecated Replaced by {@link StudioWizardLayout}.
 */
public class WizardStepHeaderPanel extends JPanel {
  @NotNull String myTitle = "Title Label";
  @Nullable String myDescription;
  @Nullable Icon myWizardIcon;
  @Nullable Icon myStepIcon;
  @Nullable private JLabel myTitleLabel;
  @NotNull private ComponentHolder<String, JLabel> myDescriptionLabel = new LabelHolder();
  @NotNull private ComponentHolder<Icon, ImageComponent> myWizardIconComponent = new ImageComponentHolder();
  @NotNull private ComponentHolder<Icon, ImageComponent> myStepIconComponent = new ImageComponentHolder();

  public WizardStepHeaderPanel() {
    setBorder(new EmptyBorder(WizardConstants.STUDIO_WIZARD_INSETS));
    //noinspection UseJBColor
    setForeground(Color.WHITE);
    setBackground(WizardConstants.ANDROID_NPW_HEADER_COLOR);
    updateHeader();
  }

  private static GridConstraints createHeaderLabelGridConstraints(int row, int column, int anchor) {
    return new GridConstraints(row, column, 1, 1, anchor, GridConstraints.FILL_HORIZONTAL,
                               GridConstraints.SIZEPOLICY_CAN_GROW | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED,
                               null, null, null);
  }

  public static WizardStepHeaderPanel create(@NotNull final JBColor headerColor, @Nullable Icon wizardIcon, @Nullable Icon stepIcon,
                              @NotNull String title, @Nullable String description) {
    final WizardStepHeaderPanel panel = new WizardStepHeaderPanel();
    panel.setBackground(headerColor);
    panel.setTitle(title);
    panel.setDescription(description);
    panel.setStepIcon(stepIcon);
    panel.setWizardIcon(wizardIcon);
    UIManager.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
        // Force an update of static JBColor.DARK. This is required to show the correct color after a LookAndFeel change.
        JBColor.setDark(UIUtil.isUnderDarcula());
        panel.setBackground(headerColor);

        // The font size was not set correctly after a LookAndFeel change from Darcula to Standard.
        Font font = UIManager.getFont("Label.font");
        panel.myTitleLabel.setFont(new Font(font.getFontName(), font.getStyle(), 24));
      }
    });
    return panel;
  }

  public void setTitle(@Nullable String title) {
    myTitle = StringUtil.notNullize(title, "Title Label");
    updateHeader();
  }

  public void setDescription(@Nullable String description) {
    myDescription = description;
    updateHeader();
  }

  public void setStepIcon(@Nullable Icon stepIcon) {
    myStepIcon = stepIcon;
    updateHeader();
  }

  public void setWizardIcon(@Nullable Icon wizardIcon) {
    myWizardIcon = wizardIcon;
    updateHeader();
  }

  private void updateHeader() {
    boolean updateLayout = false;
    if (myTitleLabel == null) {
      myTitleLabel = new JLabel(myTitle);
      updateLayout = true;
    }
    myTitleLabel.setText(myTitle);

    updateLayout |= myDescriptionLabel.updateValue(StringUtil.nullize(myDescription, true)) ||
                    myWizardIconComponent.updateValue(myWizardIcon) ||
                    myStepIconComponent.updateValue(myStepIcon);

    if (updateLayout) {
      final int rows = myDescriptionLabel.getComponent() == null ? 1 : 2;
      final int columns = 1 + (myWizardIconComponent.getComponent() == null ? 0 : 1) + (myStepIconComponent.getComponent() == null ? 0 : 1);
      for (Component component : getComponents()) {
        remove(component);
      }
      setLayout(new GridLayoutManager(rows, columns, new Insets(18, 0, 12, 0), 2, 2));
      int currentColumn = addIconIfExists(myWizardIconComponent.getComponent(), 0, rows) ? 1 : 0;
      addLabels(myTitleLabel, myDescriptionLabel.getComponent(), currentColumn);
      addIconIfExists(myStepIconComponent.getComponent(), currentColumn + 1, rows);
    }
  }

  private void addLabels(@NotNull JLabel titleLabel, @Nullable JLabel descriptionLabel, int column) {
    boolean hasDescription = descriptionLabel != null;
    int anchor = hasDescription ? GridConstraints.ANCHOR_SOUTHWEST : GridConstraints.ANCHOR_WEST;
    titleLabel.setForeground(getForeground());
    titleLabel.setFont(titleLabel.getFont().deriveFont(JBUI.scale(24f)));
    add(titleLabel, createHeaderLabelGridConstraints(0, column, anchor));
    if (hasDescription) {
      descriptionLabel.setForeground(getForeground());
      add(descriptionLabel, createHeaderLabelGridConstraints(1, column, GridConstraints.ANCHOR_NORTHWEST));
    }
  }

  private boolean addIconIfExists(@Nullable ImageComponent iconComponent, int column, int spanningRows) {
    if (iconComponent != null) {
      GridConstraints imageConstraints =
        new GridConstraints(0, column, spanningRows, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, JBUI.size(60, 60), null);
      add(iconComponent, imageConstraints);
      return true;
    }
    else {
      return false;
    }
  }

  private static abstract class ComponentHolder<V, C extends JComponent> {
    @Nullable private C myComponent;

    public final boolean updateValue(@Nullable V value) {
      boolean updateLayout;
      if (value == null) {
        updateLayout = myComponent != null;
        myComponent = null;
      }
      else {
        if (myComponent == null) {
          updateLayout = true;
          myComponent = createComponent();
        }
        else {
          updateLayout = false;
        }
        setValue(myComponent, value);
      }
      return updateLayout;
    }

    protected abstract void setValue(@NotNull C component, @NotNull V value);

    @NotNull
    protected abstract C createComponent();

    @Nullable
    public final C getComponent() {
      return myComponent;
    }
  }

  private static class ImageComponentHolder extends ComponentHolder<Icon, ImageComponent> {
    @Override
    protected void setValue(@NotNull ImageComponent component, @NotNull Icon value) {
      component.setIcon(value);
    }

    @NotNull
    @Override
    protected ImageComponent createComponent() {
      return new ImageComponent();
    }
  }

  private static class LabelHolder extends ComponentHolder<String, JLabel> {
    @Override
    protected void setValue(@NotNull JLabel component, @NotNull String value) {
      component.setText(value);
    }

    @NotNull
    @Override
    protected JLabel createComponent() {
      return new JLabel();
    }
  }
}
