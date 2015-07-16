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

import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.wizard.SdkQuickfixWizard;
import com.android.tools.idea.welcome.wizard.WelcomeUIUtils;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.utils.HtmlBuilder;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.updateSettings.impl.AbstractUpdateDialog;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;

/**
 * Confirmation dialog for installing updates. Allows ignore/remind later/install/show release notes,
 * and links to settings.
 */
public class UpdateInfoDialog extends AbstractUpdateDialog {
  private static final String RELEASE_NOTES_URL = "http://developer.android.com/tools/revisions/index.html";

  private final List<IPkgDesc> myPackages;

  protected UpdateInfoDialog(boolean enableLink, List<IPkgDesc> packages) {
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
        SdkQuickfixWizard sdkQuickfixWizard =
          new SdkQuickfixWizard(null, null, myPackages, new DialogWrapperHost(null, DialogWrapper.IdeModalityType.PROJECT));
        sdkQuickfixWizard.init();
        if (sdkQuickfixWizard.showAndGet()) {
          close(0);
        }
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
        for (IPkgDesc desc : myPackages) {
          ignores.add(desc.getInstallId());
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

    public UpdateInfoPanel(List<IPkgDesc> packages) {
      configureMessageArea(mySettingsLink);
      myDownloadSize.setText(WelcomeUIUtils.getSizeLabel(computeDownloadSize(packages)));
      HtmlBuilder packageHtmlBuilder = new HtmlBuilder();
      packageHtmlBuilder.openHtmlBody();
      packageHtmlBuilder.beginList();
      for (IPkgDesc desc : packages) {
        packageHtmlBuilder.listItem().add(desc.getListDescription() + " revision " + desc.getPreciseRevision());
      }
      packageHtmlBuilder.closeHtmlBody();
      myPackages.setText(packageHtmlBuilder.getHtml());
    }

    public JPanel getRootPanel() {
      return myRootPanel;
    }

    private long computeDownloadSize(List<IPkgDesc> packages) {
      AndroidSdkData data = AndroidSdkUtils.tryToChooseAndroidSdk();
      SdkState state = SdkState.getInstance(data);
      // Should already be loaded at this point--just reload to be sure.
      state.loadSynchronously(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, null, null, false);
      Map<String, UpdatablePkgInfo> sdkPackages = state.getPackages().getConsolidatedPkgs();
      long size = 0;
      boolean preview =
        SdkComponentSource.PREVIEW_CHANNEL.equals(UpdateSettings.getInstance().getExternalUpdateChannels().get(SdkComponentSource.NAME));
      for (IPkgDesc pkg : packages) {
        String iid = pkg.getInstallId();
        UpdatablePkgInfo updatablePkgInfo = sdkPackages.get(iid);
        if (updatablePkgInfo == null && iid.endsWith(PkgDesc.PREVIEW_SUFFIX)) {
          iid = iid.substring(0, iid.indexOf(PkgDesc.PREVIEW_SUFFIX));
          updatablePkgInfo = sdkPackages.get(iid);
        }
        size += updatablePkgInfo.getRemote(preview).getDownloadSize();
      }
      return size;
    }
  }
}
