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
package com.android.tools.idea.avdmanager;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.repository.io.FileOpUtils;
import com.android.resources.Keyboard;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.*;
import com.android.sdklib.internal.avd.GpuMode;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.observable.*;
import com.android.tools.idea.observable.expressions.value.AsObjectProperty;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.wizard.deprecated.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.*;
import java.util.List;

/**
 * Options panel for configuring various AVD options. Has an "advanced" mode and a "simple" mode.
 * Help and error messaging appears on the right hand side.
 */
public class ConfigureAvdOptionsStep extends ModelWizardStep<AvdOptionsModel> {

  // Labels used for the advanced settings toggle button
  private static final String ADVANCED_SETTINGS = "Advanced Settings";
  private static final String SHOW = "Show " + ADVANCED_SETTINGS;
  private static final String HIDE = "Hide " + ADVANCED_SETTINGS;
  private static final String FOCUS_OWNER = "focusOwner";

  // @formatter:off
  private static final Map<ScreenOrientation, NamedIcon> ORIENTATIONS = ImmutableMap.of(
    ScreenOrientation.PORTRAIT, new NamedIcon("Portrait", AndroidIcons.Portrait),
    ScreenOrientation.LANDSCAPE, new NamedIcon("Landscape", AndroidIcons.Landscape));
  // @formatter:on

  final AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();

  private JPanel myRoot;
  private ValidatorPanel myValidatorPanel;
  private StudioWizardStepPanel myStudioWizardStepPanel;
  private AvdConfigurationOptionHelpPanel myAvdConfigurationOptionHelpPanel;

  private JBScrollPane myScrollPane;
  private JBLabel myAvdId;
  private JBLabel myRamLabel;
  private JLabel myAvdIdLabel;
  private JBLabel mySpeedLabel;
  private JBLabel myDeviceName;
  private JBLabel myVmHeapLabel;
  private JBLabel mySdCardLabel;
  private JBLabel myCameraLabel;
  private JBLabel myNetworkLabel;
  private JBLabel myLatencyLabel;
  private JBLabel myKeyboardLabel;
  private JBLabel myDeviceDetails;
  private JBLabel myBackCameraLabel;
  private JBLabel mySystemImageName;
  private JBLabel myFrontCameraLabel;
  private JBLabel mySystemImageDetails;
  private JBLabel mySkinDefinitionLabel;
  private JBLabel myInternalStorageLabel;
  private JBLabel myMemoryAndStorageLabel;
  private HyperlinkLabel myHardwareSkinHelpLabel;
  private JComboBox myCoreCount;
  private JComboBox mySpeedCombo;
  private JComboBox myLatencyCombo;
  private SkinChooser mySkinComboBox;
  private JComboBox myBackCameraCombo;
  private JComboBox myFrontCameraCombo;
  private JCheckBox myQemu2CheckBox;
  private JCheckBox myDeviceFrameCheckbox;
  private JCheckBox myEnableComputerKeyboard;
  private StorageField myRamStorage;
  private StorageField myVmHeapStorage;
  private StorageField myInternalStorage;
  private StorageField myBuiltInSdCardStorage;
  private JRadioButton myBuiltInRadioButton;
  private JRadioButton myExternalRadioButton;
  private ASGallery<ScreenOrientation> myOrientationToggle;
  private JButton myChangeDeviceButton;
  private JButton myChangeSystemImageButton;
  private JButton myShowAdvancedSettingsButton;
  private TextFieldWithBrowseButton myExternalSdCard;
  private JTextField myAvdDisplayName;
  private JBLabel myOrientationLabel;
  private JComboBox myHostGraphics;
  private JPanel myDevicePanel;
  private JPanel myAvdNamePanel;
  private JPanel myImagePanel;
  private JPanel myOrientationPanel;
  private JPanel myCameraPanel;
  private JPanel myNetworkPanel;
  private JPanel myPerformancePanel;
  private JPanel myStoragePanel;
  private JPanel myFramePanel;
  private JPanel myKeyboardPanel;
  private JPanel myQemu2Panel;
  private JPanel myAvdIdRow;
  private JPanel myCustomSkinPanel;
  private JPanel myScrollRootPane;
  private JPanel myBootOptionPanel;
  private JRadioButton myColdBootRadioButton;
  private JRadioButton myFastBootRadioButton;
  private Iterable<JComponent> myAdvancedOptionsComponents;

  private Project myProject;
  private BindingsManager myBindings = new BindingsManager();
  private ListenerManager myListeners = new ListenerManager();

  /**
   * String used as a placeholder to verify that we are not using a repeated name.
   */
  private String myOriginalName;
  /**
   * Device's original SD card size. Used to warn about changing the size.
   */
  private Storage myOriginalSdCard;
  /**
   * The selected core count while enabled
   */
  private int mySelectedCoreCount;

