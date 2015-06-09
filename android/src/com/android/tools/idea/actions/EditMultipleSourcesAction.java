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
package com.android.tools.idea.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.util.gotoByName.GotoFileCellRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class EditMultipleSourcesAction extends AnAction {
  public EditMultipleSourcesAction() {
    Presentation presentation = getTemplatePresentation();
    presentation.setText(ActionsBundle.actionText("EditSource"));
    presentation.setIcon(AllIcons.Actions.EditSource);
    presentation.setDescription(ActionsBundle.actionDescription("EditSource"));
    // TODO shortcuts
    // setShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getShortcutSet());
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    Navigatable[] navigatables = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
    e.getPresentation().setEnabled(navigatables != null && navigatables.length > 0);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;

    final Navigatable[] files = e.getData(CommonDataKeys.NAVIGATABLE_ARRAY);
    assert files != null && files.length > 0;

    if (files.length > 1) {
      DefaultListModel listModel = new DefaultListModel();
      for (int i = 0; i < files.length; ++i) {
        assert files[i] instanceof PsiFileAndLineNavigation;
        listModel.add(i, ((PsiFileAndLineNavigation)files[i]).getPsiFile());
      }
      final JBList list = new JBList(listModel);
      int width = WindowManager.getInstance().getFrame(project).getSize().width;
      list.setCellRenderer(new GotoFileCellRenderer(width));

      JBPopup popup =
        JBPopupFactory.getInstance().createListPopupBuilder(list).setTitle("Choose Target File").setItemChoosenCallback(new Runnable() {
          @Override
          public void run() {
            Object selectedValue = list.getSelectedValue();
            PsiFileAndLineNavigation navigationWrapper = null;
            for (int i = 0; i < files.length; ++i) {
              if (selectedValue == ((PsiFileAndLineNavigation)files[i]).getPsiFile()) {
                navigationWrapper = (PsiFileAndLineNavigation)files[i];
                break;
              }
            }
            assert navigationWrapper != null;
            if (navigationWrapper.canNavigate()) {
              navigationWrapper.navigate(true);
            }
          }
        }).createPopup();

      InputEvent event = e.getInputEvent();
      if (event instanceof MouseEvent) {
        Point location = ((MouseEvent)event).getPoint();
        Component owner = e.getInputEvent().getComponent();
        SwingUtilities.convertPointToScreen(location, owner);
        popup.showInScreenCoordinates(owner, location);
      }
      else {
        popup.showInFocusCenter();
      }
    }
    else {
      assert files[0] instanceof PsiFileAndLineNavigation;
      PsiFileAndLineNavigation file = (PsiFileAndLineNavigation)files[0];
      if (file.canNavigate()) {
        file.navigate(true);
      }
    }
  }
}
