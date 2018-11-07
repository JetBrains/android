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

import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.FormFactorSdkControls;
import com.android.tools.idea.observable.ObservableValue;
import com.android.tools.idea.observable.collections.ObservableList;
import com.android.tools.idea.observable.core.BoolValueProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.expressions.bool.AndExpression;
import com.android.tools.idea.observable.expressions.bool.BooleanExpression;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.labels.LinkListener;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ModuleDownloadConditions {
  public JPanel myRootPanel;
  public JCheckBox myMinimumSDKLevelCheckBox;
  private JPanel myDeviceFeaturesContainer;
  private FormFactorSdkControls myFormFactorSdkControls;
  @SuppressWarnings("unused") // Defined to make things clearer in UI designer.
  private JPanel myFormFactorSdkControlsPanel;
  private LinkLabel<Void> myAddDeviceFeatureLinkLabel;
  private ObservableList<DeviceFeatureModel> myModel;
  private ObservableValue<Boolean> myIsPanelActive;
  private Project myProject;
  private ValidatorPanel myValidatorPanel;

  public ModuleDownloadConditions() {
    // Note: BoxLayout can't be set in the Forms designer
    myDeviceFeaturesContainer.setLayout(new BoxLayout(myDeviceFeaturesContainer, BoxLayout.Y_AXIS));

    myAddDeviceFeatureLinkLabel.setIcon(null); // Clear default icon

    // Handle the "+ device-feature" button
    myAddDeviceFeatureLinkLabel.setListener(new LinkListener<Void>() {
      @Override
      public void linkSelected(LinkLabel aSource, Void aLinkData) {
        addDeviceFeatureRow();
      }
    }, null);
  }

  public void init(@NotNull Project project,
                   @NotNull Disposable parentDisposable,
                   @NotNull OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo,
                   @NotNull ValidatorPanel validatorPanel,
                   @NotNull ObservableValue<Boolean> isPanelActive) {
    myProject = project;
    myValidatorPanel = validatorPanel;
    myFormFactorSdkControls.init(androidSdkInfo, parentDisposable);
    myIsPanelActive = isPanelActive;
  }

  public void setModel(@NotNull ObservableList<DeviceFeatureModel> model) {
    myModel = model;
  }

  public void startSdkDataLoading(FormFactor factor, int sdk) {
    myFormFactorSdkControls.startDataLoading(factor, sdk);
  }

  public void setSdkControlEnabled(Boolean enabled) {
    myFormFactorSdkControls.setEnabled(enabled);
  }

  private void addDeviceFeatureRow() {
    if (myModel != null) {
      // Create model and form for new device feature
      DeviceFeatureModel deviceFeature = new DeviceFeatureModel();
      myModel.add(deviceFeature);

      BoolValueProperty isFeatureActive = new BoolValueProperty(true);
      BooleanExpression isFeatureActiveExpression = new AndExpression(isFeatureActive, myIsPanelActive);
      ModuleDownloadDeviceFeature deviceFeatureForm =
        new ModuleDownloadDeviceFeature(myProject, deviceFeature, isFeatureActiveExpression, myValidatorPanel);
      deviceFeatureForm.addListener(new ModuleDownloadDeviceFeatureListener() {
        @Override
        public void removeFeatureInvoked() {
          isFeatureActive.set(false);
          removeDeviceFeatureRow(deviceFeature);
        }
      });

      // Add new component at bottom of layout
      myDeviceFeaturesContainer.add(deviceFeatureForm.getComponent(), -1);
      myDeviceFeaturesContainer.revalidate();
      myDeviceFeaturesContainer.repaint();
    }
  }

  private void removeDeviceFeatureRow(@NotNull DeviceFeatureModel deviceFeatureModel) {
    int rowIndex = myModel.indexOf(deviceFeatureModel);
    if (rowIndex < 0) {
      //TODO: warning
      return;
    }

    // Remove from model
    myModel.remove(rowIndex);

    // Remove component at [rowIndex] from container
    myDeviceFeaturesContainer.remove(rowIndex);
    myDeviceFeaturesContainer.revalidate();
    myDeviceFeaturesContainer.repaint();
  }

  private void createUIComponents() {
    myFormFactorSdkControls = new FormFactorSdkControls();
    myFormFactorSdkControls.showStatsPanel(false);
    myFormFactorSdkControlsPanel = myFormFactorSdkControls.getRoot();
  }
}
