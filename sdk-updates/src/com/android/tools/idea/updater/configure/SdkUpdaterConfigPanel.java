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

import com.android.SdkConstants;
import com.android.repository.api.*;
import com.android.repository.api.RepoManager.RepoLoadedCallback;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.tools.idea.npw.WizardUtils;
import com.android.tools.idea.npw.WizardUtils.ValidationResult;
import com.android.tools.idea.npw.WizardUtils.WritableCheckMode;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.progress.StudioProgressRunner;
import com.android.tools.idea.stats.UsageTracker;
import com.android.tools.idea.ui.ApplicationUtils;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.install.FirstRunWizardDefaults;
import com.android.tools.idea.welcome.wizard.ConsolidatedProgressStep;
import com.android.tools.idea.welcome.wizard.InstallComponentsPath;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DialogWrapperHost;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardHost;
import com.android.tools.idea.wizard.dynamic.SingleStepPath;
import com.google.common.collect.*;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.updateSettings.impl.UpdateSettingsConfigurable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.dualView.TreeTableView;
import com.intellij.ui.table.SelectionProvider;
import com.intellij.util.ui.accessibility.ScreenReader;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.android.actions.RunAndroidSdkManagerAction;
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
import java.util.Collections;
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
   * Tab pane containing {@link #myPlatformComponentsPanel}, {@link #myToolComponentsPanel}, and {@link #myUpdateSitesPanel}.
   */
  private JBTabbedPane myTabPane;

  /**
   * {@link Downloader} for fetching remote source lists and packages.
   */
  private final Downloader myDownloader;

  /**
   * Settings for the downloader.
   */
  private final SettingsController mySettings;

  /**
   * Reference to the {@link Configurable} that created us, for retrieving sdk state.
   */
  private final SdkUpdaterConfigurable myConfigurable;

  /**
   * {@link RepoLoadedCallback} that runs when we've finished reloading our local packages.
   */
  private final RepoLoadedCallback myLocalUpdater = new RepoLoadedCallback() {
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
  private final RepoLoadedCallback myRemoteUpdater = new RepoLoadedCallback() {
    @Override
    public void doRun(@NotNull final RepositoryPackages packages) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          loadPackages(packages);
          myPlatformComponentsPanel.finishLoading();
          myToolComponentsPanel.finishLoading();
        }
      }, ModalityState.any());
    }
  };

  /**
   * Construct a new SdkUpdaterConfigPanel.
   *
   * @param channelChangedCallback Callback to allow us to notify the channel picker panel if we change the selected channel.
   * @param downloader             {@link Downloader} to download remote site lists and for installing packages. If {@code null} we will
   *                               only show local packages.
   * @param settings               {@link SettingsController} for e.g. proxy settings.
   * @param configurable           The {@link SdkUpdaterConfigurable} that created this.
   */
  public SdkUpdaterConfigPanel(@NotNull final Runnable channelChangedCallback,
                               @Nullable Downloader downloader,
                               @Nullable SettingsController settings,
                               @NotNull SdkUpdaterConfigurable configurable) {
    UsageTracker.getInstance().trackEvent(UsageTracker.CATEGORY_SDK_MANAGER, UsageTracker.ACTION_SDK_MANAGER_LOADED, null, null);
    myConfigurable = configurable;
    myUpdateSitesPanel.setConfigurable(configurable);
    myDownloader = downloader;
    mySettings = settings;

    myLaunchStandaloneLink.setHyperlinkText("Launch Standalone SDK Manager");
    myLaunchStandaloneLink.addHyperlinkListener(new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        File path = IdeSdks.getAndroidSdkPath();
        assert path != null;

        RunAndroidSdkManagerAction.runSpecificSdkManager(null, path);
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
            DownloadingComponentsStep progressStep = new DownloadingComponentsStep(myHost.getDisposable(), myHost);

            String sdkPath = mySdkLocation.getText();
            File location;
            if (StringUtil.isEmpty(sdkPath)) {
              location = FirstRunWizardDefaults.getInitialSdkLocation(FirstRunWizardMode.MISSING_SDK);
            }
            else {
              location = new File(sdkPath);
            }

            InstallComponentsPath path =
              new InstallComponentsPath(FirstRunWizardMode.MISSING_SDK, location, progressStep, false);

            progressStep.setInstallComponentsPath(path);

            addPath(path);
            addPath(new SingleStepPath(progressStep));
            super.init();
          }

          @Override
          public void performFinishingActions() {
            File sdkLocation = IdeSdks.getAndroidSdkPath();

            if (sdkLocation == null) {
              return;
            }

            String stateSdkLocationPath = myState.get(WizardConstants.KEY_SDK_INSTALL_LOCATION);
            assert stateSdkLocationPath != null;

            File stateSdkLocation = new File(stateSdkLocationPath);

            if (!FileUtil.filesEqual(sdkLocation, stateSdkLocation)) {
              setAndroidSdkLocation(stateSdkLocation);
              sdkLocation = stateSdkLocation;
            }

            setAndroidSdkLocationText(sdkLocation.getAbsolutePath());
          }

          private void setAndroidSdkLocationText(final String text) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                mySdkLocation.setText(text);
                refresh();
              }
            });
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
    myToolComponentsPanel.setConfigurable(myConfigurable);
    myPlatformComponentsPanel.setConfigurable(myConfigurable);
  }

  private static final class DownloadingComponentsStep extends ConsolidatedProgressStep {
    private InstallComponentsPath myInstallComponentsPath;

    private DownloadingComponentsStep(@NotNull Disposable disposable, @NotNull DynamicWizardHost host) {
      super(disposable, host);
    }

    private void setInstallComponentsPath(InstallComponentsPath installComponentsPath) {
      setPaths(Collections.singletonList(installComponentsPath));
      myInstallComponentsPath = installComponentsPath;
    }

    @Override
    public boolean isStepVisible() {
      return myInstallComponentsPath.shouldDownloadingComponentsStepBeShown();
    }
  }

  private static void setAndroidSdkLocation(final File sdkLocation) {
    ApplicationUtils.invokeWriteActionAndWait(ModalityState.any(), new Runnable() {
      @Override
      public void run() {
        // TODO Do we have to pass the default project here too instead of null?
        IdeSdks.setAndroidSdkPath(sdkLocation, null);
      }
    });
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
   * @param tt       The {@link TreeTableView} for which to set properties.
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
   * @param table    The {@link JTable} for which to set properties.
   * @param listener {@link ChangeListener} to be notified when a node's state is changed.
   */
  static void setTableProperties(@NotNull final JTable table, @Nullable final ChangeListener listener) {
    assert table instanceof SelectionProvider;
    ActionMap am = table.getActionMap();
    final CycleAction forwardAction = new CycleAction(false);
    final CycleAction backwardAction = new CycleAction(true);

    // With a screen reader, we need to let the user navigate through all the
    // cells so that they can be read, so don't override the prev/next cell actions.
    if (!ScreenReader.isActive()) {
      am.put("selectPreviousColumnCell", backwardAction);
      am.put("selectNextColumnCell", forwardAction);
    }

    table.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_ENTER || e.getKeyChar() == KeyEvent.VK_SPACE) {
          @SuppressWarnings("unchecked") Iterable<MultiStateRow> selection =
            (Iterable<MultiStateRow>)((SelectionProvider)table).getSelection();

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

  /**
   * Revalidates and refreshes our packages. Notifies platform and tools components of the start and end, so they can update their UIs.
   */
  public void refresh() {
    validate();

    myPlatformComponentsPanel.startLoading();
    myToolComponentsPanel.startLoading();

    // TODO: make progress runner handle invokes?
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    StudioProgressRunner progressRunner =
      new StudioProgressRunner(false, true, false, "Loading SDK", false, projects.length == 0 ? null : projects[0]);
    myConfigurable.getRepoManager()
      .load(0, ImmutableList.of(myLocalUpdater), ImmutableList.of(myRemoteUpdater), null,
            progressRunner, myDownloader, mySettings, false);
  }

  /**
   * Validates {@link #mySdkLocation} and shows appropriate errors in the UI if needed.
   */
  private void validate() {
    File sdkLocation = myConfigurable.getRepoManager().getLocalPath();
    String sdkLocationPath = sdkLocation == null ? null : sdkLocation.getAbsolutePath();

    ValidationResult result =
      WizardUtils.validateLocation(sdkLocationPath, "Android SDK location", false, WritableCheckMode.NOT_WRITABLE_IS_WARNING);

    switch (result.getStatus()) {
      case OK:
        mySdkLocationLabel.setForeground(JBColor.foreground());
        mySdkErrorLabel.setVisible(false);
        myPlatformComponentsPanel.setEnabled(true);
        myTabPane.setEnabled(true);

        break;
      case WARN:
        mySdkErrorLabel.setIcon(AllIcons.General.BalloonWarning);
        mySdkErrorLabel.setText(result.getFormattedMessage());
        mySdkErrorLabel.setVisible(true);

        myPlatformComponentsPanel.setEnabled(false);
        myTabPane.setEnabled(false);

        break;
      case ERROR:
        mySdkErrorLabel.setIcon(AllIcons.General.BalloonError);
        mySdkErrorLabel.setText(result.getFormattedMessage());
        mySdkErrorLabel.setVisible(true);

        myPlatformComponentsPanel.setEnabled(false);
        myTabPane.setEnabled(false);

        break;
    }
  }

  private void loadPackages(RepositoryPackages packages) {
    Multimap<AndroidVersion, UpdatablePackage> platformPackages = TreeMultimap.create();
    Set<UpdatablePackage> buildToolsPackages = Sets.newTreeSet();
    Set<UpdatablePackage> toolsPackages = Sets.newTreeSet();
    for (UpdatablePackage info : packages.getConsolidatedPkgs().values()) {
      RepoPackage p = info.getRepresentative();
      TypeDetails details = p.getTypeDetails();
      if (details instanceof DetailsTypes.ApiDetailsType) {
        platformPackages.put(DetailsTypes.getAndroidVersion((DetailsTypes.ApiDetailsType)details), info);
      }
      else if (p.getPath().startsWith(SdkConstants.FD_BUILD_TOOLS)) {
        buildToolsPackages.add(info);
      }
      else {
        toolsPackages.add(info);
      }
    }
    // TODO: when should we show this?
    //myChannelLink.setVisible(myHasPreview && !myIncludePreview);
    myPlatformComponentsPanel.setPackages(platformPackages);
    myToolComponentsPanel.setPackages(toolsPackages, buildToolsPackages);
  }

  /**
   * Gets the consolidated list of {@link NodeStateHolder}s from our children so they can be applied.
   *
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
    final boolean myBackward;

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
