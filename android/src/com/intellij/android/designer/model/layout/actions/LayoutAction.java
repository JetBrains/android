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
package com.intellij.android.designer.model.layout.actions;

import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class LayoutAction extends AnAction {
  protected final DesignerEditorPanel myDesigner;

  protected LayoutAction(@NotNull DesignerEditorPanel designer, @NotNull String description, @Nullable String label, @Nullable Icon icon) {
    myDesigner = designer;
    Presentation presentation = getTemplatePresentation();
    presentation.setDescription(description);
    if (label != null) {
      presentation.setText(label);
    }
    if (icon != null) {
      presentation.setIcon(icon);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            performWriteAction();
          }
        });
      }
    }, getTemplatePresentation().getDescription(), true);
  }

  protected abstract void performWriteAction();
}
