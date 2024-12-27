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

import com.android.resources.Navigation;
import com.android.sdklib.SystemImageTags;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.adtui.TooltipLabel;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.Validator.Result;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector;
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBox;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.adapters.StringToDoubleAdapterProperty;
import com.android.tools.idea.observable.adapters.StringToIntAdapterProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.JBDimension;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Collections;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ModelWizardStep} used for configuring a Device Hardware Profile.
 */
public final class ConfigureDeviceOptionsStep extends ModelWizardStep<ConfigureDeviceModel> {
  private static final String DEFAULT_DEVICE_TYPE_LABEL = "Phone/Tablet";
  private static final String CREATE_SKIN_HELP_LINK = "http://developer.android.com/tools/devices/managing-avds.html#skins";

  private JPanel myRootPanel;
  @SuppressWarnings("unused") // Control is meant for display only.
  private DeviceDefinitionPreview myDeviceDefinitionPreview;
  private JCheckBox myHasBackFacingCamera;
  private JCheckBox myHasFrontFacingCamera;
  private JCheckBox myHasAccelerometer;
  private JCheckBox myHasGyroscope;
  private JCheckBox myHasGps;
  private JCheckBox myHasProximitySensor;
  private JCheckBox mySupportsLandscape;
  private JCheckBox mySupportsPortrait;
  private JCheckBox myHasHardwareKeyboard;
  private JCheckBox myHasHardwareButtons;
  private JTextField myDeviceName;
  private JTextField myScreenResolutionWidth;
  private JTextField myScreenResolutionHeight;

  @NotNull
  private SkinComboBox myCustomSkinPath;

  private BrowserLink myHardwareSkinHelpLabel;
  private ComboBox<IdDisplay> myDeviceTypeComboBox;
  private JTextField myDiagonalScreenSize;
  private StorageField myRamField;
  private JComboBox<Navigation> myNavigationControlsCombo;
  private TooltipLabel myHelpAndErrorLabel;
  private JCheckBox myIsScreenRound;
  private JBScrollPane myScrollPane;
  private StringToDoubleAdapterProperty myDiagonalScreenSizeAdapter;
  private StringToIntAdapterProperty myScreenResWidthAdapter;
  private StringToIntAdapterProperty myScreenResHeightAdapter;

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;

  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  public ConfigureDeviceOptionsStep(@NotNull ConfigureDeviceModel model) {
    super(model, "Configure Hardware Profile");
    setupUI();
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, "Configure this hardware profile");
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myDeviceTypeComboBox.setModel(new CollectionComboBoxModel<>(AvdWizardUtils.ALL_DEVICE_TAGS));

