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
package com.android.tools.idea.editors.hierarchyview;

import com.android.ddmlib.Client;
import com.android.tools.idea.editors.hierarchyview.model.ClientWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class WindowPickerDialog extends DialogWrapper {
  @NonNls private static final String WINDOW_PICKER_DIMENSIONS_KEY = "HierarchyViewer.WindowPicker.Options.Dimensions";

  private final JPanel myPanel;
  private final JComboBox myWindowsCombo;

  @Nullable ClientWindow mySelectedWindow;

  public WindowPickerDialog(@NotNull Project project, @NotNull final Client client, @NotNull List<ClientWindow> windows) {
    super(project, true);
    setTitle(AndroidBundle.message("android.ddms.actions.hierarchyview.windowpicker"));

    myPanel = new JPanel(new BorderLayout());

    myWindowsCombo = new ComboBox(new CollectionComboBoxModel<ClientWindow>(windows));
    myWindowsCombo.setRenderer(new ClientWindowRenderer());
    myWindowsCombo.setSelectedIndex(0);
    myPanel.add(myWindowsCombo, BorderLayout.CENTER);

    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected String getDimensionServiceKey() {
    return WINDOW_PICKER_DIMENSIONS_KEY;
  }

  @Override
  protected void doOKAction() {
    Object selection = myWindowsCombo.getSelectedItem();
    if (selection instanceof ClientWindow) {
      mySelectedWindow = (ClientWindow) selection;
    }
    super.doOKAction();
  }

  @Nullable
  public ClientWindow getSelectedWindow() {
    return mySelectedWindow;
  }

  private static class ClientWindowRenderer extends ColoredListCellRenderer {
    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      assert value instanceof ClientWindow;
      append(((ClientWindow)value).getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
  }
}
