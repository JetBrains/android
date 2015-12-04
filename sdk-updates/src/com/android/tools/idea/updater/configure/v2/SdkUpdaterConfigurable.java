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
package com.android.tools.idea.updater.configure.v2;

import com.android.repository.api.*;
import com.android.repository.io.FileOp;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.sdk.wizard.v2.SdkQuickfixUtils;
import com.android.tools.idea.sdkv2.RepoProgressIndicatorAdapter;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.android.tools.idea.updater.SdkComponentSource;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.AncestorListenerAdapter;
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
  private AndroidSdkHandler mySdkHandler;

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
    mySdkHandler = AndroidSdkHandler.getInstance();
    myPanel = new SdkUpdaterConfigPanel(mySdkHandler, channelChangedCallback, StudioDownloader.getInstance(), StudioSettingsController.getInstance());
    JComponent component = myPanel.getComponent();
    component.addAncestorListener(new AncestorListenerAdapter() {
      @Override
      public void ancestorAdded(AncestorEvent event) {
        channelChangedCallback.run();
      }
    });

    return myPanel.getComponent();
  }

  private RepoManager getRepoManager() {
    return mySdkHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass()));
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
    final List<LocalPackage> toDelete = Lists.newArrayList();
    final List<UpdatablePackage> requestedPackages = Lists.newArrayList();
    for (NodeStateHolder holder : myPanel.getStates()) {
      if (holder.getState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        if (holder.getPkg().hasLocal()) {
          toDelete.add(holder.getPkg().getLocalInfo());
        }
      }
      else if (holder.getState() == NodeStateHolder.SelectedState.INSTALLED &&
               (holder.getPkg().isUpdate(myIncludePreview) || !holder.getPkg().hasLocal())) {
        requestedPackages.add(holder.getPkg());
      }
    }
    boolean found = false;
    if (!toDelete.isEmpty()) {
      found = true;
      message.add("The following components will be deleted: \n");
      message.beginList();
      for (LocalPackage item : toDelete) {
        message.listItem().add(item.getDisplayName()).add(", Revision: ")
          .add(item.getVersion().toString());
      }
      message.endList();
    }
    if (!requestedPackages.isEmpty()) {
      found = true;
      message.add("The following components will be installed: \n");
      message.beginList();
      for (UpdatablePackage p : requestedPackages) {
        // TODO: channels
        RepoPackage item = p.getRemote(myIncludePreview);
        assert item != null;
        message.listItem().add(String.format("%1$s %2$s %3$s", item.getDisplayName(),
                                             item.getTypeDetails() instanceof DetailsTypes.ApiDetailsType ? "revision" : "version",
                                             item.getVersion()));
      }
      message.endList();
    }
    message.closeHtmlBody();
    if (found) {
      if (Messages.showOkCancelDialog((Project)null, message.getHtml(), "Confirm Change", AllIcons.General.Warning) == Messages.OK) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
          @Override
          public void run() {
            ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
            com.android.repository.api.ProgressIndicator repoProgress = new RepoProgressIndicatorAdapter(progress);
            FileOp fop = FileOpUtils.create();
            for (LocalPackage item : toDelete) {
              AndroidSdkHandler.findBestInstaller(item).uninstall(item, repoProgress, getRepoManager(), fop);
            }
          }
        }, "Uninstalling", false, null, myPanel.getComponent());
        if (!requestedPackages.isEmpty()) {
          ModelWizardDialog dialog = SdkQuickfixUtils.createDialog(myPanel.getComponent(), requestedPackages);
          if (dialog != null) {
            dialog.show();
          }
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
  }
}
