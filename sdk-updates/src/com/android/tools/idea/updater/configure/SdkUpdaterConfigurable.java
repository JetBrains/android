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
package com.android.tools.idea.updater.configure;

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.List;

/**
 * Configurable for the Android SDK Manager.
 * TODO(jbakermalone): implement the searchable interface more completely. Unfortunately it seems that this involves not using forms
 *                     for the UI?
 */
public class SdkUpdaterConfigurable implements SearchableConfigurable {
  SdkUpdaterConfigPanel myPanel;

  @NotNull
  @Override
  public String getId() {
    return "AndroidSdkUpdater";
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Android SDK Updater";
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return null;
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();
    if (data == null) {
      JPanel errorPanel = new JPanel();
      errorPanel.add(new JBLabel("Failed to find android sdk"));
      return errorPanel;
    }
    SdkState state = SdkState.getInstance(data);
    myPanel = new SdkUpdaterConfigPanel(state);
    return myPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          IdeSdks.setAndroidSdkPath(new File(myPanel.getSdkPath()), null);
        }
      });

    myPanel.saveSources();

    StringBuilder message = new StringBuilder();

    List<UpdatablePkgInfo> toDelete = Lists.newArrayList();
    List<IPkgDesc> requestedPackages = Lists.newArrayList();
    for (NodeStateHolder holder : myPanel.getStates()) {
      if (holder.getState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        if (holder.getPkg().hasLocal()) {
          toDelete.add(holder.getPkg());
        }
      } else if (holder.getState() == NodeStateHolder.SelectedState.INSTALLED && (holder.getPkg().isUpdate() || !holder.getPkg().hasLocal())) {
        requestedPackages.add(holder.getPkg().getPkgDesc());
      }
    }
    if (!toDelete.isEmpty()) {
      message.append("The following components will be deleted: \n");
      for (UpdatablePkgInfo item : toDelete) {
        message.append("    -");
        message.append(item.getPkgDesc().getListDescription());
        message.append(", Revision: ");
        message.append(item.getPkgDesc().getPreciseRevision());
        message.append("\n");
      }
      if (!requestedPackages.isEmpty()) {
        message.append("\n");
      }
    }
    if (!requestedPackages.isEmpty()) {
      message.append("The following components will be installed: \n");
      for (IPkgDesc item : requestedPackages) {
        message.append("    -");
        message.append(item.getListDescription());
        message.append("\n");
      }
    }
    String messageStr = message.toString();
    if (!messageStr.isEmpty()) {
      if (Messages.showOkCancelDialog(myPanel.getComponent(), messageStr, "Confirm Delete", AllIcons.General.Warning) == Messages.OK) {
        for (UpdatablePkgInfo item : toDelete) {
          item.getLocalInfo().delete();
        }
        if (!requestedPackages.isEmpty()) {
          SdkQuickfixWizard sdkQuickfixWizard = new SdkQuickfixWizard(null, null, requestedPackages);
          sdkQuickfixWizard.init();
          sdkQuickfixWizard.show();
        }
        myPanel.refresh();
      }
      else {
        throw new ConfigurationException("Installation was canceled.");
      }
    }
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.disposeUIResources();
    }
  }
}
