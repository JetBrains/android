/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.core.BoolValueProperty;
import javax.swing.JPanel;
import org.jetbrains.android.AndroidTestCase;

public class ModuleDownloadDeviceFeatureTest extends AndroidTestCase {

  public void testValidation() {
    DeviceFeatureModel deviceFeatureModel = new DeviceFeatureModel();
    ValidatorPanel validatorPanel = new ValidatorPanel(getProject(), new JPanel());

    new ModuleDownloadDeviceFeature(getProject(), deviceFeatureModel, new BoolValueProperty(true), validatorPanel);

    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Device feature value must be set", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureValue().set("test<");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Illegal character '<' in Name 'test<'", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureValue().set("\"test");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Illegal character '\"' in Name '\"test'", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureValue().set("\"tes&t");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Illegal character '\"' in Name '\"tes&t'", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureValue().set("<\"tes&t");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Illegal character '<' in Name '<\"tes&t'", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureType().set(DeviceFeatureKind.GL_ES_VERSION);

    deviceFeatureModel.deviceFeatureValue().set("&<\"tes&t");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Illegal character '&' in OpenGL ES Version '&<\"tes&t'", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureValue().set("test&<\"tes&t");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Illegal character '&' in OpenGL ES Version 'test&<\"tes&t'", validatorPanel.getValidationLabel().getText());

    deviceFeatureModel.deviceFeatureValue().set("test");
    assertFalse(validatorPanel.hasErrors().get());

    deviceFeatureModel.deviceFeatureValue().set("");
    assertTrue(validatorPanel.hasErrors().get());
    assertEquals("Device feature value must be set", validatorPanel.getValidationLabel().getText());
  }
}