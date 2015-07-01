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

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.*;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.stats.UsageTracker;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.updateSettings.impl.UpdateSettingsConfigurable;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreePath;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Main panel for {@link SdkUpdaterConfigurable}
 */
public class SdkUpdaterConfigPanel {
  private JPanel myRootPane;
  private TextFieldWithBrowseButton mySdkLocation;
  private PlatformComponentsPanel myPlatformComponentsPanel;
  private ToolComponentsPanel myToolComponentsPanel;
  private UpdateSitesPanel myUpdateSitesPanel;
  private HyperlinkLabel myLaunchStandaloneLink;
  private HyperlinkLabel myChannelLink;
  private SdkSources mySdkSources;
  private Runnable mySourcesChangeListener = new DispatchRunnable() {
    @Override
    public void doRun() {
      refresh();
    }
  };

  private final SdkState mySdkState;
  private boolean myHasPreview;
  private boolean myIncludePreview;

  SdkLoadedCallback myUpdater = new SdkLoadedCallback(true) {
    @Override
    public void doRun(@NotNull SdkPackages packages) {
      updateItems(packages);
    }
  };


  public SdkUpdaterConfigPanel(SdkState sdkState, final Runnable channelChangedCallback) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_SDK_MANAGER, UsageTracker.ACTION_SDK_MANAGER_LOADED, null, null);

    mySdkState = sdkState;
    ILogger logger = new LogWrapper(Logger.getInstance(getClass()));
    mySdkSources = mySdkState.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, logger);
    mySdkSources.addChangeListener(mySourcesChangeListener);
    myUpdateSitesPanel.setSdkState(sdkState);
    mySdkLocation.setText(IdeSdks.getAndroidSdkPath().getPath());
    myLaunchStandaloneLink.setHyperlinkText("Launch Standalone SDK Manager");
    myLaunchStandaloneLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        RunAndroidSdkManagerAction.runSpecificSdkManager(null, IdeSdks.getAndroidSdkPath());
      }
    });
    myChannelLink.setHyperlinkText("Preview packages available! ", "Switch", " to Preview Channel to see them");
    myChannelLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        UpdateSettingsConfigurable settings = new UpdateSettingsConfigurable();
        settings.setCheckNowEnabled(false);
        ShowSettingsUtil.getInstance().editConfigurable(getComponent(), settings);
        channelChangedCallback.run();
      }
    });
  }

  public void setIncludePreview(boolean includePreview) {
    myIncludePreview = includePreview;
    myChannelLink.setVisible(myHasPreview && !myIncludePreview);
    myPlatformComponentsPanel.setIncludePreview(includePreview);
    myToolComponentsPanel.setIncludePreview(includePreview);
  }

  public String getSdkPath() {
    return mySdkLocation.getText();
  }

  public JComponent getComponent() {
    return myRootPane;
  }

  public boolean isModified() {
    return myPlatformComponentsPanel.isModified() || myToolComponentsPanel.isModified() || myUpdateSitesPanel.isModified();
  }

  static void setTreeTableProperties(TreeTableView tt, UpdaterTreeNode.Renderer renderer) {
    tt.setTreeCellRenderer(renderer);
    new CheckboxClickListener(tt, renderer).installOn(tt);
    TreeUtil.installActions(tt.getTree());

    tt.setSelectionModel(new DefaultListSelectionModel() {
      @Override
      public void setSelectionInterval(int index0, int index1) {
        // do nothing
      }

      @Override
      public void addSelectionInterval(int index0, int index1) {
        // do nothing
      }
    });
    tt.getTree().setSelectionModel(new DefaultTreeSelectionModel() {
      @Override
      public void addSelectionPaths(TreePath[] path) {
        // do nothing
      }

      @Override
      public void setSelectionPaths(TreePath[] path) {
        // do nothing
      }
    });
    tt.getTree().setToggleClickCount(0);
    tt.getTree().setShowsRootHandles(true);
  }

  public void refresh() {
    myPlatformComponentsPanel.startLoading();
    myToolComponentsPanel.startLoading();
    myUpdateSitesPanel.startLoading();

    SdkLoadedCallback remoteComplete = new SdkLoadedCallback(true) {
      @Override
      public void doRun(@NotNull SdkPackages packages) {
        updateItems(packages);
        myPlatformComponentsPanel.finishLoading();
        myToolComponentsPanel.finishLoading();
        myUpdateSitesPanel.finishLoading();
      }
    };
    mySdkState.loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, myUpdater, remoteComplete, null, true);
  }

  private void loadPackages(SdkPackages packages) {
    Multimap<AndroidVersion, UpdatablePkgInfo> platformPackages = TreeMultimap.create();
    Set<UpdatablePkgInfo> buildToolsPackages = Sets.newTreeSet();
    Set<UpdatablePkgInfo> toolsPackages = Sets.newTreeSet();
    for (UpdatablePkgInfo info : packages.getConsolidatedPkgs().values()) {
      IPkgDesc desc = info.getPkgDesc(myIncludePreview);
      if (desc == null) {
        // We're not looking for previews, and this only has a preview available.
        continue;
      }
      AndroidVersion version = desc.getAndroidVersion();
      PkgType type = info.getPkgDesc(myIncludePreview).getType();
      if (type == PkgType.PKG_SAMPLE) {
        continue;
      }
      if (version != null && type != PkgType.PKG_DOC) {
        platformPackages.put(version, info);
      }
      else if (type == PkgType.PKG_BUILD_TOOLS) {
        buildToolsPackages.add(info);
      }
      else {
        toolsPackages.add(info);
      }
      if (info.hasPreview()) {
        myHasPreview = true;
      }
    }
    myChannelLink.setVisible(myHasPreview && !myIncludePreview);
    myPlatformComponentsPanel.setPackages(platformPackages);
    myToolComponentsPanel.setPackages(toolsPackages, buildToolsPackages);
  }

  private void updateItems(SdkPackages packages) {
    myPlatformComponentsPanel.clearState();
    myToolComponentsPanel.clearState();
    loadPackages(packages);
  }

  public Collection<NodeStateHolder> getStates() {
    List<NodeStateHolder> result = Lists.newArrayList();
    result.addAll(myPlatformComponentsPanel.myStates);
    result.addAll(myToolComponentsPanel.myStates);
    return result;
  }

  public void reset() {
    refresh();
    myPlatformComponentsPanel.reset();
    myToolComponentsPanel.reset();
    myUpdateSitesPanel.reset();
  }

  public void disposeUIResources() {
    mySdkSources.removeChangeListener(mySourcesChangeListener);
  }

  public void saveSources() {
    myUpdateSitesPanel.save();
  }
}
