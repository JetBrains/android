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
package com.android.tools.idea.avdmanager.ui;

import static com.android.SdkConstants.FN_BUILD_PROP;
import static com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI;
import static com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABI2;
import static com.android.adblib.DevicePropertyNames.RO_PRODUCT_CPU_ABILIST;

import com.android.emulator.SnapshotProtoException;
import com.android.emulator.SnapshotProtoParser;
import com.android.io.CancellableFileIo;
import com.android.repository.io.FileOpUtils;
import com.android.resources.Keyboard;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.devices.CameraLocation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Hardware;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.internal.avd.AvdCamera;
import com.android.sdklib.internal.avd.AvdNetworkLatency;
import com.android.sdklib.internal.avd.AvdNetworkSpeed;
import com.android.sdklib.internal.avd.EmulatedProperties;
import com.android.sdklib.internal.avd.EmulatorAdvancedFeatures;
import com.android.sdklib.internal.avd.EmulatorFeaturesChannel;
import com.android.sdklib.internal.avd.EmulatorPackage;
import com.android.sdklib.internal.avd.EmulatorPackages;
import com.android.sdklib.internal.avd.GpuMode;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.adtui.ASGallery;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.AvdNameVerifier;
import com.android.tools.idea.avdmanager.EmulatorFeatures;
import com.android.tools.idea.avdmanager.SkinUtils;
import com.android.tools.idea.avdmanager.SystemImageDescription;
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector;
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBox;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.log.LogWrapper;
import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.SettableValue;
import com.android.tools.idea.observable.core.ObjectProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.ObservableOptional;
import com.android.tools.idea.observable.expressions.string.StringExpression;
import com.android.tools.idea.observable.ui.EnabledProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.JBUI;
import icons.AndroidIcons;
import icons.StudioIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

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
    ScreenOrientation.PORTRAIT, new NamedIcon("Portrait", StudioIcons.Avd.PORTRAIT),
    ScreenOrientation.LANDSCAPE, new NamedIcon("Landscape", StudioIcons.Avd.LANDSCAPE));
  // @formatter:on

  final AvdManagerConnection connection = AvdManagerConnection.getDefaultAvdManagerConnection();

  private JPanel myRoot;
  private final ValidatorPanel myValidatorPanel;
  private final StudioWizardStepPanel myStudioWizardStepPanel;
  private AvdConfigurationOptionHelpPanel myAvdConfigurationOptionHelpPanel;

  private JBScrollPane myScrollPane;
  private JBLabel myAvdId;
  private JBLabel myRamLabel;
  private JLabel myAvdIdLabel;
  private JBLabel myDeviceName;
  private JBLabel myDeviceDetails;
  private JBLabel mySystemImageName;
  private JBLabel mySystemImageDetails;
  private JBLabel mySkinDefinitionLabel;
  private BrowserLink myHardwareSkinHelpLabel;
  private JComboBox<Integer> myCoreCount;
  private JComboBox<AvdNetworkSpeed> mySpeedCombo;
  private JComboBox<AvdNetworkLatency> myLatencyCombo;
  private SkinComboBox mySkinComboBox;
  private JComboBox<AvdCamera> myBackCameraCombo;
  private JComboBox<AvdCamera> myFrontCameraCombo;
  private JCheckBox myQemu2CheckBox;
  private JCheckBox myDeviceFrameCheckbox;
  private JCheckBox myEnableComputerKeyboard;
  private StorageField myRamStorage;
  private StorageField myVmHeapStorage;
  private StorageField myInternalStorage;
  private StorageField myBuiltInSdCardStorage;
  private JRadioButton myBuiltInRadioButton;
  private JRadioButton myExternalRadioButton;
  private JRadioButton myNoSDCardRadioButton;
  private ASGallery<ScreenOrientation> myOrientationToggle;
  private JButton myChangeDeviceButton;
  private JButton myChangeSystemImageButton;
  private JButton myShowAdvancedSettingsButton;
  private TextFieldWithBrowseButton myExternalSdCard;
  private JTextField myAvdDisplayName;
  private JBLabel myOrientationLabel;
  private JComboBox<GpuMode> myHostGraphics;
  private JPanel myOrientationPanel;
  private JPanel myCameraPanel;
  private JPanel myNetworkPanel;
  private JPanel myStoragePanel;
  private JPanel myKeyboardPanel;
  private JPanel myQemu2Panel;
  private JPanel myAvdIdRow;
  private JPanel myCustomSkinPanel;
  private JPanel myScrollRootPane;
  private JPanel myBootOptionPanel;
  private JRadioButton myColdBootRadioButton;
  private JRadioButton myFastBootRadioButton;
  private JRadioButton myChooseBootRadioButton;
  private JComboBox<String> myChosenSnapshotComboBox;
  private JBLabel myDeviceFrameTitle;
  private JPanel myCommandLineOptionsPanel;
  private JTextArea myCommandLineOptions;
  private JComboBox<String> myPreferredAbi;
  private Iterable<JComponent> myAdvancedOptionsComponents;

  private final Project myProject;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

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

  private final AvdOptionsModel myModel;

  @NotNull
  private String mySelectedSnapshotFileName = "";

  private class SnapshotListItem implements Comparable<SnapshotListItem> {
    public final String fileName;
    public final String logicalName;
    public final Long date;

    public SnapshotListItem(@NotNull String theFileName, @NotNull String theLogicalName, Long theDate) {
      fileName = theFileName;
      logicalName = theLogicalName;
      date = theDate;
    }

    @Override
    public int compareTo(@NotNull SnapshotListItem that) {
      // Sort by:
      //   a) Selected fileName
      //   b) Date
      boolean thisIsSelected = mySelectedSnapshotFileName.equals(fileName);
      boolean thatIsSelected = mySelectedSnapshotFileName.equals(that.fileName);

      if (thisIsSelected) return thatIsSelected ? 0 : -1;
      if (thatIsSelected) return 1;

      return date.compareTo(that.date);
    }
  }

  private ArrayList<SnapshotListItem> mySnapshotList;

  private EmulatorPackage myEmulator;
  private Set<String> myEmulatorFeatures;

  public ConfigureAvdOptionsStep(@Nullable Project project, @NotNull AvdOptionsModel model) {
    this(project, model, new SkinComboBox(project, SkinCollector::updateAndCollect));
  }

  @VisibleForTesting
  ConfigureAvdOptionsStep(@Nullable Project project, @NotNull AvdOptionsModel model, @NotNull SkinComboBox skinComboBox) {
    super(model, "Android Virtual Device (AVD)");
    myModel = model;
    initSkinComboBox(skinComboBox);

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
    myPreferredAbi.setModel(new DefaultComboBoxModel<>(new String[]{null}));
    myPreferredAbi.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        if (value == null) {
          setText("Optimal");
        }
        return c;
      }
    });
    mySelectedSnapshotFileName = getModel().chosenSnapshotFile().get();
    populateSnapshotList();
    refreshSnapshotPullDown();
    myChosenSnapshotComboBox.addItemListener(mySnapshotComboListener);

    myEmulator =
        EmulatorPackages.getEmulatorPackage(
            AndroidSdks.getInstance().tryToChooseSdkHandler(),
            new StudioLoggerProgressIndicator(ConfigureAvdOptionsStep.class));
    myEmulatorFeatures = EmulatorFeatures.getEmulatorFeatures(myEmulator);

    boolean supportsVirtualCamera = myEmulatorFeatures.contains(EmulatorAdvancedFeatures.VIRTUAL_SCENE);

    setupCameraComboBox(myFrontCameraCombo, false);
    setupCameraComboBox(myBackCameraCombo, supportsVirtualCamera);

    mySpeedCombo.setModel(new DefaultComboBoxModel<>(AvdNetworkSpeed.values()));
    myLatencyCombo.setModel(new DefaultComboBoxModel<>(AvdNetworkLatency.values()));
  }

  private void initSkinComboBox(@NotNull SkinComboBox skinComboBox) {
    skinComboBox.load();
    mySkinComboBox = skinComboBox;

    GridConstraints constraints = new GridConstraints();
    constraints.setColumn(1);
    constraints.setAnchor(GridConstraints.ANCHOR_WEST);

    myCustomSkinPanel.add(skinComboBox, constraints);
  }

  private static void setupCameraComboBox(JComboBox<AvdCamera> comboBox, boolean withVirtualScene) {
    AvdCamera[] allCameras = AvdCamera.values();
    if (!withVirtualScene) {
      List<AvdCamera> allCamerasButVirtualSceneList = new ArrayList<>(Arrays.asList(allCameras));
      allCamerasButVirtualSceneList.remove(AvdCamera.VIRTUAL_SCENE);
      comboBox.setModel(new DefaultComboBoxModel<>(allCamerasButVirtualSceneList.toArray(new AvdCamera[0])));
    }
    else {
      comboBox.setModel(new DefaultComboBoxModel<>(allCameras));
    }
    comboBox.setToolTipText("<html>" +
                            "None - no camera installed for AVD<br>" +
                            (withVirtualScene ? "VirtualScene - use a virtual camera in a simulated environment<br>" : "") +
                            "Emulated - use a simulated camera<br>" +
                            "Device - use host computer webcam or built-in camera" +
                            "</html>");
  }

  private void initCpuCoreDropDown() {
    for (int core = 1; core <= EmulatedProperties.MAX_NUMBER_OF_CORES; core++) {
      myCoreCount.addItem(core);
    }
  }

  private void updatePreferredAbiComboBox(@NotNull AvdOptionsModel model) {
    myPreferredAbi.setModel(new DefaultComboBoxModel<>(new String[]{null})); // fallback

    if (!StudioFlags.RISC_V.get()) {
      return;
    }

    SystemImageDescription sysImgDesc = model.systemImage().getValueOrNull();
    if (sysImgDesc == null) {
      return;
    }
    Path systemImageLocation = sysImgDesc.getSystemImage().getLocation();
    PathFileWrapper buildPropFile = new PathFileWrapper(systemImageLocation.resolve(FN_BUILD_PROP));
    if (!buildPropFile.exists()) {
      return;
    }

    Map<String, String> buildProp = ProjectProperties.parsePropertyFile(buildPropFile, null);
    if (buildProp == null) {
      return;
    }

    String systemAbiList = buildProp.getOrDefault("ro.system.product.cpu.abilist", "");
    List<String> abiList = systemAbiList.isBlank() ? null : Arrays.asList(systemAbiList.split(","));
    if (abiList == null) {
      String cpuAbiList = buildProp.getOrDefault(RO_PRODUCT_CPU_ABILIST, "");
      abiList = cpuAbiList.isBlank() ? null : Arrays.asList(cpuAbiList.split(","));
    }
    if (abiList == null) {
      abiList = new ArrayList<>(2);
      String property = buildProp.get(RO_PRODUCT_CPU_ABI);
      if (property != null) {
        abiList.add(property);
      }
      property = buildProp.get(RO_PRODUCT_CPU_ABI2);
      if (property != null) {
        abiList.add(property);
      }
    }
    DefaultComboBoxModel<String> comboBoxModel = new DefaultComboBoxModel<>();
    comboBoxModel.addElement(null);
    comboBoxModel.addAll(abiList);
    myPreferredAbi.setModel(comboBoxModel);
  }

  @TestOnly
  @NotNull
  public List<String> getSnapshotNamesList(@NotNull String selectedSnapshotFileName) {
    mySelectedSnapshotFileName = selectedSnapshotFileName;
    populateSnapshotList();
    List<String> nameList = new ArrayList<>();
    mySnapshotList.forEach(item -> nameList.add(item.fileName));
    return nameList;
  }

  private void populateSnapshotList() {
    mySnapshotList = new ArrayList<>();
    if (myModel == null) {
      return;
    }
    Path avdDir = myModel.getAvdLocation();
    if (avdDir == null) {
      return;
    }
    if (!CancellableFileIo.isDirectory(avdDir)) {
      return;
    }
    Path snapshotBaseDir = avdDir.resolve("snapshots");
    Path[] possibleSnapshotDirs = FileOpUtils.listFiles(snapshotBaseDir);
    if (possibleSnapshotDirs.length == 0) {
      return;
    }
    // Check every subdirectory under "snapshots/"
    for (Path snapshotDir : possibleSnapshotDirs) {
      if (!CancellableFileIo.isDirectory(snapshotDir)) continue;
      Path snapshotProtoBuf = snapshotDir.resolve("snapshot.pb");
      if (CancellableFileIo.notExists(snapshotProtoBuf)) continue;
      String snapshotFileName = snapshotDir.getFileName().toString();
      if ("default_boot".equals(snapshotFileName)) continue; // Don't include the "Quick boot" option
      try {
        SnapshotProtoParser protoParser = new SnapshotProtoParser(snapshotProtoBuf, snapshotFileName);
        String logicalName = protoParser.getLogicalName();
        if (!logicalName.isEmpty()) {
          mySnapshotList.add(new SnapshotListItem(snapshotFileName, logicalName, protoParser.getCreationTime()));
        }
      }
      catch (SnapshotProtoException ssException) {
        // Ignore this directory
        Logger.getInstance(ConfigureAvdOptionsStep.class)
          .info("Could not parse Snapshot protobuf: " + snapshotFileName, ssException);
      }
    }
    Collections.sort(mySnapshotList);
  }

  private void refreshSnapshotPullDown() {
    CollectionComboBoxModel<String> snapshotModel = new CollectionComboBoxModel<>();
    // Put up to 3 snapshots onto the pull-down
    mySnapshotList.stream()
      .limit(3)
      .forEach(item -> snapshotModel.add(item.logicalName));
    int numNotShown = mySnapshotList.size() - snapshotModel.getSize();
    String finalLine = (mySnapshotList.isEmpty()) ? "(no snapshots)" :
                       (numNotShown == 0) ? "  Details ..." :
                       String.format(Locale.US, "  Details ... (+%d others)", numNotShown);
    snapshotModel.add(finalLine);
    myChosenSnapshotComboBox.setModel(snapshotModel);
    myChosenSnapshotComboBox.setSelectedIndex(0);
    // Make sure the boot mode is compatible with the snapshots
    // that we found.
    if (mySnapshotList.isEmpty()) {
      mySelectedSnapshotFileName = "";
      myChosenSnapshotComboBox.setEnabled(false);
      myChooseBootRadioButton.setEnabled(false);
      if (getModel().useChosenSnapshotBoot().get()) {
        getModel().useChosenSnapshotBoot().set(false);
        getModel().useFastBoot().set(true);
      }
    }
    else {
      boolean previousSelectionExists = (mySelectedSnapshotFileName.equals(mySnapshotList.get(0).fileName));
      mySelectedSnapshotFileName = mySnapshotList.get(0).fileName;
      myChosenSnapshotComboBox.setEnabled(true);
      myChooseBootRadioButton.setEnabled(true);
      if (getModel().useChosenSnapshotBoot().get() && !previousSelectionExists) {
        // The boot mode says to use a chosen snapshot, but that snapshot
        // was not found. Change the boot mode.
        getModel().useChosenSnapshotBoot().set(false);
        getModel().useColdBoot().set(true);
        getModel().chosenSnapshotFile().set(mySelectedSnapshotFileName);
        // Note: If the user clicks Cancel, these changes will not be saved.
        //       That's actually OK: we'll command the Emulator with an
        //       invalid snapshot and the Emulator will Cold Boot. The actual
        //       behavior is exactly what the UI says.
      }
    }
  }

  private void populateHostGraphicsDropDown() {
    myHostGraphics.removeAllItems();
    GpuMode otherMode = gpuOtherMode(getSelectedApiLevel(), isIntel(), isGoogleApiSelected());

    myHostGraphics.addItem(GpuMode.AUTO);
    myHostGraphics.addItem(GpuMode.HOST);
    myHostGraphics.addItem(otherMode);
  }

  @VisibleForTesting
  static GpuMode gpuOtherMode(int apiLevel, boolean isIntel, boolean isGoogle) {
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

    myHostGraphics.setSelectedIndex(switch (mode) {
      case AUTO -> 0;
      case HOST -> 1;
      default -> 2;
    });
  }

  private boolean isGoogleApiSelected() {
    assert getModel().systemImage().get().isPresent();
    return getModel().systemImage().getValue().hasGoogleApis();
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
    toggleOptionals(getModel().device().get().orElse(null));
    if (getModel().useExternalSdCard().get()) {
      myBuiltInSdCardStorage.setEnabled(false);
      myExternalSdCard.setEnabled(true);
    }
    else if (getModel().useBuiltInSdCard().get()) {
      myBuiltInSdCardStorage.setEnabled(true);
      myExternalSdCard.setEnabled(false);
    }
    else {
      myExternalSdCard.setEnabled(false);
      myBuiltInSdCardStorage.setEnabled(false);
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
    myChooseBootRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Boot Option");
    myChosenSnapshotComboBox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Boot Option");
    mySkinComboBox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Custom Device Frame");
    myVmHeapStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Virtual Machine Heap");
    myOrientationToggle.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Default Orientation");
    myBuiltInSdCardStorage.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Built-in SD Card Size");
    myDeviceFrameCheckbox.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Enable device frame");
    myBuiltInRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Built-in SD Card Size");
    myEnableComputerKeyboard.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Enable keyboard input");
    myExternalSdCard.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Location of external SD Card image");
    myExternalRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Location of external SD Card image");
    myNoSDCardRadioButton.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "No SD Card");
    myCommandLineOptions.putClientProperty(AvdConfigurationOptionHelpPanel.TITLE_KEY, "Command Line Options");
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

    if (!getModel().isInEditMode().get() && getModel().systemImage().get().isPresent() && getModel().device().get().isPresent()) {
      Device device = getModel().device().getValue();
      AndroidVersion version = getModel().systemImage().getValue().getVersion();
      getModel().avdDisplayName().set(connection.getDefaultDeviceDisplayName(device, version));
    }

    myOriginalName = getModel().isInEditMode().get() ? getModel().avdDisplayName().get() : "";

    updateSystemImageData();

    updatePreferredAbiComboBox(getModel());

    mySelectedCoreCount = getModel().useQemu2().get() ? getModel().cpuCoreCount().getValueOr(1)
                                                      : EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES;
  }

  private boolean shouldEnableDeviceFrameCheckbox() {
    // Enable the checkbox iff the AVD has the custom skin to use when the checkbox is turned on.
    var skin = getModel().getAvdDeviceData().customSkinFile().get();
    return skin.isPresent() && !skin.orElseThrow().equals(SkinUtils.noSkin());
  }

  @VisibleForTesting
  void addListeners() {
    myAvdId.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        myAvdId.requestFocusInWindow();
      }
    });

    myShowAdvancedSettingsButton.addActionListener(myToggleAdvancedSettingsListener);
    myChangeDeviceButton.addActionListener(myChangeDeviceButtonListener);
    myChangeSystemImageButton.addActionListener(myChangeSystemImageButtonListener);

    myExternalRadioButton.addActionListener(event -> {
      myExternalSdCard.setEnabled(true);
      myBuiltInSdCardStorage.setEnabled(false);
    });
    myBuiltInRadioButton.addActionListener(event -> {
      myExternalSdCard.setEnabled(false);
      myBuiltInSdCardStorage.setEnabled(true);
    });
    myNoSDCardRadioButton.addActionListener(event -> {
      myExternalSdCard.setEnabled(false);
      myBuiltInSdCardStorage.setEnabled(false);
    });

    myOrientationToggle.setOpaque(false);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUS_OWNER, myPropertyChangeListener);

    myListeners.listen(getModel().device(), device -> {
      toggleOptionals(device.orElse(null));
      if (device.isPresent()) {
        myDeviceName.setIcon(DeviceDefinitionPreview.getIcon(getModel().getAvdDeviceData()));
        myDeviceName.setText(getModel().device().getValue().getDisplayName());
        updateDeviceDetails();
      }
    });

    List<AbstractProperty<?>> deviceProperties = AbstractProperty.getAll(getModel().getAvdDeviceData());
    deviceProperties.add(getModel().systemImage());
    myListeners.listenAll(deviceProperties).with(() -> {
      AvdOptionsModel model = getModel();
      var customSkinFileProperty = model.getAvdDeviceData().customSkinFile();

      ObservableOptional<SystemImageDescription> systemImageProperty = model.systemImage();
      var optionalCustomSkinFile = customSkinFileProperty.get();

      if (systemImageProperty.get().isPresent() && optionalCustomSkinFile.isPresent()) {
        var skin = AvdWizardUtils.pathToUpdatedSkins(customSkinFileProperty.getValue(), systemImageProperty.getValue()).toPath();
        customSkinFileProperty.setValue(skin);
        myDeviceFrameCheckbox.setSelected(!skin.equals(SkinUtils.noSkin()));
      }

      boolean checkBoxEnabled = shouldEnableDeviceFrameCheckbox();

      myDeviceFrameCheckbox.setEnabled(checkBoxEnabled);
      myDeviceFrameTitle.setEnabled(checkBoxEnabled);
      mySkinDefinitionLabel.setEnabled(true);
      mySkinComboBox.setEnabled(true);
    });

    myListeners.listen(getModel().systemImage(), this::updateSystemImageData);

    myListeners.listen(getModel().useQemu2(), () -> toggleSystemOptionals(true));

    myListeners.listen(getModel().selectedAvdOrientation(),
                       screenOrientation -> myOrientationToggle.setSelectedElement(screenOrientation));
  }

  @VisibleForTesting
  void updateSystemImageData() {
    if (getModel().systemImage().get().isPresent()) {
      SystemImageDescription image = getModel().systemImage().getValue();

      AndroidVersion androidVersion = image.getVersion();
      String codeName = SdkVersionInfo.getCodeName(androidVersion.getFeatureLevel());
      String displayName = codeName;
      if (displayName == null) {
        displayName = androidVersion.getCodename();
      }
      if (displayName == null) {
        displayName = "";
      }
      getModel().systemImageName().set(displayName);

      Icon icon = null;
      try {
        icon = IconLoader.findResolvedIcon(String.format("icons/versions/%s_32.png", codeName), AndroidIcons.class.getClassLoader());
      }
      catch (RuntimeException ignored) {
      }
      if (icon == null) {
        try {
          icon = IconLoader.findResolvedIcon("icons/versions/Default_32.png", AndroidIcons.class.getClassLoader());
        }
        catch (RuntimeException ignored) {
        }
      }
      mySystemImageName.setIcon(icon);

      String descriptionLabel = image.getName() + " " + image.getPrimaryAbiType();
      if (!androidVersion.isBaseExtension() && androidVersion.getExtensionLevel() != null) {
        descriptionLabel += " (Extension Level " + androidVersion.getExtensionLevel() + ")";
      }
      getModel().systemImageDetails().set(descriptionLabel);
      myAvdConfigurationOptionHelpPanel.setSystemImageDescription(image);
      updateGpuControlsAfterSystemImageChange();
      toggleSystemOptionals(false);
    }
  }

  @VisibleForTesting
  ASGallery<ScreenOrientation> getOrientationToggle() {
    return myOrientationToggle;
  }

  @VisibleForTesting
  JBLabel getDeviceFrameTitle() {
    return myDeviceFrameTitle;
  }

  @VisibleForTesting
  JCheckBox getDeviceFrameCheckbox() {
    return myDeviceFrameCheckbox;
  }

  @VisibleForTesting
  JBLabel getSkinDefinitionLabel() {
    return mySkinDefinitionLabel;
  }

  @VisibleForTesting
  @NotNull
  Component getSkinComboBox() {
    return mySkinComboBox;
  }

  @VisibleForTesting
  @Nullable
  Icon getSystemImageIcon() {
    return mySystemImageName == null ? null : mySystemImageName.getIcon();
  }

  @VisibleForTesting
  @NotNull
  Component getAdvancedOptionsButton() {
    return myShowAdvancedSettingsButton;
  }

  @NotNull
  @VisibleForTesting
  String getSystemImageDetailsText() {
    return getModel().systemImageDetails().get();
  }

  @NotNull
  @VisibleForTesting
  JComboBox<String> getPreferredAbi() { return myPreferredAbi; }

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

  private final ItemListener mySnapshotComboListener = new ItemListener() {
    @Override
    public void itemStateChanged(ItemEvent itemEvent) {
      if (itemEvent.getStateChange() != ItemEvent.SELECTED) {
        return;
      }
      if (myChosenSnapshotComboBox.getSelectedIndex() != myChosenSnapshotComboBox.getItemCount() - 1) {
        // A snapshot was selected (not the "Details ..." line)
        mySelectedSnapshotFileName = mySnapshotList.get(myChosenSnapshotComboBox.getSelectedIndex()).fileName;
        getModel().chosenSnapshotFile().set(mySelectedSnapshotFileName);
        myChooseBootRadioButton.setSelected(true);
        return;
      }

      // The bottom item in the drop-down was selected. When the user selects this item,
      // we invoke the detailed UI page in the Emulator.
      invokeEmulatorSnapshotControl();
    }

    /** Launch the Emulator to display more details about snapshots and
     * allow the user to select one.
     */
    private void invokeEmulatorSnapshotControl() {
      File tempDir = null;
      File paramFile = null;
      File emuOutputFile = null;

      try {
        try {
          // Get the name of a different temporary file for the Emulator to return parameters to us
          emuOutputFile = File.createTempFile("emu_output_", ".tmp");
          // Tell the Emulator to use this second file
          String emuOutputFileInfo = "snapshotTempFile=" + emuOutputFile.getAbsolutePath();
          paramFile = AvdManagerConnection.writeTempFile(Collections.singletonList(emuOutputFileInfo));
        }
        catch (IOException ioEx) {
          Logger.getInstance(ConfigureAvdOptionsStep.class)
            .info("Could not create temporary file", ioEx);
          return;
        }
        if (paramFile == null) {
          return;
        }
        // Launch the Emulator
        if (launchEmulatorForSnapshotControl(paramFile)) {
          readEmulatorSnapshotSelection(emuOutputFile);
        }
        // The Emulator may have modified some snapshots, and we may have modified mySelectedSnapshotName.
        // Refresh our list.
        populateSnapshotList();
        refreshSnapshotPullDown();
      }
      finally {
        // Clean up the temporary files that we created
        deleteTempFile(emuOutputFile, "Could not delete temporary emulator snapshot output file ");
        deleteTempFile(paramFile, "Could not delete temporary emulator snapshot parameter file ");
        deleteTempFile(tempDir, "Could not delete temporary emulator snapshot directory ");
      }
    }

    /** Launches the Emulator for the Snapshot Control UI.
     * Creates a command line containing:
     * "-ui-only snapshot-control -studio-params <paramFileForEmulator>"
     *
     * @param paramFileForEmulator The file with parameters for the Emulator
     * @return true on success, false on failure
     */
    private boolean launchEmulatorForSnapshotControl(@NotNull File paramFileForEmulator) {
      if (myEmulator == null) {
        return false;
      }
      Path emulatorBinary = myEmulator.getEmulatorBinary();
      if (emulatorBinary == null) {
        return false;
      }
      GeneralCommandLine commandLine = new GeneralCommandLine();
      commandLine.setExePath(emulatorBinary.toString());
      commandLine.addParameter("@" + myAvdId.getText());
      commandLine.addParameters("-ui-only", "snapshot-control");
      commandLine.addParameters("-studio-params", paramFileForEmulator.getAbsolutePath());

      try {
        // We need to wait for the emulator response, so we create a modal task to block Studio until we have the result of the UI
        // selection in the emulator.
        return ProgressManager.getInstance().run(new Task.WithResult<Boolean, ExecutionException>(myProject,
                                                                                                  "Waiting for Emulator Snapshot Selection",
                                                                                                  true) {
          @Override
          protected Boolean compute(@NotNull ProgressIndicator indicator) throws ExecutionException {
            int exitValue;
            // Launch the Emulator
            CapturingProcessHandler process = new CapturingProcessHandler(commandLine);
            ProcessOutput output = process.runProcessWithProgressIndicator(indicator);
            exitValue = output.getExitCode();
            return (exitValue == 0);
          }
        });
      }
      catch (ExecutionException execEx) {
        Logger.getInstance(ConfigureAvdOptionsStep.class)
          .info("Could not launch emulator for snapshot control", execEx);
        return false;
      }
    }

    /** Read the file from the Emulator that tells us what Snapshot file was chosen.
     * If successful, this will set {@link ConfigureAvdOptionsStep#mySelectedSnapshotFileName}.
     *
     * @param fileToRead The temp file that the Emulator used to pass us the information
     */
    private void readEmulatorSnapshotSelection(@NotNull File fileToRead) {
      try (final FileInputStream inputStream = new FileInputStream(fileToRead);
           final InputStreamReader streamReader = new InputStreamReader(inputStream);
           final BufferedReader reader = new BufferedReader(streamReader)
      ) {
        final String keyString = "selectedSnapshotFile=";
        String inputLine;
        while ((inputLine = reader.readLine()) != null) {
          if (inputLine.startsWith(keyString)) {
            String responseName = inputLine.substring(keyString.length());
            if (!responseName.isEmpty()) {
              mySelectedSnapshotFileName = responseName;
              getModel().chosenSnapshotFile().set(mySelectedSnapshotFileName);
            }
            break;
          }
        }
      }
      catch (IOException ioEx) {
        Logger.getInstance(ConfigureAvdOptionsStep.class)
          .info("Could not read snapshot selection from emulator", ioEx);
        // Ignore
      }
    }

    private void deleteTempFile(@Nullable File fileToDelete, @NotNull String errorString) {
      if (fileToDelete == null) {
        return;
      }
      try {
        if (!fileToDelete.delete()) {
          // Delete failed. Log and ignore.
          Logger.getInstance(ConfigureAvdOptionsStep.class)
            .warn(errorString + fileToDelete.getAbsolutePath());
        }
      }
      catch (Exception deleteEx) {
        Logger.getInstance(ConfigureAvdOptionsStep.class)
          .warn(errorString + fileToDelete.getAbsolutePath(), deleteEx);
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
    myBindings.bindTwoWay(myBuiltInSdCardStorage.storage(), ObjectProperty.wrap(getModel().sdCardStorage()));

    myBindings.bindTwoWay(new SelectedItemProperty<>(myHostGraphics), getModel().hostGpuMode());

    var deviceFrameEnabled = new SelectedProperty(myDeviceFrameCheckbox);
    myBindings.bindTwoWay(deviceFrameEnabled, getModel().hasDeviceFrame());

    myBindings.bindTwoWay(new SelectedProperty(myColdBootRadioButton), getModel().useColdBoot());
    myBindings.bindTwoWay(new SelectedProperty(myFastBootRadioButton), getModel().useFastBoot());
    myBindings.bindTwoWay(new SelectedProperty(myChooseBootRadioButton), getModel().useChosenSnapshotBoot());

    myBindings.bind(new EnabledProperty(mySkinComboBox), deviceFrameEnabled);
    myBindings.bindTwoWay(new SkinComboBoxProperty(mySkinComboBox), getModel().getAvdDeviceData().customSkinFile());

    myBindings.bindTwoWay(new SelectedItemProperty<>(myChosenSnapshotComboBox), getModel().getAvdDeviceData().selectedSnapshotFile());
    myOrientationToggle.addListSelectionListener(event -> {
      var orientation = Objects.requireNonNullElse(myOrientationToggle.getSelectedElement(), ScreenOrientation.PORTRAIT);
      getModel().selectedAvdOrientation().set(orientation);
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

    myBindings.bindTwoWay(ObjectProperty.wrap(new SelectedItemProperty<>(myFrontCameraCombo)), getModel().selectedFrontCamera());
    myBindings.bindTwoWay(ObjectProperty.wrap(new SelectedItemProperty<>(myBackCameraCombo)), getModel().selectedBackCamera());

    myBindings.bindTwoWay(ObjectProperty.wrap(new SelectedItemProperty<>(mySpeedCombo)), getModel().selectedNetworkSpeed());
    myBindings.bindTwoWay(ObjectProperty.wrap(new SelectedItemProperty<>(myLatencyCombo)), getModel().selectedNetworkLatency());

    myBindings.bindTwoWay(new SelectedProperty(myEnableComputerKeyboard), getModel().enableHardwareKeyboard());
    myBindings.bindTwoWay(new SelectedProperty(myExternalRadioButton), getModel().useExternalSdCard());
    myBindings.bindTwoWay(new SelectedProperty(myBuiltInRadioButton), getModel().useBuiltInSdCard());

    myBindings.bind(new SelectedProperty(myNoSDCardRadioButton),
                    getModel().useBuiltInSdCard().not().and(getModel().useExternalSdCard().not()));

    if (StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.get()) {
      myBindings.bindTwoWay(new TextProperty(myCommandLineOptions), getModel().commandLineOptions());
    }

    if (StudioFlags.RISC_V.get()) {
      myBindings.bindTwoWay(new SelectedItemProperty<>(myPreferredAbi), getModel().preferredAbi());
    }
    myBindings.bind(new EnabledProperty(myShowAdvancedSettingsButton), getModel().systemImage().isPresent());
  }

  // TODO: jameskaye Add unit tests for these validators. (b.android.com/230192)
  private void addValidators() {
    var model = getModel();
    var device = model.getAvdDeviceData();

    myValidatorPanel.registerValidator(device.ramStorage(), new Validator<>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage ram) {
        return (ram.getSizeAsUnit(Storage.Unit.MiB) < 128)
               ? new Result(Severity.ERROR, "RAM must be at least 128 MB. Recommendation is 1 GB.")
               : Result.OK;
      }
    });

    myValidatorPanel.registerValidator(model.vmHeapStorage(), new Validator<>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage heap) {
        return (heap.getSizeAsUnit(Storage.Unit.MiB) < 16)
               ? new Result(Severity.ERROR, "VM Heap must be at least 16 MB.")
               : Result.OK;
      }
    });

    myValidatorPanel.registerValidator(model.internalStorage(), new Validator<>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage internalMem) {
        if (!internalMem.lessThan(myModel.minInternalMemSize())) {
          return Result.OK;
        }
        String errorMessage = myModel.isPlayStoreCompatible() ?
                              "Internal storage for Play Store devices must be at least %s." :
                              "Internal storage must be at least %s.";
        return new Result(Severity.ERROR, String.format(Locale.US, errorMessage, myModel.minInternalMemSize()));
      }
    });

    // If we're using an external SD card, make sure it exists
    myValidatorPanel.registerValidator(model.externalSdCardLocation(), new Validator<>() {
      @NotNull
      @Override
      public Result validate(@NotNull String path) {
        return (getModel().useExternalSdCard().get() && !new File(path).isFile())
               ? new Result(Severity.ERROR, "The specified SD image file must be a valid image file")
               : Result.OK;
      }
    }, model.useExternalSdCard());

    // If we are using an internal SD card, make sure it has enough memory.
    myValidatorPanel.registerValidator(model.sdCardStorage(), new Validator<>() {
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

    var selectedSkinLargeEnough = device.compatibleSkinSize();
    var enableDeviceFrame = model.hasDeviceFrame();

    var validator = new CustomSkinValidator.Builder()
      .setSelectedSkinLargeEnough(selectedSkinLargeEnough)
      .setEnableDeviceFrame(enableDeviceFrame)
      .build();

    myValidatorPanel.registerValidator(device.customSkinFile(), validator, selectedSkinLargeEnough, enableDeviceFrame);
    myOriginalName = model.avdDisplayName().get();

    myValidatorPanel.registerValidator(model.avdDisplayName(), new Validator<>() {
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
        else if (!value.equals(myOriginalName) &&
                 AvdManagerConnection.getDefaultAvdManagerConnection().findAvdWithDisplayName(value)) {
          // Another device with this name already exists
          severity = Severity.ERROR;
          errorMessage = String.format("An AVD with the name \"%1$s\" already exists.", getModel().avdDisplayName());
        }
        return new Result(severity, errorMessage);
      }
    });

    myValidatorPanel.registerValidator(model.device().isPresent().and(model.systemImage().isPresent()), new Validator<>() {
      @NotNull
      @Override
      public Result validate(@NotNull Boolean deviceAndImageArePresent) {
        if (deviceAndImageArePresent) {
          Optional<Device> device = getModel().device().get();
          Optional<SystemImageDescription> systemImage = getModel().systemImage().get();
          if (!ChooseSystemImagePanel.systemImageMatchesDevice(systemImage.orElseThrow(), device.orElseThrow())) {
            return new Validator.Result(Validator.Severity.ERROR, "The selected system image is incompatible with the selected device.");
          }
        }
        else {
          if (getModel().device().get().isEmpty()) {
            return new Result(Severity.ERROR, "You must select a Device to create an AVD.");
          }
          else if (getModel().systemImage().get().isEmpty()) {
            return new Result(Severity.ERROR, "You must select a System Image to create an AVD.");
          }
        }

        return Result.OK;
      }
    });
  }

  @Override
  protected void onProceeding() {
    AvdOptionsModel model = getModel();

    var customSkinDefinitionProperty = model.getAvdDeviceData().customSkinFile();
    SettableValue<Optional<File>> customSkinDefinitionBackupProperty = model.backupSkinFile();

    var customSkinDefinition = customSkinDefinitionProperty.get().orElse(null);
    Path customSkinDefinitionBackup = customSkinDefinitionBackupProperty.get().map(File::toPath).orElse(null);

    CustomSkinDefinitionResolver resolver = new CustomSkinDefinitionResolver(FileSystems.getDefault(),
                                                                             model.hasDeviceFrame().get(),
                                                                             customSkinDefinition,
                                                                             customSkinDefinitionBackup);

    customSkinDefinitionProperty.set(resolver.getCustomSkinDefinition());
    customSkinDefinitionBackupProperty.set(resolver.getCustomSkinDefinitionBackup().map(Path::toFile));

    if (getSelectedApiLevel() < 16 || model.hostGpuMode().getValueOrNull() == GpuMode.OFF) {
      model.useHostGpu().set(false);
      model.hostGpuMode().setValue(GpuMode.OFF);
    }
    else {
      model.useHostGpu().set(true);
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
    myBootOptionPanel.setVisible(show && myEmulatorFeatures.contains(EmulatorAdvancedFeatures.FAST_BOOT));

    toggleSystemOptionals(false);

    // The following is necessary to get the scroll pane to realize that its children have been
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

    boolean isAutomotive = myModel.getAvdDeviceData().isAutomotive().get();

    // Enforce the restrictions
    myChangeDeviceButton.setEnabled(enable);
    myChangeSystemImageButton.setEnabled(enable && deviceIsPresent);

    myHostGraphics.setEnabled(enable);
    myQemu2CheckBox.setEnabled(enable);
    // Do not enforce ram restriction if it is automotive
    myRamStorage.setEnabled(enable | isAutomotive);
    myVmHeapStorage.setEnabled(enable);
    myBuiltInRadioButton.setEnabled(enable);
    myExternalRadioButton.setEnabled(enable);
    myNoSDCardRadioButton.setEnabled(enable);
    Device device = getModel().device().getValueOrNull();
    if (device != null && device.getDefaultHardware().getScreen().isFoldable()) {
      mySkinComboBox.setEnabled(false);
    }
    else {
      mySkinComboBox.setEnabled(enable);
    }

    if (!enable) {
      // Selectively disable, but don't enable
      myCoreCount.setEnabled(false);
    }
  }

  private void toggleOrientationPanel() {
    AvdDeviceData deviceData = getModel().getAvdDeviceData();
    boolean showOrientation = deviceData.supportsPortrait().get() && deviceData.supportsLandscape().get();
    myOrientationPanel.setVisible(showOrientation);
  }

  private void toggleSystemOptionals(boolean useQemu2Changed) {
    boolean showMultiCoreOption = isAdvancedPanel() && doesSystemImageSupportQemu2();
    myQemu2Panel.setVisible(showMultiCoreOption);
    if (showMultiCoreOption) {
      boolean showCores = supportsMultipleCpuCores() && getModel().useQemu2().get() && EmulatedProperties.MAX_NUMBER_OF_CORES > 1;
      if (useQemu2Changed) {
        if (showCores) {
          getModel().cpuCoreCount().setValue(mySelectedCoreCount);
        }
        else {
          mySelectedCoreCount = getModel().cpuCoreCount().getValueOr(EmulatedProperties.RECOMMENDED_NUMBER_OF_CORES);
          getModel().cpuCoreCount().setValue(1);
        }
      }
      myCoreCount.setEnabled(showCores);
    }
    enforcePlayStore();
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
    AvdDeviceData deviceData = getModel().getAvdDeviceData();
    String densityString = deviceData.density().get().getResourceValue();
    String result = Joiner.on(' ')
      .join(getModel().device().getValue().getDefaultHardware().getScreen().getDiagonalLength(), dimensionString, densityString);
    myDeviceDetails.setText(result);
  }

  private void registerAdvancedOptionsVisibility() {
    var advancedOptionsComponents =
      Lists.<JComponent>newArrayList(myStoragePanel, myCameraPanel, myNetworkPanel, myQemu2Panel, myKeyboardPanel, myCustomSkinPanel,
                                     myAvdIdRow);

    if (StudioFlags.AVD_COMMAND_LINE_OPTIONS_ENABLED.get()) {
      myCommandLineOptionsPanel.setVisible(true);
      advancedOptionsComponents.add(myCommandLineOptionsPanel);
    }

    myAdvancedOptionsComponents = advancedOptionsComponents;
  }

  @Override
  public void dispose() {
    super.dispose();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(FOCUS_OWNER, myPropertyChangeListener);
  }

  private void createUIComponents() {
    myOrientationToggle = new ASGallery<>(JBList.createDefaultListModel(ScreenOrientation.PORTRAIT, ScreenOrientation.LANDSCAPE),
                                          input -> ORIENTATIONS.get(input).myIcon,
                                          input -> ORIENTATIONS.get(input).myName,
                                          JBUI.size(48, 48),
                                          null);

    myOrientationToggle.setCellMargin(JBUI.insets(5, 20, 4, 20));
    myOrientationToggle.setBackground(JBColor.background());
    myOrientationToggle.setForeground(JBColor.foreground());
    myHardwareSkinHelpLabel = new BrowserLink("How do I create a custom hardware skin?", AvdWizardUtils.CREATE_SKIN_HELP_LINK);
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
    Abi abi = Abi.getEnum(getModel().systemImage().getValue().getPrimaryAbiType());
    return abi != null && abi.supportsMultipleCpuCores();
  }

  /**
   * Enable/Disable controls based on the capabilities of the selected device. For example, some devices may
   * not have a front facing camera.
   */
  private void toggleOptionals(@Nullable Device device) {
    boolean IsDevicePresent = device != null;
    Hardware deviceDefaultHardware = IsDevicePresent ? device.getDefaultHardware() : null;

    myFrontCameraCombo.setEnabled(IsDevicePresent && deviceDefaultHardware.getCamera(CameraLocation.FRONT) != null);
    myBackCameraCombo.setEnabled(IsDevicePresent && deviceDefaultHardware.getCamera(CameraLocation.BACK) != null);
    myOrientationToggle.setEnabled(IsDevicePresent && device.getDefaultState().getOrientation() != ScreenOrientation.SQUARE);
    myEnableComputerKeyboard.setEnabled(IsDevicePresent && !deviceDefaultHardware.getKeyboard().equals(Keyboard.QWERTY));
    myOrientationToggle.setSelectedElement(IsDevicePresent ? device.getDefaultState().getOrientation() : ScreenOrientation.PORTRAIT);

    toggleOrientationPanel();
    var customSkin = getModel().getAvdDeviceData().customSkinFile().get().orElse(null);
    var backupSkin = getModel().backupSkinFile().get().map(File::toPath).orElse(null);
    // If there is a backup skin but no normal skin, the "use device frame" checkbox should be unchecked.
    if (backupSkin != null && customSkin == null) {
      getModel().hasDeviceFrame().set(false);
    }
    File hardwareSkin = null;
    if (IsDevicePresent && getModel().systemImage().get().isPresent()) {

      File defaultHardwareSkinFile = deviceDefaultHardware.getSkinFile();
      hardwareSkin = AvdWizardUtils.pathToUpdatedSkins(defaultHardwareSkinFile == null ? null : defaultHardwareSkinFile.toPath(),
                                                       getModel().systemImage().getValue());

      myDeviceName.setIcon(DeviceDefinitionPreview.getIcon(getModel().getAvdDeviceData()));
      myDeviceName.setText(getModel().device().getValue().getDisplayName());
      updateDeviceDetails();
    }

    if (customSkin == null) {
      if (backupSkin != null) {
        customSkin = backupSkin;
      }
      else if (hardwareSkin != null) {
        customSkin = hardwareSkin.toPath();
      }
    }

    myDeviceFrameCheckbox.setEnabled(shouldEnableDeviceFrameCheckbox());

    if (customSkin != null) {
      getModel().getAvdDeviceData().customSkinFile().setValue(customSkin);
    }

    enforcePlayStore();
  }

  private final ActionListener myChangeSystemImageButtonListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      final ChooseSystemImagePanel chooseImagePanel =
        new ChooseSystemImagePanel(myProject, getModel().device().getValueOrNull(), getModel().systemImage().getValueOrNull());

      DialogWrapper dialog = new DialogWrapper(myProject) {
        {
          setTitle("Select a System Image");
          init();
          chooseImagePanel.addSystemImageListener(systemImage -> setOKActionEnabled(systemImage != null));
        }

        @NotNull
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

  private final ActionListener myChangeDeviceButtonListener = new ActionListener() {
    @Override
    public void actionPerformed(ActionEvent e) {
      final ChooseDeviceDefinitionPanel chooseDevicePanel = new ChooseDeviceDefinitionPanel(getModel().device().getValueOrNull());
      DialogWrapper dialog = new DialogWrapper(myProject) {
        {
          setTitle("Select a Device");
          init();
          chooseDevicePanel.addDeviceListener(device -> setOKActionEnabled(device != null));
        }

        @NotNull
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

  private final PropertyChangeListener myPropertyChangeListener = new PropertyChangeListener() {
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      Object value = evt.getNewValue();
      if (evt.getNewValue() instanceof JComponent) {
        JComponent component = (JComponent)value;
        if (component.getToolTipText() != null) {
          myAvdConfigurationOptionHelpPanel.setValues(component);
        }
        else if (component.getParent() instanceof JComponent parent) {
          if (parent.getToolTipText() != null) {
            myAvdConfigurationOptionHelpPanel.setValues(parent);
          }
          else {
            myAvdConfigurationOptionHelpPanel.clearValues();
          }
        }
      }
    }
  };
}
