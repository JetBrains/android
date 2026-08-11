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

import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceItem;
import com.android.tools.idea.editors.strings.table.FrozenColumnTableEvent;
import com.android.tools.idea.editors.strings.table.StringResourceTableModel;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.event.ActionEvent;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GoToDeclarationAction extends AbstractAction {

  private final Project myProject;

  @Nullable private ResourceItem myItemAtMouseClickLocation;

  public GoToDeclarationAction(@NotNull Project project) {
    super("Go to Declaration");
    myProject = project;
  }

  public void update(@NotNull JMenuItem goTo, @NotNull FrozenColumnTableEvent event) {
    int row = event.getModelRowIndex();
    int column = event.getModelColumnIndex();
    StringResourceTableModel model = (StringResourceTableModel)event.getSource().getModel();
    Locale locale = model.getLocale(column);
    StringResource resource = model.getStringResourceAt(row);

    myItemAtMouseClickLocation =
      locale == null ? resource.getDefaultValueAsResourceItem() : resource.getTranslationAsResourceItem(locale);

    goTo.setVisible(myItemAtMouseClickLocation != null);
  }

  @Override
  public void actionPerformed(@Nullable ActionEvent e) {
    if (myItemAtMouseClickLocation == null) {
      return;
    }
    ResourceItem item = myItemAtMouseClickLocation;
    ReadAction.nonBlocking(() -> {
      XmlTag tag = IdeResourcesUtil.getItemTag(myProject, item);
      if (tag == null) {
        return null;
      }
      PsiFile file = tag.getContainingFile();
      if (file == null) {
        return null;
      }
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) {
        return null;
      }
      return new OpenFileDescriptor(myProject, virtualFile, tag.getTextOffset());
    })
    .expireWith(myProject)
    .finishOnUiThread(ModalityState.defaultModalityState(), descriptor -> {
      if (descriptor != null) {
        FileEditorManager.getInstance(myProject).openEditor(descriptor, true);
      }
    })
    .submit(AppExecutorUtil.getAppExecutorService());
  }
}
