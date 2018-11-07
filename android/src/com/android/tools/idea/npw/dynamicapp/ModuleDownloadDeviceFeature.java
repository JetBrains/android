/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.dynamicapp;

import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.core.StringProperty;
import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.observable.expressions.bool.AndExpression;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.android.tools.idea.observable.expressions.string.IsEmptyExpression;
import com.android.tools.idea.observable.expressions.string.TrimExpression;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.UIUtil;
import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ModuleDownloadDeviceFeature {
  private JPanel myRootPanel;
  private JComboBox<DeviceFeatureKind> myFeatureNameCombo;
  private JPanel myFeatureValueContainer;
  private TextFieldWithAutoCompletion<String> myFeatureValueTextField;
  private LinkLabel<Void> myRemoveFeatureLinkLabel;

  @NotNull
  private final DeviceFeatureModel myModel;

  @NotNull
  private final List<ModuleDownloadDeviceFeatureListener> myListeners = new ArrayList<>();

  @NotNull
  private final BindingsManager myBindings = new BindingsManager();

  @NotNull
  private final ListenerManager myBindingsListeners = new ListenerManager();

  public ModuleDownloadDeviceFeature(@NotNull Project project,
                                     @NotNull DeviceFeatureModel model,
                                     @NotNull ObservableValue<Boolean> isActive,
                                     @NotNull ValidatorPanel validator) {
    myModel = model;

    myFeatureNameCombo.setModel(new DefaultComboBoxModel<>(DeviceFeatureKind.values()));
    myFeatureValueTextField = TextFieldWithAutoCompletion.create(project, new ArrayList<>(), true, null);
    myFeatureValueContainer.add(myFeatureValueTextField, BorderLayout.CENTER);

    UIUtil.addParentChangeListener(myRootPanel, new ActivationListener());

    // Invoke listeners when close button is pressed
    myRemoveFeatureLinkLabel.setListener((aSource, aLinkData) -> myListeners.forEach(x -> x.removeFeatureInvoked()), null);

    myBindings.bindTwoWay(new SelectedItemProperty<>(myFeatureNameCombo), myModel.deviceFeatureType());

    TextFieldProperty deviceFeatureValueComboTextProperty = new TextFieldProperty(myFeatureValueTextField);
    myBindings.bindTwoWay(deviceFeatureValueComboTextProperty, myModel.deviceFeatureValue());

    // Ensure that each item in the "feature type" combo box has its own
    // backing (temporary) property, so that when switching item in the combo
    // box, the associated value is saved and/or restored.

    // Save UI value into temporary property for each "device feature type"
    List<StringProperty> tempValues = new ArrayList<>();
    for (DeviceFeatureKind value : DeviceFeatureKind.values()) {
      StringProperty tempProp = new StringValueProperty();
      tempValues.add(tempProp);
      myBindings.bind(tempProp, deviceFeatureValueComboTextProperty, myModel.deviceFeatureType().isEqualTo(Optional.of(value)));
    }

    // Restore UI value from temporary property when a "device feature type" item is selected
    myBindingsListeners.receiveAndFire(myModel.deviceFeatureType(), value -> {
      if (value.isPresent()) {
        int index = 0;
        for (DeviceFeatureKind featureType : DeviceFeatureKind.values()) {
          if (value.get() == featureType) {
            myFeatureValueTextField.setVariants(getModelForFeatureType(featureType));
            myFeatureValueTextField.setText(tempValues.get(index).get());
          }
          index++;
        }
      }
    });

    // isActive && device feature value is empty
    BooleanExpression isInvalidExpression =
      new AndExpression(isActive, new IsEmptyExpression(new TrimExpression(myModel.deviceFeatureValue())));
    validator.registerValidator(isInvalidExpression, isInvalid -> isInvalid
                                                                  ? new Validator.Result(Validator.Severity.ERROR,
                                                                                         "Device feature value must be set")
                                                                  : Validator.Result.OK);
  }

  @NotNull
  private static List<String> getModelForFeatureType(DeviceFeatureKind featureType) {
    switch (featureType) {
      case GL_ES_VERSION:
        return ImmutableList.of(
          "0x00020000",
          "0x00030000",
          "0x00030001");

      case NAME:
        // Note: From https://developer.android.com/guide/topics/manifest/uses-feature-element#features-reference
        return ImmutableList.of(
          "android.hardware.audio.low_latency",
          "android.hardware.audio.output",
          "android.hardware.audio.pro",
          "android.hardware.microphone",
          "android.hardware.bluetooth",
          "android.hardware.bluetooth_le",
          "android.hardware.camera",
          "android.hardware.camera.any",
          "android.hardware.camera.autofocus",
          "android.hardware.camera.capability.manual_post_processing",
          "android.hardware.camera.capability.manual_sensor",
          "android.hardware.camera.capability.raw",
          "android.hardware.camera.external",
          "android.hardware.camera.flash",
          "android.hardware.camera.front",
          "android.hardware.camera.level.full",
          "android.hardware.type.automotive",
          "android.hardware.type.television",
          "android.hardware.type.watch",
          "android.hardware.fingerprint",
          "android.hardware.gamepad",
          "android.hardware.consumerir",
          "android.hardware.location",
          "android.hardware.location.gps",
          "android.hardware.location.network",
          "android.hardware.nfc",
          "android.hardware.nfc.hce",
          "android.hardware.opengles.aep",
          "android.hardware.sensor.accelerometer",
          "android.hardware.sensor.ambient_temperature",
          "android.hardware.sensor.barometer",
          "android.hardware.sensor.compass",
          "android.hardware.sensor.gyroscope",
          "android.hardware.sensor.hifi_sensors",
          "android.hardware.sensor.heartrate",
          "android.hardware.sensor.heartrate.ecg",
          "android.hardware.sensor.light",
          "android.hardware.sensor.proximity",
          "android.hardware.sensor.relative_humidity",
          "android.hardware.sensor.stepcounter",
          "android.hardware.sensor.stepdetector",
          "android.hardware.screen.landscape",
          "android.hardware.screen.portrait",
          "android.hardware.telephony",
          "android.hardware.telephony.cdma",
          "android.hardware.telephony.gsm",
          "android.hardware.faketouch",
          "android.hardware.faketouch.multitouch.distinct",
          "android.hardware.faketouch.multitouch.jazzhand",
          "android.hardware.touchscreen",
          "android.hardware.touchscreen.multitouch",
          "android.hardware.touchscreen.multitouch.distinct",
          "android.hardware.touchscreen.multitouch.jazzhand",
          "android.hardware.usb.accessory",
          "android.hardware.usb.host",
          "android.hardware.vulkan.compute",
          "android.hardware.vulkan.level",
          "android.hardware.vulkan.version",
          "android.hardware.wifi",
          "android.hardware.wifi.direct",
          "android.software.sip",
          "android.software.sip.voip",
          "android.software.webview",
          "android.software.input_methods",
          "android.software.backup",
          "android.software.device_admin",
          "android.software.managed_users",
          "android.software.securely_removes_users",
          "android.software.verified_boot",
          "android.software.midi",
          "android.software.print",
          "android.software.leanback",
          "android.software.live_tv",
          "android.software.app_widgets",
          "android.software.home_screen",
          "android.software.live_wallpaper"
        );
      default:
        throw new IllegalArgumentException();
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myRootPanel;
  }

  public void addListener(@NotNull ModuleDownloadDeviceFeatureListener listener) {
    myListeners.add(listener);
  }

  public void removeListener(@NotNull ModuleDownloadDeviceFeatureListener listener) {
    myListeners.remove(listener);
  }

  private class ActivationListener implements PropertyChangeListener {
    @Override
    public void propertyChange(@NotNull PropertyChangeEvent evt) {
      // Setup/release bindings when ancestor changes
      if (evt.getNewValue() == null) {
        myBindings.releaseAll();
        myBindingsListeners.releaseAll();
      }
      else {
        myBindings.bindTwoWay(new SelectedItemProperty<>(myFeatureNameCombo), myModel.deviceFeatureType());
        myBindings.bindTwoWay(new TextFieldProperty(myFeatureValueTextField), myModel.deviceFeatureValue());
      }
    }
  }

  private static class TextFieldProperty extends StringProperty {
    @NotNull
    private TextFieldWithAutoCompletion<String> myTextField;

    private TextFieldProperty(@NotNull TextFieldWithAutoCompletion<String> textField) {
      myTextField = textField;
      myTextField.addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          notifyInvalidated();
        }
      });
    }

    @Override
    protected void setDirectly(@NotNull String value) {
      myTextField.setText(value);
    }

    @NotNull
    @Override
    public String get() {
      return myTextField.getText();
    }
  }
}
