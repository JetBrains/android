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
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
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
  private static class ProfilerDarculaComboBoxUI extends DarculaComboBoxUI {

    @Override
    protected ComboPopup createPopup() {
      return new ProfilerCustomComboPopup(this.comboBox);
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
