/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome.wizard.deprecated;

import static com.android.tools.idea.welcome.wizard.SdkComponentsStepKt.getDiskSpace;

import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.ComponentTreeNode;
import com.android.tools.idea.welcome.wizard.ComponentsTableModel;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wizard page for selecting SDK components to download.
 * @deprecated use {@link com.android.tools.idea.welcome.wizard.SdkComponentsStep}
 */
@Deprecated
public class SdkComponentsStep extends FirstRunWizardStep {
  @NotNull private final ComponentTreeNode myRootNode;
  @NotNull private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;
  @NotNull private final ScopedStateStore.Key<String> mySdkDownloadPathKey;

  @NotNull private final SdkComponentsStepForm myForm = new SdkComponentsStepForm();
  @NotNull private final SdkComponentsStepController myController;

  public SdkComponentsStep(@Nullable Project project,
                           @NotNull ComponentTreeNode rootNode,
                           @NotNull ScopedStateStore.Key<Boolean> keyCustomInstall,
                           @NotNull ScopedStateStore.Key<String> sdkDownloadPathKey,
                           @NotNull FirstRunWizardMode mode,
                           @NotNull ObjectValueProperty<AndroidSdkHandler> sdkHandlerProperty,
                           @Nullable LicenseAgreementStep licenseAgreementStep,
                           @NotNull Disposable parent) {
    super("SDK Components Setup");
    Disposer.register(parent, myForm);

    myRootNode = rootNode;
    myKeyCustomInstall = keyCustomInstall;
    mySdkDownloadPathKey = sdkDownloadPathKey;

    ComponentsTableModel tableModel = new ComponentsTableModel(rootNode);
    myForm.setTableModel(tableModel);

    myForm.setCellRenderer(new SdkComponentsRenderer(tableModel, myForm.getComponentsTable()) {
      @Override
      public void onCheckboxUpdated() {
        invokeUpdate(null);
      }
    });
    myForm.setCellEditor(new SdkComponentsRenderer(tableModel, myForm.getComponentsTable()) {
      @Override
      public void onCheckboxUpdated() {
        invokeUpdate(null);
      }
    });

    myController = new SdkComponentsStepController(project, mode, myRootNode, sdkHandlerProperty) {
      @Override
      public void setError(@Nullable Icon icon, @Nullable String message) {
        myForm.setErrorIcon(icon);
        setErrorHtml(message);
      }

      @Override
      public void onLoadingStarted() {
        myForm.startLoading();
        invokeUpdate(null);
      }

      @Override
      public void onLoadingFinished() {
        invokeUpdate(null);
        myForm.stopLoading();
      }

      @Override
      public void onLoadingError() {
        invokeUpdate(null);
        myForm.setLoadingText("Error loading components");
      }

      @Override
      public void reloadLicenseAgreementStep() {
        if (licenseAgreementStep != null) {
          licenseAgreementStep.reload();
        }
      }
    };

    setComponent(myForm.getContents());
  }

  @Override
  public boolean validate() {
    return myController.validate(getPath());
  }

  @Override
  public void deriveValues(Set<? extends ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (modified.contains(WizardConstants.KEY_SDK_INSTALL_LOCATION)) {
      String sdkPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
      if (sdkPath != null) {
        myController.onPathUpdated(sdkPath, ModalityState.stateForComponent(myForm.getContents()));
      }
    }
    myForm.setDiskSpace(getDiskSpace(myState.get(mySdkDownloadPathKey)));
    myForm.setDownloadSize(myController.getComponentsSize());
  }

  @Override
  public void init() {
    register(mySdkDownloadPathKey, myForm.getPath());
    if (!myRootNode.getImmediateChildren().isEmpty()) {
      myForm.getComponentsTable().getSelectionModel().setSelectionInterval(0, 0);
    }
  }

  @NotNull
  @Override
  public JLabel getMessageLabel() {
    return myForm.getErrorLabel();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myForm.getComponentsTable();
  }

  @Override
  public boolean isStepVisible() {
    boolean isCustomInstall = myState.getNotNull(myKeyCustomInstall, true);
    return myController.isStepVisible(isCustomInstall, getPath());
  }

  @Override
  public boolean commitStep() {
    myController.warnIfRequiredComponentsUnavailable();
    return true;
  }

  @NotNull
  private String getPath() {
    return StringUtil.notNullize(myState.get(mySdkDownloadPathKey));
  }
}