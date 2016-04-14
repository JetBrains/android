/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.SdkConstants;
import com.android.repository.io.FileOpUtils;
import com.android.resources.Density;
import com.android.resources.Keyboard;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenSize;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.devices.*;
import com.android.sdklib.internal.avd.GpuMode;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.wizard.dynamic.*;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.android.sdklib.devices.Storage.Unit;
import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;
import static com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;

/**
 * Options panel for configuring various AVD options. Has an "advanced" mode and a "simple" mode.
 * Help and error messaging appears on the right hand side.
 */
public class ConfigureAvdOptionsStep extends DynamicWizardStepWithDescription {
  private JBLabel myDeviceName;
  private JBLabel myDeviceDetails;
  private JButton myChangeDeviceButton;
  private JBLabel mySystemImageName;
  private JBLabel mySystemImageDetails;
  private JButton myChangeSystemImageButton;
  private TextFieldWithBrowseButton myExistingSdCard;
  private JComboBox myScalingComboBox;
  private ASGallery<ScreenOrientation> myOrientationToggle;
  private JPanel myRoot;
  private JComboBox myFrontCameraCombo;
  private JComboBox myBackCameraCombo;
  private JComboBox mySpeedCombo;
  private JComboBox myLatencyCombo;
  private JButton myShowAdvancedSettingsButton;
  private com.android.tools.idea.avdmanager.legacy.StorageField myRamStorage;
  private com.android.tools.idea.avdmanager.legacy.StorageField myVmHeapStorage;
  private com.android.tools.idea.avdmanager.legacy.StorageField myInternalStorage;
  private com.android.tools.idea.avdmanager.legacy.StorageField myNewSdCardStorage;
  private JBLabel myMemoryAndStorageLabel;
  private JBLabel myRamLabel;
  private JBLabel myVmHeapLabel;
  private JBLabel myInternalStorageLabel;
  private JBLabel mySdCardLabel;
  private HyperlinkLabel myHardwareSkinHelpLabel;
  private JTextField myAvdDisplayName;
  private JBLabel mySkinDefinitionLabel;
  private JBLabel myAvdId;
  private JLabel myAvdIdLabel;
  private com.android.tools.idea.avdmanager.legacy.SkinChooser mySkinComboBox;
  private JPanel myAvdDisplayNamePanel;
  private JBLabel myAvdNameLabel;
  private JCheckBox myEnableComputerKeyboard;
  private JRadioButton myBuiltInRadioButton;
  private JRadioButton myExternalRadioButton;
  private JCheckBox myDeviceFrameCheckbox;
  private JBLabel myDeviceFrameLabel;
  private Iterable<JComponent> myAdvancedOptionsComponents;
  private String myOriginalName;
  private JSeparator myStorageSeparator;
  private JBLabel myCameraLabel;
  private JBLabel myFrontCameraLabel;
  private JBLabel myBackCameraLabel;
  private JSeparator myCameraSeparator;
  private JBLabel myNetworkLabel;
  private JBLabel mySpeedLabel;
  private JBLabel myLatencyLabel;
  private JBLabel myKeyboardLabel;
  private JSeparator myKeyboardSeparator;
  private JSeparator myNetworkSeparator;
  private AvdConfigurationOptionHelpPanel myAvdConfigurationOptionHelpPanel;
  private JBScrollPane myScrollPane;
  private JCheckBox myRanchuCheckBox;
  private JComboBox myCoreCount;
  private JSeparator myMultiCoreDivider;
  private JLabel myMultiCoreExperimentalLabel;
  private JComboBox myHostGraphics;
  private JBLabel myHostGraphicProblem;

  private PropertyChangeListener myFocusListener;
  private int myMaxCores;
  private int mySelectedCoreCount;

  // Labels used for the advanced settings toggle button
  private static final String ADVANCED_SETTINGS = "Advanced Settings";
  private static final String SHOW = "Show " + ADVANCED_SETTINGS;
  private static final String HIDE = "Hide " + ADVANCED_SETTINGS;

  private Set<JComponent> myErrorStateComponents = Sets.newHashSet();

  private class MyActionListener<T> implements ActionListener {
    DynamicWizardStep myStep;
    String myDescription;
    Key<T> myResultKey;

    public MyActionListener(DynamicWizardStep step, String description, Key<T> resultKey) {
      myStep = step;
      myDescription = description;
      myResultKey = resultKey;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      DynamicWizard wizard = new SingleStepWizard(getProject(), getModule(), myStep, new SingleStepDialogWrapperHost(getProject())) {
        @NotNull
        @Override
        protected String getProgressTitle() {
          return "Updating AVD...";
        }

        @Override
        protected String getWizardActionDescription() {
          return myDescription;
        }
      };
      ScopedStateStore subState = wizard.getState();
      subState.putAllInWizardScope(myState);
      subState.put(IS_IN_EDIT_MODE_KEY, false);
      wizard.init();
      if (wizard.showAndGet()) {
        myState.put(myResultKey, subState.get(myResultKey));
      }
    }
  }

  public <T> MyActionListener<T> createListener(DynamicWizardStep step, String description, Key<T> resultKey) {
    return new MyActionListener<T>(step, description, resultKey);
  }


