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
package com.android.tools.idea.editors.theme.attributes;

import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Action to display a Javadoc popup for attributes in the theme editor.
 */
public class ShowJavadocAction extends AnAction {
  private static final Point ORIGIN = new Point(0, 0);

  protected final JTable myAttributesTable;
  private final ThemeEditorContext myContext;

  public ShowJavadocAction(@NotNull JTable attributesTable, @NotNull ThemeEditorContext context) {
    myAttributesTable = attributesTable;
    myContext = context;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    int selectedRow = myAttributesTable.getSelectedRow();
    int selectedColumn = myAttributesTable.getSelectedColumn();
    Object selectedItem = myAttributesTable.getValueAt(selectedRow, selectedColumn);

    if (selectedItem == null || !(selectedItem instanceof EditedStyleItem)) {
      // We can not display javadoc for this item.
      return;
    }

    EditedStyleItem item = (EditedStyleItem)selectedItem;

    Project project = e.getProject();
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    final DocumentationComponent docComponent = new DocumentationComponent(documentationManager);
    String tooltip = ThemeEditorUtils.generateToolTipText(item.getSelectedValue(), myContext.getCurrentThemeModule(), myContext.getConfiguration());
    docComponent.setText(tooltip, e.getData(CommonDataKeys.PSI_FILE), true);

    JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(docComponent, docComponent).setProject(project)
      .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false).setResizable(true).setMovable(true)
      .setRequestFocus(true).setTitle(item.getName()).setCancelCallback(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          Disposer.dispose(docComponent);
          return Boolean.TRUE;
        }
      }).createPopup();
    docComponent.setHint(hint);
    Disposer.register(hint, docComponent);
    hint.show(new RelativePoint(myAttributesTable.getParent(), ORIGIN));
  }
}
