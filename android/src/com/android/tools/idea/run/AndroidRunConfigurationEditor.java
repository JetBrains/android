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
import com.android.tools.idea.run.cloud.CloudConfiguration;
import com.android.tools.idea.run.cloud.CloudConfiguration.Kind;
import com.android.tools.idea.run.cloud.CloudConfigurationComboBox;
import com.android.tools.idea.run.cloud.CloudConfigurationProvider;
import com.android.tools.idea.run.cloud.CloudProjectIdLabel;
import com.android.tools.idea.run.testing.AndroidTestRunConfiguration;
import com.google.common.base.Predicate;
import com.intellij.application.options.ModulesComboBox;
import com.intellij.execution.ui.ConfigurationModuleSelector;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.util.ThreeState;
import com.intellij.util.ui.JBUI;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.net.URI;

import static com.android.tools.idea.run.cloud.CloudConfiguration.Kind.MATRIX;
import static com.android.tools.idea.run.cloud.CloudConfiguration.Kind.SINGLE_DEVICE;

public class AndroidRunConfigurationEditor<T extends AndroidRunConfigurationBase> extends SettingsEditor<T> implements PanelWithAnchor {
  private final Project myProject;

  private JPanel myPanel;
  protected JBTabbedPane myTabbedPane;
  private ModulesComboBox myModulesComboBox;
  private JPanel myConfigurationSpecificPanel;
  private JCheckBox myWipeUserDataCheckBox;
  private JComboBox myNetworkSpeedCombo;
  private JComboBox myNetworkLatencyCombo;
  private JCheckBox myDisableBootAnimationCombo;
  private JBLabel myModuleJBLabel;
  private JRadioButton myShowChooserRadioButton;
  private JRadioButton myEmulatorRadioButton;
  private JRadioButton myUsbDeviceRadioButton;
  private ComboboxWithBrowseButton myAvdComboComponent;
  private JBLabel myMinSdkInfoMessageLabel;
  private JBCheckBox myUseAdditionalCommandLineOptionsCheckBox;
  private RawCommandLineEditor myCommandLineField;
  private JCheckBox myUseLastSelectedDeviceCheckBox;
  private JRadioButton myRunTestsInGoogleCloudRadioButton;

  // Misc. options tab
  private JCheckBox myClearLogCheckBox;
  private JCheckBox myShowLogcatCheckBox;
  private JCheckBox mySkipNoOpApkInstallation;
  private JCheckBox myForceStopRunningApplicationCheckBox;

  private CloudConfigurationComboBox myCloudMatrixConfigurationCombo;
  private JBLabel myCloudMatrixProjectLabel;
  private JBLabel myCloudMatrixConfigLabel;
  private CloudProjectIdLabel myCloudMatrixProjectIdLabel;
  private ActionButton myCloudMatrixProjectIdUpdateButton;
  private JRadioButton myLaunchCloudDeviceRadioButton;
  private JBLabel myCloudDeviceConfigLabel;
  private JBLabel myCloudDeviceProjectLabel;
  private CloudProjectIdLabel myCloudDeviceProjectIdLabel;
  private ActionButton myCloudDeviceProjectIdUpdateButton;
  private CloudConfigurationComboBox myCloudDeviceConfigurationCombo;
  private ActionButton myCloudMatrixHelpButton;
  @Nullable private final CloudConfigurationProvider myCloudConfigurationProvider;

  private AvdComboBox myAvdCombo;
  private String incorrectPreferredAvd;
  private JComponent anchor;

  @NonNls private final static String[] NETWORK_SPEEDS = new String[]{"Full", "GSM", "HSCSD", "GPRS", "EDGE", "UMTS", "HSPDA"};
  @NonNls private final static String[] NETWORK_LATENCIES = new String[]{"None", "GPRS", "EDGE", "UMTS"};

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

    myCloudConfigurationProvider = CloudConfigurationProvider.getCloudConfigurationProvider();

