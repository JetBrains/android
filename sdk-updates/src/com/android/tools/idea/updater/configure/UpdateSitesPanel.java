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

import com.android.repository.api.RepositorySource;
import com.android.repository.api.SettingsController;
import com.android.tools.idea.sdk.StudioSettingsController;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.TableView;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.util.ui.AsyncProcessIcon;
import java.awt.Dimension;
import java.awt.Insets;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import org.jetbrains.annotations.Nullable;

/**
 * Panel that shows the current {@link RepositorySource}s.
 */
public class UpdateSitesPanel {
  private JPanel myRootPanel;
  private TableView myUpdateSitesTable;
  @SuppressWarnings("unused") private JPanel mySourcesPanel;
  private JPanel mySourcesLoadingPanel;
  @SuppressWarnings("unused") private AsyncProcessIcon mySourcesLoadingIcon;
  private JCheckBox myForceHttp;
  private SourcesTableModel mySourcesTableModel;
  private static SettingsController ourSettingsController = StudioSettingsController.getInstance();

  public UpdateSitesPanel(@NotNull Runnable refreshCallback) {
    setupUI();
    init(refreshCallback);
  }

  // IJ tries to be smart and generates weird code if this isn't a separate method and is instead in the constructor...
  private void init(@NotNull Runnable refreshCallback) {
    mySourcesTableModel.setRefreshCallback(refreshCallback);
  }

  private void createUIComponents() {
    mySourcesLoadingIcon = new AsyncProcessIcon("Loading...");
    Runnable finishLoadingCallback = () -> {
      mySourcesLoadingPanel.setVisible(false);

      DataManager dataManager = DataManager.getInstance();
      DataContext dataContext = dataManager.getDataContext(myRootPanel);
      @Nullable Settings settings = Settings.KEY.getData(dataContext);
      if (settings != null) {
        // Since loading happens asynchronously, the states of the apply/reset buttons won't be updated unless we force a revalidation.
        settings.revalidate();
      }
    };

    mySourcesTableModel =
      new SourcesTableModel(() -> mySourcesLoadingPanel.setVisible(true), finishLoadingCallback, ModalityState.current());
    myUpdateSitesTable = new TableView<>(mySourcesTableModel);
    ToolbarDecorator userDefinedDecorator = ToolbarDecorator.createDecorator(myUpdateSitesTable);
    mySourcesPanel = addExtraActions(userDefinedDecorator).createPanel();
    SdkUpdaterConfigPanel.setTableProperties(myUpdateSitesTable, null);
  }

  private ToolbarDecorator addExtraActions(final ToolbarDecorator decorator) {
    return decorator.setEditAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton anActionButton) {
        mySourcesTableModel.editRow(myUpdateSitesTable.getSelectedRow());
      }
    }).setEditActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
        return myUpdateSitesTable.getSelectedRowCount() == 1 && mySourcesTableModel.isEditable(myUpdateSitesTable.getSelectedRow());
      }
    }).setAddActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
        return mySourcesTableModel.isEditable();
      }
    }).addExtraAction(new DumbAwareAction("Select All", null, AllIcons.Actions.Selectall) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(mySourcesTableModel.hasEditableRows());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        mySourcesTableModel.setAllEnabled(true);
      }
    }).addExtraAction(new DumbAwareAction("Deselect All", null, AllIcons.Actions.Unselectall) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
      }

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(mySourcesTableModel.hasEditableRows());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        mySourcesTableModel.setAllEnabled(false);
      }
    }).setMoveDownAction(null).setMoveUpAction(null).setRemoveActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(@NotNull AnActionEvent e) {
        if (myUpdateSitesTable.getSelectedRowCount() < 1) {
          return false;
        }
        for (int i : myUpdateSitesTable.getSelectedRows()) {
          if (!mySourcesTableModel.isEditable(i)) {
            return false;
          }
        }
        return true;
      }
    });
  }

  public boolean isModified() {
    return mySourcesTableModel.isSourcesModified()
           || ourSettingsController.getForceHttp() != myForceHttp.isSelected();
  }

  public void reset() {
    mySourcesTableModel.reset();
    myForceHttp.setSelected(ourSettingsController.getForceHttp());
  }

  public void setConfigurable(@NotNull SdkUpdaterConfigurable configurable) {
    mySourcesTableModel.setConfigurable(configurable);
  }

  public void save() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        mySourcesTableModel.save(ProgressManager.getInstance().getProgressIndicator());
        ourSettingsController.setForceHttp(myForceHttp.isSelected());
      }
    }, "Saving Sources", false, null, myRootPanel);
  }

  private void setupUI() {
    createUIComponents();
    myRootPanel = new JPanel();
    myRootPanel.setLayout(new GridLayoutManager(5, 3, new Insets(0, 0, 0, 0), -1, -1));
    final JBLabel jBLabel1 = new JBLabel();
    jBLabel1.setText("These sites are consulted for installing and updating SDK platforms and tools.");
    myRootPanel.add(jBLabel1, new GridConstraints(0, 0, 1, 3, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null,
                                                  new Dimension(600, -1), new Dimension(600, -1), 0, false));
    myRootPanel.add(mySourcesPanel, new GridConstraints(1, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                        null, null, 0, false));
    mySourcesLoadingPanel = new JPanel();
    mySourcesLoadingPanel.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
    myRootPanel.add(mySourcesLoadingPanel, new GridConstraints(2, 0, 1, 3, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                               null, null, null, 0, false));
    final JBLabel jBLabel2 = new JBLabel();
    jBLabel2.setText("Loading sources...");
    mySourcesLoadingPanel.add(jBLabel2, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                            null, 0, false));
    mySourcesLoadingPanel.add(mySourcesLoadingIcon, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW,
                                                                        GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                        GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
    myForceHttp = new JCheckBox();
    myForceHttp.setText("Force https://... sources to be fetched using http://...");
    myRootPanel.add(myForceHttp, new GridConstraints(4, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                     GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
    final JBLabel jBLabel3 = new JBLabel();
    myRootPanel.add(jBLabel3, new GridConstraints(3, 1, 1, 2, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE,
                                                  GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0,
                                                  false));
    final Spacer spacer1 = new Spacer();
    myRootPanel.add(spacer1, new GridConstraints(4, 2, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL,
                                                 GridConstraints.SIZEPOLICY_WANT_GROW, 1, null, null, null, 0, false));
  }

  public JComponent getRootComponent() { return myRootPanel; }
}
