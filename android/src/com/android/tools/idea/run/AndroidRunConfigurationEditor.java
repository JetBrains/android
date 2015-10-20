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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.internal.avd.AvdManager;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.base.Predicate;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.ui.PanelWithAnchor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ThreeState;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor {
  private final Project myProject;

  private JPanel myPanel;
  protected JBTabbedPane myTabbedPane;
  private JComboBox myModulesComboBox;
  private JPanel myConfigurationSpecificPanel;
  private JBLabel myModuleJBLabel;
  private JRadioButton myShowChooserRadioButton;
  private JRadioButton myEmulatorRadioButton;
  private JRadioButton myUsbDeviceRadioButton;
  private ComboboxWithBrowseButton myAvdComboComponent;
  private JBLabel myMinSdkInfoMessageLabel;
  private JCheckBox myUseLastSelectedDeviceCheckBox;

  // Misc. options tab
  private JCheckBox myClearLogCheckBox;
  private JCheckBox myShowLogcatCheckBox;
  private JCheckBox mySkipNoOpApkInstallation;
  private JCheckBox myForceStopRunningApplicationCheckBox;

  private AvdComboBox myAvdCombo;
  private String incorrectPreferredAvd;
  private JComponent anchor;

  private final ConfigurationModuleSelector myModuleSelector;
  private ConfigurationSpecificEditor<T> myConfigurationSpecificEditor;

  public void setConfigurationSpecificEditor(ConfigurationSpecificEditor<T> configurationSpecificEditor) {
    myConfigurationSpecificEditor = configurationSpecificEditor;
    myConfigurationSpecificPanel.add(configurationSpecificEditor.getComponent());
    setAnchor(myConfigurationSpecificEditor.getAnchor());
    myShowLogcatCheckBox.setVisible(configurationSpecificEditor instanceof ApplicationRunParameters);
  }

  public AndroidRunConfigurationEditor(final Project project, final Predicate<AndroidFacet> libraryProjectValidator) {
    myProject = project;
    $$$setupUI$$$(); // Create UI components after myProject is available. Also see https://youtrack.jetbrains.com/issue/IDEA-67765

    myModuleSelector = new ConfigurationModuleSelector(project, myModulesComboBox) {
      @Override
      public boolean isModuleAccepted(Module module) {
        if (module == null || !super.isModuleAccepted(module)) {
          return false;
        }

        final AndroidFacet facet = AndroidFacet.getInstance(module);
        if (facet == null) {
          return false;
        }

        return !facet.isLibraryProject() || libraryProjectValidator.apply(facet);
      }
    };

    myAvdCombo.getComboBox().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        final String warning = myEmulatorRadioButton.isSelected() ? getAvdCompatibilityWarning() : null;
        resetAvdCompatibilityWarningLabel(warning);
      }
    });
    myMinSdkInfoMessageLabel.setBorder(IdeBorderFactory.createEmptyBorder(10, 0, 0, 0));
    myMinSdkInfoMessageLabel.setIcon(AllIcons.General.BalloonWarning);

    Disposer.register(this, myAvdCombo);

    final ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabled();
      }
    };
    myModulesComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myAvdCombo.startUpdatingAvds(ModalityState.current());
      }
    });
    myShowChooserRadioButton.addActionListener(listener);
    myEmulatorRadioButton.addActionListener(listener);
    myUsbDeviceRadioButton.addActionListener(listener);
    myUseLastSelectedDeviceCheckBox.addActionListener(listener);

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (mySkipNoOpApkInstallation == e.getSource()) {
          myForceStopRunningApplicationCheckBox.setEnabled(mySkipNoOpApkInstallation.isSelected());
        }
      }
    };
    mySkipNoOpApkInstallation.addActionListener(actionListener);

    updateEnabled();
  }

  private void $$$setupUI$$$() {
  }

  private void createUIComponents() {
    myAvdCombo = new AvdComboBox(myProject, true, false) {
      @Override
      public Module getModule() {
        return getModuleSelector().getModule();
      }
    };
    myAvdCombo.getComboBox().setRenderer(new ListCellRendererWrapper() {
      @Override
      public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof IdDisplay) {
          setText(((IdDisplay)value).getDisplay());
        }
        else {
          setText(String.format("<html><font color='red'>Unknown AVD %1$s</font></html>", value == null ? "" : value.toString()));
        }
      }
    });

    myAvdComboComponent = new ComboboxWithBrowseButton(myAvdCombo.getComboBox());
  }

  private void updateEnabled() {
    boolean emulatorSelected = myEmulatorRadioButton.isSelected();
    myAvdComboComponent.setEnabled(emulatorSelected);

    final String warning = emulatorSelected ? getAvdCompatibilityWarning() : null;
    resetAvdCompatibilityWarningLabel(warning);
  }

  private void resetAvdCompatibilityWarningLabel(@Nullable String warning) {
    if (warning != null) {
      myMinSdkInfoMessageLabel.setVisible(true);
      myMinSdkInfoMessageLabel.setText(warning);
    }
    else {
      myMinSdkInfoMessageLabel.setVisible(false);
    }
  }

  @Nullable
  private String getAvdCompatibilityWarning() {
    IdDisplay selectedItem = (IdDisplay)myAvdCombo.getComboBox().getSelectedItem();

    if (selectedItem != null) {
      final String selectedAvdName = selectedItem.getId();
      final Module module = getModuleSelector().getModule();
      if (module == null) {
        return null;
      }

      final AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet == null) {
        return null;
      }

      final AvdManager avdManager = facet.getAvdManagerSilently();
      if (avdManager == null) {
        return null;
      }

      final AvdInfo avd = avdManager.getAvd(selectedAvdName, false);
      if (avd == null || avd.getTarget() == null) {
        return null;
      }

      AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
      if (platform == null) {
        return null;
      }

      AndroidVersion minSdk = AndroidModuleInfo.get(facet).getRuntimeMinSdkVersion();
      LaunchCompatibility compatibility = LaunchCompatibility.canRunOnAvd(minSdk, platform.getTarget(), avd.getTarget());
      if (compatibility.isCompatible() == ThreeState.NO) {
        // todo: provide info about current module configuration
        return String.format("'%1$s' may be incompatible with your configuration (%2$s)", selectedAvdName,
                             StringUtil.notNullize(compatibility.getReason()));
      }
    }
    return null;
  }

  @Override
  public JComponent getAnchor() {
    return anchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    this.anchor = anchor;
    myModuleJBLabel.setAnchor(anchor);
  }

  @Nullable
  private static Object findAvdWithName(@NotNull JComboBox avdCombo, @NotNull String avdName) {
    for (int i = 0, n = avdCombo.getItemCount(); i < n; i++) {
      Object item = avdCombo.getItemAt(i);
      if (item instanceof IdDisplay && avdName.equals(((IdDisplay)item).getId())) {
        return item;
      }
    }
    return null;
  }

  @Override
  protected void resetEditorFrom(T configuration) {
    // Set configurations before resetting the module selector to avoid premature calls to setFacet.
    myModuleSelector.reset(configuration);

    final JComboBox combo = myAvdCombo.getComboBox();
    final String avd = configuration.PREFERRED_AVD;
    if (avd != null) {
      Object item = findAvdWithName(combo, avd);
      if (item != null) {
        combo.setSelectedItem(item);
      }
      else {
        combo.setSelectedItem(null);
        incorrectPreferredAvd = avd;
      }
    }

    final TargetSelectionMode targetSelectionMode = configuration.getTargetSelectionMode();

    myShowChooserRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.SHOW_DIALOG);
    myEmulatorRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.EMULATOR);
    myUsbDeviceRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.USB_DEVICE);
    myUseLastSelectedDeviceCheckBox.setSelected(configuration.USE_LAST_SELECTED_DEVICE);

    myAvdComboComponent.setEnabled(targetSelectionMode == TargetSelectionMode.EMULATOR);

    resetAvdCompatibilityWarningLabel(targetSelectionMode == TargetSelectionMode.EMULATOR ? getAvdCompatibilityWarning() : null);

    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
    myShowLogcatCheckBox.setSelected(configuration.SHOW_LOGCAT_AUTOMATICALLY);
    mySkipNoOpApkInstallation.setSelected(configuration.SKIP_NOOP_APK_INSTALLATIONS);
    myForceStopRunningApplicationCheckBox.setSelected(configuration.FORCE_STOP_RUNNING_APP);

    myConfigurationSpecificEditor.resetFrom(configuration);

    updateEnabled();
  }

  @Override
  protected void applyEditorTo(T configuration) throws ConfigurationException {
    myModuleSelector.applyTo(configuration);

    if (myShowChooserRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.SHOW_DIALOG);
    }
    else if (myEmulatorRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.EMULATOR);
    }
    else if (myUsbDeviceRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.USB_DEVICE);
    }

    configuration.USE_LAST_SELECTED_DEVICE = myUseLastSelectedDeviceCheckBox.isSelected();
    configuration.PREFERRED_AVD = "";

    configuration.CLEAR_LOGCAT = myClearLogCheckBox.isSelected();
    configuration.SHOW_LOGCAT_AUTOMATICALLY = myShowLogcatCheckBox.isSelected();
    configuration.SKIP_NOOP_APK_INSTALLATIONS = mySkipNoOpApkInstallation.isSelected();
    configuration.FORCE_STOP_RUNNING_APP = myForceStopRunningApplicationCheckBox.isSelected();

    if (myAvdComboComponent.isEnabled()) {
      JComboBox combo = myAvdCombo.getComboBox();
      IdDisplay preferredAvd = (IdDisplay)combo.getSelectedItem();
      if (preferredAvd == null) {
        configuration.PREFERRED_AVD = incorrectPreferredAvd != null ? incorrectPreferredAvd : "";
      }
      else {
        configuration.PREFERRED_AVD = preferredAvd.getId();
      }
    }
    myConfigurationSpecificEditor.applyTo(configuration);
  }

  @Override
  @NotNull
  protected JComponent createEditor() {
    return myPanel;
  }

  public ConfigurationModuleSelector getModuleSelector() {
    return myModuleSelector;
  }
}
