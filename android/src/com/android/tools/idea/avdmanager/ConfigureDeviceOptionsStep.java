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
import com.android.resources.Navigation;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.adtui.TooltipLabel;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.Validator.Result;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.adapters.StringToDoubleAdapterProperty;
import com.android.tools.idea.observable.adapters.StringToIntAdapterProperty;
import com.android.tools.idea.observable.core.ObservableBool;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.idea.wizard.ui.deprecated.StudioWizardStepPanel;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.BrowserLink;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.concurrency.EdtExecutorService;
import java.io.File;
import java.util.List;
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
  private SkinChooser myCustomSkinPath;
  private BrowserLink myHardwareSkinHelpLabel;
  private ComboBox<IdDisplay> myDeviceTypeComboBox;
  private JTextField myDiagonalScreenSize;
  private StorageField myRamField;
  private JComboBox myNavigationControlsCombo;
  private TooltipLabel myHelpAndErrorLabel;
  private JCheckBox myIsScreenRound;
  private JBScrollPane myScrollPane;
  private StringToDoubleAdapterProperty myDiagScreenSizeAdapter;
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

    initSkinComboBox();
  }

  private void initSkinComboBox() {
    OptionalProperty<File> optionalSkin = getModel().getDeviceData().customSkinFile();
    File skin = optionalSkin.getValueOrNull();

    FutureCallback<List<File>> callback = new LoadSkinsFutureCallback(myCustomSkinPath, skin) {
      @Override
      public void onSuccess(@NotNull List<@NotNull File> skins) {
        super.onSuccess(skins);

        if (skin != null) {
          optionalSkin.setValue(skin);
        }

        myCustomSkinPath.setEnabled(true);
      }
    };

    Futures.addCallback(myCustomSkinPath.loadSkins(), callback, EdtExecutorService.getInstance());
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myDeviceTypeComboBox.setModel(new CollectionComboBoxModel<>(AvdWizardUtils.ALL_DEVICE_TAGS));

    myDeviceTypeComboBox.setRenderer(SimpleListCellRenderer.create(
      DEFAULT_DEVICE_TYPE_LABEL, value -> SystemImage.DEFAULT_TAG.equals(value) ? DEFAULT_DEVICE_TYPE_LABEL : value.getDisplay()));

    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);

    myHelpAndErrorLabel.setBackground(JBColor.background());
    myHelpAndErrorLabel.setForeground(JBColor.foreground());

    myHelpAndErrorLabel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 10));

    myDiagScreenSizeAdapter = new StringToDoubleAdapterProperty(new TextProperty(myDiagonalScreenSize), 1, 2);
    myScreenResWidthAdapter = new StringToIntAdapterProperty(new TextProperty(myScreenResolutionWidth));
    myScreenResHeightAdapter = new StringToIntAdapterProperty(new TextProperty(myScreenResolutionHeight));

    attachBindingsAndValidators();
  }

  @Override
  protected void onEntering() {
    final AvdDeviceData deviceModel = getModel().getDeviceData();
    myDiagScreenSizeAdapter.set(deviceModel.diagonalScreenSize());
    myScreenResWidthAdapter.set(deviceModel.screenResolutionWidth());
    myScreenResHeightAdapter.set(deviceModel.screenResolutionHeight());
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void attachBindingsAndValidators() {
    final AvdDeviceData deviceModel = getModel().getDeviceData();
    myBindings.bindTwoWay(new TextProperty(myDeviceName), deviceModel.name());

    myBindings.bind(deviceModel.diagonalScreenSize(), myDiagScreenSizeAdapter);
    myBindings.bind(deviceModel.screenResolutionWidth(), myScreenResWidthAdapter);
    myBindings.bind(deviceModel.screenResolutionHeight(), myScreenResHeightAdapter);

    myBindings.bindTwoWay(myRamField.storage(), deviceModel.ramStorage());

    myBindings.bindTwoWay(new SelectedProperty(myHasHardwareButtons), deviceModel.hasHardwareButtons());
    myBindings.bindTwoWay(new SelectedProperty(myHasHardwareKeyboard), deviceModel.hasHardwareKeyboard());
    myBindings.bindTwoWay(new SelectedItemProperty<>(myNavigationControlsCombo), deviceModel.navigation());

    myBindings.bindTwoWay(new SelectedProperty(myIsScreenRound), deviceModel.isScreenRound());
    myBindings.bindTwoWay(new SelectedProperty(mySupportsLandscape), deviceModel.supportsLandscape());
    myBindings.bindTwoWay(new SelectedProperty(mySupportsPortrait), deviceModel.supportsPortrait());
    myBindings.bindTwoWay(new SelectedProperty(myHasBackFacingCamera), deviceModel.hasBackCamera());
    myBindings.bindTwoWay(new SelectedProperty(myHasFrontFacingCamera), deviceModel.hasFrontCamera());

    myBindings.bindTwoWay(new SelectedProperty(myHasAccelerometer), deviceModel.hasAccelerometer());
    myBindings.bindTwoWay(new SelectedProperty(myHasGyroscope), deviceModel.hasGyroscope());
    myBindings.bindTwoWay(new SelectedProperty(myHasGps), deviceModel.hasGps());
    myBindings.bindTwoWay(new SelectedProperty(myHasProximitySensor), deviceModel.hasProximitySensor());
    myBindings.bindTwoWay(new SelectedItemProperty<>(myCustomSkinPath.getComboBox()), deviceModel.customSkinFile());

    SelectedItemProperty<IdDisplay> selectedDeviceType = new SelectedItemProperty<>(myDeviceTypeComboBox);
    myBindings.bindTwoWay(selectedDeviceType, deviceModel.deviceType());
    myListeners.listen(selectedDeviceType, idDisplayOptional -> {
      if (idDisplayOptional.isPresent()) {
        IdDisplay selectedType = idDisplayOptional.get();
        /**
         * TODO When the user selects round, the following could be done to make the UI cleaner
         * if(selectedType == WEAR){
         *     disable and hide width textbox
         *     addListener to height textbox to set the new value to width so it is always round (square)
         * }else{
         *     enable and show width textbox
         *    remove listener
         * }
         */
        getModel().getDeviceData().isWear().set(selectedType.equals(SystemImage.WEAR_TAG));
        getModel().getDeviceData().isTv().set(selectedType.equals(SystemImage.ANDROID_TV_TAG)
                                              || selectedType.equals(SystemImage.GOOGLE_TV_TAG));
        getModel().getDeviceData().isDesktop().set(selectedType.equals(SystemImage.DESKTOP_TAG));
        myIsScreenRound.setEnabled(selectedType.equals(SystemImage.WEAR_TAG));
        myIsScreenRound.setSelected(getModel().getDeviceData().isScreenRound().get());
      }
    });


    myValidatorPanel.registerTest(deviceModel.name().isEmpty().not(),
                                  "Please write a name for the new device.");

    myValidatorPanel.registerTest(
      myDiagScreenSizeAdapter.inSync().and(deviceModel.diagonalScreenSize().isEqualTo(myDiagScreenSizeAdapter)),
      "Please enter a non-zero positive floating point value for the screen size.");

    myValidatorPanel.registerTest(
      myScreenResWidthAdapter.inSync().and(deviceModel.screenResolutionWidth().isEqualTo(myScreenResWidthAdapter)),
      "Please enter a valid value for the screen width.");
    myValidatorPanel.registerTest(
      myScreenResHeightAdapter.inSync().and(deviceModel.screenResolutionHeight().isEqualTo(myScreenResHeightAdapter)),
      "Please enter a valid value for the screen height.");

    myValidatorPanel.registerValidator(
      deviceModel.ramStorage(),
      value -> (value.getSize() > 0) ? Result.OK : new Result(Validator.Severity.ERROR, "Please specify a non-zero amount of RAM."));

    myValidatorPanel.registerTest(deviceModel.screenDpi().isGreaterThan(0),
                                  "The given resolution and screen size specified have a DPI that is too low.");

    myValidatorPanel.registerTest(deviceModel.supportsLandscape().or(deviceModel.supportsPortrait()),
                                  "A device must support at least one orientation (Portrait or Landscape).");

    myValidatorPanel.registerValidator(deviceModel.customSkinFile(), value -> {
      File skinPath = value.orElse(null);
      if (skinPath != null && !FileUtil.filesEqual(skinPath, AvdWizardUtils.NO_SKIN) &&
          !skinPath.getPath().equals(skinPath.getName())) {
        File layoutFile = new File(skinPath, SdkConstants.FN_SKIN_LAYOUT);
        if (!layoutFile.isFile()) {
          return new Result(Validator.Severity.ERROR, "The skin directory does not point to a valid skin.");
        }
      }
      return Result.OK;
    });

    myValidatorPanel.registerTest(getModel().getDeviceData().compatibleSkinSize(),
                                  Validator.Severity.WARNING, "The selected skin is not large enough to view the entire screen.");
  }

  private void createUIComponents() {
    myNavigationControlsCombo = new ComboBox(new EnumComboBoxModel<>(Navigation.class));
    myNavigationControlsCombo.setRenderer(SimpleListCellRenderer.create("", Navigation::getShortDisplayValue));

    myHardwareSkinHelpLabel = new BrowserLink("How do I create a custom hardware skin?", AvdWizardUtils.CREATE_SKIN_HELP_LINK);
    myCustomSkinPath = new SkinChooser(null, false);
    myDeviceDefinitionPreview = new DeviceDefinitionPreview(getModel().getDeviceData());
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }
}
