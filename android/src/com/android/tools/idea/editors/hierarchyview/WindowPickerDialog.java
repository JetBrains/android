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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBProgressBar;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class WindowPickerDialog extends DialogWrapper {
  @NonNls private static final String WINDOW_PICKER_DIMENSIONS_KEY = "HierarchyViewer.WindowPicker.Options.Dimensions";

  private final JPanel myPanel;

  private final JBProgressBar myProgressBar;
  private final JComboBox myWindowList;

  @Nullable ClientWindow mySelectedWindow;

  public WindowPickerDialog(@NotNull Project project, @NotNull final Client client) {
    super(project, true);
    setTitle(AndroidBundle.message("android.ddms.actions.hierarchyview.windowpicker"));

    myPanel = new JPanel(new BorderLayout());

    myProgressBar = new JBProgressBar();
    myProgressBar.setIndeterminate(true);
    myPanel.add(myProgressBar, BorderLayout.CENTER);

    myWindowList = new JComboBox();
    myWindowList.setRenderer(new WindowComboBoxRenderer());

    init();
    getOKAction().setEnabled(false);

    // Load active windows
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        final List<ClientWindow> windows = ClientWindow.getAll(client, 2, TimeUnit.SECONDS);
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            setActiveWindows(windows);
          }
        });
      }
    });
  }

  private void setActiveWindows(@Nullable List<ClientWindow> windows) {
    if (windows == null) {
      myWindowList.addItem("Error loading windows");
    } else if (windows.isEmpty()) {
      myWindowList.addItem("No active window");
    } else {
      for (ClientWindow window : windows) {
        myWindowList.addItem(window);
      }
      getOKAction().setEnabled(true);
    }

    myPanel.remove(myProgressBar);
    myPanel.add(myWindowList, BorderLayout.CENTER);
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
    Object selection = myWindowList.getSelectedItem();
    if (selection instanceof ClientWindow) {
      mySelectedWindow = (ClientWindow) selection;
    }
    super.doOKAction();
  }

  @Nullable
  public ClientWindow getSelectedWindow() {
    return mySelectedWindow;
  }

  private static class WindowComboBoxRenderer extends ColoredListCellRenderer {

    @Override
    protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
      if (value instanceof ClientWindow) {
        append(((ClientWindow) value).getDisplayName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
      } else if (value instanceof String){
        append((String) value, SimpleTextAttributes.ERROR_ATTRIBUTES);
      }
    }
  }
}
