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
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ModelWizardStep} used for configuring a Device Hardware Profile.
 */
public final class ConfigureDeviceOptionsStep extends ModelWizardStep<ConfigureDeviceModel> {
  private static final String DEFAULT_DEVICE_TYPE_LABEL = "Phone/Tablet";

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
        IdDisplay selectedType = idDisplayOptional.get();

        boolean isWear = selectedType.equals(SystemImageTags.WEAR_TAG);
        getModel().getDeviceData().isWear().set(isWear);
        getModel().getDeviceData().isTv().set(selectedType.equals(SystemImageTags.ANDROID_TV_TAG)
                                              || selectedType.equals(SystemImageTags.GOOGLE_TV_TAG));
        getModel().getDeviceData().isDesktop().set(selectedType.equals(SystemImageTags.DESKTOP_TAG));

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

    myHardwareSkinHelpLabel = new BrowserLink("How do I create a custom hardware skin?", AvdWizardUtils.CREATE_SKIN_HELP_LINK);
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
}
