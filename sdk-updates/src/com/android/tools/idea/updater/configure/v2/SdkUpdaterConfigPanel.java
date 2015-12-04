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
import com.android.repository.api.RepoManager.RepoLoadedCallback;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repositoryv2.AndroidSdkHandler;
import com.android.sdklib.repositoryv2.meta.DetailsTypes;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdkv2.StudioLoggerProgressIndicator;
import com.android.tools.idea.sdkv2.StudioProgressRunner;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.wizard.ConsolidatedProgressStep;
import com.android.tools.idea.welcome.wizard.InstallComponentsPath;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.google.common.collect.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.updateSettings.impl.UpdateSettingsConfigurable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.SelectionProvider;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.CausedFocusEvent;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Main panel for {@link SdkUpdaterConfigurable}
 */
public class SdkUpdaterConfigPanel {
  /**
   * Main panel for the sdk configurable.
   */
  private JPanel myRootPane;

  /**
   * "Android SDK Location" text box at the top.
   */
  private JTextField mySdkLocation;

  /**
   * Label for SDK location.
   */
  private JBLabel mySdkLocationLabel;

  /**
   * Link to allow you to edit the sdk location.
   */
  private HyperlinkLabel myEditSdkLink;

  /**
   * Error message that shows if the selected SDK location is invalid.
   */
  private JBLabel mySdkErrorLabel;

  /**
   * Panel showing platform-specific components.
   */
  private PlatformComponentsPanel myPlatformComponentsPanel;

  /**
   * Panel showing non-platform-specific components.
   */
  private ToolComponentsPanel myToolComponentsPanel;

  /**
   * Panel showing what remote sites are checked for updates and new components.
   */
  private UpdateSitesPanel myUpdateSitesPanel;

  /**
   * Link to launch the legacy standalone sdk manager.
   */
  private HyperlinkLabel myLaunchStandaloneLink;

  /**
   * Link to let you switch to the preview channel if there are previews available.
   */
  private HyperlinkLabel myChannelLink;

  /**
   * Whether there are any preview packages available.
   */
  private boolean myHasPreview;

  /**
   * Channel-based setting of whether to show preview packages.
   */
  private boolean myIncludePreview;

  /**
   * Tab pane containing {@link #myPlatformComponentsPanel}, {@link #myToolComponentsPanel}, and {@link #myUpdateSitesPanel}.
   */
  private JBTabbedPane myTabPane;

  /**
   * {@link AndroidSdkHandler} that we use to get the repo manager etc.
   */
  private AndroidSdkHandler mySdkHandler;

  /**
   * {@link Downloader} for fetching remote source lists and packages.
   */
  private Downloader myDownloader;

  /**
   * Settings for the downloader.
   */
  private SettingsController mySettings;