    myCommandLineField.setDialogCaption("Emulator Additional Command Line Options");

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
        Module module = getModuleSelector().getModule();
        AndroidFacet facet = module != null ? AndroidFacet.getInstance(module) : null;
        if (facet == null) {
          updateCloudMatrixTestEnabled(false);
          updateCloudDeviceLaunchEnabled(false);
        } else {
          myCloudMatrixProjectIdLabel.setFacet(facet);
          myCloudMatrixConfigurationCombo.setFacet(facet);
          myCloudDeviceProjectIdLabel.setFacet(facet);
          myCloudDeviceConfigurationCombo.setFacet(facet);
          updateCloudMatrixTestEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
          updateCloudDeviceLaunchEnabled(myLaunchCloudDeviceRadioButton.isSelected());
        }
        myAvdCombo.startUpdatingAvds(ModalityState.current());
      }
    });
    myShowChooserRadioButton.addActionListener(listener);
    myEmulatorRadioButton.addActionListener(listener);
    myUsbDeviceRadioButton.addActionListener(listener);
    myUseLastSelectedDeviceCheckBox.addActionListener(listener);
    myRunTestsInGoogleCloudRadioButton.addActionListener(listener);
    myLaunchCloudDeviceRadioButton.addActionListener(listener);

    myNetworkSpeedCombo.setModel(new DefaultComboBoxModel(NETWORK_SPEEDS));
    myNetworkLatencyCombo.setModel(new DefaultComboBoxModel(NETWORK_LATENCIES));

    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myUseAdditionalCommandLineOptionsCheckBox == e.getSource()) {
          myCommandLineField.setEnabled(myUseAdditionalCommandLineOptionsCheckBox.isSelected());
        }
        else if (mySkipNoOpApkInstallation == e.getSource()) {
          myForceStopRunningApplicationCheckBox.setEnabled(mySkipNoOpApkInstallation.isSelected());
        }
      }
    };
    myUseAdditionalCommandLineOptionsCheckBox.addActionListener(actionListener);
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

    myCloudMatrixProjectIdLabel = new CloudProjectIdLabel(MATRIX);
    myCloudMatrixConfigurationCombo = new CloudConfigurationComboBox(MATRIX);
    AnAction cloudMatrixProjectAction = new SelectCloudProjectAction(myCloudMatrixProjectIdLabel, myCloudMatrixConfigurationCombo);
    myCloudMatrixProjectIdUpdateButton = new ActionButton(
      cloudMatrixProjectAction, new PresentationFactory().getPresentation(cloudMatrixProjectAction), "MyPlace", JBUI.size(25, 25));
    Disposer.register(this, myCloudMatrixConfigurationCombo);

    myCloudDeviceProjectIdLabel = new CloudProjectIdLabel(SINGLE_DEVICE);
    myCloudDeviceConfigurationCombo = new CloudConfigurationComboBox(SINGLE_DEVICE);
    AnAction cloudDeviceProjectAction = new SelectCloudProjectAction(myCloudDeviceProjectIdLabel, myCloudDeviceConfigurationCombo);
    myCloudDeviceProjectIdUpdateButton = new ActionButton(
      cloudDeviceProjectAction, new PresentationFactory().getPresentation(cloudDeviceProjectAction), "MyPlace", JBUI.size(25, 25));
    AnAction cloudMatrixHelpAction = new CloudMatrixHelpAction();
    myCloudMatrixHelpButton = new ActionButton(
      cloudMatrixHelpAction, new PresentationFactory().getPresentation(cloudMatrixHelpAction), "MyPlace", JBUI.size(25, 25));

    Disposer.register(this, myCloudDeviceConfigurationCombo);
  }

  private void updateGoogleCloudVisible(AndroidRunConfigurationBase configuration) {
    boolean shouldShowCloudDevice = CloudConfigurationProvider.isEnabled();
    myLaunchCloudDeviceRadioButton.setVisible(shouldShowCloudDevice);
    myCloudDeviceConfigurationCombo.setVisible(shouldShowCloudDevice);
    myCloudDeviceProjectLabel.setVisible(shouldShowCloudDevice);
    myCloudDeviceConfigLabel.setVisible(shouldShowCloudDevice);
    myCloudDeviceProjectIdLabel.setVisible(shouldShowCloudDevice);
    myCloudDeviceProjectIdUpdateButton.setVisible(shouldShowCloudDevice);

    boolean shouldShowCloudMatrix = configuration instanceof AndroidTestRunConfiguration && shouldShowCloudDevice;
    myRunTestsInGoogleCloudRadioButton.setVisible(shouldShowCloudMatrix);
    myCloudMatrixConfigurationCombo.setVisible(shouldShowCloudMatrix);
    myCloudMatrixProjectLabel.setVisible(shouldShowCloudMatrix);
    myCloudMatrixConfigLabel.setVisible(shouldShowCloudMatrix);
    myCloudMatrixProjectIdLabel.setVisible(shouldShowCloudMatrix);
    myCloudMatrixProjectIdUpdateButton.setVisible(shouldShowCloudMatrix);
    myCloudMatrixHelpButton.setVisible(shouldShowCloudMatrix);
  }

  private void updateEnabled() {
    boolean emulatorSelected = myEmulatorRadioButton.isSelected();
    myAvdComboComponent.setEnabled(emulatorSelected);
    updateCloudMatrixTestEnabled(myRunTestsInGoogleCloudRadioButton.isSelected());
    updateCloudDeviceLaunchEnabled(myLaunchCloudDeviceRadioButton.isSelected());

    final String warning = emulatorSelected ? getAvdCompatibilityWarning() : null;
    resetAvdCompatibilityWarningLabel(warning);
  }

  private void updateCloudMatrixTestEnabled(boolean isEnabled) {
    myCloudMatrixConfigurationCombo.setEnabled(isEnabled);
    myCloudMatrixProjectLabel.setEnabled(isEnabled);
    myCloudMatrixConfigLabel.setEnabled(isEnabled);
    myCloudMatrixProjectIdLabel.setEnabled(isEnabled);
    myCloudMatrixProjectIdUpdateButton.setEnabled(isEnabled);
  }

  private void updateCloudDeviceLaunchEnabled(boolean isEnabled) {
    myCloudDeviceConfigurationCombo.setEnabled(isEnabled);
    myCloudDeviceProjectLabel.setEnabled(isEnabled);
    myCloudDeviceConfigLabel.setEnabled(isEnabled);
    myCloudDeviceProjectIdLabel.setEnabled(isEnabled);
    myCloudDeviceProjectIdUpdateButton.setEnabled(isEnabled);
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

  private CloudConfiguration getCloudConfigurationComboSelection(Kind configurationKind) {
    if (configurationKind == Kind.MATRIX) {
      return (CloudConfiguration)myCloudMatrixConfigurationCombo.getComboBox().getSelectedItem();
    }
    return (CloudConfiguration)myCloudDeviceConfigurationCombo.getComboBox().getSelectedItem();
  }

  private boolean isCloudProjectSpecified(Kind configurationKind) {
    if (configurationKind == Kind.MATRIX) {
      return myCloudMatrixProjectIdLabel.isProjectSpecified();
    }
    return myCloudDeviceProjectIdLabel.isProjectSpecified();
  }

  public int getSelectedCloudConfigurationId(Kind configurationKind) {
    CloudConfiguration selection = getCloudConfigurationComboSelection(configurationKind);
    if (selection == null) {
      return -1;
    }
    return selection.getId();
  }

  private boolean isValidCloudSelection(Kind configurationKind) {
    CloudConfiguration selection = getCloudConfigurationComboSelection(configurationKind);
    return selection != null && selection.getDeviceConfigurationCount() > 0 && isCloudProjectSpecified(configurationKind);
  }

  private String getInvalidSelectionErrorMessage(Kind configurationKind) {
    CloudConfiguration selection = getCloudConfigurationComboSelection(configurationKind);
    if (selection == null) {
      return "Cloud configuration not specified";
    }
    if (selection.getDeviceConfigurationCount() < 1) {
      return "Selected cloud configuration is empty";
    }
    if (!isCloudProjectSpecified(configurationKind)) {
      return "Cloud project not specified";
    }
    return "";
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
    myCloudMatrixProjectIdLabel.setConfiguration(configuration);
    myCloudMatrixConfigurationCombo.setConfiguration(configuration);
    myCloudDeviceProjectIdLabel.setConfiguration(configuration);
    myCloudDeviceConfigurationCombo.setConfiguration(configuration);

    myModuleSelector.reset(configuration);

    updateGoogleCloudVisible(configuration);

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

    myRunTestsInGoogleCloudRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.CLOUD_MATRIX_TEST);
    myCloudMatrixConfigurationCombo.selectConfiguration(configuration.SELECTED_CLOUD_MATRIX_CONFIGURATION_ID);
    myCloudMatrixProjectIdLabel.updateCloudProjectId(configuration.SELECTED_CLOUD_MATRIX_PROJECT_ID);

    myLaunchCloudDeviceRadioButton.setSelected(targetSelectionMode == TargetSelectionMode.CLOUD_DEVICE_LAUNCH);
    myCloudDeviceConfigurationCombo.selectConfiguration(configuration.SELECTED_CLOUD_DEVICE_CONFIGURATION_ID);
    myCloudDeviceProjectIdLabel.updateCloudProjectId(configuration.SELECTED_CLOUD_DEVICE_PROJECT_ID);

    myAvdComboComponent.setEnabled(targetSelectionMode == TargetSelectionMode.EMULATOR);

    resetAvdCompatibilityWarningLabel(targetSelectionMode == TargetSelectionMode.EMULATOR ? getAvdCompatibilityWarning() : null);

    EmulatorLaunchOptions emulatorLaunchOptions = configuration.getEmulatorLaunchOptions();
    myUseAdditionalCommandLineOptionsCheckBox.setSelected(emulatorLaunchOptions.USE_COMMAND_LINE);
    myCommandLineField.setText(emulatorLaunchOptions.COMMAND_LINE);
    myWipeUserDataCheckBox.setSelected(emulatorLaunchOptions.WIPE_USER_DATA);
    myDisableBootAnimationCombo.setSelected(emulatorLaunchOptions.DISABLE_BOOT_ANIMATION);
    selectItemCaseInsensitively(myNetworkSpeedCombo, emulatorLaunchOptions.NETWORK_SPEED);
    selectItemCaseInsensitively(myNetworkLatencyCombo, emulatorLaunchOptions.NETWORK_LATENCY);

    myClearLogCheckBox.setSelected(configuration.CLEAR_LOGCAT);
    myShowLogcatCheckBox.setSelected(configuration.SHOW_LOGCAT_AUTOMATICALLY);
    mySkipNoOpApkInstallation.setSelected(configuration.SKIP_NOOP_APK_INSTALLATIONS);
    myForceStopRunningApplicationCheckBox.setSelected(configuration.FORCE_STOP_RUNNING_APP);

    myConfigurationSpecificEditor.resetFrom(configuration);

    updateEnabled();
  }

  private static void selectItemCaseInsensitively(@NotNull JComboBox comboBox, @Nullable String item) {
    if (item == null) {
      comboBox.setSelectedItem(null);
      return;
    }

    final ComboBoxModel model = comboBox.getModel();

    for (int i = 0, n = model.getSize(); i < n; i++) {
      final Object element = model.getElementAt(i);
      if (element instanceof String && item.equalsIgnoreCase((String)element)) {
        comboBox.setSelectedItem(element);
        return;
      }
    }
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
    else if (myRunTestsInGoogleCloudRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.CLOUD_MATRIX_TEST);
    }
    else if (myLaunchCloudDeviceRadioButton.isSelected()) {
      configuration.setTargetSelectionMode(TargetSelectionMode.CLOUD_DEVICE_LAUNCH);
    }

    configuration.SELECTED_CLOUD_MATRIX_CONFIGURATION_ID = getSelectedCloudConfigurationId(MATRIX);
    configuration.SELECTED_CLOUD_MATRIX_PROJECT_ID = myCloudMatrixProjectIdLabel.getText();
    configuration.IS_VALID_CLOUD_MATRIX_SELECTION = isValidCloudSelection(MATRIX);
    configuration.INVALID_CLOUD_MATRIX_SELECTION_ERROR = getInvalidSelectionErrorMessage(MATRIX);

    configuration.SELECTED_CLOUD_DEVICE_CONFIGURATION_ID = getSelectedCloudConfigurationId(SINGLE_DEVICE);
    configuration.SELECTED_CLOUD_DEVICE_PROJECT_ID = myCloudDeviceProjectIdLabel.getText();
    configuration.IS_VALID_CLOUD_DEVICE_SELECTION = isValidCloudSelection(SINGLE_DEVICE);
    configuration.INVALID_CLOUD_DEVICE_SELECTION_ERROR = getInvalidSelectionErrorMessage(SINGLE_DEVICE);

    configuration.USE_LAST_SELECTED_DEVICE = myUseLastSelectedDeviceCheckBox.isSelected();
    configuration.PREFERRED_AVD = "";

    EmulatorLaunchOptions emulatorLaunchOptions = configuration.getEmulatorLaunchOptions();
    emulatorLaunchOptions.COMMAND_LINE = myCommandLineField.getText();
    emulatorLaunchOptions.USE_COMMAND_LINE = myUseAdditionalCommandLineOptionsCheckBox.isSelected();
    emulatorLaunchOptions.WIPE_USER_DATA = myWipeUserDataCheckBox.isSelected();
    emulatorLaunchOptions.DISABLE_BOOT_ANIMATION = myDisableBootAnimationCombo.isSelected();
    emulatorLaunchOptions.NETWORK_SPEED = ((String)myNetworkSpeedCombo.getSelectedItem()).toLowerCase();
    emulatorLaunchOptions.NETWORK_LATENCY = ((String)myNetworkLatencyCombo.getSelectedItem()).toLowerCase();

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

  private class SelectCloudProjectAction extends AnAction {

    private final CloudProjectIdLabel myLabel;
    private final CloudConfigurationComboBox myComboBox;


    public SelectCloudProjectAction(CloudProjectIdLabel label, CloudConfigurationComboBox comboBox) {
      myLabel = label;
      myComboBox = comboBox;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      if (myCloudConfigurationProvider == null) {
        return;
      }

      String selectedProjectId = myCloudConfigurationProvider.openCloudProjectConfigurationDialog(myProject, myLabel.getText());

      if (selectedProjectId != null) {
        myLabel.updateCloudProjectId(selectedProjectId);
        // Simulate a change event such that it is picked up by the editor validation mechanisms.
        for (ItemListener itemListener : myComboBox.getComboBox().getItemListeners()) {
          itemListener.itemStateChanged(new ItemEvent(myComboBox.getComboBox(), ItemEvent.ITEM_STATE_CHANGED, myComboBox.getComboBox(),
                                                      ItemEvent.SELECTED));
        }
      }
    }

    @Override
    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setIcon(AllIcons.General.Settings);
    }
  }

  private class CloudMatrixHelpAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
      try {
        Desktop.getDesktop().browse(new URI("https://cloud.google.com/test-lab/android-studio"));
      } catch (Exception ex) {
        // ignore
      }
    }

    @Override
    public void update(AnActionEvent event) {
      Presentation presentation = event.getPresentation();
      presentation.setText("Learn about using Cloud Test Lab from Android Studio");
      presentation.setIcon(AllIcons.Actions.Help);
    }
  }

}
