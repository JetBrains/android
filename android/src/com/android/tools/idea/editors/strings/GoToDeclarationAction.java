/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.editors.strings;

import com.android.ide.common.resources.ResourceItem;
import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;

public class GoToDeclarationAction extends AbstractAction {

  private final StringResourceViewPanel myPanel;

  @Nullable private ResourceItem myItemAtMouseClickLocation;

  public GoToDeclarationAction(@NotNull StringResourceViewPanel panel) {
    super("Go to Declaration");
    myPanel = panel;
  }

  public void update(@NotNull JMenuItem goTo, @NotNull MouseEvent e) {
    StringResourceTable table = myPanel.getTable();
    int tableRow = table.rowAtPoint(e.getPoint());
    int tableColumn = table.columnAtPoint(e.getPoint());
    if (tableRow < 0 || tableColumn < 0) {
      goTo.setVisible(false);
      return;
    }
    int row = table.convertRowIndexToModel(tableRow);
    int column = table.convertColumnIndexToModel(tableColumn);
    StringResourceTableModel model = table.getModel();
    Locale locale = model.getLocale(column);
    StringResource resource = model.getStringResourceAt(row);

    myItemAtMouseClickLocation =
      locale == null ? resource.getDefaultValueAsResourceItem() : resource.getTranslationAsResourceItem(locale);

    goTo.setVisible(myItemAtMouseClickLocation != null);
  }

  @Override
  public void actionPerformed(@Nullable ActionEvent e) {
    Project project = myPanel.getFacet().getModule().getProject();
    assert myItemAtMouseClickLocation != null;
    XmlTag tag = LocalResourceRepository.getItemTag(project, myItemAtMouseClickLocation);
    if (tag == null) {
      // TODO strings can also be defined in gradle, find a way to go there too
      return;
    }
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, tag.getContainingFile().getVirtualFile(), tag.getTextOffset());
    FileEditorManager.getInstance(project).openEditor(descriptor, true);
  }
}
