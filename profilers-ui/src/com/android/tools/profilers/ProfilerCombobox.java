/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers;

import com.intellij.ide.ui.laf.darcula.ui.DarculaComboBoxUI;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import java.awt.Component;
import java.awt.Graphics;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.ComboPopup;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ComboBox} used across multiple profilers UI, such as memory and CPU.
 * TODO(b/117083308): unify this class with {@link com.android.tools.adtui.stdui.CommonComboBox}.
 */
public class ProfilerCombobox<T> extends ComboBox<T> {

  public ProfilerCombobox() {
    super();
  }

  public ProfilerCombobox(@NotNull ComboBoxModel<T> model) {
    super(model);
  }

  @Override
  public void setUI(ComboBoxUI ui) {
    if (ApplicationManager.getApplication() != null && !ApplicationManager.getApplication().isUnitTestMode()) {
      // We hardcode the ComboBoxUI here because IntelliJ's default LAF will draw a 2px black border around the Combobox Popup. We need this
      // border to be a 1px gray one, and that's exactly what we define in ProfilerDarculaComboBoxUI.
      // Note: forcing the Darcula UI does not imply dark colors.
      ui = new ProfilerDarculaComboBoxUI();
    }
    super.setUI(ui);
  }

  /**
   * Custom {@link ComboBoxUI} to be user by instances of {@link ProfilerCombobox}. It's essentially a {@link DarculaComboBoxUI},
   * used by IntelliJ, with a different popup border color and thickness.
   */
  private static class ProfilerDarculaComboBoxUI extends DarculaComboBoxUI implements Border {

    @Override
    protected ComboPopup createPopup() {
      return new ProfilerCustomComboPopup(comboBox);
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      // Unset the JComboBox.isTableCellEditor before calling parent's paintBorder because it checks for this property and doesn't draw the
      // line around the border if it's set. We need this line to be drawn, and the reason to set this property to true afterwards is
      // explained below.
      comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.FALSE);
      super.paintBorder(c, g, x, y, width, height);
      // This disables firing actions like setSelectedItem when the user is using keyboard to navigate through the combobox menu.
      // That's the behavior of IntelliJ comboboxes. For instance, if we have a combobox entry that triggers a popup (e.g. "Settings..."),
      // we shouldn't trigger it while navigating with the keyboard, but only when actually selecting it.
      comboBox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);
    }

    private static class ProfilerCustomComboPopup extends CustomComboPopup {

      public ProfilerCustomComboPopup(JComboBox combo) {
        super(combo);
      }

      @Override
      protected void configurePopup() {
        super.configurePopup();
        setBorder(new LineBorder(ProfilerColors.COMBOBOX_BORDER, 1));
      }
    }
  }
}
