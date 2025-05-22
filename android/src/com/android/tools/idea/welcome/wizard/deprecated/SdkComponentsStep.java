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

import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.observable.core.ObjectValueProperty;
import com.android.tools.idea.sdk.wizard.legacy.LicenseAgreementStep;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.SdkComponentTreeNode;
import com.android.tools.idea.welcome.wizard.FirstRunWizardTracker;
import com.android.tools.idea.welcome.wizard.SdkComponentsRenderer;
import com.android.tools.idea.welcome.wizard.SdkComponentsStepController;
import com.android.tools.idea.welcome.wizard.SdkComponentsStepUtils;
import com.android.tools.idea.welcome.wizard.SdkComponentsTableModel;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.wireless.android.sdk.stats.SetupWizardEvent;
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
@UiThread
public class SdkComponentsStep extends FirstRunWizardStep {
  @NotNull private final SdkComponentTreeNode myRootNode;
  @NotNull private final ScopedStateStore.Key<Boolean> myKeyCustomInstall;
  @NotNull private final ScopedStateStore.Key<String> mySdkDownloadPathKey;

  @NotNull private final SdkComponentsStepForm myForm = new SdkComponentsStepForm();
  @NotNull private final SdkComponentsStepController myController;

  public SdkComponentsStep(@Nullable Project project,
                           @NotNull SdkComponentTreeNode rootNode,
                           @NotNull ScopedStateStore.Key<Boolean> keyCustomInstall,
                           @NotNull ScopedStateStore.Key<String> sdkDownloadPathKey,
                           @NotNull FirstRunWizardMode mode,
                           @NotNull ObjectValueProperty<AndroidSdkHandler> sdkHandlerProperty,
                           @Nullable LicenseAgreementStep licenseAgreementStep,
                           @NotNull Disposable parent,
                           @NotNull FirstRunWizardTracker tracker) {
    super("SDK Components Setup", tracker);
    Disposer.register(parent, myForm);

    myRootNode = rootNode;
    myKeyCustomInstall = keyCustomInstall;
    mySdkDownloadPathKey = sdkDownloadPathKey;

    SdkComponentsTableModel tableModel = new SdkComponentsTableModel(rootNode);
    myForm.setTableModel(tableModel);

    myForm.setCellRenderer(new SdkComponentsRenderer(tableModel, myForm.getComponentsTable()) {
      @Override
      public void onCheckboxUpdated() {
        invokeUpdate(null);

        if (licenseAgreementStep != null) {
          licenseAgreementStep.reload();
        }
      }
    });
    myForm.setCellEditor(new SdkComponentsRenderer(tableModel, myForm.getComponentsTable()) {
      @Override
      public void onCheckboxUpdated() {
        invokeUpdate(null);

        if (licenseAgreementStep != null) {
          licenseAgreementStep.reload();
        }
      }
    });

    myController = new SdkComponentsStepController(project, mode, myRootNode, sdkHandlerProperty) {
      @UiThread
      @Override
      public void setError(@Nullable Icon icon, @Nullable String message) {
        myForm.setErrorIcon(icon);
        setErrorHtml(message);
      }

      @UiThread
      @Override
      public void onLoadingStarted() {
        myForm.startLoading();
        invokeUpdate(null);
      }

      @UiThread
      @Override
      public void onLoadingFinished() {
        invokeUpdate(null);
        myForm.stopLoading();
      }

      @UiThread
      @Override
      public void onLoadingError() {
        invokeUpdate(null);
        myForm.setLoadingText("Error loading components");
      }

      @UiThread
      @Override
      public void reloadLicenseAgreementStep() {
        if (licenseAgreementStep != null) {
          licenseAgreementStep.reload();
        }
      }
    };
    Disposer.register(parent, myController);

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
        boolean updated = myController.onPathUpdated(sdkPath, ModalityState.defaultModalityState());
        if (updated) myTracker.trackSdkInstallLocationChanged();
      }
    }
    myForm.setDiskSpace(SdkComponentsStepUtils.getDiskSpace(myState.get(mySdkDownloadPathKey)));
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

  @Override
  protected SetupWizardEvent.WizardStep.WizardStepKind getWizardStepKind() {
    return SetupWizardEvent.WizardStep.WizardStepKind.SDK_COMPONENTS;
  }
}