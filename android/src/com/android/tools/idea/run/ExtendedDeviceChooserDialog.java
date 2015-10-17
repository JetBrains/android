/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Alarm;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExtendedDeviceChooserDialog extends DialogWrapper {
  private final Project myProject;
  private final DeviceChooser myDeviceChooser;

  private JPanel myPanel;
  private JRadioButton myChooserRunningDeviceRadioButton;
  private JPanel myDeviceChooserWrapper;
  private JCheckBox myReuseSelectionCheckbox;
  private JRadioButton myLaunchEmulatorRadioButton;
  private final AvdComboBox myAvdCombo;
  private JLabel myAvdLabel;
  private JPanel myComboBoxWrapper;

  @NonNls private static final String SELECTED_AVD_PROPERTY = "ANDROID_EXTENDED_DEVICE_CHOOSER_AVD";
  @NonNls private static final String SELECTED_SERIALS_PROPERTY = "ANDROID_EXTENDED_DEVICE_CHOOSER_SERIALS";


  public ExtendedDeviceChooserDialog(@NotNull final AndroidFacet facet,
                                     @NotNull IAndroidTarget projectTarget,
                                     boolean multipleSelection,
                                     boolean showReuseDevicesCheckbox,
                                     boolean selectReuseDevicesCheckbox) {
    super(facet.getModule().getProject(), true, IdeModalityType.PROJECT);

    setTitle(AndroidBundle.message("choose.device.dialog.title"));

    myProject = facet.getModule().getProject();
    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);

    final String[] selectedSerials;
    final String serialsStr = properties.getValue(SELECTED_SERIALS_PROPERTY);
    if (serialsStr != null) {
      selectedSerials = serialsStr.split(" ");
    }
    else {
      selectedSerials = null;
    }

    getOKAction().setEnabled(false);

    myDeviceChooser = new DeviceChooser(multipleSelection, getOKAction(), facet, projectTarget, null);
    Disposer.register(myDisposable, myDeviceChooser);
    myDeviceChooser.addListener(new DeviceChooserListener() {
      @Override
      public void selectedDevicesChanged() {
        myLaunchEmulatorRadioButton.setSelected(!myDeviceChooser.hasDevices());
        myChooserRunningDeviceRadioButton.setSelected(myDeviceChooser.hasDevices());
        updateEnabled();
      }
    });

    myAvdCombo = new AvdComboBox(myProject, false, true) {
      @Override
      public Module getModule() {
        return facet.getModule();
      }
    };
    Disposer.register(myDisposable, myAvdCombo);

    myAvdCombo.getComboBox().setRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value == null) {
          append(AndroidBundle.message("android.ddms.nodevices"),
                 myAvdCombo.getComboBox().isEnabled() ? SimpleTextAttributes.ERROR_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          append(((IdDisplay)value).getDisplay());
        }
      }
    });
    myComboBoxWrapper.add(myAvdCombo);
    myAvdLabel.setLabelFor(myAvdCombo);
    myDeviceChooserWrapper.add(myDeviceChooser.getPanel());

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabled();
      }
    };
    myLaunchEmulatorRadioButton.addActionListener(listener);
    myChooserRunningDeviceRadioButton.addActionListener(listener);
    myAvdCombo.getComboBox().addActionListener(listener);

    init();

    myDeviceChooser.init(selectedSerials);

    myLaunchEmulatorRadioButton.setSelected(!myDeviceChooser.hasDevices());
    myChooserRunningDeviceRadioButton.setSelected(myDeviceChooser.hasDevices());

    myAvdCombo.startUpdatingAvds(ModalityState.stateForComponent(myPanel));
    final String savedAvd = PropertiesComponent.getInstance(myProject).getValue(SELECTED_AVD_PROPERTY);
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
      myAvdCombo.getComboBox().setSelectedItem(new IdDisplay(avdToSelect, ""));
    }
    else if (myAvdCombo.getComboBox().getModel().getSize() > 0) {
      myAvdCombo.getComboBox().setSelectedIndex(0);
    }

    myReuseSelectionCheckbox.setVisible(showReuseDevicesCheckbox);
    myReuseSelectionCheckbox.setSelected(selectReuseDevicesCheckbox);

    updateEnabled();
    initValidation();
  }

  private void updateOkButton() {
    if (myLaunchEmulatorRadioButton.isSelected()) {
      getOKAction().setEnabled(getSelectedAvd() != null);
    }
    else {
      for (IDevice selectedDevice : getSelectedDevices()) {
        if (!selectedDevice.isOffline()) {
          getOKAction().setEnabled(true);
          break;
        }
      }
    }
  }

  private void updateEnabled() {
    myAvdCombo.setEnabled(myLaunchEmulatorRadioButton.isSelected());
    myAvdLabel.setEnabled(myLaunchEmulatorRadioButton.isSelected());
    myDeviceChooser.setEnabled(myChooserRunningDeviceRadioButton.isSelected());
    updateOkButton();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myDeviceChooser.getPreferredFocusComponent();
  }

  @Nullable
  @Override
  protected String getHelpId() {
    return "reference.android.chooseDevice";
  }

  @Override
  protected void doOKAction() {
    myDeviceChooser.finish();

    final PropertiesComponent properties = PropertiesComponent.getInstance(myProject);
    properties.setValue(SELECTED_SERIALS_PROPERTY, DeviceSelectionUtils.serialize(getSelectedDevices()));

    final IdDisplay selectedAvd = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();
    if (selectedAvd != null) {
      properties.setValue(SELECTED_AVD_PROPERTY, selectedAvd.getId());
    }
    else {
      properties.unsetValue(SELECTED_AVD_PROPERTY);
    }

    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidExtendedDeviceChooserDialog";
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    return myDeviceChooser.doValidate();
  }

  @NotNull
  @Override
  protected Alarm.ThreadToUse getValidationThreadToUse() {
    // The default swing thread doesn't work for some reason - the doValidate method is never called.
    return Alarm.ThreadToUse.POOLED_THREAD;
  }

  @NotNull
  public IDevice[] getSelectedDevices() {
    return myDeviceChooser.getSelectedDevices();
  }

  @Nullable
  public String getSelectedAvd() {
    IdDisplay value = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();
    return value == null ? null : value.getId();
  }

  public boolean isToLaunchEmulator() {
    return myLaunchEmulatorRadioButton.isSelected();
  }

  public boolean useSameDevicesAgain() {
    return myReuseSelectionCheckbox.isSelected();
  }
}
