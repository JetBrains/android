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
package com.android.tools.idea.npw.project;

import static java.lang.String.format;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.annotations.concurrency.Slow;
import com.android.ide.common.sdk.LoadStatus;
import com.android.repository.api.UpdatablePackage;
import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.npw.module.AndroidApiLevelComboBox;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.OptionalProperty;
import com.android.tools.idea.observable.ui.SelectedItemProperty;
import com.android.tools.idea.stats.DistributionService;
import com.android.tools.idea.ui.ChooseApiLevelDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.AsyncProcessIcon;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.HyperlinkEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FormFactorSdkControls implements Disposable {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();
  private final AndroidVersionsInfo myAndroidVersionsInfo = new AndroidVersionsInfo();

  private LoadStatus mySdkDataLoadingStatus;
  private LoadStatus myStatsDataLoadingStatus;

  private JPanel myStatsPanel;
  private JBLabel myApiPercentIcon;
  private JBLabel myApiPercentLabel;
  private HyperlinkLabel myLearnMoreLink;
  private JPanel myLoadingDataPanel;
  private AsyncProcessIcon myLoadingDataIcon;
  private JLabel myLoadingDataLabel;
  private final AndroidApiLevelComboBox myMinSdkCombobox = new AndroidApiLevelComboBox();
  private JPanel myRoot;

  public void init(OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo, Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);

    myBindings.bind(androidSdkInfo, new SelectedItemProperty<>(myMinSdkCombobox));
    myListeners.listen(androidSdkInfo, value ->
      value.ifPresent(item -> updateApiPercentLabel(item))
    );

    myLoadingDataLabel.setForeground(JBColor.GRAY);
    myApiPercentIcon.setIcon(AllIcons.General.BalloonInformation);

    myLearnMoreLink.setHyperlinkText(message("android.wizard.module.help.choose"));
    myLearnMoreLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        int minApiLevel = getSelectedApiLevel().getMinApiLevel();
        ChooseApiLevelDialog chooseApiLevelDialog = new ChooseApiLevelDialog(null, minApiLevel);
        Disposer.register(FormFactorSdkControls.this, chooseApiLevelDialog.getDisposable());
        if (!chooseApiLevelDialog.showAndGet()) {
          return;
        }

        int selectedApiLevel = chooseApiLevelDialog.getSelectedApiLevel();
        for (int i = 0; i < myMinSdkCombobox.getItemCount(); i++) {
          AndroidVersionsInfo.VersionItem item = myMinSdkCombobox.getItemAt(i);
          if (item.getMinApiLevel() == selectedApiLevel) {
            myMinSdkCombobox.setSelectedItem(item);
            break;
          }
        }
      }
    });
  }

  @NotNull
  public AndroidApiLevelComboBox getMinSdkComboBox() {
    return myMinSdkCombobox;
  }

  @NotNull
  public JPanel getRoot() {
    return myRoot;
  }

  public void showStatsPanel(boolean show) {
    myStatsPanel.setVisible(show);
  }

  public void startDataLoading(FormFactor formFactor, int minSdk) {
    mySdkDataLoadingStatus = LoadStatus.LOADING;
    myStatsDataLoadingStatus = myStatsPanel.isVisible() ? LoadStatus.LOADING : LoadStatus.LOADED;
    updateLoadingProgress();

    myAndroidVersionsInfo.loadLocalVersions();
    myMinSdkCombobox.init(formFactor, myAndroidVersionsInfo.getKnownTargetVersions(formFactor, minSdk)); // Pre-populate
    myAndroidVersionsInfo.loadRemoteTargetVersions(formFactor, minSdk, items -> {
      myMinSdkCombobox.init(formFactor, items);
      mySdkDataLoadingStatus = LoadStatus.LOADED;
      updateLoadingProgress();
    });

    if (myStatsPanel.isVisible()) {
      DistributionService.getInstance().refresh(
        () -> ApplicationManager.getApplication().invokeLater(() -> {
          myStatsDataLoadingStatus = LoadStatus.LOADED;
          updateLoadingProgress();
        }, ModalityState.any()),
        () -> ApplicationManager.getApplication().invokeLater(() -> {
          myStatsDataLoadingStatus = LoadStatus.FAILED;
          updateLoadingProgress();
        }, ModalityState.any()), false);
    }
  }

  public Collection<? extends UpdatablePackage> getSdkInstallPackageList() {
    return myAndroidVersionsInfo.loadInstallPackageList(Collections.singletonList(getSelectedApiLevel()));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  private void createUIComponents() {
    myLoadingDataIcon = new AsyncProcessIcon(message("android.wizard.module.help.loading"));
  }

  private void updateLoadingProgress() {
    myLoadingDataPanel.setVisible(mySdkDataLoadingStatus != LoadStatus.LOADED || myStatsDataLoadingStatus != LoadStatus.LOADED);
    myLoadingDataIcon.setVisible(mySdkDataLoadingStatus == LoadStatus.LOADING || myStatsDataLoadingStatus == LoadStatus.LOADING);

    if (mySdkDataLoadingStatus == LoadStatus.LOADING) {
      myLoadingDataLabel.setText(message("android.wizard.project.loading.sdks"));
    }
    else if (myStatsDataLoadingStatus == LoadStatus.LOADING) {
      myLoadingDataLabel.setText(message("android.wizard.module.help.refreshing"));
    }
    else if (myStatsDataLoadingStatus == LoadStatus.LOADED) {
      AndroidVersionsInfo.VersionItem currentVersionItem = getSelectedApiLevel();
      if (currentVersionItem != null) {
        updateApiPercentLabel(currentVersionItem);
      }
    }
    else if (myStatsDataLoadingStatus == LoadStatus.FAILED) {
      myLoadingDataLabel.setText(message("android.wizard.project.loading.stats.fail"));
    }
  }

  @Slow
  private static String getApiHelpText(int selectedApi) {
    double percentage = DistributionService.getInstance().getSupportedDistributionForApiLevel(selectedApi) * 100;
    String percentageStr = percentage < 1 ? "<b>&lt; 1%</b>" :
                           format("approximately <b>" + (percentage >= 10 ? "%.3g%%" : "%.2g%%") + "</b>", percentage);
    return format("<html>Your app will run on %1$s of devices.</html>", percentageStr);
  }

  private void updateApiPercentLabel(AndroidVersionsInfo.VersionItem item) {
    CompletableFuture.supplyAsync(() -> getApiHelpText(item.getMinApiLevel()), AppExecutorUtil.getAppExecutorService())
      .thenAcceptAsync(text -> myApiPercentLabel.setText(text), EdtExecutorService.getInstance());
  }

  @Nullable
  AndroidVersionsInfo.VersionItem getSelectedApiLevel() {
    return (AndroidVersionsInfo.VersionItem)myMinSdkCombobox.getSelectedItem();
  }
}
