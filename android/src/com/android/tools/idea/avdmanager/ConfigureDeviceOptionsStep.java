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
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.ui.TooltipLabel;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.adapters.StringToDoubleAdapterProperty;
import com.android.tools.idea.ui.properties.adapters.StringToIntAdapterProperty;
import com.android.tools.idea.ui.properties.core.ObservableBool;
import com.android.tools.idea.ui.properties.swing.SelectedItemProperty;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.validation.validators.PositiveDoubleValidator;
import com.android.tools.idea.ui.validation.validators.PositiveIntValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Optional;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

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
  private HyperlinkLabel myHardwareSkinHelpLabel;
  private ComboBox myDeviceTypeComboBox;
  private JTextField myDiagonalScreenSize;
  private StorageField myRamField;
  private JComboBox myNavigationControlsCombo;
  private TooltipLabel myHelpAndErrorLabel;
  private JCheckBox myIsScreenRound;
  private JBScrollPane myScrollPane;

  private final StudioWizardStepPanel myStudioPanel;
  private final ValidatorPanel myValidatorPanel;

  private final Project myProject;

  private BindingsManager myBindings = new BindingsManager();
  private ListenerManager myListeners = new ListenerManager();

  public ConfigureDeviceOptionsStep(@NotNull ConfigureDeviceModel model, @Nullable Project project) {
    super(model, "Configure Hardware Profile");
    FormScalingUtil.scaleComponentTree(this.getClass(), UIUtil.getRootPane(myRootPanel));
    myValidatorPanel = new ValidatorPanel(this, myRootPanel);
    myStudioPanel = new StudioWizardStepPanel(myValidatorPanel, getModel().wizardPanelDescription().get());
    myProject = project;
  }


  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    myDeviceTypeComboBox.setModel(new CollectionComboBoxModel<IdDisplay>(AvdWizardConstants.ALL_TAGS));

    myDeviceTypeComboBox.setRenderer(new ListCellRendererWrapper<IdDisplay>() {
      @Override
      public void customize(JList list, IdDisplay value, int index, boolean selected, boolean hasFocus) {
        if (value == null || SystemImage.DEFAULT_TAG.equals(value)) {
          setText(DEFAULT_DEVICE_TYPE_LABEL);
        }
        else {
          setText(value.getDisplay());
        }
      }
    });

    myScrollPane.getVerticalScrollBar().setUnitIncrement(10);

    myHelpAndErrorLabel.setBackground(JBColor.background());
    myHelpAndErrorLabel.setForeground(JBColor.foreground());

    myHelpAndErrorLabel.setBorder(BorderFactory.createEmptyBorder(15, 15, 10, 10));

    bindUi();
    attachValidators();
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  private void bindUi() {
    myBindings.bindTwoWay(new TextProperty(myDeviceName), getModel().getDeviceData().name());

    myBindings.bindTwoWay(new StringToDoubleAdapterProperty(new TextProperty(myDiagonalScreenSize)), getModel().getDeviceData().diagonalScreenSize());
    myBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myScreenResolutionWidth)), getModel().getDeviceData().screenResolutionWidth());
    myBindings.bindTwoWay(new StringToIntAdapterProperty(new TextProperty(myScreenResolutionHeight)), getModel().getDeviceData().screenResolutionHeight());

    myBindings.bindTwoWay(myRamField.storage(), getModel().getDeviceData().ramStorage());

    myBindings.bindTwoWay(new SelectedProperty(myHasHardwareButtons), getModel().getDeviceData().hasHardwareButtons());
    myBindings.bindTwoWay(new SelectedProperty(myHasHardwareKeyboard), getModel().getDeviceData().hasHardwareKeyboard());
    myBindings.bindTwoWay(new SelectedItemProperty<Navigation>(myNavigationControlsCombo), getModel().getDeviceData().navigation());

    myBindings.bindTwoWay(new SelectedProperty(myIsScreenRound), getModel().getDeviceData().isScreenRound());
    myBindings.bindTwoWay(new SelectedProperty(mySupportsLandscape), getModel().getDeviceData().supportsLandscape());
    myBindings.bindTwoWay(new SelectedProperty(mySupportsPortrait), getModel().getDeviceData().supportsPortrait());
    myBindings.bindTwoWay(new SelectedProperty(myHasBackFacingCamera), getModel().getDeviceData().hasBackCamera());
    myBindings.bindTwoWay(new SelectedProperty(myHasFrontFacingCamera), getModel().getDeviceData().hasFrontCamera());

    myBindings.bindTwoWay(new SelectedProperty(myHasAccelerometer), getModel().getDeviceData().hasAccelerometer());
    myBindings.bindTwoWay(new SelectedProperty(myHasGyroscope), getModel().getDeviceData().hasGyroscope());
    myBindings.bindTwoWay(new SelectedProperty(myHasGps), getModel().getDeviceData().hasGps());
    myBindings.bindTwoWay(new SelectedProperty(myHasProximitySensor), getModel().getDeviceData().hasProximitySensor());
    myBindings.bindTwoWay(new SelectedItemProperty<File>(myCustomSkinPath.getComboBox()), getModel().getDeviceData().customSkinFile());

    SelectedItemProperty<IdDisplay> selectedDeviceType = new SelectedItemProperty<IdDisplay>(myDeviceTypeComboBox);
    myBindings.bindTwoWay(selectedDeviceType, getModel().getDeviceData().deviceType());
    myListeners.listen(selectedDeviceType, new Consumer<Optional<IdDisplay>>() {
      @Override
      public void consume(Optional<IdDisplay> idDisplayOptional) {
        IdDisplay selectedType = idDisplayOptional.orNull();
        if (selectedType != null) {
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
          getModel().getDeviceData().isWear().set(selectedType.equals(AvdWizardConstants.WEAR_TAG));
          getModel().getDeviceData().isTv().set(selectedType.equals(AvdWizardConstants.TV_TAG));
          myIsScreenRound.setEnabled(selectedType.equals(AvdWizardConstants.WEAR_TAG));
          myIsScreenRound.setSelected(getModel().getDeviceData().isScreenRound().get());
        }
      }
    });
  }

  private void createUIComponents() {
    myNavigationControlsCombo = new ComboBox(new EnumComboBoxModel<Navigation>(Navigation.class)) {
      @Override
      public ListCellRenderer getRenderer() {
        return new ColoredListCellRenderer() {
          @Override
          protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
            append(((Navigation)value).getShortDisplayValue());
          }
        };
      }
    };

    myHardwareSkinHelpLabel = new HyperlinkLabel("How do I create a custom hardware skin?");
    myHardwareSkinHelpLabel.setHyperlinkTarget(AvdWizardConstants.CREATE_SKIN_HELP_LINK);
    myCustomSkinPath = new SkinChooser(myProject);
    myDeviceDefinitionPreview = new DeviceDefinitionPreview(getModel().getDeviceData());
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myStudioPanel;
  }

  private void attachValidators() {
    myValidatorPanel.registerValidator(getModel().getDeviceData().name(), new Validator<String>() {
      @NotNull
      @Override
      public Result validate(@NotNull String value) {
        return StringUtil.isNotEmpty(value) ? Result.OK : new Result(Severity.ERROR, "Please write a name for the new device.");
      }
    });

    myValidatorPanel.registerValidator(getModel().getDeviceData().diagonalScreenSize(), new PositiveDoubleValidator(
      "Please enter a non-zero positive floating point value for the screen size."));

    myValidatorPanel.registerValidator(getModel().getDeviceData().screenResolutionWidth(), new PositiveIntValidator(
      "Please enter non-zero positive integer values for the screen resolution width."));

    myValidatorPanel.registerValidator(getModel().getDeviceData().screenResolutionHeight(), new PositiveIntValidator(
      "Please enter non-zero positive integer values for the screen resolution height."));

    myValidatorPanel.registerValidator(getModel().getDeviceData().ramStorage(), new Validator<Storage>() {
      @NotNull
      @Override
      public Result validate(@NotNull Storage value) {
        return (value.getSize() > 0) ? Result.OK : new Result(Severity.ERROR, "Please specify a non-zero amount of RAM.");
      }
    });

    myValidatorPanel.registerValidator(getModel().getDeviceData().screenDpi(), new Validator<Double>() {
      @NotNull
      @Override
      public Result validate(@NotNull Double value) {
        return (value < 0)
               ? new Result(Severity.ERROR, "The given resolution and screen size specified have a DPI that is too low.")
               : Result.OK;
      }
    });

    Validator<Boolean> orientationValidator = new Validator<Boolean>() {
      @NotNull
      @Override
      public Result validate(@NotNull Boolean value) {
        return (getModel().getDeviceData().supportsLandscape().or(getModel().getDeviceData().supportsPortrait()).not().get()
                ? new Result(Severity.ERROR, "A device must support at least one orientation (Portrait or Landscape).")
                : Result.OK);
      }
    };

    myValidatorPanel.registerValidator(getModel().getDeviceData().supportsLandscape(), orientationValidator);
    myValidatorPanel.registerValidator(getModel().getDeviceData().supportsPortrait(), orientationValidator);

    myValidatorPanel.registerValidator(getModel().getDeviceData().customSkinFile(), new Validator<Optional<File>>() {
      @NotNull
      @Override
      public Result validate(@NotNull Optional<File> value) {
        File skinPath = value.orNull();
        if (skinPath != null && !FileUtil.filesEqual(skinPath, AvdWizardConstants.NO_SKIN)) {
          File layoutFile = new File(skinPath, SdkConstants.FN_SKIN_LAYOUT);
          if (!layoutFile.isFile()) {
            return new Result(Severity.ERROR, "The skin directory does not point to a valid skin.");
          }
        }
        return Result.OK;
      }
    });
  }
}
