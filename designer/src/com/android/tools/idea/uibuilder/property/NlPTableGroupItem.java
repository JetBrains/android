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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.adtui.ptable.PTableGroupItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;

public abstract class NlPTableGroupItem extends PTableGroupItem {

  @Override
  public void deleteGroupValues(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) {
      return;
    }
    PsiFile containingFile = PsiManager.getInstance(project).findFile(file);
    if (containingFile == null) {
      return;
    }
    new WriteCommandAction.Simple(project, "Delete " + this.getName(), containingFile) {
      @Override
      protected void run() throws Throwable {
        getChildren().forEach(item -> item.setValue(null));
      }
    }.execute();
  }
}
