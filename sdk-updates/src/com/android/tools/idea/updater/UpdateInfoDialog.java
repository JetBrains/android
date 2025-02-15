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

import com.android.repository.api.RemotePackage;
import com.android.repository.impl.meta.Archive;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.AndroidVersionUtils;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.welcome.wizard.WelcomeUiUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.android.utils.HtmlBuilder;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.updateSettings.impl.AbstractUpdateDialog;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import javax.swing.border.TitledBorder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Confirmation dialog for installing updates. Allows ignore/remind later/install/show release notes,
 * and links to settings.
 */
public class UpdateInfoDialog extends AbstractUpdateDialog {
  private static final String RELEASE_NOTES_URL = "https://developer.android.com/tools/revisions/index.html";

  private final List<RemotePackage> myPackages;
  private final JComponent myComponent;

  protected UpdateInfoDialog(boolean enableLink, List<RemotePackage> packages) {
    super(enableLink);
    myPackages = packages;
    getCancelAction().putValue(DEFAULT_ACTION, Boolean.TRUE);
    myComponent = new UpdateInfoPanel(myPackages).getRootPanel();
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    List<Action> actions = new ArrayList<>();

    actions.add(new AbstractAction("Update Now") {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<String> paths = new ArrayList<>();
        for (RemotePackage p : myPackages) {
          paths.add(p.getPath());
        }
        ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(myComponent, paths, true);
        // Can be null if there was an error, in theory. In that case createDialogForPaths shows the error itself.
        if (dialog != null) {
          dialog.show();
        }
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
    private JBScrollPane myScrollPane;

    public UpdateInfoPanel(List<RemotePackage> packages) {
      setupUI();
      configureMessageArea(mySettingsLink);
      myDownloadSize.setText(WelcomeUiUtils.getSizeLabel(computeDownloadSize(packages)));

      // Unfortunately null keys aren't allowed, so non-versioned packages get this marker.
      AndroidVersion noVersion = new AndroidVersion(-1, null);
      // Split up by android version, sorted by both key and value
      Map<AndroidVersion, Set<RemotePackage>> versionedPackages =
        packages.stream().collect(Collectors.groupingBy(
          pkg -> pkg.getTypeDetails() instanceof DetailsTypes.ApiDetailsType
                 ? ((DetailsTypes.ApiDetailsType)pkg.getTypeDetails()).getAndroidVersion()
                 : noVersion,
          TreeMap::new, Collectors.toCollection(TreeSet::new)
        ));

      HtmlBuilder packageHtmlBuilder = new HtmlBuilder();
      packageHtmlBuilder.openHtmlBody();

      if (versionedPackages.containsKey(noVersion)) {
        for (RemotePackage p : versionedPackages.getOrDefault(noVersion, Collections.emptySet())) {
          packageHtmlBuilder.addNbsps(4).add(p.getDisplayName() + " revision " + p.getVersion()).newline();
        }
        versionedPackages.remove(noVersion);
      }
      boolean first = true;
      for (AndroidVersion version : versionedPackages.keySet()) {
        if (!first) {
          packageHtmlBuilder.newline();
        }
        else {
          first = false;
        }
        packageHtmlBuilder.addNbsps(4).addBold(AndroidVersionUtils.getFullApiName(version, true, true) + ":").newline();
        for (RemotePackage p : versionedPackages.get(version)) {
          packageHtmlBuilder.addNbsps(8).add(p.getDisplayName() + " revision " + p.getVersion()).newline();
        }
      }

      packageHtmlBuilder.closeHtmlBody();
      myPackages.setText(packageHtmlBuilder.getHtml());
      myScrollPane.getVerticalScrollBar().setUnitIncrement(10);
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

    /**
     * This code was originally generated by formc, so ignore inspections.
     * If it is modified manually, inspections should be reinstated and issues fixed.
     */
    @SuppressWarnings("HtmlPaneColors")
    private void setupUI() {
      myRootPanel = new JPanel();
      myRootPanel.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
      final JBLabel jBLabel1 = new JBLabel();
      jBLabel1.setText(
        "<html>Updates are available for the Android SDK and Tools<br><br>Android SDK updates ensure you have the latest features and enhancements.<br>Android Studio will update the following components:</html>");
      myRootPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                    false));
      final Spacer spacer1 = new Spacer();
      myRootPanel.add(spacer1, new GridConstraints(4, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
      final JBLabel jBLabel2 = new JBLabel();
      jBLabel2.setText("Download Size:");
      myRootPanel.add(jBLabel2, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                    false));
      myDownloadSize = new JBLabel();
      myRootPanel.add(myDownloadSize, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                          null, 0, false));
      final Spacer spacer2 = new Spacer();
      myRootPanel.add(spacer2, new GridConstraints(2, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                   GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
      mySettingsLink = new JEditorPane();
      mySettingsLink.setContentType("text/html");
      myRootPanel.add(mySettingsLink, new GridConstraints(3, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                          GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                          null, null, 0, false));
      myScrollPane = new JBScrollPane();
      myRootPanel.add(myScrollPane, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
      myScrollPane.setBorder(
        BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0), null, TitledBorder.DEFAULT_JUSTIFICATION,
                                         TitledBorder.DEFAULT_POSITION, null, null));
      myPackages = new JBLabel();
      myScrollPane.setViewportView(myPackages);
    }
  }
}
