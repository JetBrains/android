/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.ui;

import com.android.tools.idea.editors.theme.ThemeEditorConstants;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.ui.resourcechooser.ResourceSwatchComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Component for displaying a color or a drawable resource with attribute name, type and value text.
 */
public class ResourceComponent extends JPanel {
  public static final String NAME_LABEL = "Name Label";

  private final ResourceSwatchComponent myResourceSwatchComponent;
  private final JLabel myNameLabel = new JLabel();
  protected final JLabel myWarningLabel = new JLabel();

  private final VariantsComboBox myVariantCombo = new VariantsComboBox();

  public ResourceComponent(@NotNull Project project, boolean isEditor) {
    super(new BorderLayout(0, ThemeEditorConstants.ATTRIBUTE_ROW_GAP));
    setBorder(BorderFactory.createEmptyBorder(ThemeEditorConstants.ATTRIBUTE_MARGIN / 2, 0, ThemeEditorConstants.ATTRIBUTE_MARGIN / 2, 0));

    myWarningLabel.setIcon(AllIcons.General.BalloonWarning);
    myWarningLabel.setVisible(false);

    myNameLabel.setName(NAME_LABEL);
    myNameLabel.setForeground(ThemeEditorConstants.RESOURCE_ITEM_COLOR);

    Box topRowPanel = new Box(BoxLayout.LINE_AXIS);
    topRowPanel.add(myNameLabel);
    topRowPanel.add(myWarningLabel);

    myVariantCombo.setVisible(false);

    topRowPanel.add(Box.createHorizontalGlue());
    topRowPanel.add(myVariantCombo);
    add(topRowPanel, BorderLayout.CENTER);

    myResourceSwatchComponent = new ResourceSwatchComponent(project, isEditor);
    add(myResourceSwatchComponent, BorderLayout.SOUTH);

    ThemeEditorUtils.setInheritsPopupMenuRecursive(this);
  }

  @Override
  public Dimension getPreferredSize() {
    if (!isPreferredSizeSet()) {
      int firstRowHeight = Math.max(getFontMetrics(getFont()).getHeight(), myVariantCombo.getPreferredSize().height);
      int secondRowHeight = myResourceSwatchComponent.getPreferredSize().height;

      return new Dimension(0, ThemeEditorConstants.ATTRIBUTE_MARGIN +
                              ThemeEditorConstants.ATTRIBUTE_ROW_GAP +
                              firstRowHeight +
                              secondRowHeight);
    }

    return super.getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    if (isMaximumSizeSet()) {
      return super.getMaximumSize();
    }
    return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
  }

  public void setSwatchIcon(@NotNull ResourceSwatchComponent.SwatchIcon icon) {
    myResourceSwatchComponent.setSwatchIcon(icon);
  }

  public void setNameText(@NotNull String name) {
    myNameLabel.setText(name);
  }

  public void setWarning(@Nullable String warning) {
    if (!StringUtil.isEmpty(warning)) {
      myWarningLabel.setToolTipText(warning);
      myWarningLabel.setVisible(true);
    }
    else {
      myWarningLabel.setVisible(false);
    }
  }

  public void setVariantsModel(@NotNull ComboBoxModel comboBoxModel) {
    myVariantCombo.setModel(comboBoxModel);
    myVariantCombo.setVisible(comboBoxModel.getSize() > 0);
  }

  public void addVariantItemListener(@NotNull ItemListener itemListener) {
    myVariantCombo.addItemListener(itemListener);
  }

  public void addVariantPopupClosingListener(@NotNull VariantsComboBox.PopupClosingListener listener) {
    myVariantCombo.addPopupClosingListener(listener);
  }

  public void setValueText(@NotNull String value) {
    myResourceSwatchComponent.setText(value);
  }

  @NotNull
  public String getValueText() {
    return myResourceSwatchComponent.getText();
  }

  @Override
  public void setFont(final Font font) {
    super.setFont(font);
    // We need a null check here as this can be called from the setUI that's called from the constructor.
    if (myResourceSwatchComponent != null) {
      myResourceSwatchComponent.setFont(font);
    }
    if (myNameLabel != null) {
      myNameLabel.setFont(font);
    }
  }

  public void addSwatchListener(@NotNull final ActionListener listener) {
    myResourceSwatchComponent.addSwatchListener(listener);
  }

  public void addTextListener(@NotNull final ActionListener listener) {
    myResourceSwatchComponent.addTextListener(listener);
  }

  public void addTextDocumentListener(@NotNull final DocumentListener listener) {
    myResourceSwatchComponent.addTextDocumentListener(listener);
  }

  public void addTextFocusListener(@NotNull final FocusListener listener) {
    myResourceSwatchComponent.addTextFocusListener(listener);
  }

  public void setCompletionStrings(@NotNull List<String> completions) {
    myResourceSwatchComponent.setCompletionStrings(completions);
  }

  @Override
  public String getToolTipText(MouseEvent event) {
    if (myWarningLabel.isVisible()) {
      validate();
      if (SwingUtilities.getLocalBounds(myWarningLabel)
        .contains(SwingUtilities.convertMouseEvent(this, event, myWarningLabel).getPoint())) {
        return myWarningLabel.getToolTipText();
      }
    }
    return super.getToolTipText(event);
  }

  /**
   * Returns the current swatch icon size in pixels.
   */
  public Dimension getSwatchIconSize() {
    // Since the icons are square we just use the height of the component.
    return new Dimension(myResourceSwatchComponent.getHeight(), myResourceSwatchComponent.getHeight());
  }

  @Nullable/*if there is no error*/
  public ValidationInfo doValidate(int minApi, @NotNull AndroidTargetData androidTargetData) {
    return myResourceSwatchComponent.doValidate(minApi, androidTargetData);
  }
}
