/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.jetbrains.android.actions;

import com.intellij.ide.IdeView;
import com.intellij.ide.actions.ElementCreator;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Based on {@link com.intellij.ide.actions.CreateElementActionBase} but
 * customized to let us <b>not</b> show the target directory chooser. This
 * is because we'll be able to pick the target directory ourselves based
 * on inputs in the dialog (e.g. the resource type, the source set chosen, etc).
 *
 * Also, instead of passing a specific directory <b>to</b> the dialog, we pass just
 * the data context, such that the dialog can figure out the actual directory on
 * its own (based on dialog choices like which source provider to use.)
 */
public abstract class CreateResourceActionBase extends AnAction {

  protected CreateResourceActionBase() {
  }

  protected CreateResourceActionBase(String text, String description, Icon icon) {
    super(text, description, icon);
  }

  /**
   * @return created elements.
   */
  @NotNull
  protected abstract PsiElement[] invokeDialog(@NotNull Project project, @NotNull DataContext dataContext);

  /**
   * @return created elements.
   */
  @NotNull
  protected abstract PsiElement[] create(String newName, PsiDirectory directory) throws Exception;

  protected abstract String getErrorTitle();

  @SuppressWarnings("UnusedDeclaration") // For symmetry with CreateElementActionBase
  protected abstract String getCommandName();

  protected abstract String getActionName(PsiDirectory directory, String newName);

  @Override
  public final void actionPerformed(@NotNull final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null) {
      return;
    }

    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    // This is where we differ from CreateElementActionBase:
    // Rather than asking for a directory here, we just allow
    // *any* directory
    final PsiElement[] createdElements = invokeDialog(project, dataContext);

    for (PsiElement createdElement : createdElements) {
      view.selectElement(createdElement);
    }
  }

  @Override
  public void update(@NotNull final AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Presentation presentation = e.getPresentation();

    final boolean enabled = isAvailable(dataContext);

    presentation.setEnabledAndVisible(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isDumbAware() {
    return false;
  }

  protected boolean isAvailable(final DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return false;
    }

    if (DumbService.getInstance(project).isDumb() && !isDumbAware()) {
      return false;
    }

    final IdeView view = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (view == null || view.getDirectories().length == 0) {
      return false;
    }

    return true;
  }

  protected class MyInputValidator extends ElementCreator implements ElementCreatingValidator {
    private final PsiDirectory myDirectory;
    private PsiElement[] myCreatedElements = PsiElement.EMPTY_ARRAY;

    public MyInputValidator(final Project project, final PsiDirectory directory) {
      super(project, getErrorTitle());
      myDirectory = directory;
    }

    @Override
    public boolean checkInput(final String inputString) {
      return true;
    }

    @Override
    public PsiElement[] create(String newName) throws Exception {
      return CreateResourceActionBase.this.create(newName, myDirectory);
    }

    @Override
    public String getActionName(String newName) {
      return CreateResourceActionBase.this.getActionName(myDirectory, newName);
    }

    @Override
    public boolean canClose(final String inputString) {
      myCreatedElements = tryCreate(inputString);
      return myCreatedElements.length > 0;
    }

    @NotNull
    @Override
    public final PsiElement[] getCreatedElements() {
      return myCreatedElements;
    }
  }
}