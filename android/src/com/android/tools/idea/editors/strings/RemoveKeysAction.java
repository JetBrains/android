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

import com.android.tools.idea.editors.strings.table.StringResourceTable;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.res.LocalResourceRepository;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

final class RemoveKeysAction extends AnAction {
  private final StringResourceViewPanel myPanel;

  RemoveKeysAction(@NotNull StringResourceViewPanel panel) {
    super("Remove Keys", null, AllIcons.ToolbarDecorator.Remove);
    myPanel = panel;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabled(myPanel.getTable().getSelectedRowCount() != 0);
  }

  @Override
  public void actionPerformed(@Nullable AnActionEvent event) {
    StringResourceTable table = myPanel.getTable();
    StringResourceTableModel model = table.getModel();
    StringResourceRepository repository = model.getRepository();
    Project project = myPanel.getFacet().getModule().getProject();

    PsiElement[] elements = Arrays.stream(table.getSelectedRowModelIndices())
      .mapToObj(index -> model.getStringResourceAt(index).getKey())
      .flatMap(key -> repository.getItems(key).stream())
      .map(item -> LocalResourceRepository.getItemTag(project, item))
      .toArray(PsiElement[]::new);

    DelegateDeleteHandler.deletePsiElement(project, elements, myPanel.getFacet().getModule());
  }
}
