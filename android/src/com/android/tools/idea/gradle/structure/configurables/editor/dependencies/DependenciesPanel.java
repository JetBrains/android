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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.android.tools.idea.gradle.structure.configurables.ModuleConfigurationState;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION;

class DependenciesPanel extends JPanel {
  @NotNull private final ModuleConfigurationState myState;
  @NotNull private final JBTable myDependencyTable;

  private List<DependencyAction> myPopupActions;

  DependenciesPanel(@NotNull ModuleConfigurationState state) {
    super(new BorderLayout());
    myState = state;
    myDependencyTable = new JBTable();
    setUpTableView();
  }

  private void setUpTableView() {
    myDependencyTable.setDragEnabled(false);
    myDependencyTable.setIntercellSpacing(new Dimension(0, 0));
    myDependencyTable.setShowGrid(false);

    myDependencyTable.getSelectionModel().setSelectionMode(MULTIPLE_INTERVAL_SELECTION);

    ToolbarDecorator decorator = ToolbarDecorator.createDecorator(myDependencyTable);
    decorator.setAddAction(new AnActionButtonRunnable() {
      @Override
      public void run(AnActionButton button) {
        initPopupActions();
        ListPopup popup = JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<DependencyAction>(null, myPopupActions) {
          @Override
          @NotNull
          public Icon getIconFor(DependencyAction value) {
            return value.icon;
          }

          @Override
          public boolean isMnemonicsNavigationEnabled() {
            return true;
          }

          @Override
          public PopupStep onChosen(final DependencyAction selectedValue, boolean finalChoice) {
            return doFinalStep(new Runnable() {
              @Override
              public void run() {
                selectedValue.execute();
              }
            });
          }

          @Override
          @NotNull
          public String getTextFor(DependencyAction value) {
            return "&" + value.index + "  " + value.text;
          }
        });

        RelativePoint point = button.getPreferredPopupPoint();
        assert point != null;
        popup.show(point);
      }
    });


    JPanel panel = decorator.createPanel();
    panel.setBorder(BorderFactory.createEmptyBorder());
    add(panel, BorderLayout.CENTER);
  }

  private void initPopupActions() {
    if (myPopupActions == null) {
      int index = 1;
      List<DependencyAction> actions = Lists.newArrayList();
      actions.add(new AddExternalDependencyAction(this, index++));
      actions.add(new AddFileDependencyAction(this, index++));
      actions.add(new AddModuleDependencyAction(this, index));
      myPopupActions = actions;
    }
  }

  void runAction(@NotNull  DependencyAction action) {

  }
}

