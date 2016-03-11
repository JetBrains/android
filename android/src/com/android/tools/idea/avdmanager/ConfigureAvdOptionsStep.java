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
import com.android.repository.io.FileOpUtils;
import com.android.resources.Keyboard;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.*;
import com.android.sdklib.internal.avd.GpuMode;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.tools.idea.ui.ASGallery;
import com.android.tools.idea.ui.properties.*;
import com.android.tools.idea.ui.properties.adapters.OptionalToValuePropertyAdapter;
import com.android.tools.idea.ui.properties.expressions.string.StringExpression;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.EnumComboBoxModel;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
  private JLabel myMultiCoreExperimentalLabel;
  private HyperlinkLabel myHardwareSkinHelpLabel;
  private JComboBox myCoreCount;
  private JComboBox mySpeedCombo;
  private JComboBox myLatencyCombo;
  private SkinChooser mySkinComboBox;
  private JComboBox myScalingComboBox;
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
  private JBLabel myHostGraphicProblem;
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
  private Iterable<JComponent> myAdvancedOptionsComponents;

  private Project myProject;
  private BindingsManager myBindings = new BindingsManager();
  private ListenerManager myListeners = new ListenerManager();

  /**
   * String used as a placeholder to verify that we are not using a repeated name.
   */
  private String myOriginalName;
  /**
   * Boolean used to control if we should warn the user about how changing the size of the SD will erase it.
   */
  private boolean myCheckSdForChanges;
  /**
   * Device's original Sd card
   */
  private Storage myOriginalSdCard;
  /**
   * The selected core count while enabled
   */
  private int mySelectedCoreCount;


  public ConfigureAvdOptionsStep(@Nullable Project project, @NotNull AvdOptionsModel model) {
    super(model, "Android Virtual Device (AVD)");
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
    GpuMode mode = getModel().hostGpuMode().getValueOr(GpuMode.AUTO);
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

  private boolean isGoogleApiSelected() {
    assert getModel().systemImage().get().isPresent();
    SystemImageDescription systemImage = getModel().systemImage().getValue();
    IdDisplay tag = systemImage.getTag();
    return AvdWizardUtils.WEAR_TAG.equals(tag) || AvdWizardUtils.TV_TAG.equals(tag) || AvdWizardUtils.GOOGLE_APIS_TAG.equals(tag);
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
    myScalingComboBox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Start-Up Size");
    myFrontCameraCombo.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Front Camera");
    myQemu2CheckBox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Number of cores");
    myInternalStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Internal Flash");
    myHostGraphics.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Graphics Rendering");
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

    // Add labelFor property for custom components since its not allowed from the designer
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
      avdDisplayName = getModel().device().getValue().getDisplayName();
      getModel().avdDisplayName()
        .set(connection.uniquifyDisplayName(String.format(Locale.getDefault(), "%1$s API %2$s", avdDisplayName, getSelectedApiString())));
    }

    myOriginalName = getModel().isInEditMode().get() ? getModel().avdDisplayName().get() : "";

    updateSystemImageData();

    myOriginalSdCard = getModel().sdCardStorage().getValue();

    mySelectedCoreCount = getModel().useQemu2().get() ? getModel().cpuCoreCount().getValueOr(1) : AvdOptionsModel.MAX_NUMBER_OF_CORES;
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

    myListeners.listen(getModel().device(), myDeviceConsumer);

    List<ObservableProperty<?>> deviceProperties = ObservableProperty.getAll(getModel().getAvdDeviceData());
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

    myListeners.listen(getModel().sdCardStorage(), mySdCardStorageConsumer);

    myListeners.listen(getModel().useQemu2(), new InvalidationListener() {
      @Override
      public void onInvalidated(@NotNull ObservableValue<?> sender) {
        toggleSystemOptionals(true);
      }
    });

    myListeners.listen(getModel().selectedAvdOrientation(), new Consumer<ScreenOrientation>() {
      @Override
      public void consume(ScreenOrientation screenOrientation) {
        myOrientationToggle.setSelectedElement(screenOrientation);
      }
    });
  }

  private void updateSystemImageData() {
    if (getModel().systemImage().get().isPresent()) {
      SystemImageDescription image = getModel().systemImage().getValue();

      String codeName = SdkVersionInfo.getCodeName(image.getVersion().getApiLevel());
      if (codeName != null) {
        getModel().systemImageName().set(codeName);
      }
      try {
        Icon icon = IconLoader.findIcon(String.format("/icons/versions/%s_32.png", codeName), AndroidIcons.class);
        mySystemImageName.setIcon(icon);
      }
      catch (RuntimeException ignored) {
      }

      getModel().systemImageDetails().set(image.getName() + " " + image.getAbiType());
      myAvdConfigurationOptionHelpPanel.setSystemImageDescription(image);
      updateGpuControlsAfterSystemImageChange();
      toggleSystemOptionals(false);
    }
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

  private Consumer<Optional<Device>> myDeviceConsumer = new Consumer<Optional<Device>>() {
    @Override
    public void consume(Optional<Device> device) {
      toggleOptionals(device, true);
      if (device.isPresent()) {
        myDeviceName.setIcon(DeviceDefinitionPreview.getIcon(getModel().getAvdDeviceData()));
        myDeviceName.setText(getModel().device().getValue().getDisplayName());
        updateDeviceDetails();
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
    myBindings.bindTwoWay(new SelectedItemProperty<Integer>(myCoreCount), getModel().cpuCoreCount());
    myBindings.bindTwoWay(myRamStorage.storage(), getModel().getAvdDeviceData().ramStorage());
    myBindings.bindTwoWay(myVmHeapStorage.storage(), getModel().vmHeapStorage());
    myBindings.bindTwoWay(myInternalStorage.storage(), getModel().internalStorage());
    myBindings.bindTwoWay(myBuiltInSdCardStorage.storage(), new OptionalToValuePropertyAdapter<Storage>(getModel().sdCardStorage()));

    myBindings.bindTwoWay(new SelectedItemProperty<GpuMode>(myHostGraphics), getModel().hostGpuMode());

    myBindings.bindTwoWay(new SelectedProperty(myDeviceFrameCheckbox), getModel().hasDeviceFrame());

    myBindings.bindTwoWay(new SelectedItemProperty<File>(mySkinComboBox.getComboBox()), getModel().getAvdDeviceData().customSkinFile() /*myDisplaySkinFile*/);
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

    myBindings.bindTwoWay(new OptionalToValuePropertyAdapter<AvdCamera>(new SelectedItemProperty<AvdCamera>(myFrontCameraCombo)),
                          getModel().selectedFrontCamera());
    myBindings.bindTwoWay(new OptionalToValuePropertyAdapter<AvdCamera>(new SelectedItemProperty<AvdCamera>(myBackCameraCombo)),
                          getModel().selectedBackCamera());

    myBindings.bindTwoWay(new OptionalToValuePropertyAdapter<AvdNetworkSpeed>(new SelectedItemProperty<AvdNetworkSpeed>(mySpeedCombo)),
                          getModel().selectedNetworkSpeed());
    myBindings
      .bindTwoWay(new OptionalToValuePropertyAdapter<AvdNetworkLatency>(new SelectedItemProperty<AvdNetworkLatency>(myLatencyCombo)),
                  getModel().selectedNetworkLatency());

    myBindings.bindTwoWay(new SelectedItemProperty<AvdScaleFactor>(myScalingComboBox), getModel().selectedAvdScale());

    myBindings.bindTwoWay(new SelectedProperty(myEnableComputerKeyboard), getModel().enableHardwareKeyboard());
    myBindings.bindTwoWay(new SelectedProperty(myExternalRadioButton), getModel().useExternalSdCard());
    myBindings.bindTwoWay(new SelectedProperty(myBuiltInRadioButton), getModel().useBuiltInSdCard());

    myCheckSdForChanges = true;
  }

  private void addValidators() {
    myValidatorPanel.registerValidator(getModel().getAvdDeviceData().ramStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage ram) {
        return (ram.getSizeAsUnit(Storage.Unit.MiB) < 128)
               ? new Result(Severity.ERROR, "RAM must be a numeric (integer) value of at least 128MB. Recommendation is 1GB.")
               : Result.OK;
      }
    });

    myValidatorPanel.registerValidator(getModel().vmHeapStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage heap) {
        return (heap.getSizeAsUnit(Storage.Unit.MiB) < 16)
               ? new Result(Severity.ERROR, "VM Heap must be a numeric (integer) value of at least 16MB.")
               : Result.OK;
      }
    });

    myValidatorPanel.registerValidator(getModel().internalStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage heap) {
        return (heap.getSizeAsUnit(Storage.Unit.MiB) < 200)
               ? new Result(Severity.ERROR, "Internal storage must be a numeric (integer) value of at least 200MB.")
               : Result.OK;
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

    // If we are not lets make sure it has the right amount of memory
    myValidatorPanel.registerValidator(getModel().sdCardStorage(), new Validator<Optional<Storage>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<Storage> value) {
        return (!getModel().useExternalSdCard().get() && getModel().sdCardStorage().get().isPresent() &&
                getModel().sdCardStorage().getValue().getSizeAsUnit(Storage.Unit.MiB) < 10)
               ? new Result(Severity.ERROR, "The SD card must be larger than 10MB")
               : Result.OK;
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

    myValidatorPanel.registerValidator(getModel().avdDisplayName(), new Validator<String>() {
      @NotNull
      @Override
      public Result validate(@NotNull String value) {
        value = value.trim();
        Severity severity = Severity.OK;
        String errorMessage = "";
        if (value.isEmpty()) {
          severity = Severity.ERROR;
          errorMessage = "The AVD name cannot be empty";
        }
        if (!value.matches("^[0-9a-zA-Z-_. ()]+$")) {
          severity = Severity.ERROR;
          errorMessage = "The AVD name can only contain the characters a-z A-Z 0-9 . _ - ( )";
        }
        if (!getModel().isInEditMode().get() && AvdManagerConnection.getDefaultAvdManagerConnection().findAvdWithName(value)) {
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

    myValidatorPanel.registerValidator(getModel().getAvdDeviceData().compatibleSkinSize(),
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
    toggleSystemOptionals(false);

    // The following is necessary to get the scrollpane to realize that its children have been
    // relaid out and now scrolling may or may not be needed.
    myScrollRootPane.setPreferredSize(myScrollRootPane.getLayout().preferredLayoutSize(myScrollRootPane));
  }

  private boolean isAdvancedPanel() {
    return myShowAdvancedSettingsButton.getText().equals(HIDE);
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
          mySelectedCoreCount = getModel().cpuCoreCount().getValueOr(AvdOptionsModel.MAX_NUMBER_OF_CORES);
          getModel().cpuCoreCount().setValue(1);
        }
      }
      myCoreCount.setEnabled(showCores);
    }
  }

  private boolean doesSystemImageSupportQemu2() {
    assert getModel().systemImage().get().isPresent();
    return AvdManagerConnection.doesSystemImageSupportQemu2(getModel().systemImage().getValue());
  }

  private int getSelectedApiLevel() {
    assert getModel().systemImage().get().isPresent();
    AndroidVersion version = getModel().systemImage().getValue().getVersion();
    return version.getApiLevel();
  }

  private void updateDeviceDetails() {
    Dimension dimension = getModel().getAvdDeviceData().getDeviceScreenDimension();
    String dimensionString = String.format(Locale.getDefault(), "%dx%d", dimension.width, dimension.height);
    String densityString = AvdScreenData.getScreenDensity(getModel().getAvdDeviceData().screenDpi().get()).getResourceValue();
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
      new ASGallery<ScreenOrientation>(JBList.createDefaultListModel(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE),
                                       orientationIconFunction, orientationNameFunction, JBUI.size(50, 50), null);

    myOrientationToggle.setCellMargin(JBUI.insets(5, 20, 4, 20));
    myOrientationToggle.setBackground(JBColor.background());
    myOrientationToggle.setForeground(JBColor.foreground());
    myScalingComboBox = new ComboBox(new EnumComboBoxModel<AvdScaleFactor>(AvdScaleFactor.class));
    myHardwareSkinHelpLabel = new HyperlinkLabel("How do I create a custom hardware skin?");
    myHardwareSkinHelpLabel.setHyperlinkTarget(AvdWizardUtils.CREATE_SKIN_HELP_LINK);
    mySkinComboBox = new SkinChooser(myProject);
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

    myChangeSystemImageButton.setEnabled(IsDevicePresent);
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

  private Consumer<Optional<Storage>> mySdCardStorageConsumer = new Consumer<Optional<Storage>>() {
    @Override
    public void consume(Optional<Storage> storageOptional) {
      if (myCheckSdForChanges &&
          getModel().sdCardStorage().get().isPresent() &&
          !getModel().sdCardStorage().getValue().equals(myOriginalSdCard)) {
        int result = Messages.showYesNoDialog((Project)null, "Changing the size of the built-in SD card will erase " +
                                                             "the current contents of the card. Continue?", "Confirm Data Wipe",
                                              AllIcons.General.QuestionDialog);
        if (result == Messages.YES) {
          myCheckSdForChanges = false;
        }
        else {
          getModel().sdCardStorage().setValue(myOriginalSdCard);
        }
      }
    }
  };
}