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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.observable.BatchInvoker;
import com.android.tools.idea.observable.TestInvokeStrategy;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import javax.swing.JPanel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@RunsInEdt
public class ModuleDownloadDeviceFeatureTest {

  private AndroidProjectRule myProjectRule = AndroidProjectRule.inMemory();
  // AndroidProjectRule should not be initialized on the EDT
  @Rule
  public RuleChain myRuleChain = RuleChain.outerRule(myProjectRule).around(new EdtRule());

  private final TestInvokeStrategy myInvokeStrategy = new TestInvokeStrategy();

  @Before
  public void setUp() {
    BatchInvoker.setOverrideStrategy(myInvokeStrategy);
  }

  @After
  public void tearDown() {
    BatchInvoker.clearOverrideStrategy();
  }

  @Test
  public void testValidation() {
    Project project = myProjectRule.getProject();

    DeviceFeatureModel deviceFeatureModel = new DeviceFeatureModel();
    ValidatorPanel validatorPanel = new ValidatorPanel(project, new JPanel());

    new ModuleDownloadDeviceFeature(project, deviceFeatureModel, new BoolValueProperty(true), validatorPanel);

    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Device feature value must be set");

    deviceFeatureModel.deviceFeatureValue.set("test<");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Illegal character '<' in Name 'test<'");

    deviceFeatureModel.deviceFeatureValue.set("\"test");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Illegal character '\"' in Name '\"test'");

    deviceFeatureModel.deviceFeatureValue.set("\"tes&t");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Illegal character '\"' in Name '\"tes&t'");

    deviceFeatureModel.deviceFeatureValue.set("<\"tes&t");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Illegal character '<' in Name '<\"tes&t'");

    deviceFeatureModel.deviceFeatureType.set(DeviceFeatureKind.GL_ES_VERSION);

    deviceFeatureModel.deviceFeatureValue.set("&<\"tes&t");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Illegal character '&' in OpenGL ES Version '&<\"tes&t'");

    deviceFeatureModel.deviceFeatureValue.set("test&<\"tes&t");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Illegal character '&' in OpenGL ES Version 'test&<\"tes&t'");

    deviceFeatureModel.deviceFeatureValue.set("test");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isFalse();

    deviceFeatureModel.deviceFeatureValue.set("");
    myInvokeStrategy.updateAllSteps();
    assertThat(validatorPanel.hasErrors().get()).isTrue();
    assertThat(getValidationText(validatorPanel)).isEqualTo("Device feature value must be set");
  }

  private static String getValidationText(ValidatorPanel validatorPanel) {
    return StringUtil.removeHtmlTags(validatorPanel.getValidationText().getText());
  }
}