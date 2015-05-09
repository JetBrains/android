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
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.LogWrapper;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.RemoteSdk;
import com.android.tools.idea.sdk.remote.UpdatablePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.utils.ILogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
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
  private SdkSources mySdkSources;
  private Runnable mySourcesChangeListener = new DispatchRunnable() {
    @Override
    public void doRun() {
      refresh();
    }
  };

  private final SdkState mySdkState;

  Runnable myUpdater = new DispatchRunnable() {
    @Override
    public void doRun() {
      updateItems();
    }
  };


  public SdkUpdaterConfigPanel(SdkState sdkState) {
    mySdkState = sdkState;
    ILogger logger = new LogWrapper(Logger.getInstance(getClass()));
    mySdkSources = mySdkState.getRemoteSdk().fetchSources(RemoteSdk.DEFAULT_EXPIRATION_PERIOD_MS, logger);
    mySdkSources.addChangeListener(mySourcesChangeListener);
    myUpdateSitesPanel.setSdkState(sdkState);
    mySdkLocation.setText(IdeSdks.getAndroidSdkPath().getPath());
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
  }

  public void refresh() {
    myPlatformComponentsPanel.startLoading();
    myToolComponentsPanel.startLoading();
    myUpdateSitesPanel.startLoading();

    Runnable remoteComplete = new DispatchRunnable() {
      @Override
      public void doRun() {
        updateItems();
        myPlatformComponentsPanel.finishLoading();
        myToolComponentsPanel.finishLoading();
        myUpdateSitesPanel.finishLoading();
      }
    };
    mySdkState.loadAsync(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, myUpdater, remoteComplete, null, true);
  }

  private void loadPackages() {
    Multimap<AndroidVersion, UpdatablePkgInfo> platformPackages = TreeMultimap.create();
    Set<UpdatablePkgInfo> buildToolsPackages = Sets.newTreeSet();
    Set<UpdatablePkgInfo> toolsPackages = Sets.newTreeSet();
    for (UpdatablePkgInfo info : mySdkState.getPackages().getConsolidatedPkgs().values()) {
      AndroidVersion version = info.getPkgDesc().getAndroidVersion();
      PkgType type = info.getPkgDesc().getType();
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
    }
    myPlatformComponentsPanel.setPackages(platformPackages);
    myToolComponentsPanel.setPackages(toolsPackages, buildToolsPackages);
  }

  private void updateItems() {
    myPlatformComponentsPanel.clearState();
    myToolComponentsPanel.clearState();
    loadPackages();
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