  /**
   * {@link RepoLoadedCallback} that runs when we've finished reloading our local packages.
   */
  private RepoLoadedCallback myLocalUpdater = new RepoLoadedCallback() {
    @Override
    public void doRun(@NotNull final RepositoryPackages packages) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          loadPackages(packages);
        }
      }, ModalityState.any());
    }
  };

  /**
   * {@link RepoLoadedCallback} that runs when we've completely finished reloading our packages.
   */
  private RepoLoadedCallback myRemoteUpdater = new RepoLoadedCallback() {
    @Override
    public void doRun(@NotNull final RepositoryPackages packages) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          loadPackages(packages);
          myPlatformComponentsPanel.finishLoading();
          myToolComponentsPanel.finishLoading();
        }
      });
    }
  };

  /**
   * Construct a new SdkUpdaterConfigPanel.
   *
   * @param manager The {@link RepoManager} from which to get our package information.
   * @param channelChangedCallback Callback to allow us to notify the channel picker panel if we change the selected channel.
   * @param downloader {@link Downloader} to download remote site lists and for installing packages.
   * @param settings {@link SettingsController} for e.g. proxy settings.
   */
  public SdkUpdaterConfigPanel(AndroidSdkHandler handler, final Runnable channelChangedCallback, Downloader downloader, SettingsController settings) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_SDK_MANAGER, UsageTracker.ACTION_SDK_MANAGER_LOADED, null, null);
    myDownloader = downloader;
    mySettings = settings;

    mySdkHandler = handler;
    myUpdateSitesPanel.setSdkManager(handler);
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
        UpdateSettingsConfigurable settings = new UpdateSettingsConfigurable(false);
        ShowSettingsUtil.getInstance().editConfigurable(getComponent(), settings);
        channelChangedCallback.run();
      }
    });
    myEditSdkLink.setHyperlinkText("Edit");
    myEditSdkLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        final DynamicWizardHost host = new DialogWrapperHost(null);
        DynamicWizard wizard = new DynamicWizard(null, null, "SDK Setup", host) {
          @Override
          public void init() {
            ConsolidatedProgressStep progressStep = new ConsolidatedProgressStep(myHost.getDisposable(), host);
            String sdkPath = mySdkLocation.getText();
            File location;
            if (StringUtil.isEmpty(sdkPath)) {
              location = FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK);
            }
            else {
              location = new File(sdkPath);
            }

            // We need to use the old mechanism here until the first run wizard is updated.
            SdkState state = SdkState.getInstance(AndroidSdkUtils.tryToChooseAndroidSdk());
            state.loadSynchronously(SdkState.DEFAULT_EXPIRATION_PERIOD_MS, false, null, null, null, false);
            InstallComponentsPath path =
              new InstallComponentsPath(progressStep, FirstRunWizardMode.MISSING_SDK, location,
                                        state.getPackages().getRemotePkgInfos(), false);
            progressStep.setPaths(Lists.newArrayList(path));
            addPath(path);
            addPath(new SingleStepPath(progressStep));
            super.init();
          }

          @Override
          public void performFinishingActions() {
            final File newPath = IdeSdks.getAndroidSdkPath();
            if (newPath != null) {
              // TODO: remove once first run wizard adopts new framework
              AndroidSdkHandler.getInstance().setLocation(newPath);
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                @Override
                public void run() {
                  mySdkLocation.setText(newPath.getAbsolutePath());
                  refresh();
                }
              });
            }
          }

          @NotNull
          @Override
          protected String getProgressTitle() {
            return "Setting up SDK...";
          }

          @Override
          protected String getWizardActionDescription() {
            return "Setting up SDK...";
          }
        };
        wizard.init();
        wizard.show();
      }
    });
    mySdkLocation.setEditable(false);
    mySdkErrorLabel.setIcon(AllIcons.General.BalloonError);
    mySdkErrorLabel.setForeground(JBColor.RED);
  }

  /**
   * Sets whether (based on the selected channel) we should show preview packages or not.
   * @param includePreview
   */
  public void setIncludePreview(boolean includePreview) {
    myIncludePreview = includePreview;
    myChannelLink.setVisible(myHasPreview && !myIncludePreview);
    myPlatformComponentsPanel.setIncludePreview(includePreview);
    myToolComponentsPanel.setIncludePreview(includePreview);
    loadPackages(getRepoManager().getPackages());
  }

  /**
   * Gets our main component. Useful for e.g. creating modal dialogs that need to show on top of this (that is, with this as the parent).
   */
  public JComponent getComponent() {
    return myRootPane;
  }

  /**
   * @return {@code true} if the user has made any changes, and the "apply" button should be active and "Reset" link shown.
   */
  public boolean isModified() {
    return myPlatformComponentsPanel.isModified() || myToolComponentsPanel.isModified() || myUpdateSitesPanel.isModified();
  }

  /**
   * Sets the standard properties for our {@link TreeTableView}s (platform and tools panels).
   *
   * @param tt The {@link TreeTableView} for which to set properties.
   * @param renderer The {@link UpdaterTreeNode.Renderer} that renders the table.
   * @param listener {@link ChangeListener} to be notified when a node's state is changed.
   */
  static void setTreeTableProperties(final TreeTableView tt, UpdaterTreeNode.Renderer renderer, final ChangeListener listener) {
    tt.setTreeCellRenderer(renderer);
    new CheckboxClickListener(tt, renderer).installOn(tt);
    TreeUtil.installActions(tt.getTree());

    tt.getTree().setToggleClickCount(0);
    tt.getTree().setShowsRootHandles(true);

    setTableProperties(tt, listener);
  }

  /**
   * Sets the standard properties for our {@link JTable}s (platform, tools, and sources panels).
   *
   * @param table The {@link JTable} for which to set properties.
   * @param listener {@link ChangeListener} to be notified when a node's state is changed.
   */
  static void setTableProperties(@NotNull final JTable table, @Nullable final ChangeListener listener) {
    assert table instanceof SelectionProvider;
    ActionMap am = table.getActionMap();
    final CycleAction forwardAction = new CycleAction(false);
    final CycleAction backwardAction = new CycleAction(true);
    am.put("selectPreviousColumnCell", backwardAction);
    am.put("selectNextColumnCell", forwardAction);

    table.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
          List<MultiStateRow> selection = (List<MultiStateRow>)((SelectionProvider)table).getSelection();
          for (MultiStateRow node : selection) {
            node.cycleState();
            table.repaint();
            if (listener != null) {
              listener.stateChanged(new ChangeEvent(node));
            }
          }
        }
      }
    });
    table.addFocusListener(new FocusListener() {
      @Override
      public void focusLost(FocusEvent e) {
        if (e.getOppositeComponent() != null) {
          table.getSelectionModel().clearSelection();
        }
      }

      @Override
      public void focusGained(FocusEvent e) {
        JTable table = (JTable)e.getSource();
        if (table.getSelectionModel().getMinSelectionIndex() != -1) {
          return;
        }
        if (e instanceof CausedFocusEvent && ((CausedFocusEvent)e).getCause() == CausedFocusEvent.Cause.TRAVERSAL_BACKWARD) {
          backwardAction.doAction(table);
        }
        else {
          forwardAction.doAction(table);
        }
      }
    });
  }

  /**
   * Helper to size a table's columns to fit their normal contents.
   */
  protected static void resizeColumnsToFit(JTable table) {
    TableColumnModel columnModel = table.getColumnModel();
    for (int column = 1; column < table.getColumnCount(); column++) {
      int width = 50;
      for (int row = 0; row < table.getRowCount(); row++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        Component comp = table.prepareRenderer(renderer, row, column);
        width = Math.max(comp.getPreferredSize().width + 1, width);
      }
      columnModel.getColumn(column).setPreferredWidth(width);
    }
  }

  private RepoManager getRepoManager() {
    return mySdkHandler.getSdkManager(new StudioLoggerProgressIndicator(getClass()));
  }

  /**
   * Revalidates and refreshes our packages. Notifies platform and tools components of the start and end, so they can update their UIs.
   */
  public void refresh() {
    validate();

    myPlatformComponentsPanel.startLoading();
    myToolComponentsPanel.startLoading();

    // TODO: make progress runner handle invokes?
    StudioProgressRunner progressRunner = new StudioProgressRunner(false, false, false, "Loading SDK", false, null);
    getRepoManager().load(0, ImmutableList.of(myLocalUpdater), ImmutableList.of(myRemoteUpdater),
                       null, progressRunner, myDownloader, mySettings, false);
  }

  /**
   * Validates {@link #mySdkLocation} and shows appropriate errors in the UI if needed.
   */
  private void validate() {
    File sdkLocation = getRepoManager().getLocalPath();
    WizardUtils.ValidationResult result =
      WizardUtils.validateLocation(sdkLocation != null ? sdkLocation.getAbsolutePath() : null, "Android SDK Location", false);
    myTabPane.setEnabled(!result.isError());
    myPlatformComponentsPanel.setEnabled(!result.isError());
    mySdkLocationLabel.setForeground(result.isOk() ? JBColor.foreground() : JBColor.RED);
    mySdkErrorLabel.setVisible(!result.isOk());
    if (!result.isOk()) {
      mySdkErrorLabel.setText(result.getFormattedMessage());
    }
  }

  /**
   * Reads the packages provided by {@link #myRepoManager} and updates our subpanels appropriately.
   */
  private void loadPackages(RepositoryPackages packages) {
    Multimap<AndroidVersion, UpdatablePackage> platformPackages = TreeMultimap.create();
    Set<UpdatablePackage> buildToolsPackages = Sets.newTreeSet();
    Set<UpdatablePackage> toolsPackages = Sets.newTreeSet();
    for (UpdatablePackage info : packages.getConsolidatedPkgs().values()) {
      if (!myIncludePreview && !info.hasLocal() && !info.hasRemote(false)) {
        // We're not looking for previews, and this only has a preview available.
        continue;
      }
      RepoPackage p = info.getRepresentative();
      TypeDetails details = p.getTypeDetails();
      if (details instanceof DetailsTypes.ApiDetailsType) {
        platformPackages.put(DetailsTypes.getAndroidVersion((DetailsTypes.ApiDetailsType)details), info);
      }
      else if (details instanceof DetailsTypes.BuildToolDetailsType) {
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

  /**
   * Gets the consolidated list of {@link NodeStateHolder}s from our children so they can be applied.
   * @return
   */
  public Collection<NodeStateHolder> getStates() {
    List<NodeStateHolder> result = Lists.newArrayList();
    result.addAll(myPlatformComponentsPanel.myStates);
    result.addAll(myToolComponentsPanel.myStates);
    return result;
  }

  /**
   * Resets our state back to what it was before the user made any changes.
   */
  public void reset() {
    refresh();
    File path = IdeSdks.getAndroidSdkPath();
    if (path != null) {
      mySdkLocation.setText(path.getPath());
    }
    myPlatformComponentsPanel.reset();
    myToolComponentsPanel.reset();
    myUpdateSitesPanel.reset();
  }

  /**
   * Save any changes to our {@link RepositorySource}s.
   */
  public void saveSources() {
    myUpdateSitesPanel.save();
  }

  /**
   * Create our UI components that need custom creation.
   */
  private void createUIComponents() {
    myUpdateSitesPanel = new UpdateSitesPanel(new Runnable() {
      @Override
      public void run() {
        refresh();
      }
    });
  }

  /**
   * Generic action to cycle through the rows in a table, either forward or backward.
   */
  private static class CycleAction extends AbstractAction {
    boolean myBackward;

    CycleAction(boolean backward) {
      myBackward = backward;
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
      doAction((JTable)evt.getSource());
    }

    public void doAction(JTable table) {
      KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
      ListSelectionModel selectionModel = table.getSelectionModel();
      int row = myBackward ? selectionModel.getMinSelectionIndex() : selectionModel.getMaxSelectionIndex();

      if (row == -1) {
        if (myBackward) {
          row = table.getRowCount();
        }
      }
      row += myBackward ? -1 : 1;
      if (row < 0) {
        manager.focusPreviousComponent(table);
      }
      else if (row >= table.getRowCount()) {
        manager.focusNextComponent(table);
      }
      else {
        selectionModel.setSelectionInterval(row, row);
        table.setColumnSelectionInterval(1, 1);
        table.scrollRectToVisible(table.getCellRect(row, 1, true));
      }
      table.repaint();
    }
  }

  /**
   * Convenience to allow us to easily and consistently implement keyboard accessibility features on our tables.
   */
  public interface MultiStateRow {
    void cycleState();
  }
}
