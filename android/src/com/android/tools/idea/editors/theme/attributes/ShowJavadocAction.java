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
import com.android.tools.idea.editors.theme.ThemeEditorUtils;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.intellij.codeInsight.documentation.DocumentationComponent;
import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Action to display a Javadoc popup for attributes in the theme editor.
 */
public class ShowJavadocAction extends AbstractAction {
  private static final Point ORIGIN = new Point(0, 0);

  protected final JTable myAttributesTable;
  private final ThemeEditorContext myContext;
  private EditedStyleItem myCurrentItem;

  public ShowJavadocAction(@NotNull JTable attributesTable, @NotNull ThemeEditorContext context) {
    super("Show documentation");
    myAttributesTable = attributesTable;
    myContext = context;
  }

  public void setCurrentItem(EditedStyleItem currentItem) {
    myCurrentItem = currentItem;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    EditedStyleItem item = myCurrentItem;
    if (item == null) {
      return;
    }

    Project project = myContext.getProject();
    DocumentationManager documentationManager = DocumentationManager.getInstance(project);
    final DocumentationComponent docComponent = new DocumentationComponent(documentationManager);
    String tooltip = ThemeEditorUtils.generateToolTipText(item.getSelectedValue(), myContext.getCurrentContextModule(), myContext.getConfiguration());
    // images will not work unless we pass a valid PsiElement {@link DocumentationComponent#myImageProvider}
    docComponent.setText(tooltip, new FakePsiElement() {
      @Override
      public boolean isValid() {
        // this needs to return true for the DocumentationComponent to accept this PsiElement {@link DocumentationComponent#setData(PsiElement, String, boolean, String, String)}
        return true;
      }

      @NotNull
      @Override
      public Project getProject() {
        // superclass implementation throws an exception
        return myContext.getProject();
      }

      @Override
      public PsiElement getParent() {
        return null;
      }

      @Override
      public PsiFile getContainingFile() {
        // superclass implementation throws an exception
        return null;
      }
    }, true);

    JBPopup hint = JBPopupFactory.getInstance().createComponentPopupBuilder(docComponent, docComponent).setProject(project)
      .setDimensionServiceKey(project, DocumentationManager.JAVADOC_LOCATION_AND_SIZE, false).setResizable(true).setMovable(true)
      .setRequestFocus(true).setTitle(item.getAttrName()).setCancelCallback(new Computable<Boolean>() {
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
