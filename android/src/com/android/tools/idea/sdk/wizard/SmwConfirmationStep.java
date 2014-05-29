/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.sdk.wizard;

import com.android.tools.idea.sdk.SdkState;
import com.android.tools.idea.wizard.TemplateWizardStep;
import com.intellij.ide.wizard.CommitStepException;
import com.intellij.openapi.Disposable;
import com.intellij.ui.GuiUtils;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.table.JBTable;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

public class SmwConfirmationStep extends TemplateWizardStep implements Disposable {
  private final SmwState myWizardState;
  private JPanel myContentPanel;
  private JLabel myTextDescription; // TODO: display details based on selection
  private JBLabel myLabelSdkPath;
  private JBTable myTable;
  private JLabel myErrorLabel;

  private SmwConfirmationTableModel myTableModel;
  private boolean myInitOnce = true;
  private boolean myInInit;

  public SmwConfirmationStep(@NotNull SmwState wizardState, @Nullable UpdateListener updateListener) {
    super(wizardState, null /*project*/, null /*module*/, null /*sidePanelIcon*/, updateListener);
    myWizardState = wizardState;
  }

  @Override
  public void dispose() {
  }

  @Override
  public JComponent getComponent() {
    return myContentPanel;
  }

  private void createUIComponents() {
    myTable = new SmwConfirmationTable();
  }

  @Override
  public void _init() {
    if (myInInit) {
      return;
    }
    myInInit = true;
    try {
      if (myInitOnce) {
        myInitOnce = false;

        GuiUtils.replaceJSplitPaneWithIDEASplitter(myContentPanel);
        super._init();

        SdkState sdkState = myWizardState.getSdkState();
        if (sdkState != null) {
          AndroidSdkData sdkData = sdkState.getSdkData();
          //noinspection ConstantConditions
          myLabelSdkPath.setText(sdkData.getLocalSdk().getLocation().getPath());
        }

        SmwConfirmationTableModel.LabelColumnInfo pkgColumn = new SmwConfirmationTableModel.LabelColumnInfo("Package");
        SmwConfirmationTableModel.InstallColumnInfo selColumn = new SmwConfirmationTableModel.InstallColumnInfo("Accept");
        myTableModel = new SmwConfirmationTableModel(pkgColumn, selColumn);
        myTable.setModel(myTableModel);

        myTableModel.addTableModelListener(new TableModelListener() {
          @Override
          public void tableChanged(TableModelEvent e) {
            SmwConfirmationStep.this.fireStateChanged();
          }
        });
      }

      myTableModel.fillModel(myWizardState.getSelectedActions());

      ListSelectionModel lsm = myTable.getSelectionModel();
      lsm.addListSelectionListener(new ListSelectionListener() {
        @Override
        public void valueChanged(ListSelectionEvent e) {
          onTableSelection(e);
        }
      });

      onTableSelection(new ListSelectionEvent(lsm, lsm.getMinSelectionIndex(), lsm.getMaxSelectionIndex(), lsm.getValueIsAdjusting()));

    } finally {
      myInInit = false;
    }
  }

  @NotNull
  @Override
  protected JLabel getDescription() {
    // We're not using the Description field of the Template Wizard Step here.
    // Since nullable isn't supported, share it with the error label (which is
    // fine since, again, template wizard description field isn't useful here.)
    return myErrorLabel;
  }

  @NotNull
  @Override
  protected JLabel getError() {
    return myErrorLabel;
  }

  @Override
  public void _commit(boolean finishChosen) throws CommitStepException {
    super._commit(finishChosen);
  }

  private void onTableSelection(ListSelectionEvent e) {
    Object src = e.getSource();
    if (!(src instanceof ListSelectionModel) || e.getValueIsAdjusting()) {
      return;
    }

    ListSelectionModel lsm = (ListSelectionModel)src;

    StringBuilder sb = new StringBuilder("<html>");

    SmwConfirmationTableModel.LineInfo item = lsm.isSelectionEmpty() ? null : myTableModel.getObjectAt(lsm.getMinSelectionIndex());
    if (item == null) {
      sb.append("Please select a package to see its details.");

    } else switch (item.getType()) {
      case REMOVE:
        sb.append(item.getRemovedPkg().getLongDescription());
        break;
      case UPDATE:
        sb.append(item.getUpdatePkg().getLongDescription());
        break;
      case INSTALL:
        sb.append(item.getInstallNewPkg().getLongDescription());
        break;
      default:
        sb.append("Please select a package to see its details.");
    }

    sb.append("</html>");
    myTextDescription.setText(sb.toString().replace("\n", "<br/>\n"));
  }
}
