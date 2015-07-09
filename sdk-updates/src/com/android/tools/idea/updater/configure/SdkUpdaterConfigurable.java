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
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.updater.SdkComponentSource;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import java.util.List;

/**
 * Configurable for the Android SDK Manager.
 * TODO(jbakermalone): implement the searchable interface more completely. Unfortunately it seems that this involves not using forms
 * for the UI?
 */
public class SdkUpdaterConfigurable implements SearchableConfigurable {
  private SdkUpdaterConfigPanel myPanel;
  private boolean myIncludePreview;

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
    final Runnable channelChangedCallback = new Runnable() {
      @Override
      public void run() {
        boolean newIncludePreview =
          SdkComponentSource.PREVIEW_CHANNEL.equals(UpdateSettings.getInstance().getExternalUpdateChannels().get(SdkComponentSource.NAME));
        if (newIncludePreview != myIncludePreview) {
          myIncludePreview = newIncludePreview;
          myPanel.setIncludePreview(myIncludePreview);
        }
      }
    };
    SdkState state = SdkState.getInstance(data);
    myPanel = new SdkUpdaterConfigPanel(state, channelChangedCallback);
    JComponent component = myPanel.getComponent();
    component.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        channelChangedCallback.run();
      }
    });

    return myPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.saveSources();

    HtmlBuilder message = new HtmlBuilder();
    message.openHtmlBody();
    List<UpdatablePkgInfo> toDelete = Lists.newArrayList();
    List<IPkgDesc> requestedPackages = Lists.newArrayList();
    for (NodeStateHolder holder : myPanel.getStates()) {
      if (holder.getState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        if (holder.getPkg().hasLocal()) {
          toDelete.add(holder.getPkg());
        }
      }
      else if (holder.getState() == NodeStateHolder.SelectedState.INSTALLED &&
               (holder.getPkg().isUpdate(myIncludePreview) || !holder.getPkg().hasLocal())) {
        requestedPackages.add(holder.getPkg().getRemote(myIncludePreview).getPkgDesc());
      }
    }
    boolean found = false;
    if (!toDelete.isEmpty()) {
      found = true;
      message.add("The following components will be deleted: \n");
      message.beginList();
      for (UpdatablePkgInfo item : toDelete) {
        message.listItem().add(item.getPkgDesc(myIncludePreview).getListDescription()).add(", Revision: ")
          .add(item.getPkgDesc(myIncludePreview).getPreciseRevision().toString());
      }
      message.endList();
    }
    if (!requestedPackages.isEmpty()) {
      found = true;
      message.add("The following components will be installed: \n");
      message.beginList();
      for (IPkgDesc item : requestedPackages) {
        message.listItem().add(String.format("%1$s %2$s %3$s", item.getListDescription(), item.hasAndroidVersion() ? "revision" : "version",
                                             item.getPreciseRevision()));
      }
      message.endList();
    }
    message.closeHtmlBody();
    if (found) {
      if (Messages.showOkCancelDialog((Project)null, message.getHtml(), "Confirm Change", AllIcons.General.Warning) == Messages.OK) {
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