  private AvdOptionsModel myModel;

  public ConfigureAvdOptionsStep(@Nullable Project project, @NotNull AvdOptionsModel model) {
    super(model, "Android Virtual Device (AVD)");
    myModel = model;
    myValidatorPanel = new ValidatorPanel(this, myRoot);
    myStudioWizardStepPanel = new StudioWizardStepPanel(myValidatorPanel, "Verify Configuration");

    FormScalingUtil.scaleComponentTree(this.getClass(), myStudioWizardStepPanel);
    myOrientationToggle.setOpaque(false);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);

    myProject = project;

    registerAdvancedOptionsVisibility();
    myShowAdvancedSettingsButton.setText(SHOW);
    setAdvanceSettingsVisible(false);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);
    initCpuCoreDropDown();

    myFrontCameraCombo.setModel(new DefaultComboBoxModel(AvdCamera.values()));
    myBackCameraCombo.setModel(new DefaultComboBoxModel(AvdCamera.values()));
    mySpeedCombo.setModel(new DefaultComboBoxModel(AvdNetworkSpeed.values()));
    myLatencyCombo.setModel(new DefaultComboBoxModel(AvdNetworkLatency.values()));
  }

  private void initCpuCoreDropDown() {
    for (int core = 1; core <= AvdOptionsModel.MAX_NUMBER_OF_CORES; core++) {
      myCoreCount.addItem(core);
    }
  }

  private void populateHostGraphicsDropDown() {
    myHostGraphics.removeAllItems();
    GpuMode otherMode = gpuOtherMode(getSelectedApiLevel(), isIntel(), isGoogleApiSelected(), SystemInfo.isMac);

    myHostGraphics.addItem(GpuMode.AUTO);
    myHostGraphics.addItem(GpuMode.HOST);
    myHostGraphics.addItem(otherMode);
  }

  @VisibleForTesting
  static
  GpuMode gpuOtherMode(int apiLevel, boolean isIntel, boolean isGoogle, boolean isMac) {
    boolean supportGuest = (apiLevel >= 23) && isIntel && isGoogle;
    GpuMode otherMode = GpuMode.OFF;
    if (supportGuest) {
      otherMode = GpuMode.SWIFT;
    }
    return otherMode;
  }

  private void updateGpuControlsAfterSystemImageChange() {
    GpuMode mode = getModel().hostGpuMode().getValueOr(GpuMode.AUTO);
    populateHostGraphicsDropDown();
    switch (mode) {
      case AUTO:
        myHostGraphics.setSelectedIndex(0);
        break;
      case HOST:
        myHostGraphics.setSelectedIndex(1);
        break;
      case SWIFT:
      case OFF:
      default:
        myHostGraphics.setSelectedIndex(2);
        break;
    }
  }

  private boolean isGoogleApiSelected() {
    assert getModel().systemImage().get().isPresent();
    SystemImageDescription systemImage = getModel().systemImage().getValue();
    return isGoogleApiTag(systemImage.getTag());
  }

  @VisibleForTesting
  static
  boolean isGoogleApiTag(IdDisplay tag) {
    return SystemImage.WEAR_TAG.equals(tag) ||
           SystemImage.TV_TAG.equals(tag) ||
           SystemImage.GOOGLE_APIS_TAG.equals(tag);
  }

  private boolean isIntel() {
    return supportsMultipleCpuCores();
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    addTitles();
    addListeners();
    addValidators();
    bindComponents();
    initComponents();
  }

  @Override
  protected void onEntering() {
    updateComponents();
    myShowAdvancedSettingsButton.setText(SHOW);
    setAdvanceSettingsVisible(false);
    toggleOptionals(getModel().device().get(), false);
    if (getModel().useExternalSdCard().get()) {
      myBuiltInSdCardStorage.setEnabled(false);
      myExternalSdCard.setEnabled(true);
    }
    else {
      myBuiltInSdCardStorage.setEnabled(true);
      myExternalSdCard.setEnabled(false);
    }
    myModel.ensureMinimumMemory();
    // Set 'myOriginalSdCard' so we don't warn the user about making this change
    myOriginalSdCard = myModel.sdCardStorage().getValue();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  /**
   * Convenience method to add titles to be displayed in {@link AvdConfigurationOptionHelpPanel} when focus changes.
   */
  private void addTitles() {
    myAvdId.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "AVD Id");
    myRamStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Device RAM");
    myAvdDisplayName.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "AVD Name");
    myCoreCount.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Number of cores");
    mySpeedCombo.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Network Speed");
    myBackCameraCombo.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Back Camera");
    myLatencyCombo.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Network Latency");
    myFrontCameraCombo.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Front Camera");
    myQemu2CheckBox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Number of cores");
    myInternalStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Internal Flash");
    myHostGraphics.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Graphics Rendering");
    myBootOptionPanel.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Boot Option");
    myColdBootRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Boot Option");
    myFastBootRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Boot Option");
    mySkinComboBox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Custom Device Frame");
    myVmHeapStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Virtual Machine Heap");
    myOrientationToggle.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Default Orientation");
    myBuiltInSdCardStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Built-in SD Card Size");
    myDeviceFrameCheckbox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Enable device frame");
    myBuiltInRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Built-in SD Card Size");
    myEnableComputerKeyboard.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Enable keyboard input");
    myExternalSdCard.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Location of external SD card image");
    myExternalRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Location of external SD card image");
  }

  private void initComponents() {
    myCoreCount.setPreferredSize(myRamStorage.getPreferredSizeOfUnitsDropdown());
    setAdvanceSettingsVisible(false);

    // Add labelFor property for custom components since it's not allowed from the designer
    myAvdIdLabel.setLabelFor(myAvdId);
    myDeviceDetails.setLabelFor(myDeviceName);
    mySystemImageDetails.setLabelFor(mySystemImageName);
    myOrientationLabel.setLabelFor(myOrientationToggle);
    myRamLabel.setLabelFor(myRamStorage);
  }

  private void updateComponents() {
    myAvdConfigurationOptionHelpPanel.setSystemImageDescription(getModel().systemImage().getValueOrNull());
    myOrientationToggle.setSelectedElement(getModel().selectedAvdOrientation().get());

    String avdDisplayName;
    if (!getModel().isInEditMode().get() && getModel().systemImage().get().isPresent() && getModel().device().get().isPresent()) {
      // A device name might include the device's screen size as, e.g., 7". The " is not allowed in
      // a display name. Ensure that the display name does not include any forbidden characters.
      avdDisplayName = AvdNameVerifier.stripBadCharacters( getModel().device().getValue().getDisplayName() );

      getModel().avdDisplayName()
        .set(connection.uniquifyDisplayName(String.format(Locale.getDefault(), "%1$s API %2$s", avdDisplayName, getSelectedApiString())));
    }

    myOriginalName = getModel().isInEditMode().get() ? getModel().avdDisplayName().get() : "";

    updateSystemImageData();


    mySelectedCoreCount = getModel().useQemu2().get() ? getModel().cpuCoreCount().getValueOr(1)
                                                      : AvdOptionsModel.RECOMMENDED_NUMBER_OF_CORES;
  }

  private void addListeners() {
    myAvdId.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        myAvdId.requestFocusInWindow();
      }
    });

    myShowAdvancedSettingsButton.addActionListener(myToggleAdvancedSettingsListener);
    myChangeDeviceButton.addActionListener(myChangeDeviceButtonListener);
    myChangeSystemImageButton.addActionListener(myChangeSystemImageButtonListener);

    myExternalRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myExternalSdCard.setEnabled(true);
        myBuiltInSdCardStorage.setEnabled(false);
      }
    });
    myBuiltInRadioButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myExternalSdCard.setEnabled(false);
        myBuiltInSdCardStorage.setEnabled(true);
      }
    });

    myOrientationToggle.setOpaque(false);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUS_OWNER, myPropertyChangeListener);

    myListeners.receive(getModel().device(), device -> {
      toggleOptionals(device, true);
      if (device.isPresent()) {
        myDeviceName.setIcon(DeviceDefinitionPreview.getIcon(getModel().getAvdDeviceData()));
        myDeviceName.setText(getModel().device().getValue().getDisplayName());
        updateDeviceDetails();
      }
    });

    List<AbstractProperty<?>> deviceProperties = AbstractProperty.getAll(getModel().getAvdDeviceData());
    deviceProperties.add(getModel().systemImage());
    myListeners.listenAll(deviceProperties).with(new Runnable() {
      @Override
      public void run() {
        if (getModel().systemImage().get().isPresent() && getModel().getAvdDeviceData().customSkinFile().get().isPresent()) {
          File skin =
            AvdWizardUtils.resolveSkinPath(getModel().getAvdDeviceData().customSkinFile().getValue(), getModel().systemImage().getValue(),
                                           FileOpUtils.create());
          if (skin != null) {
            getModel().getAvdDeviceData().customSkinFile().setValue(skin);
            if (FileUtil.filesEqual(skin, AvdWizardUtils.NO_SKIN)) {
              myDeviceFrameCheckbox.setSelected(false);
            }
          }
          else {
            getModel().getAvdDeviceData().customSkinFile().setValue(AvdWizardUtils.NO_SKIN);
          }
        }
      }
    });

    myListeners.listen(getModel().systemImage(), new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        updateSystemImageData();
      }
    });

    myListeners.listen(getModel().useQemu2(), new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        toggleSystemOptionals(true);
      }
    });

    myListeners.receive(getModel().selectedAvdOrientation(),
                        screenOrientation -> myOrientationToggle.setSelectedElement(screenOrientation));
  }

  @VisibleForTesting
  void updateSystemImageData() {
    if (getModel().systemImage().get().isPresent()) {
      SystemImageDescription image = getModel().systemImage().getValue();

      String codeName = SdkVersionInfo.getCodeName(image.getVersion().getFeatureLevel());
      String displayName = codeName;
      if (displayName == null) {
        displayName = image.getVersion().getCodename();
      }
      if (displayName == null) {
        displayName = "";
      }
      getModel().systemImageName().set(displayName);

      Icon icon = null;
      try {
        icon = IconLoader.findIcon(String.format("/icons/versions/%s_32.png", codeName), AndroidIcons.class);
      }
      catch (RuntimeException ignored) {
      }
      if (icon == null) {
        try {
          icon = IconLoader.findIcon("/icons/versions/Default_32.png", AndroidIcons.class);
        }
        catch (RuntimeException ignored) {
        }
      }
      mySystemImageName.setIcon(icon);

      getModel().systemImageDetails().set(image.getName() + " " + image.getAbiType());
      myAvdConfigurationOptionHelpPanel.setSystemImageDescription(image);
      updateGpuControlsAfterSystemImageChange();
      toggleSystemOptionals(false);
    }
  }

  @VisibleForTesting
  @Nullable
  Icon getSystemImageIcon() {
    return mySystemImageName == null ? null : mySystemImageName.getIcon();
  }

  private final ActionListener myToggleAdvancedSettingsListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (isAdvancedPanel()) {
        myShowAdvancedSettingsButton.setText(SHOW);
        setAdvanceSettingsVisible(false);
      }
      else {
        myShowAdvancedSettingsButton.setText(HIDE);
        setAdvanceSettingsVisible(true);
      }
    }
  };

  private void bindComponents() {
    myBindings.bindTwoWay(new TextProperty(myAvdDisplayName), getModel().avdDisplayName());
    myBindings.bind(new TextProperty(myAvdId), new StringExpression(getModel().avdDisplayName()) {
      @NotNull
      @Override
      public String get() {
        String displayName = getModel().avdDisplayName().get();
        getModel().avdId().set(StringUtil.isNotEmpty(displayName) ?
                               AvdWizardUtils.cleanAvdName(connection, displayName, !displayName.equals(myOriginalName)) : "");
        return getModel().avdId().get();
      }
    });

    myBindings.bindTwoWay(new TextProperty(mySystemImageName), getModel().systemImageName());
    myBindings.bindTwoWay(new TextProperty(mySystemImageDetails), getModel().systemImageDetails());

    myBindings.bindTwoWay(new SelectedProperty(myQemu2CheckBox), getModel().useQemu2());
    myBindings.bindTwoWay(new SelectedItemProperty<>(myCoreCount), getModel().cpuCoreCount());
    myBindings.bindTwoWay(myRamStorage.storage(), getModel().getAvdDeviceData().ramStorage());
    myBindings.bindTwoWay(myVmHeapStorage.storage(), getModel().vmHeapStorage());
    myBindings.bindTwoWay(myInternalStorage.storage(), getModel().internalStorage());
    myBindings.bindTwoWay(myBuiltInSdCardStorage.storage(), new AsObjectProperty<>(getModel().sdCardStorage()));

    myBindings.bindTwoWay(new SelectedItemProperty<>(myHostGraphics), getModel().hostGpuMode());

    myBindings.bindTwoWay(new SelectedProperty(myDeviceFrameCheckbox), getModel().hasDeviceFrame());
    myBindings.bindTwoWay(new SelectedProperty(myColdBootRadioButton), getModel().useColdBoot());

    myBindings.bindTwoWay(new SelectedItemProperty<>(mySkinComboBox.getComboBox()), getModel().getAvdDeviceData().customSkinFile() /*myDisplaySkinFile*/);
    myOrientationToggle.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        ScreenOrientation orientation = myOrientationToggle.getSelectedElement();
        if (orientation == null) {
          getModel().selectedAvdOrientation().set(ScreenOrientation.PORTRAIT);
        }
        else {
          getModel().selectedAvdOrientation().set(orientation);
        }
      }
    });

    FileChooserDescriptor fileChooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return super.isFileVisible(file, true);
      }
    };

    fileChooserDescriptor.setHideIgnored(false);
    myExternalSdCard.addBrowseFolderListener("Select SD Card", "Select an existing SD card image", myProject, fileChooserDescriptor);

    myBindings.bindTwoWay(new TextProperty(myExternalSdCard.getTextField()), getModel().externalSdCardLocation());

    myBindings.bindTwoWay(new AsObjectProperty<>(new SelectedItemProperty<>(myFrontCameraCombo)), getModel().selectedFrontCamera());
    myBindings.bindTwoWay(new AsObjectProperty<>(new SelectedItemProperty<>(myBackCameraCombo)), getModel().selectedBackCamera());

    myBindings.bindTwoWay(new AsObjectProperty<>(new SelectedItemProperty<>(mySpeedCombo)), getModel().selectedNetworkSpeed());
    myBindings.bindTwoWay(new AsObjectProperty<>(new SelectedItemProperty<>(myLatencyCombo)), getModel().selectedNetworkLatency());

    myBindings.bindTwoWay(new SelectedProperty(myEnableComputerKeyboard), getModel().enableHardwareKeyboard());
    myBindings.bindTwoWay(new SelectedProperty(myExternalRadioButton), getModel().useExternalSdCard());
    myBindings.bindTwoWay(new SelectedProperty(myBuiltInRadioButton), getModel().useBuiltInSdCard());
  }

  // TODO: jameskaye Add unit tests for these validators. (b.android.com/230192)
  private void addValidators() {
    myValidatorPanel.registerValidator(getModel().getAvdDeviceData().ramStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage ram) {
        return (ram.getSizeAsUnit(Storage.Unit.MiB) < 128)
               ? new Result(Severity.ERROR, "RAM must be at least 128 MB. Recommendation is 1 GB.")
               : Result.OK;
      }
    });

    myValidatorPanel.registerValidator(getModel().vmHeapStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage heap) {
        return (heap.getSizeAsUnit(Storage.Unit.MiB) < 16)
               ? new Result(Severity.ERROR, "VM Heap must be at least 16 MB.")
               : Result.OK;
      }
    });

    myValidatorPanel.registerValidator(getModel().internalStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage internalMem) {
        if (!internalMem.lessThan(myModel.minInternalMemSize())) {
          return Result.OK;
        }
        String errorMessage = myModel.isPlayStoreCompatible() ?
                              "Internal storage for Play Store devices must be at least %s." :
                              "Internal storage must be at least %s.";
        return new Result(Severity.ERROR, String.format(errorMessage, myModel.minInternalMemSize()));
      }
    });

    // If we're using an external SD card, make sure it exists
    myValidatorPanel.registerValidator(getModel().externalSdCardLocation(), new Validator<String>() {
      @NotNull
      @Override
      public Result validate(@NotNull String path) {
        return (getModel().useExternalSdCard().get() && !new File(path).isFile())
               ? new Result(Severity.ERROR, "The specified SD image file must be a valid image file")
               : Result.OK;
      }
    });

    // If we are using an internal SD card, make sure it has enough memory.
    myValidatorPanel.registerValidator(getModel().sdCardStorage(), new Validator<Optional<Storage>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<Storage> value) {
        if (myOriginalSdCard == null) {
          myOriginalSdCard = getModel().sdCardStorage().getValue();
        }

        if (!getModel().useExternalSdCard().get() && getModel().sdCardStorage().get().isPresent()) {
          // Internal storage has been selected. Make sure it's big enough.
          if (getModel().sdCardStorage().getValue().lessThan(myModel.minSdCardSize())) {
            String errorMessage = myModel.isPlayStoreCompatible() ?
                                  "The SD card for Play Store devices must be at least %s." :
                                  "The SD card must be at least %s.";
            return new Result(Severity.ERROR, String.format(errorMessage, myModel.minSdCardSize()));
          }
        }
        if (!getModel().sdCardStorage().getValue().equals(myOriginalSdCard)) {
          return new Result(Severity.WARNING, "Modifying the SD card size will erase the card's contents! " +
                                              "Click Cancel to abort.");
        }
        return Result.OK;
      }
    });

    myValidatorPanel.registerValidator(getModel().getAvdDeviceData().customSkinFile(), new Validator<Optional<File>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<File> value) {
        Result result = Result.OK;
        if (value.isPresent() && !FileUtil.filesEqual(value.get(), AvdWizardUtils.NO_SKIN)) {
          File layoutFile = new File(value.get(), SdkConstants.FN_SKIN_LAYOUT);
          if (!layoutFile.isFile()) {
            result = new Result(Severity.ERROR, "The skin directory does not point to a valid skin.");
          }
        }
        return result;
      }
    });

    myOriginalName = getModel().avdDisplayName().get();

    myValidatorPanel.registerValidator(getModel().avdDisplayName(), new Validator<String>() {
      @NotNull
      @Override
      public Result validate(@NotNull String value) {
        value = value.trim();
        Severity severity = Severity.OK;
        String errorMessage = "";
        if (value.isEmpty()) {
          severity = Severity.ERROR;
          errorMessage = "The AVD name cannot be empty.";
        }
        else if (!AvdNameVerifier.isValid(value)) {
          severity = Severity.ERROR;
          errorMessage = "The AVD name can contain only the characters " + AvdNameVerifier.humanReadableAllowedCharacters();
        }
        else if ( !value.equals(myOriginalName) &&
            AvdManagerConnection.getDefaultAvdManagerConnection().findAvdWithName(value)) {
          // Another device with this name already exists
          severity = Severity.ERROR;
          errorMessage = String.format("An AVD with the name \"%1$s\" already exists.", getModel().avdDisplayName());
        }
        return new Result(severity, errorMessage);
      }
    });

    myValidatorPanel.registerValidator(getModel().device().isPresent().and(getModel().systemImage().isPresent()), new Validator<Boolean>() {
      @NotNull
      @Override
      public Result validate(@NotNull Boolean deviceAndImageArePresent) {
        if (deviceAndImageArePresent) {
          Optional<Device> device = getModel().device().get();
          Optional<SystemImageDescription> systemImage = getModel().systemImage().get();
          if (!ChooseSystemImagePanel.systemImageMatchesDevice(systemImage.get(), device.get())) {
            return new Validator.Result(Validator.Severity.ERROR, "The selected system image is incompatible with the selected device.");
          }
        }
        else {
          if (!getModel().device().get().isPresent()) {
            return new Result(Severity.ERROR, "You must select a Device to create an AVD.");
          }
          else if (!getModel().systemImage().get().isPresent()) {
            return new Result(Severity.ERROR, "You must select a System Image to create an AVD.");
          }
        }

        return Result.OK;
      }
    });

    myValidatorPanel.registerTest(getModel().getAvdDeviceData().compatibleSkinSize(),
                                  Validator.Severity.WARNING, "The selected skin is not large enough to view the entire screen.");
  }

  @Override
  protected void onProceeding() {
    boolean hasFrame = getModel().hasDeviceFrame().get();
    if (hasFrame && getModel().getAvdDeviceData().customSkinFile().get().isPresent()) {
      getModel().backupSkinFile().clear();
    }
    else {
      getModel().getAvdDeviceData().customSkinFile().setValue(AvdWizardUtils.NO_SKIN);
      getModel().backupSkinFile().set(getModel().getAvdDeviceData().customSkinFile());
    }

    if (getSelectedApiLevel() < 16 || getModel().hostGpuMode().getValueOrNull() == GpuMode.OFF) {
      getModel().useHostGpu().set(false);
      getModel().hostGpuMode().setValue(GpuMode.OFF);
    }
    else {
      getModel().useHostGpu().set(true);
    }
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioWizardStepPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAvdDisplayName;
  }

  private void setAdvanceSettingsVisible(boolean show) {
    for (JComponent c : myAdvancedOptionsComponents) {
      c.setVisible(show);
    }
    // Separately handle the Boot Option. It is only
    // shown if the Emulator supports it.
    myBootOptionPanel.setVisible(show && AvdWizardUtils.emulatorSupportsFastBoot(AndroidSdks.getInstance().tryToChooseSdkHandler()));

    toggleSystemOptionals(false);

    // The following is necessary to get the scrollpane to realize that its children have been
    // relaid out and now scrolling may or may not be needed.
    myScrollRootPane.setPreferredSize(myScrollRootPane.getLayout().preferredLayoutSize(myScrollRootPane));
  }

  private boolean isAdvancedPanel() {
    return myShowAdvancedSettingsButton.getText().equals(HIDE);
  }

  /**
   * Selectively enables or disables certain editing options <br>
   * If the selected device and system image both support Google Play Store,
   * restrict most of the configuration to ensure that the final AVD is
   * Play Store compatible.
   */
  private void enforcePlayStore() {
    boolean deviceIsPresent = getModel().device().isPresent().get();
    // Enable if NOT Play Store
    boolean enable = !myModel.isPlayStoreCompatible();

    // Enforce the restrictions
    myChangeDeviceButton.setEnabled(enable);
    myChangeSystemImageButton.setEnabled(enable && deviceIsPresent);

    myHostGraphics.setEnabled(enable);
    myQemu2CheckBox.setEnabled(enable);
    myRamStorage.setEnabled(enable);
    myVmHeapStorage.setEnabled(enable);
    myBuiltInRadioButton.setEnabled(enable);
    myExternalRadioButton.setEnabled(enable);
    mySkinComboBox.setEnabled(enable);
    if (!enable) {
      // Selectively disable, but don't enable
      myCoreCount.setEnabled(false);
    }
  }

  private void toggleSystemOptionals(boolean useQemu2Changed) {
    boolean showMultiCoreOption = isAdvancedPanel() && doesSystemImageSupportQemu2();
    myQemu2Panel.setVisible(showMultiCoreOption);
    if (showMultiCoreOption) {
      boolean showCores = supportsMultipleCpuCores() && getModel().useQemu2().get() && AvdOptionsModel.MAX_NUMBER_OF_CORES > 1;
      if (useQemu2Changed) {
        if (showCores) {
          getModel().cpuCoreCount().setValue(mySelectedCoreCount);
        }
        else {
          mySelectedCoreCount = getModel().cpuCoreCount().getValueOr(AvdOptionsModel.RECOMMENDED_NUMBER_OF_CORES);
          getModel().cpuCoreCount().setValue(1);
        }
      }
      myCoreCount.setEnabled(showCores);
    }
    enforcePlayStore();
  }

  private boolean doesSystemImageSupportQemu2() {
    assert getModel().systemImage().get().isPresent();
    return AvdManagerConnection.doesSystemImageSupportQemu2(getModel().systemImage().getValue(), FileOpUtils.create());
  }

  private int getSelectedApiLevel() {
    assert getModel().systemImage().get().isPresent();
    AndroidVersion version = getModel().systemImage().getValue().getVersion();
    return version.getApiLevel();
  }

  private void updateDeviceDetails() {
    Dimension dimension = getModel().getAvdDeviceData().getDeviceScreenDimension();
    String dimensionString = String.format(Locale.getDefault(), "%dx%d", dimension.width, dimension.height);
    AvdDeviceData deviceData = getModel().getAvdDeviceData();
    String densityString = AvdScreenData.getScreenDensity(deviceData.isTv().get(),
                                                          deviceData.screenDpi().get(),
                                                          dimension.height).getResourceValue();
    String result = Joiner.on(' ')
      .join(getModel().device().getValue().getDefaultHardware().getScreen().getDiagonalLength(), dimensionString, densityString);
    myDeviceDetails.setText(result);
  }

  private String getSelectedApiString() {
    assert getModel().systemImage().get().isPresent();
    AndroidVersion version = getModel().systemImage().getValue().getVersion();
    return version.getApiString();
  }

  private void registerAdvancedOptionsVisibility() {
    myAdvancedOptionsComponents =
      Lists.<JComponent>newArrayList(myStoragePanel, myCameraPanel, myNetworkPanel, myQemu2Panel, myKeyboardPanel, myCustomSkinPanel,
                                     myAvdIdRow);
  }

  @Override
  public void dispose() {
    super.dispose();
    if (myPropertyChangeListener != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(FOCUS_OWNER, myPropertyChangeListener);
    }
  }

  private void createUIComponents() {
    Function<ScreenOrientation, Image> orientationIconFunction = new Function<ScreenOrientation, Image>() {
      @Override
      public Image apply(ScreenOrientation input) {
        return IconUtil.toImage(ORIENTATIONS.get(input).myIcon);
      }
    };
    Function<ScreenOrientation, String> orientationNameFunction = new Function<ScreenOrientation, String>() {
      @Override
      public String apply(ScreenOrientation input) {
        return ORIENTATIONS.get(input).myName;
      }
    };
    myOrientationToggle =
      new ASGallery<>(JBList.createDefaultListModel(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE),
                      orientationIconFunction, orientationNameFunction, JBUI.size(50, 50), null);

    myOrientationToggle.setCellMargin(JBUI.insets(5, 20, 4, 20));
    myOrientationToggle.setBackground(JBColor.background());
    myOrientationToggle.setForeground(JBColor.foreground());
    myHardwareSkinHelpLabel = new HyperlinkLabel("How do I create a custom hardware skin?");
    myHardwareSkinHelpLabel.setHyperlinkTarget(AvdWizardUtils.CREATE_SKIN_HELP_LINK);
    mySkinComboBox = new SkinChooser(myProject, true);
  }

  private static final class NamedIcon {

    @NotNull private final String myName;

    @NotNull private final Icon myIcon;

    public NamedIcon(@NotNull String name, @NotNull Icon icon) {
      myName = name;
      myIcon = icon;
    }
  }

  private boolean supportsMultipleCpuCores() {
    assert getModel().systemImage().get().isPresent();
    Abi abi = Abi.getEnum(getModel().systemImage().getValue().getAbiType());
    return abi != null && abi.supportsMultipleCpuCores();
  }

  /**
   * Enable/Disable controls based on the capabilities of the selected device. For example, some devices may
   * not have a front facing camera.
   */
  private void toggleOptionals(@NotNull Optional<Device> device, boolean deviceChange) {
    boolean IsDevicePresent = device.isPresent();
    Hardware deviceDefaultHardware = IsDevicePresent ? device.get().getDefaultHardware() : null;

    myFrontCameraCombo.setEnabled(IsDevicePresent && deviceDefaultHardware.getCamera(CameraLocation.FRONT) != null);
    myBackCameraCombo.setEnabled(IsDevicePresent && deviceDefaultHardware.getCamera(CameraLocation.BACK) != null);
    myOrientationToggle.setEnabled(IsDevicePresent && device.get().getDefaultState().getOrientation() != ScreenOrientation.SQUARE);
    myEnableComputerKeyboard.setEnabled(IsDevicePresent && !deviceDefaultHardware.getKeyboard().equals(Keyboard.QWERTY));
    if (deviceChange) {
      ScreenOrientation orientation = IsDevicePresent ? device.get().getDefaultState().getOrientation() : ScreenOrientation.PORTRAIT;
      myOrientationToggle.setSelectedElement(orientation);
    }

    File customSkin = getModel().getAvdDeviceData().customSkinFile().getValueOrNull();
    File backupSkin = getModel().backupSkinFile().getValueOrNull();
    // If there is a backup skin but no normal skin, the "use device frame" checkbox should be unchecked.
    if (backupSkin != null && customSkin == null) {
      getModel().hasDeviceFrame().set(false);
    }
    File hardwareSkin = null;
    if (IsDevicePresent && getModel().systemImage().get().isPresent()) {

      hardwareSkin =
        AvdWizardUtils.resolveSkinPath(deviceDefaultHardware.getSkinFile(), getModel().systemImage().getValue(),
                                       FileOpUtils.create());

      myDeviceName.setIcon(DeviceDefinitionPreview.getIcon(getModel().getAvdDeviceData()));
      myDeviceName.setText(getModel().device().getValue().getDisplayName());
      updateDeviceDetails();
    }

    if (customSkin == null) {
      if (backupSkin != null) {
        customSkin = backupSkin;
      }
      else {
        customSkin = hardwareSkin;
      }
    }

    if (customSkin != null) {
      mySkinComboBox.getComboBox().setSelectedItem(customSkin);
      getModel().getAvdDeviceData().customSkinFile().setValue(customSkin);
    }
    enforcePlayStore();
  }

  private ActionListener myChangeSystemImageButtonListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      final ChooseSystemImagePanel chooseImagePanel =
        new ChooseSystemImagePanel(myProject, getModel().device().getValueOrNull(), getModel().systemImage().getValueOrNull());

      DialogWrapper dialog = new DialogWrapper(myProject) {
        {
          setTitle("Select a System Image");
          init();
          chooseImagePanel.addSystemImageListener(new Consumer<SystemImageDescription>() {
            @Override
            public void consume(SystemImageDescription systemImage) {
              setOKActionEnabled(systemImage != null);
            }
          });
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
          return chooseImagePanel;
        }
      };

      if (dialog.showAndGet()) {
        SystemImageDescription image = chooseImagePanel.getSystemImage();

        if (image != null) {
          getModel().systemImage().setValue(image);
        }
        myModel.ensureMinimumMemory();
        // Set 'myOriginalSdCard' so we don't warn the user about making this change
        myOriginalSdCard = myModel.sdCardStorage().getValue();
      }
    }
  };

  private ActionListener myChangeDeviceButtonListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      final ChooseDeviceDefinitionPanel chooseDevicePanel = new ChooseDeviceDefinitionPanel(getModel().device().getValueOrNull());
      DialogWrapper dialog = new DialogWrapper(myProject) {
        {
          setTitle("Select a Device");
          init();
          chooseDevicePanel.addDeviceListener(new Consumer<Device>() {
            @Override
            public void consume(Device device) {
              setOKActionEnabled(device != null);
            }
          });
        }

        @Nullable
        @Override
        protected JComponent createCenterPanel() {
          return chooseDevicePanel;
        }
      };

      if (dialog.showAndGet()) {
        getModel().device().setNullableValue(chooseDevicePanel.getDevice());
      }
    }
  };

  private PropertyChangeListener myPropertyChangeListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      Object value = evt.getNewValue();
      if (evt.getNewValue() instanceof JComponent) {
        JComponent component = (JComponent)value;
        if (component.getToolTipText() != null) {
          myAvdConfigurationOptionHelpPanel.setValues(component);
        }
        else if (component.getParent() instanceof JComponent) {
          final JComponent parent = (JComponent)component.getParent();
          if (parent.getToolTipText() != null) {
            myAvdConfigurationOptionHelpPanel.setValues(parent);
          }
          else {
            myAvdConfigurationOptionHelpPanel.clearValues();
          }
        }

        if (component.getParent() instanceof JComponent) {
          final JComponent parent = (JComponent)component.getParent();
          parent.scrollRectToVisible(component.getBounds());
        }
      }
    }
  };

}
