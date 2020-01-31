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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import com.android.tools.idea.npw.dynamicapp.DeviceFeatureKind;
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind;
import com.android.tools.idea.tests.gui.framework.fixture.LinkLabelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorTextFieldFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.intellij.ui.components.labels.LinkLabel;
import java.util.Collection;
import java.util.Objects;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.text.JTextComponent;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ConfigureDynamicFeatureDeliveryStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureDynamicFeatureDeliveryStepFixture, W> {

  ConfigureDynamicFeatureDeliveryStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureDynamicFeatureDeliveryStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> enterName(@NotNull String text) {
    JTextComponent textField = findTextFieldWithLabel("Module title (this may be visible to users)");
    replaceText(textField, text);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> setOnDemand(boolean select) {
    selectCheckBoxWithText("Enable on-demand", select);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> setFusing(boolean select) {
    selectCheckBoxWithText("Fusing (include module at install-time for pre-Lollipop devices)", select);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> setDownloadInstallKind(@NotNull DownloadInstallKind value) {
    JComboBox comboBox = robot().finder().findByType(JComboBox.class, true);
    new JComboBoxFixture(robot(), comboBox).selectItem(value.getDisplayName());
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> checkMinimumSdkApiCheckBox() {
    selectCheckBoxWithName("ModuleDownloadConditions.myMinimumSDKLevelCheckBox", true);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> uncheckMinimumSdkApiCheckBox() {
    selectCheckBoxWithName("ModuleDownloadConditions.myMinimumSDKLevelCheckBox", false);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> selectMinimumSdkApi(@NotNull String api) {
    ApiLevelComboBoxFixture apiLevelComboBox =
      new ApiLevelComboBoxFixture(robot(), robot().finder().findByName(target(), "Mobile.minSdk", JComboBox.class));
    apiLevelComboBox.selectApiLevel(api);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> addConditionalDeliveryFeature(@NotNull DeviceFeatureKind featureKind, @NotNull String value) {
    // Click the "+ device-feature" link
    LinkLabel ll = robot().finder().findByName("ModuleDownloadConditions.myAddDeviceFeatureLinkLabel", LinkLabel.class);
    new LinkLabelFixture(robot(), ll).click();

    // Find the panel containing the new "device feature" entry (it is the last panel added in the container)
    JPanel container = robot().finder().findByName("ModuleDownloadConditions.myDeviceFeaturesContainer", JPanel.class);
    Collection<JPanel> featurePanels = robot().finder().findAll(new GenericTypeMatcher<JPanel>(JPanel.class) {
      @Override
      protected boolean isMatching(@NotNull JPanel component) {
        return component.getParent() == container;
      }
    });
    JPanel featurePanel = featurePanels.stream().reduce((fist, second) -> second).get();

    // Set the feature kind and value
    JComboBox featureKindCombo = robot().finder().findByType(featurePanel, JComboBox.class, true);
    new JComboBoxFixture(robot(), featureKindCombo).selectItem(featureKind.getDisplayName());
    EditorTextFieldFixture.find(robot(), featurePanel).replaceText(value);
    return this;
  }

  @NotNull
  public ConfigureDynamicFeatureDeliveryStepFixture<W> removeConditionalDeliveryFeature(@NotNull DeviceFeatureKind featureKind, @NotNull String value) {
    // Find the panel containing the "device feature" entry matching feature kind and value
    JPanel container = robot().finder().findByName("ModuleDownloadConditions.myDeviceFeaturesContainer", JPanel.class);
    Collection<JPanel> featurePanels = robot().finder().findAll(new GenericTypeMatcher<JPanel>(JPanel.class) {
      @Override
      protected boolean isMatching(@NotNull JPanel component) {
        return component.getParent() == container;
      }
    });
    JPanel featurePanel = featurePanels.stream().filter(panel -> {
      JComboBox featureKindCombo = robot().finder().findByType(panel, JComboBox.class, true);
      String featureName = new JComboBoxFixture(robot(), featureKindCombo).selectedItem();
      String featureValue = EditorTextFieldFixture.find(robot(), panel).getText();
      return Objects.equals(featureName, featureKind.getDisplayName()) &&
             Objects.equals(featureValue, value);
    }).findFirst().get();

    // Find the "remove" button and click it
    LinkLabel removeLabel = robot().finder().findByType(featurePanel, LinkLabel.class);
    new LinkLabelFixture(robot(), removeLabel).clickLink();

    Wait.seconds(2).expecting("Remove Label to disappear")
      .until(() -> !removeLabel.isShowing());

    return this;
  }
}