    myDeviceTypeComboBox.setRenderer(SimpleListCellRenderer.create(
      DEFAULT_DEVICE_TYPE_LABEL, value -> SystemImageTags.DEFAULT_TAG.equals(value) ? DEFAULT_DEVICE_TYPE_LABEL : value.getDisplay()));

    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);

    myHelpAndErrorLabel.setBackground(JBColor.background());
    myHelpAndErrorLabel.setForeground(JBColor.foreground());

    myHelpAndErrorLabel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 10));

    myDiagonalScreenSizeAdapter = new StringToDoubleAdapterProperty(new TextProperty(myDiagonalScreenSize), 1, 2);
    myScreenResWidthAdapter = new StringToIntAdapterProperty(new TextProperty(myScreenResolutionWidth));
    myScreenResHeightAdapter = new StringToIntAdapterProperty(new TextProperty(myScreenResolutionHeight));

    attachBindingsAndValidators();
  }

  @Override
  protected void onEntering() {
    final AvdDeviceData deviceModel = getModel().getDeviceData();
    myDiagonalScreenSizeAdapter.set(deviceModel.diagonalScreenSize());
    myScreenResWidthAdapter.set(deviceModel.screenResolutionWidth());
    myScreenResHeightAdapter.set(deviceModel.screenResolutionHeight());
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void isScreenRoundChanged(boolean isRound) {
    myScreenResolutionHeight.setEnabled(!isRound);
    if (isRound) {
      myScreenResHeightAdapter.set(myScreenResWidthAdapter.get());
      myListeners.listen(myScreenResWidthAdapter, width -> myScreenResHeightAdapter.set(width));
    }
    else {
      myListeners.release((ObservableValue<?>)myScreenResWidthAdapter);
    }
  }

  private void attachBindingsAndValidators() {
    var device = getModel().getDeviceData();
    myBindings.bindTwoWay(new TextProperty(myDeviceName), device.name());

    myBindings.bind(device.diagonalScreenSize(), myDiagonalScreenSizeAdapter);
    myBindings.bind(device.screenResolutionWidth(), myScreenResWidthAdapter);
    myBindings.bind(device.screenResolutionHeight(), myScreenResHeightAdapter);

    myBindings.bindTwoWay(myRamField.storage(), device.ramStorage());

    myBindings.bindTwoWay(new SelectedProperty(myHasHardwareButtons), device.hasHardwareButtons());
    myBindings.bindTwoWay(new SelectedProperty(myHasHardwareKeyboard), device.hasHardwareKeyboard());
    myBindings.bindTwoWay(new SelectedItemProperty<>(myNavigationControlsCombo), device.navigation());

    myBindings.bindTwoWay(new SelectedProperty(mySupportsLandscape), device.supportsLandscape());
    myBindings.bindTwoWay(new SelectedProperty(mySupportsPortrait), device.supportsPortrait());
    myBindings.bindTwoWay(new SelectedProperty(myHasBackFacingCamera), device.hasBackCamera());
    myBindings.bindTwoWay(new SelectedProperty(myHasFrontFacingCamera), device.hasFrontCamera());

    myBindings.bindTwoWay(new SelectedProperty(myHasAccelerometer), device.hasAccelerometer());
    myBindings.bindTwoWay(new SelectedProperty(myHasGyroscope), device.hasGyroscope());
    myBindings.bindTwoWay(new SelectedProperty(myHasGps), device.hasGps());
    myBindings.bindTwoWay(new SelectedProperty(myHasProximitySensor), device.hasProximitySensor());
    myBindings.bindTwoWay(new SkinComboBoxProperty(myCustomSkinPath), device.customSkinFile());

    SelectedProperty isScreenRound = new SelectedProperty(myIsScreenRound);
    myBindings.bindTwoWay(isScreenRound, device.isScreenRound());
    myListeners.listen(isScreenRound, this::isScreenRoundChanged);

    SelectedItemProperty<IdDisplay> selectedDeviceType = new SelectedItemProperty<>(myDeviceTypeComboBox);
    myBindings.bindTwoWay(selectedDeviceType, device.deviceType());

    myListeners.listen(selectedDeviceType, idDisplayOptional -> {
      if (idDisplayOptional.isPresent()) {
        List<IdDisplay> deviceTag = Collections.singletonList(idDisplayOptional.get());

        boolean isWear = SystemImageTags.isWearImage(deviceTag);
        getModel().getDeviceData().isWear().set(isWear);
        getModel().getDeviceData().isAutomotive().set(SystemImageTags.isAutomotiveImage(deviceTag));
        getModel().getDeviceData().isTv().set(SystemImageTags.isTvImage(deviceTag));
        getModel().getDeviceData().isDesktop().set(SystemImageTags.isDesktopImage(deviceTag));

        myIsScreenRound.setEnabled(isWear);
        boolean isRound = getModel().getDeviceData().isScreenRound().get();
        myIsScreenRound.setSelected(isRound);
        isScreenRoundChanged(isWear && isRound);
      }
    });

    myValidatorPanel.registerTest(device.name().isEmpty().not(), "Please write a name for the new device.");

    myValidatorPanel.registerTest(
      myDiagonalScreenSizeAdapter.inSync().and(device.diagonalScreenSize().isEqualTo(myDiagonalScreenSizeAdapter)),
      "Please enter a non-zero positive floating point value for the screen size.");

    myValidatorPanel.registerTest(myScreenResWidthAdapter.inSync().and(device.screenResolutionWidth().isEqualTo(myScreenResWidthAdapter)),
                                  "Please enter a valid value for the screen width.");
    myValidatorPanel.registerTest(
      myScreenResHeightAdapter.inSync().and(device.screenResolutionHeight().isEqualTo(myScreenResHeightAdapter)),
      "Please enter a valid value for the screen height.");

    myValidatorPanel.registerValidator(device.ramStorage(), value -> (value.getSize() > 0)
                                                                     ? Result.OK
                                                                     : new Result(Validator.Severity.ERROR,
                                                                                  "Please specify a non-zero amount of RAM."));

    myValidatorPanel.registerTest(device.screenDpi().isGreaterThan(0),
                                  "The given resolution and screen size specified have a DPI that is too low.");

    myValidatorPanel.registerTest(device.supportsLandscape().or(device.supportsPortrait()),
                                  "A device must support at least one orientation (Portrait or Landscape).");

    var selectedSkinLargeEnough = device.compatibleSkinSize();

    var validator = new CustomSkinValidator.Builder()
      .setSelectedSkinLargeEnough(selectedSkinLargeEnough)
      .build();

    myValidatorPanel.registerValidator(device.customSkinFile(), validator, selectedSkinLargeEnough);
  }

  private void createUIComponents() {
    myNavigationControlsCombo = new ComboBox<>(new EnumComboBoxModel<>(Navigation.class));
    myNavigationControlsCombo.setRenderer(SimpleListCellRenderer.create("", Navigation::getShortDisplayValue));

    myHardwareSkinHelpLabel = new BrowserLink("How do I create a custom hardware skin?", CREATE_SKIN_HELP_LINK);
    createDefaultSkinComboBox();
    myDeviceDefinitionPreview = new DeviceDefinitionPreview(getModel().getDeviceData());
  }

  private void createDefaultSkinComboBox() {
    myCustomSkinPath = new SkinComboBox(null, SkinCollector::getFilenamesAndCollect);

    // HACK I'm constraining the sizes to something closer to the old SkinChooser. I prefer to let children be the sizes they want to be at
    // and tweak the parent containers instead but that was too difficult.
    var size = new JBDimension(170, 30);

    myCustomSkinPath.setMinimumSize(size);
    myCustomSkinPath.setPreferredSize(size);

    myCustomSkinPath.load();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  private void setupUI() {
    createUIComponents();
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(2, 2, new Insets(0, 0, 0, 0), -1, 0));
    myRootPanel.setMaximumSize(new Dimension(1280, 768));
    myRootPanel.add(myDeviceDefinitionPreview, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                   GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(360, -1), null,
                                                                   0, false));
    myScrollPane = new JBScrollPane();
    myRootPanel.add(myScrollPane, new GridConstraints(0, 0, 2, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null, null, 0, false));
    final JPanel panel1 = new JPanel();
    panel1.setLayout(new GridLayoutManager(28, 2, new Insets(5, 5, 5, 5), -1, 5));
    myScrollPane.setViewportView(panel1);
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("Screen");
    panel1.add(jBLabel1,
               new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Memory");
    panel1.add(jBLabel2,
               new GridConstraints(8, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    jBLabel3.setText("Cameras");
    panel1.add(jBLabel3,
               new GridConstraints(17, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel4 = new JBLabel();
    jBLabel4.setText("Sensors");
    panel1.add(jBLabel4,
               new GridConstraints(20, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasBackFacingCamera = new JCheckBox();
    myHasBackFacingCamera.setSelected(true);
    myHasBackFacingCamera.setText("Back-facing camera");
    myHasBackFacingCamera.setToolTipText("<html>Enables back-facing camera support in emulator</html>");
    panel1.add(myHasBackFacingCamera, new GridConstraints(17, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasFrontFacingCamera = new JCheckBox();
    myHasFrontFacingCamera.setSelected(true);
    myHasFrontFacingCamera.setText("Front-facing camera");
    myHasFrontFacingCamera.setToolTipText("<html>Enables front-facing camera support in emulator</html>");
    panel1.add(myHasFrontFacingCamera, new GridConstraints(18, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                           GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasAccelerometer = new JCheckBox();
    myHasAccelerometer.setSelected(true);
    myHasAccelerometer.setText("Accelerometer");
    myHasAccelerometer.setToolTipText("<html>Enables accelerometer support in emulator.</html>");
    panel1.add(myHasAccelerometer, new GridConstraints(20, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasGyroscope = new JCheckBox();
    myHasGyroscope.setSelected(true);
    myHasGyroscope.setText("Gyroscope");
    myHasGyroscope.setToolTipText("<html>Enables gyroscope support in emulator.</html>");
    panel1.add(myHasGyroscope, new GridConstraints(21, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                   GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasGps = new JCheckBox();
    myHasGps.setSelected(true);
    myHasGps.setText("GPS");
    myHasGps.setToolTipText("<html>Enables GPS (global positioning support) support in emulator.</html>");
    panel1.add(myHasGps, new GridConstraints(22, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasProximitySensor = new JCheckBox();
    myHasProximitySensor.setSelected(true);
    myHasProximitySensor.setText("Proximity Sensor");
    myHasProximitySensor.setToolTipText("<html>Enables proximity sensor support in emulator</html>");
    panel1.add(myHasProximitySensor, new GridConstraints(23, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySupportsLandscape = new JCheckBox();
    mySupportsLandscape.setSelected(true);
    mySupportsLandscape.setText("Landscape");
    mySupportsLandscape.setToolTipText("<html>Enables the landscape device screen state in emulator</html>");
    panel1.add(mySupportsLandscape, new GridConstraints(15, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    mySupportsPortrait = new JCheckBox();
    mySupportsPortrait.setSelected(true);
    mySupportsPortrait.setText("Portrait");
    mySupportsPortrait.setToolTipText("<html>Enables the portrait device screen state in emulator.</html>");
    panel1.add(mySupportsPortrait, new GridConstraints(14, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                       GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel5 = new JBLabel();
    jBLabel5.setText("<html>Supported<br>device states</html>");
    panel1.add(jBLabel5, new GridConstraints(14, 0, 2, 1, GridConstraints.ANCHOR_NORTHWEST, GridConstraints.FILL_NONE,
                                             GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                             false));
    myHasHardwareKeyboard = new JCheckBox();
    myHasHardwareKeyboard.setSelected(true);
    myHasHardwareKeyboard.setText("Has Hardware Keyboard");
    myHasHardwareKeyboard.setToolTipText("<html>Enables hardware keyboard  support in Android Virtual Device.</html>");
    panel1.add(myHasHardwareKeyboard, new GridConstraints(11, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHasHardwareButtons = new JCheckBox();
    myHasHardwareButtons.setSelected(true);
    myHasHardwareButtons.setText("Has Hardware Buttons (Back/Home/Menu)");
    myHasHardwareButtons.setToolTipText("<html>Enables hardware navigation button support in Android Virtual Device</html>");
    panel1.add(myHasHardwareButtons, new GridConstraints(10, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel6 = new JBLabel();
    jBLabel6.setText("Input");
    panel1.add(jBLabel6,
               new GridConstraints(10, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDeviceName = new JTextField();
    myDeviceName.setToolTipText("<html>Name of the Device Profile</html>");
    panel1.add(myDeviceName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                 GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                                 null, 0, false));
    final JBLabel jBLabel7 = new JBLabel();
    jBLabel7.setText("Device Name");
    panel1.add(jBLabel7,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
    final JPanel panel2 = new JPanel();
    panel2.setLayout(new GridLayoutManager(1, 5, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel2, new GridConstraints(5, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_VERTICAL,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    final JBLabel jBLabel8 = new JBLabel();
    jBLabel8.setText("Resolution:");
    panel2.add(jBLabel8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
    myScreenResolutionWidth = new JTextField();
    myScreenResolutionWidth.setColumns(5);
    myScreenResolutionWidth.setToolTipText(
      "<html>The total number of physical pixels on a screen. When adding support for multiple screens, applications do not work directly with resolution; applications should be concerned only with screen size and density, as specified by the generalized size and density groups.  Width in pixels </html>");
    panel2.add(myScreenResolutionWidth, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            null, null, null, 0, false));
    final JBLabel jBLabel9 = new JBLabel();
    jBLabel9.setText("x");
    panel2.add(jBLabel9, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                             0, false));
    myScreenResolutionHeight = new JTextField();
    myScreenResolutionHeight.setColumns(5);
    myScreenResolutionHeight.setToolTipText(
      "<html>The total number of physical pixels on a screen. When adding support for multiple screens, applications do not work directly with resolution; applications should be concerned only with screen size and density, as specified by the generalized size and density groups.  Height in pixels </html>");
    panel2.add(myScreenResolutionHeight, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             null, null, null, 0, false));
    final JBLabel jBLabel10 = new JBLabel();
    jBLabel10.setText("px");
    panel2.add(jBLabel10, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
    final Spacer spacer1 = new Spacer();
    panel1.add(spacer1, new GridConstraints(24, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                            GridConstraints.SIZEPOLICY_WANT_GROW, new Dimension(-1, 15), new Dimension(-1, 15), null, 0,
                                            false));
    final JBLabel jBLabel11 = new JBLabel();
    jBLabel11.setText("Default Skin");
    panel1.add(jBLabel11,
               new GridConstraints(26, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myCustomSkinPath.setToolTipText("<html>Path to a directory containing a custom skin</html>");
    panel1.add(myCustomSkinPath, new GridConstraints(26, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                     GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                     null, 0, false));
    panel1.add(myHardwareSkinHelpLabel,
               new GridConstraints(27, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel12 = new JBLabel();
    jBLabel12.setText("Device Type");
    panel1.add(jBLabel12,
               new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myDeviceTypeComboBox = new ComboBox();
    panel1.add(myDeviceTypeComboBox,
               new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel3 = new JPanel();
    panel3.setLayout(new GridLayoutManager(1, 3, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel3, new GridConstraints(4, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    final JBLabel jBLabel13 = new JBLabel();
    jBLabel13.setText("inch");
    panel3.add(jBLabel13, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                              GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null,
                                              0, false));
    myDiagonalScreenSize = new JTextField();
    myDiagonalScreenSize.setColumns(10);
    myDiagonalScreenSize.setToolTipText("<html>Actual Android Virtual Device size of the screen, measured as the screen's diagonal</html>");
    panel3.add(myDiagonalScreenSize, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                         null, null, 0, false));
    final JBLabel jBLabel14 = new JBLabel();
    jBLabel14.setText("Screen size:");
    panel3.add(jBLabel14,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JPanel panel4 = new JPanel();
    panel4.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel4, new GridConstraints(8, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    final JBLabel jBLabel15 = new JBLabel();
    jBLabel15.setText("RAM:");
    panel4.add(jBLabel15,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myRamField = new StorageField();
    myRamField.setToolTipText("<html>The amount of physical RAM on the device.</html>");
    panel4.add(myRamField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, 1, null, null,
                                               null, 0, false));
    final JPanel panel5 = new JPanel();
    panel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    panel1.add(panel5, new GridConstraints(12, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                           GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0,
                                           false));
    final JBLabel jBLabel16 = new JBLabel();
    jBLabel16.setText("Navigation Style:");
    panel5.add(jBLabel16,
               new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myNavigationControlsCombo.setToolTipText(
      "<html>No Navigation - No navigational controls  <br>Directional Pad - Enables direction pad support in emulator <br>Trackball - Enables trackball support in emulator</html>");
    panel5.add(myNavigationControlsCombo,
               new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_GROW,
                                   GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JSeparator separator1 = new JSeparator();
    panel1.add(separator1, new GridConstraints(1, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator2 = new JSeparator();
    panel1.add(separator2, new GridConstraints(3, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator3 = new JSeparator();
    panel1.add(separator3, new GridConstraints(7, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator4 = new JSeparator();
    panel1.add(separator4, new GridConstraints(9, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator5 = new JSeparator();
    panel1.add(separator5, new GridConstraints(13, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator6 = new JSeparator();
    panel1.add(separator6, new GridConstraints(16, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator7 = new JSeparator();
    panel1.add(separator7, new GridConstraints(19, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    final JSeparator separator8 = new JSeparator();
    panel1.add(separator8, new GridConstraints(25, 0, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                               GridConstraints.SIZEPOLICY_FIXED,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null,
                                               null, 0, false));
    myIsScreenRound = new JCheckBox();
    myIsScreenRound.setEnabled(false);
    myIsScreenRound.setSelected(true);
    myIsScreenRound.setText("Round");
    myIsScreenRound.setToolTipText("<html>Useful for wear devices with screens that can be round</html>");
    panel1.add(myIsScreenRound, new GridConstraints(6, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    myHelpAndErrorLabel = new TooltipLabel();
    myHelpAndErrorLabel.setOpaque(true);
    myHelpAndErrorLabel.setVerticalTextPosition(1);
    myRootPanel.add(myHelpAndErrorLabel, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                             GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                             GridConstraints.SIZEPOLICY_FIXED, new Dimension(-1, 100),
                                                             new Dimension(360, 100), null, 0, false));
    jBLabel7.setLabelFor(myDeviceName);
    jBLabel8.setLabelFor(myScreenResolutionWidth);
    jBLabel9.setLabelFor(myScreenResolutionHeight);
    jBLabel14.setLabelFor(myDiagonalScreenSize);
    jBLabel16.setLabelFor(myNavigationControlsCombo);
  }
}