  public ConfigureAvdOptionsStep(@Nullable final Disposable parentDisposable) {
    super(parentDisposable);
    setBodyComponent(myRoot);
    FormScalingUtil.scaleComponentTree(this.getClass(), createStepBody());

    registerAdvancedOptionsVisibility();
    myShowAdvancedSettingsButton.setText(SHOW);

    ActionListener toggleAdvancedSettingsListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!isAdvancedPanel()) {
          myShowAdvancedSettingsButton.setText(HIDE);
          toggleAdvancedSettings(true);
        }
        else {
          myShowAdvancedSettingsButton.setText(SHOW);
          toggleAdvancedSettings(false);
        }
      }
    };
    myShowAdvancedSettingsButton.addActionListener(toggleAdvancedSettingsListener);

    myChangeDeviceButton.addActionListener(
      createListener(new ChooseDeviceDefinitionStep(parentDisposable), "Select a device", DEVICE_DEFINITION_KEY));
    myChangeSystemImageButton.addActionListener(
      createListener(new ChooseSystemImageStep(getProject(), parentDisposable), "Select a system image", SYSTEM_IMAGE_KEY));

    ActionListener sdActionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateSdCardSettings();
      }
    };
    myExternalRadioButton.addActionListener(sdActionListener);
    myBuiltInRadioButton.addActionListener(sdActionListener);

    myOrientationToggle.setOpaque(false);

    myFocusListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        Object value = evt.getNewValue();
        if (evt.getNewValue() instanceof JComponent) {
          JComponent component = (JComponent)value;
          Component parent = component.getParent();
          if (parent instanceof JComponent) {
            ((JComponent)parent).scrollRectToVisible(component.getBounds());
          }
        }
      }
    };
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", myFocusListener);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);

    initCpuCoreDropDown();
  }

  @Override
  public void dispose() {
    if (myFocusListener != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", myFocusListener);
    }
  }


  /**
   * Toggle the SD card between using an existing file and creating a new file.
   */
  private void updateSdCardSettings() {
    boolean useExisting = myState.getNotNull(DISPLAY_USE_EXTERNAL_SD_KEY, true);
    if (useExisting) {
      myExistingSdCard.setEnabled(true);
      myNewSdCardStorage.setEnabled(false);
    }
    else {
      myExistingSdCard.setEnabled(false);
      myNewSdCardStorage.setEnabled(true);
    }
  }

  @Override
  public void init() {
    super.init();
    registerComponents();
    deregister(getDescriptionLabel());
    getDescriptionLabel().setVisible(false);
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    myAvdConfigurationOptionHelpPanel.setSystemImageDescription(systemImage);

    Boolean editMode = myState.get(IS_IN_EDIT_MODE_KEY);
    editMode = editMode == null ? Boolean.FALSE : editMode;
    myOriginalName = editMode ? myState.get(DISPLAY_NAME_KEY) : "";

    myAvdId.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        myAvdId.requestFocusInWindow();
      }
    });

    myCoreCount.setPreferredSize(myRamStorage.getPreferredSizeOfUnitsDropdown());

    toggleAdvancedSettings(false);
  }

  private void initCpuCoreDropDown() {
    myMaxCores = Math.max(1, getMaxCpuCores());
    for (int core = 1; core <= myMaxCores; core++) {
      //noinspection unchecked
      myCoreCount.addItem(core);
    }
  }

  private void setInitialGpuMode() {
    GpuMode mode = myState.getNotNull(HOST_GPU_MODE_KEY, getDefaultGpuMode());
    populateHostGraphicsDropDown();
    switch (mode) {
      case AUTO:
        myHostGraphics.setSelectedIndex(0);
        break;
      case HOST:
        myHostGraphics.setSelectedIndex(1);
        break;
      case MESA:
      case SWIFT:
      case OFF:
      default:
        myHostGraphics.setSelectedIndex(2);
        break;
    }
  }

  // Return our best default setting if HOST_GPU_MODE_KEY is not set yet
  private GpuMode getDefaultGpuMode() {
    if (!myState.containsKey(USE_HOST_GPU_KEY)) {
      return GpuMode.AUTO;
    }
    return myState.getNotNull(USE_HOST_GPU_KEY, true) ? GpuMode.HOST : GpuMode.OFF;
  }

  private void populateHostGraphicsDropDown() {
    boolean supportGuest = getSelectedApiLevel() >= 23 && isIntel() && isGoogleApiSelected();
    GpuMode otherMode = GpuMode.OFF;
    if (supportGuest) {
      otherMode = GpuMode.SWIFT;
    }
    else if (!SystemInfo.isMac) {
      otherMode = GpuMode.MESA;
    }
    myHostGraphics.addItem(GpuMode.AUTO);
    myHostGraphics.addItem(GpuMode.HOST);
    myHostGraphics.addItem(otherMode);

    boolean atLeastVersion16 = getSelectedApiLevel() >= 16;
    myHostGraphics.setEnabled(atLeastVersion16);
    myHostGraphicProblem.setVisible(!atLeastVersion16);
  }

  private void updateGpuControlsAfterSystemImageChange() {
    int selectedIndex = myHostGraphics.getSelectedIndex();
    myHostGraphics.removeAllItems();
    populateHostGraphicsDropDown();
    myHostGraphics.setSelectedIndex(selectedIndex);
  }

  @Override
  public boolean commitStep() {
    if (!myState.getNotNull(DISPLAY_USE_EXTERNAL_SD_KEY, false)) {
      Storage orig = myState.get(SD_CARD_STORAGE_KEY);
      Storage current = myState.get(DISPLAY_SD_SIZE_KEY);
      if (orig != null && !orig.equals(current)) {
        int result = Messages.showYesNoDialog((Project)null, "Changing the size of the built-in SD card will erase " +
                                                             "the current contents of the card. Continue?",
                                              "Confirm Data Wipe", AllIcons.General.QuestionDialog);
        if (result != Messages.YES) {
          return false;
        }
      }
    }
    File displayFile = myState.get(DISPLAY_SKIN_FILE_KEY);
    boolean hasFrame = myState.getNotNull(DEVICE_FRAME_KEY, false);
    myState.put(CUSTOM_SKIN_FILE_KEY,  hasFrame ? displayFile : NO_SKIN);
    myState.put(BACKUP_SKIN_FILE_KEY, hasFrame ? null : displayFile);

    if (!myState.getNotNull(RANCHU_KEY, false)) {
      myState.remove(CPU_CORES_KEY);  // Do NOT use the new emulator (qemu2)
    }
    else if (!myState.containsKey(CPU_CORES_KEY)) {
      myState.put(CPU_CORES_KEY, 1);  // Force the use the new emulator (qemu2)
    }
    if (getSelectedApiLevel() < 16 || myState.get(HOST_GPU_MODE_KEY) == GpuMode.OFF) {
      myState.put(USE_HOST_GPU_KEY, false);
      myState.put(HOST_GPU_MODE_KEY, GpuMode.OFF);
    }
    else {
      myState.put(USE_HOST_GPU_KEY, true);
    }

    return super.commitStep();
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    toggleOptionals(myState.get(DEVICE_DEFINITION_KEY), false);
    updateSdCardSettings();
    setInitialGpuMode();
  }

  @Override
  public boolean validate() {
    clearErrorState();
    boolean valid = true;
    // Check Ram
    Storage ram = myState.get(RAM_STORAGE_KEY);
    if (ram == null || ram.getSizeAsUnit(Unit.MiB) < 128) {
      setErrorState("RAM must be a numeric (integer) value of at least 128MB. Recommendation is 1GB.", myMemoryAndStorageLabel, myRamLabel,
                    myRamStorage);
      valid = false;
    }

    // Check VM Heap
    Storage vmHeap = myState.get(VM_HEAP_STORAGE_KEY);
    if (vmHeap == null || vmHeap.getSizeAsUnit(Unit.MiB) < 16) {
      setErrorState("VM Heap must be a numeric (integer) value of at least 16MB.", myMemoryAndStorageLabel, myVmHeapLabel, myVmHeapStorage);
      valid = false;
    }

    // Check Internal Storage
    Storage internal = myState.get(INTERNAL_STORAGE_KEY);
    if (internal == null || internal.getSizeAsUnit(Unit.MiB) < 200) {
      setErrorState("Internal storage must be a numeric (integer) value of at least 200MB.", myMemoryAndStorageLabel,
                    myInternalStorageLabel, myInternalStorage);
      valid = false;
    }

    // If we're using an existing SD card, make sure it exists
    Boolean useExistingSd = myState.get(DISPLAY_USE_EXTERNAL_SD_KEY);
    if (useExistingSd != null && useExistingSd) {
      String path = myState.get(DISPLAY_SD_LOCATION_KEY);
      if (path == null || !new File(path).isFile()) {
        setErrorState("The specified SD image file must be a valid image file", myMemoryAndStorageLabel, mySdCardLabel, myExistingSdCard);
        valid = false;
      }
    }
    else {
      Storage sdCard = myState.get(DISPLAY_SD_SIZE_KEY);
      if (sdCard != null && (sdCard.getSizeAsUnit(Unit.MiB) < 10)) {
        setErrorState("The SD card must be larger than 10MB", myMemoryAndStorageLabel, mySdCardLabel, myNewSdCardStorage);
        valid = false;
      }
    }

    File skinFile = myState.get(CUSTOM_SKIN_FILE_KEY);
    if (skinFile != null && !FileUtil.filesEqual(skinFile, NO_SKIN)) {
      File layoutFile = new File(skinFile, SdkConstants.FN_SKIN_LAYOUT);
      if (!layoutFile.isFile()) {
        setErrorState("The skin directory does not point to a valid skin.", myDeviceFrameLabel, mySkinComboBox);
        valid = false;
      }
    }

    String displayName = myState.get(DISPLAY_NAME_KEY);
    if (displayName != null) {
      displayName = displayName.trim();
      if (!displayName.equals(myOriginalName) && AvdManagerConnection.getDefaultAvdManagerConnection().findAvdWithName(displayName)) {
        setErrorState(String.format("An AVD with the name \"%1$s\" already exists.", displayName), myAvdDisplayNamePanel, myAvdNameLabel);
        valid = false;
      }
      if (!displayName.matches("^[0-9a-zA-Z-_. ()]+$")) {
        setErrorState("The AVD name can only contain the characters a-z A-Z 0-9 . _ - ( )", myAvdDisplayNamePanel, myAvdNameLabel);
        valid = false;
      }
    }

    Device device = myState.get(DEVICE_DEFINITION_KEY);
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    if (device == null) {
      setErrorState("A hardware profile must be selected.", myDeviceDetails);
      valid = false;
    }
    else if (!ChooseSystemImageStep.systemImageMatchesDevice(systemImage, device)) {
      setErrorState("The selected system image is incompatible with the selected device.", mySystemImageDetails);
      valid = false;
    }

    return valid;
  }

  private boolean doesSystemImageSupportRanchu() {
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    assert systemImage != null;
    return AvdManagerConnection.doesSystemImageSupportRanchu(systemImage);
  }

  private int getSelectedApiLevel() {
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    assert systemImage != null;
    AndroidVersion version = systemImage.getVersion();
    assert version != null;
    return version.getApiLevel();
  }

  private String getSelectedApiString() {
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    assert systemImage != null;
    AndroidVersion version = systemImage.getVersion();
    assert version != null;
    return version.getApiString();
  }

  private boolean isGoogleApiSelected() {
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    assert systemImage != null;
    return TAGS_WITH_GOOGLE_API.contains(systemImage.getTag());
  }

  private boolean isIntel() {
    return supportsMultipleCpuCores();
  }

  private boolean supportsMultipleCpuCores() {
    SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
    assert systemImage != null;
    Abi abi = Abi.getEnum(systemImage.getAbiType());
    return abi != null && abi.supportsMultipleCpuCores();
  }

  /**
   * Clear the error highlighting around any components that had previously been marked as errors
   */
  private void clearErrorState() {
    for (JComponent c : myErrorStateComponents) {
      if (c instanceof JLabel) {
        c.setForeground(JBColor.foreground());
        ((JLabel)c).setIcon(null);
      }
      else if (c instanceof com.android.tools.idea.avdmanager.legacy.StorageField) {
        ((com.android.tools.idea.avdmanager.legacy.StorageField)c).setError(false);
      }
      else if (c instanceof JCheckBox) {
        c.setForeground(JBColor.foreground());
      }
      else {
        c.setBorder(null);
      }
    }
    myAvdConfigurationOptionHelpPanel.setErrorMessage("");
    setErrorHtml(null);
  }

  /**
   * Set an error message and mark the given components as being in error state
   */
  private void setErrorState(String message, JComponent... errorComponents) {
    boolean isVisible = false;
    for (JComponent c : errorComponents) {
      if (c.isShowing()) {
        isVisible = true;
        break;
      }
    }
    if (!isVisible) {
      setErrorHtml(message);
    }
    else {
      myAvdConfigurationOptionHelpPanel.setErrorMessage(message);
      for (JComponent c : errorComponents) {
        if (c instanceof JLabel) {
          c.setForeground(JBColor.RED);
          ((JLabel)c).setIcon(AllIcons.General.BalloonError);
        }
        else if (c instanceof StorageField) {
          ((com.android.tools.idea.avdmanager.legacy.StorageField)c).setError(true);
        }
        else if (c instanceof JCheckBox) {
          c.setForeground(JBColor.RED);
        }
        else {
          c.setBorder(new LineBorder(JBColor.RED));
        }
        myErrorStateComponents.add(c);
      }
    }
  }

  /**
   * Bind components to their specified keys and help messaging.
   */
  private void registerComponents() {
    register(DISPLAY_NAME_KEY, myAvdDisplayName);
    register(AVD_ID_KEY, myAvdId);
    final AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();
    registerValueDeriver(AVD_ID_KEY, new ValueDeriver<String>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(DISPLAY_NAME_KEY);
      }

      @Nullable
      @Override
      public String deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable String currentValue) {
        String displayName = state.get(DISPLAY_NAME_KEY);
        if (displayName != null) {
          return AvdEditWizard
            .cleanAvdName(connection, displayName, !displayName.equals(myOriginalName));
        }
        return "";
      }
    });
    setControlDescription(myAvdDisplayName, myAvdConfigurationOptionHelpPanel.getDescription(DISPLAY_NAME_KEY));
    setControlDescription(myAvdId, myAvdConfigurationOptionHelpPanel.getDescription(AVD_ID_KEY));
    register(DEVICE_DEFINITION_KEY, myDeviceName, DEVICE_NAME_BINDING);
    register(DEVICE_DEFINITION_KEY, myDeviceDetails, DEVICE_DETAILS_BINDING);
    register(SYSTEM_IMAGE_KEY, mySystemImageName, SYSTEM_IMAGE_NAME_BINDING);
    register(SYSTEM_IMAGE_KEY, mySystemImageDetails, SYSTEM_IMAGE_DESCRIPTION_BINDING);
    register(RANCHU_KEY, myRanchuCheckBox);
    setControlDescription(myRanchuCheckBox, myAvdConfigurationOptionHelpPanel.getDescription(CPU_CORES_KEY));
    if (myState.containsKey(CPU_CORES_KEY)) {
      mySelectedCoreCount = myState.getNotNull(CPU_CORES_KEY, 1);
    }
    else {
      myState.put(CPU_CORES_KEY, 1);
      mySelectedCoreCount = myMaxCores;
    }
    register(CPU_CORES_KEY, myCoreCount);
    setControlDescription(myCoreCount, myAvdConfigurationOptionHelpPanel.getDescription(CPU_CORES_KEY));

    register(RAM_STORAGE_KEY, myRamStorage, myRamStorage.getBinding());
    setControlDescription(myRamStorage, myAvdConfigurationOptionHelpPanel.getDescription(RAM_STORAGE_KEY));

    register(VM_HEAP_STORAGE_KEY, myVmHeapStorage, myVmHeapStorage.getBinding());
    setControlDescription(myVmHeapStorage, myAvdConfigurationOptionHelpPanel.getDescription(VM_HEAP_STORAGE_KEY));

    register(INTERNAL_STORAGE_KEY, myInternalStorage, myInternalStorage.getBinding());
    setControlDescription(myInternalStorage, myAvdConfigurationOptionHelpPanel.getDescription(INTERNAL_STORAGE_KEY));

    register(DISPLAY_SD_SIZE_KEY, myNewSdCardStorage, myNewSdCardStorage.getBinding());
    setControlDescription(myNewSdCardStorage, myAvdConfigurationOptionHelpPanel.getDescription(SD_CARD_STORAGE_KEY));

    register(HOST_GPU_MODE_KEY, myHostGraphics);
    setControlDescription(myHostGraphics, myAvdConfigurationOptionHelpPanel.getDescription(HOST_GPU_MODE_KEY));

    if (Boolean.FALSE.equals(myState.get(IS_IN_EDIT_MODE_KEY))) {
      registerValueDeriver(RAM_STORAGE_KEY, new MemoryValueDeriver() {
        @Nullable
        @Override
        protected Storage getStorage(@NotNull Device device) {
          return getDefaultRam(device.getDefaultHardware());
        }
      });

      registerValueDeriver(VM_HEAP_STORAGE_KEY, new MemoryValueDeriver() {
        @Nullable
        @Override
        protected Storage getStorage(@NotNull Device device) {
          return calculateVmHeap(device);
        }
      });

      registerValueDeriver(DISPLAY_NAME_KEY, new ValueDeriver<String>() {
        @Nullable
        @Override
        public Set<Key<?>> getTriggerKeys() {
          return makeSetOf(DEVICE_DEFINITION_KEY, SYSTEM_IMAGE_KEY);
        }

        @Nullable
        @Override
        public String deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable String currentValue) {
          Device device = state.get(DEVICE_DEFINITION_KEY);
          SystemImageDescription systemImage = state.get(SYSTEM_IMAGE_KEY);
          if (device != null && systemImage != null) { // Should always be the case
            return connection.uniquifyDisplayName(
              String.format(Locale.getDefault(), "%1$s API %2$s", device.getDisplayName(), getSelectedApiString()));
          }
          return null; // Should never occur
        }
      });
    }

    registerValueDeriver(DISPLAY_SKIN_FILE_KEY, new ValueDeriver<File>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(DEVICE_DEFINITION_KEY, SYSTEM_IMAGE_KEY);
      }

      @Nullable
      @Override
      public File deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable File currentValue) {
        // If there was a skin specified coming in, this field will be marked as user-edited, and so that needn't be
        // taken into account here. The only case we care about is if the device is changed.
        Device device = myState.get(DEVICE_DEFINITION_KEY);
        File file = null;
        if (device != null) {
          file =
            AvdEditWizard.resolveSkinPath(device.getDefaultHardware().getSkinFile(), myState.get(SYSTEM_IMAGE_KEY), FileOpUtils.create());
        }
        return file == null ? NO_SKIN : file;
      }
    });

    registerValueDeriver(DEVICE_FRAME_KEY, new ValueDeriver<Boolean>() {
      @Nullable
      @Override
      public Set<Key<?>> getTriggerKeys() {
        return makeSetOf(DISPLAY_SKIN_FILE_KEY);
      }

      @Override
      public boolean respectUserEdits() {
        // if "No skin" is selected, we always want to uncheck and disable the checkbox,
        // regardless of whether it was modified by the user.
        return false;
      }

      @Nullable
      @Override
      public Boolean deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable Boolean currentValue) {
        File displaySkinPath = myState.get(DISPLAY_SKIN_FILE_KEY);
        boolean hasSkin = displaySkinPath != null && !FileUtil.filesEqual(NO_SKIN, displaySkinPath);
        myDeviceFrameCheckbox.setEnabled(hasSkin);
        return hasSkin && myState.getNotNull(DEVICE_FRAME_KEY, false);
      }
    });

    register(DEFAULT_ORIENTATION_KEY, myOrientationToggle, ORIENTATION_BINDING);

    setControlDescription(myOrientationToggle, myAvdConfigurationOptionHelpPanel.getDescription(DEFAULT_ORIENTATION_KEY));
    myOrientationToggle.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        saveState(myOrientationToggle);
      }
    });

    FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, true);
      }
    };
    fileChooserDescriptor.setHideIgnored(false);
    myExistingSdCard.addBrowseFolderListener("Select SD Card", "Select an existing SD card image", getProject(), fileChooserDescriptor);
    register(DISPLAY_SD_LOCATION_KEY, myExistingSdCard);
    setControlDescription(myExistingSdCard, myAvdConfigurationOptionHelpPanel.getDescription(EXISTING_SD_LOCATION));

    register(FRONT_CAMERA_KEY, myFrontCameraCombo, STRING_COMBO_BINDING);
    setControlDescription(myFrontCameraCombo, myAvdConfigurationOptionHelpPanel.getDescription(FRONT_CAMERA_KEY));

    register(BACK_CAMERA_KEY, myBackCameraCombo, STRING_COMBO_BINDING);
    setControlDescription(myBackCameraCombo, myAvdConfigurationOptionHelpPanel.getDescription(BACK_CAMERA_KEY));

    register(SCALE_SELECTION_KEY, myScalingComboBox, new ComponentBinding<AvdScaleFactor, JComboBox>() {
      @Override
      public void addActionListener(@NotNull ActionListener listener, @NotNull JComboBox component) {
        component.addActionListener(listener);
      }

      @Nullable
      @Override
      public AvdScaleFactor getValue(@NotNull JComboBox component) {
        return ((AvdScaleFactor)component.getSelectedItem());
      }

      @Override
      public void setValue(@Nullable AvdScaleFactor newValue, @NotNull JComboBox component) {
        if (newValue != null) {
          component.setSelectedItem(newValue);
        }
      }
    });
    setControlDescription(myScalingComboBox, myAvdConfigurationOptionHelpPanel.getDescription(SCALE_SELECTION_KEY));

    register(NETWORK_LATENCY_KEY, myLatencyCombo, STRING_COMBO_BINDING);
    setControlDescription(myLatencyCombo, myAvdConfigurationOptionHelpPanel.getDescription(NETWORK_LATENCY_KEY));

    register(NETWORK_SPEED_KEY, mySpeedCombo, STRING_COMBO_BINDING);
    setControlDescription(mySpeedCombo, myAvdConfigurationOptionHelpPanel.getDescription(NETWORK_SPEED_KEY));

    register(KEY_DESCRIPTION, myAvdConfigurationOptionHelpPanel, new ComponentBinding<String, AvdConfigurationOptionHelpPanel>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull AvdConfigurationOptionHelpPanel component) {
        component.setDescriptionText(newValue);
      }
    });

    register(DISPLAY_SKIN_FILE_KEY, mySkinComboBox, mySkinComboBox.getBinding());
    setControlDescription(mySkinComboBox, myAvdConfigurationOptionHelpPanel.getDescription(CUSTOM_SKIN_FILE_KEY));

    register(DEVICE_FRAME_KEY, myDeviceFrameCheckbox);
    setControlDescription(myDeviceFrameCheckbox, myAvdConfigurationOptionHelpPanel.getDescription(DEVICE_FRAME_KEY));

    if (!myState.containsKey(HAS_HARDWARE_KEYBOARD_KEY)) {
      myState.put(HAS_HARDWARE_KEYBOARD_KEY, true);
    }
    register(HAS_HARDWARE_KEYBOARD_KEY, myEnableComputerKeyboard);
    setControlDescription(myEnableComputerKeyboard, myAvdConfigurationOptionHelpPanel.getDescription(HAS_HARDWARE_KEYBOARD_KEY));

    RadioButtonGroupBinding<Boolean> sdCardBinding = new RadioButtonGroupBinding<Boolean>(ImmutableMap.of(
        myExternalRadioButton, true,
        myBuiltInRadioButton, false));

    register(DISPLAY_USE_EXTERNAL_SD_KEY, myExternalRadioButton, sdCardBinding);
    setControlDescription(myExternalRadioButton, myAvdConfigurationOptionHelpPanel.getDescription(EXISTING_SD_LOCATION));
    register(DISPLAY_USE_EXTERNAL_SD_KEY, myBuiltInRadioButton, sdCardBinding);
    setControlDescription(myBuiltInRadioButton, myAvdConfigurationOptionHelpPanel.getDescription(SD_CARD_STORAGE_KEY));

    invokeUpdate(null);
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    if (modified.contains(RANCHU_KEY) || modified.contains(SYSTEM_IMAGE_KEY)) {
      toggleSystemOptionals(modified.contains(RANCHU_KEY));
    }
    if (modified.contains(SYSTEM_IMAGE_KEY)) {
      updateGpuControlsAfterSystemImageChange();
    }
    if (modified.contains(DEVICE_DEFINITION_KEY)) {
      toggleOptionals(myState.get(DEVICE_DEFINITION_KEY), true);
    }
  }

  private void createUIComponents() {
    myOrientationToggle =
      new ASGallery<ScreenOrientation>(JBList.createDefaultListModel(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE),
                                       new Function<ScreenOrientation, Image>() {
                                         @Override
                                         public Image apply(ScreenOrientation input) {
                                           return IconUtil.toImage(ORIENTATIONS.get(input).myIcon);
                                         }
                                       }, new Function<ScreenOrientation, String>() {
        @Override
        public String apply(ScreenOrientation input) {
          return ORIENTATIONS.get(input).myName;
        }
      }, JBUI.size(50, 50));
    myOrientationToggle.setCellMargin(JBUI.insets(5, 20, 4, 20));
    myOrientationToggle.setBackground(JBColor.background());
    myOrientationToggle.setForeground(JBColor.foreground());
    myScalingComboBox = new ComboBox(new EnumComboBoxModel<AvdScaleFactor>(AvdScaleFactor.class));
    myHardwareSkinHelpLabel = new HyperlinkLabel("How do I create a custom hardware skin?");
    myHardwareSkinHelpLabel.setHyperlinkTarget(CREATE_SKIN_HELP_LINK);
    mySkinComboBox = new com.android.tools.idea.avdmanager.legacy.SkinChooser(getProject());
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Configure AVD Options";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Android Virtual Device (AVD)";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return "Verify Configuration";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAvdDisplayName;
  }

  private static final class NamedIcon {

    @NotNull private final String myName;

    @NotNull private final Icon myIcon;

    public NamedIcon(@NotNull String name, @NotNull Icon icon) {
      myName = name;
      myIcon = icon;
    }
  }

  private static final Map<ScreenOrientation, NamedIcon> ORIENTATIONS = ImmutableMap
    .of(ScreenOrientation.PORTRAIT, new NamedIcon("Portrait", AndroidIcons.Portrait), ScreenOrientation.LANDSCAPE,
        new NamedIcon("Landscape", AndroidIcons.Landscape));

  private static final ComponentBinding<Device, JBLabel> DEVICE_NAME_BINDING = new ComponentBinding<Device, JBLabel>() {
    @Override
    public void setValue(@Nullable Device newValue, @NotNull JBLabel component) {
      if (newValue != null) {
        component.setText(newValue.getDisplayName());
        Icon icon = com.android.tools.idea.avdmanager.legacy.DeviceDefinitionPreview.getIcon(newValue);

        component.setIcon(icon);
      }
    }
  };

  private static final ComponentBinding<Device, JBLabel> DEVICE_DETAILS_BINDING = new ComponentBinding<Device, JBLabel>() {
    @Override
    public void setValue(@Nullable Device newValue, @NotNull JBLabel component) {
      if (newValue != null) {
        String description = Joiner.on(' ')
          .join(DeviceDefinitionList.getDiagonalSize(newValue), DeviceDefinitionList.getDimensionString(newValue),
                DeviceDefinitionList.getDensityString(newValue));
        component.setText(description);
      }
    }
  };

  private static final ComponentBinding<SystemImageDescription, JBLabel> SYSTEM_IMAGE_NAME_BINDING =
    new ComponentBinding<SystemImageDescription, JBLabel>() {
      @Override
      public void setValue(@Nullable SystemImageDescription newValue, @NotNull JBLabel component) {
        if (newValue != null) {
          String codeName = SystemImagePreview.getCodeName(newValue);
          component.setText(codeName);
          try {
            Icon icon = IconLoader.findIcon(String.format("/icons/versions/%s_32.png", codeName), AndroidIcons.class);
            component.setIcon(icon);
          }
          catch (RuntimeException e) {
            // Pass
          }
        }
      }
    };

  private final ComponentBinding<SystemImageDescription, JBLabel> SYSTEM_IMAGE_DESCRIPTION_BINDING =
    new ComponentBinding<SystemImageDescription, JBLabel>() {
      @Override
      public void setValue(@Nullable SystemImageDescription newValue, @NotNull JBLabel component) {
        if (newValue != null) {
          component.setText(newValue.getName() + " " + newValue.getAbiType());
          myAvdConfigurationOptionHelpPanel.setSystemImageDescription(newValue);
        }
      }
    };

  public static final ComponentBinding<ScreenOrientation, ASGallery<ScreenOrientation>> ORIENTATION_BINDING =
    new ComponentBinding<ScreenOrientation, ASGallery<ScreenOrientation>>() {
      @Nullable
      @Override
      public ScreenOrientation getValue(@NotNull ASGallery<ScreenOrientation> component) {
        return component.getSelectedElement();
      }

      @Override
      public void setValue(@Nullable ScreenOrientation newValue, @NotNull ASGallery<ScreenOrientation> component) {
        component.setSelectedElement(newValue);
      }
    };

  private static final ComponentBinding<String, JComboBox> STRING_COMBO_BINDING = new ComponentBinding<String, JComboBox>() {
    @Override
    public void setValue(@Nullable String newValue, @NotNull JComboBox component) {
      if (newValue == null) {
        return;
      }
      for (int i = 0; i < component.getItemCount(); i++) {
        if (newValue.equalsIgnoreCase((String)component.getItemAt(i))) {
          component.setSelectedIndex(i);
        }
      }
    }

    @Nullable
    @Override
    public String getValue(@NotNull JComboBox component) {
      //noinspection StringToUpperCaseOrToLowerCaseWithoutLocale
      return component.getSelectedItem().toString().toLowerCase();
    }

    @Override
    public void addItemListener(@NotNull ItemListener listener, @NotNull JComboBox component) {
      component.addItemListener(listener);
    }
  };

  private void registerAdvancedOptionsVisibility() {
    // Unfortunately the use of subpanels here seems to cause layout issues, so each component must be listed separately.
    myAdvancedOptionsComponents = Iterables.concat(
      getMemoryAndStorageComponents(),
      getCameraComponents(),
      getNetworkComponents(),
      getSkinComponents(),
      getKeyboardComponents(),
      getAvdNameComponents()
      );
  }

  private List<JComponent> getAvdNameComponents() {
    return ImmutableList.<JComponent>of( myAvdIdLabel, myAvdId);
  }

  private List<JComponent> getKeyboardComponents() {
    return ImmutableList.<JComponent>of(myEnableComputerKeyboard, myKeyboardSeparator, myKeyboardLabel);
  }

  private List<JComponent> getSkinComponents() {
    return ImmutableList.<JComponent>of(mySkinComboBox, mySkinDefinitionLabel, myHardwareSkinHelpLabel);
  }

  private List<JComponent> getMemoryAndStorageComponents() {
    return ImmutableList.<JComponent>of(myMemoryAndStorageLabel, myRamLabel, myVmHeapLabel, myInternalStorageLabel, mySdCardLabel,
                                        myRamStorage, myVmHeapStorage, myInternalStorage, myExistingSdCard, myExternalRadioButton,
                                        myNewSdCardStorage, myBuiltInRadioButton, myStorageSeparator);
  }

  private List<JComponent> getCameraComponents() {
    return ImmutableList.<JComponent>of(myCameraLabel, myCameraSeparator, myBackCameraLabel,
                                        myFrontCameraLabel, myBackCameraCombo, myFrontCameraCombo);
  }

  private List<JComponent> getNetworkComponents() {
    return ImmutableList.<JComponent>of(myNetworkLabel, mySpeedLabel, mySpeedCombo, myLatencyCombo, myLatencyLabel, myNetworkSeparator);
  }

  /**
   * Show or hide the "advanced" control panels.
   */
  private void toggleAdvancedSettings(boolean show) {
    for (JComponent c : myAdvancedOptionsComponents) {
      c.setVisible(show);
    }
    toggleSystemOptionals(false);
    validate();
    myRoot.validate();
  }

  private boolean isAdvancedPanel() {
    return myShowAdvancedSettingsButton.getText().equals(HIDE);
  }

  /**
   * Enable/Disable controls based on the capabilities of the selected device. For example, some devices may
   * not have a front facing camera.
   */
  private void toggleOptionals(@Nullable Device device, boolean deviceChange) {
    myChangeSystemImageButton.setEnabled(device != null);
    myFrontCameraCombo.setEnabled(device != null && device.getDefaultHardware().getCamera(CameraLocation.FRONT) != null);
    myBackCameraCombo.setEnabled(device != null && device.getDefaultHardware().getCamera(CameraLocation.BACK) != null);
    myOrientationToggle.setEnabled(device != null && device.getDefaultState().getOrientation() != ScreenOrientation.SQUARE);
    myEnableComputerKeyboard.setEnabled(device != null && !device.getDefaultHardware().getKeyboard().equals(Keyboard.QWERTY));
    if (deviceChange || myState.get(DEFAULT_ORIENTATION_KEY) == null) {
      ScreenOrientation orientation = device != null ? device.getDefaultState().getOrientation() : ScreenOrientation.PORTRAIT;
      myState.put(DEFAULT_ORIENTATION_KEY, orientation);
    }
    File customSkin = myState.get(CUSTOM_SKIN_FILE_KEY);
    File backupSkin = myState.get(BACKUP_SKIN_FILE_KEY);
    // If there is a backup skin but no normal skin, the "use device frame" checkbox should be unchecked.
    myState.put(DEVICE_FRAME_KEY, backupSkin == null || customSkin != null);

    File hardwareSkin = null;
    if (device != null) {
      SystemImageDescription systemImage = myState.get(SYSTEM_IMAGE_KEY);
      hardwareSkin = AvdEditWizard.resolveSkinPath(device.getDefaultHardware().getSkinFile(), systemImage, FileOpUtils.create());
    }
    myState.put(DISPLAY_SKIN_FILE_KEY, hardwareSkin);

    // If customSkin is null but backupSkin is defined, we want to show it (with the checkbox unchecked).
    if (customSkin == null) {
      customSkin = backupSkin;
    }

    // If the skin is set and different from what would be provided by the hardware, set the value of the
    // control directly, so it is marked as user edited and not changed when the device is changed.
    if (customSkin != null && !FileUtil.filesEqual(customSkin, hardwareSkin)) {
      mySkinComboBox.getComboBox().setSelectedItem(customSkin);
    }
  }

  private void toggleSystemOptionals(boolean useRanchuChanged) {
    boolean showMultiCoreOption = isAdvancedPanel() && doesSystemImageSupportRanchu();
    myRanchuCheckBox.setVisible(showMultiCoreOption);
    myCoreCount.setVisible(showMultiCoreOption);
    myMultiCoreExperimentalLabel.setVisible(showMultiCoreOption);
    myMultiCoreDivider.setVisible(showMultiCoreOption);
    if (showMultiCoreOption) {
      boolean showCores = supportsMultipleCpuCores() && myState.getNotNull(RANCHU_KEY, false) && myMaxCores > 1;
      if (useRanchuChanged) {
        if (showCores) {
          myState.put(CPU_CORES_KEY, mySelectedCoreCount);
        }
        else {
          mySelectedCoreCount = myState.getNotNull(CPU_CORES_KEY, 1);
          myState.put(CPU_CORES_KEY, 1);
        }
      }
      myCoreCount.setEnabled(showCores);
    }
  }

  public static Storage calculateVmHeap(@NotNull Device device) {
    // Set the default VM heap size. This is based on the Android CDD minimums for each
    // screen size and density.
    Screen s = device.getDefaultHardware().getScreen();
    ScreenSize size = s.getSize();
    Density density = s.getPixelDensity();
    int vmHeapSize = 32;
    if (size.equals(ScreenSize.XLARGE)) {
      switch (density) {
        case LOW:
        case MEDIUM:
          vmHeapSize = 32;
          break;
        case TV:
        case HIGH:
        case DPI_280:
        case DPI_360:
          vmHeapSize = 64;
          break;
        case XHIGH:
        case DPI_400:
        case DPI_420:
        case XXHIGH:
        case DPI_560:
        case XXXHIGH:
          vmHeapSize = 128;
          break;
        case NODPI:
        case ANYDPI:
          break;
      }
    }
    else {
      switch (density) {
        case LOW:
        case MEDIUM:
          vmHeapSize = 16;
          break;
        case TV:
        case HIGH:
        case DPI_280:
        case DPI_360:
          vmHeapSize = 32;
          break;
        case XHIGH:
        case DPI_400:
        case DPI_420:
        case XXHIGH:
        case DPI_560:
        case XXXHIGH:
          vmHeapSize = 64;
          break;
        case NODPI:
        case ANYDPI:
          break;
      }
    }
    return new Storage(vmHeapSize, Unit.MiB);
  }

  private abstract static class MemoryValueDeriver extends ValueDeriver<Storage> {
    @Nullable
    @Override
    public Set<Key<?>> getTriggerKeys() {
      return makeSetOf(DEVICE_DEFINITION_KEY);
    }

    @Nullable
    @Override
    public Storage deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable Storage currentValue) {
      Device device = state.get(DEVICE_DEFINITION_KEY);
      if (device != null) {
        return getStorage(device);
      }
      else {
        return null;
      }
    }

    @Nullable
    protected abstract Storage getStorage(@NotNull Device device);
  }
}
