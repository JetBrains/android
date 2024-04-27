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
package com.android.tools.idea.uibuilder.handlers.constraint;

import com.android.resources.ResourceType;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog;
import com.intellij.openapi.module.Module;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.ui.JBUI;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.util.Arrays;
import java.util.EnumSet;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Widget to support margin editing on the ui
 */
public class MarginWidget extends JComboBox<String> {
  private static final String POPUP_MENU = "@ ...";
  private static final String DEFAULT = "0";
  private static final String PICK_A_DIMENSION = "Pick a Dimension";
  private static final String[] MENU_LIST = new String[]{DEFAULT, "8", "16", "24", "32", POPUP_MENU};
  private final String myBaseToolTipText;

  private final JTextField myTextField;
  public enum Show {
    IN_WIDGET,
    OUT_WIDGET,
    OUT_PANEL
  }

  public MarginWidget(@NotNull String name, String tooltip) {
    super(new CollectionComboBoxModel<>(Arrays.asList(MENU_LIST)));
    setBackground(StudioColorsKt.getSecondaryPanelBackground());
    setEditable(true);
    myTextField = (JTextField)getEditor().getEditorComponent();
    myTextField.setFont(myTextField.getFont().deriveFont((float)JBUI.scaleFontSize(12f)));
    myTextField.addFocusListener(new ScrollToViewFocusListener(this));
    initComboBox(name);
    setName(name);
    setToolTipText(tooltip);
    myBaseToolTipText = tooltip;
  }

  private void italicFont() {
    Font font = myTextField.getFont();
    int style = font.getStyle();
    style |= Font.ITALIC;
    font = font.deriveFont(style);
    myTextField.setFont(font);
  }

  private void normalFont() {
    Font font = myTextField.getFont();
    int style = font.getStyle();
    style &= ~Font.ITALIC;
    font = font.deriveFont(style);
    myTextField.setFont(font);
  }

  private void updateToolTip(@Nullable String resourceName) {
    if (resourceName == null) {
      setToolTipText(myBaseToolTipText);
      return;
    }

    setToolTipText(myBaseToolTipText + " (" + resourceName + ")");
  }

  private void initComboBox(@NotNull String name) {
    setAlignmentX(RIGHT_ALIGNMENT);
    setEditable(true);
    setName(name + "ComboBox");
  }

  public void setMargin(int margin) {
    String marginText = String.valueOf(margin);
    if(getSelectedItem().equals(marginText)) return;
    setSelectedItem(marginText);
  }

  /**
   * @return margin either in DP (e.g. "0") or resource (e.g. "@dimen/left_margin")
   */
  public String getMargin(@Nullable NlComponent component) {
    String item = (String)getSelectedItem();
    String toReturn = item != null ? item : DEFAULT;

    if (POPUP_MENU.equals(toReturn)) {
      toReturn = selectFromResourceDialog(component);
    }

    if (toReturn.startsWith("@")) {
      italicFont();
      updateToolTip(toReturn);
    } else {
      normalFont();
      updateToolTip(null);
    }

    return toReturn;
  }

  /**
   * @return Launch resource dialog, and return the chosen value (e.g. "@dimen/left_margin". {@link DEFAULT} if cancelled or tag invalid.
   */
  @NotNull
  private String selectFromResourceDialog(@Nullable NlComponent component) {
    if (component == null) {
      return DEFAULT;
    }

    Module module = component.getModel().getModule();
    XmlTag tag = component.getBackend().getTag();
    if (tag == null) {
      return DEFAULT;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    assert facet != null;

    ResourcePickerDialog dialog = ResourceChooserHelperKt.createResourcePickerDialog(
      PICK_A_DIMENSION,
      null,
      facet,
      EnumSet.of(ResourceType.DIMEN),
      null,
      true,
      false,
      true,
      tag.getContainingFile().getVirtualFile()
    );

    if (dialog.showAndGet()) {
      String pickedResourceName = dialog.getResourceName();
      if (pickedResourceName != null) {
        return pickedResourceName;
      }
    }

    return DEFAULT;
  }

  @Override
  public void setSelectedItem(Object anObject) {
    if(anObject != null && anObject.equals(getSelectedItem())) {
      return;
    }
    super.setSelectedItem(anObject);
    if(hasFocus()) requestFocusInWindow();
  }
}