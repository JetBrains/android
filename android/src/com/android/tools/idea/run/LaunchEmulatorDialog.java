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
package com.android.tools.idea.run;

import com.android.sdklib.repositoryv2.IdDisplay;
import com.intellij.facet.Facet;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class LaunchEmulatorDialog extends DialogWrapper {

  @NonNls private static final String SELECTED_AVD_PROPERTY = "ANDROID_EXTENDED_DEVICE_CHOOSER_AVD";

  private final Facet myFacet;
  private JPanel myPanel;
  private JLabel myAvdLabel;
  private JPanel myComboBoxWrapper;
  private final AvdComboBox myAvdCombo;


  public LaunchEmulatorDialog(@NotNull Facet facet) {
    super(facet.getModule().getProject(), true, IdeModalityType.PROJECT);

    myFacet = facet;
    Project project = myFacet.getModule().getProject();

    setTitle("Launch Emulator");

    myAvdCombo = new AvdComboBox(project, false, true) {
      @Override
      public Module getModule() {
        return myFacet.getModule();
      }
    };
    Disposer.register(myDisposable, myAvdCombo);

    myAvdCombo.getComboBox().setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          append(AndroidBundle.message("android.ddms.nodevices"),
                 myAvdCombo.getComboBox().isEnabled()
                 ? SimpleTextAttributes.ERROR_ATTRIBUTES
                 : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          append(((IdDisplay)value).getDisplay());
        }
      }
    });
    myComboBoxWrapper.add(myAvdCombo);
    myAvdLabel.setLabelFor(myAvdCombo);

    init();

    myAvdCombo.startUpdatingAvds(ModalityState.stateForComponent(myPanel));
    final String savedAvd = PropertiesComponent.getInstance(project).getValue(SELECTED_AVD_PROPERTY);
    String avdToSelect = null;
    if (savedAvd != null) {
      final ComboBoxModel model = myAvdCombo.getComboBox().getModel();
      for (int i = 0, n = model.getSize(); i < n; i++) {
        final IdDisplay item = (IdDisplay)model.getElementAt(i);
        final String id = item == null? null : item.getId();
        if (savedAvd.equals(id)) {
          avdToSelect = id;
          break;
        }
      }
    }
    if (avdToSelect != null) {
      myAvdCombo.getComboBox().setSelectedItem(IdDisplay.create(avdToSelect, ""));
    }
    else if (myAvdCombo.getComboBox().getModel().getSize() > 0) {
      myAvdCombo.getComboBox().setSelectedIndex(0);
    }

    myAvdCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        getOKAction().setEnabled(getSelectedAvd() != null);
      }
    });
  }

  @Nullable
  public String getSelectedAvd() {
    IdDisplay value = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();
    return value == null ? null : value.getId();
  }

  @Override
  protected void doOKAction() {
    final PropertiesComponent properties = PropertiesComponent.getInstance(myFacet.getModule().getProject());
    final IdDisplay selectedAvd = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();
    if (selectedAvd != null) {
      properties.setValue(SELECTED_AVD_PROPERTY, selectedAvd.getId());
    }
    else {
      properties.unsetValue(SELECTED_AVD_PROPERTY);
    }
    super.doOKAction();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

}
