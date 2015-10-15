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

import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSources;
import com.android.tools.idea.sdk.remote.internal.updater.SettingsController;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.AnActionButtonUpdater;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Panel that shows the current {@link SdkSources}.
 */
public class UpdateSitesPanel {
  private JPanel myRootPanel;
  private TableView myUpdateSitesTable;
  private JPanel mySourcesPanel;
  private JPanel mySourcesLoadingPanel;
  private AsyncProcessIcon mySourcesLoadingIcon;
  private JCheckBox myForceHttp;
  private SourcesTableModel mySourcesTableModel;
  private static SettingsController ourSettingsController = SettingsController.getInstance();

  private void createUIComponents() {
    mySourcesLoadingIcon = new AsyncProcessIcon("Loading...");
    mySourcesTableModel = new SourcesTableModel();
    myUpdateSitesTable = new TableView<SourcesTableModel.Row>(mySourcesTableModel);
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
      public boolean isEnabled(AnActionEvent e) {
        return myUpdateSitesTable.getSelectedRowCount() == 1 && mySourcesTableModel.isEditable(myUpdateSitesTable.getSelectedRow());
      }
    }).addExtraAction(new AnActionButton("Select All", AllIcons.Actions.Selectall) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        for (int i = 0; i < myUpdateSitesTable.getRowCount(); i++) {
          mySourcesTableModel.setSourceEnabled(i, true);
        }
      }

      @Override
      public boolean isEnabled() {
        return myUpdateSitesTable.getRowCount() > 0;
      }
    }).addExtraAction(new AnActionButton("Deselect All", AllIcons.Actions.Unselectall) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        for (int i = 0; i < myUpdateSitesTable.getRowCount(); i++) {
          mySourcesTableModel.setSourceEnabled(i, false);
        }
      }

      @Override
      public boolean isEnabled() {
        return myUpdateSitesTable.getRowCount() > 0;
      }
    }).setMoveDownAction(null).setMoveUpAction(null).setRemoveActionUpdater(new AnActionButtonUpdater() {
      @Override
      public boolean isEnabled(AnActionEvent e) {
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
    return mySourcesTableModel.isSourcesModified() || ourSettingsController.getForceHttp() != myForceHttp.isSelected();
  }

  public void reset() {
    mySourcesTableModel.reset();
    myForceHttp.setSelected(ourSettingsController.getForceHttp());
  }

  public void setSdkState(SdkState state) {
    mySourcesTableModel.setSdkState(state);
  }

  public void save() {
    mySourcesTableModel.save();
    ourSettingsController.setForceHttp(myForceHttp.isSelected());
  }

  public void startLoading() {
    mySourcesLoadingPanel.setVisible(true);
  }

  public void finishLoading() {
    mySourcesTableModel.refreshSources();
    mySourcesLoadingPanel.setVisible(false);
  }
}
