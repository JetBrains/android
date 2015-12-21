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
package com.android.tools.idea.updater;

import com.android.repository.api.*;
import com.android.repository.impl.meta.Archive;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.tools.idea.sdkv2.RepoProgressIndicatorAdapter;
import com.android.tools.idea.sdkv2.StudioDownloader;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioSettingsController;
import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.android.utils.HtmlBuilder;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.updateSettings.impl.AbstractUpdateDialog;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;

/**
 * Confirmation dialog for installing updates. Allows ignore/remind later/install/show release notes,
 * and links to settings.
 */
public class UpdateInfoDialog extends AbstractUpdateDialog {
  private static final String RELEASE_NOTES_URL = "http://developer.android.com/tools/revisions/index.html";

  private final List<RemotePackage> myPackages;

  protected UpdateInfoDialog(boolean enableLink, List<RemotePackage> packages) {
    super(enableLink);
    myPackages = packages;
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return new UpdateInfoPanel(myPackages).getRootPanel();
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<Action> actions = ContainerUtil.newArrayList();

    actions.add(new AbstractAction("Update Now") {
      @Override
      public void actionPerformed(ActionEvent e) {
        ProgressManager.getInstance().run(new Task.Modal(null, "Installing... ", true) {
          @Override
          public void run(@NotNull com.intellij.openapi.progress.ProgressIndicator indicator) {
            ProgressIndicator repoProgress = new RepoProgressIndicatorAdapter(indicator);
            Downloader downloader = StudioDownloader.getInstance();
            SettingsController settings = StudioSettingsController.getInstance();
            RepoManager mgr = AndroidSdkHandler.getInstance().getSdkManager(new StudioLoggerProgressIndicator(getClass()));
            for (RemotePackage p : myPackages) {
              // TODO: use quickfix wizard
              AndroidSdkHandler.findBestInstaller(p).install(p, downloader, settings, repoProgress, mgr, FileOpUtils.create());
            }
          }
        });
        close(0);
      }
    });

    actions.add(new AbstractAction("Release Notes") {
      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.browse(RELEASE_NOTES_URL);
      }
    });

    actions.add(new AbstractAction(IdeBundle.message("updates.ignore.update.button")) {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<String> ignores = UpdateSettings.getInstance().getIgnoredBuildNumbers();
        for (RemotePackage p : myPackages) {
          ignores.add(SdkComponentSource.getPackageRevisionId(p));
        }
        doCancelAction();
      }
    });

    actions.add(getCancelAction());

    return actions.toArray(new Action[actions.size()]);
  }

  @Override
  protected String getCancelButtonText() {
    return IdeBundle.message("updates.remind.later.button");
  }

  private class UpdateInfoPanel {

    private JPanel myRootPanel;
    private JBLabel myPackages;
    private JBLabel myDownloadSize;
    private JEditorPane mySettingsLink;

    public UpdateInfoPanel(List<RemotePackage> packages) {
      configureMessageArea(mySettingsLink);
      myDownloadSize.setText(WelcomeUIUtils.getSizeLabel(computeDownloadSize(packages)));
      HtmlBuilder packageHtmlBuilder = new HtmlBuilder();
      packageHtmlBuilder.openHtmlBody();
      packageHtmlBuilder.beginList();
      for (RemotePackage p : packages) {
        packageHtmlBuilder.listItem().add(p.getDisplayName() + " revision " + p.getVersion());
      }
      packageHtmlBuilder.closeHtmlBody();
      myPackages.setText(packageHtmlBuilder.getHtml());
    }

    public JPanel getRootPanel() {
      return myRootPanel;
    }

    private long computeDownloadSize(List<RemotePackage> packages) {
      long size = 0;
      for (RemotePackage pkg : packages) {
        Archive arch = pkg.getArchive();
        if (arch != null) {
          size += arch.getComplete().getSize();
        }
      }
      return size;
    }
  }
}
