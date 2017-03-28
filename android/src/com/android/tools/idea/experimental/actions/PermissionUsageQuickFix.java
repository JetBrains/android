/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.actions;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PermissionUsageQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public PermissionUsageQuickFix(@Nullable PsiElement element) {
    super(element);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable/*is null when called from inspection*/ Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {


    //The Editor can be null at any time.
    //Open the editor manually
    openFile(project, file);
    OpenFileDescriptor fileDesc = new OpenFileDescriptor(project,
                                                         file.getVirtualFile(),
                                                         startElement.getTextOffset());
    editor = FileEditorManager.getInstance(project).openTextEditor(fileDesc, true);
    assert editor != null;

    int startOffset = startElement.getTextOffset();
    int endOffset = endElement.getTextOffset() + endElement.getTextLength();
    editor.getSelectionModel().setSelection(startOffset, endOffset);

    PermissionUsageQuickFixRunnable job = new PermissionUsageQuickFixRunnable(project, editor);
    execRunnable(job, "Permission Fix");
  }

  private void execRunnable(@NotNull Runnable job, String title) {
    ApplicationManager.getApplication().runWriteAction(job);
  }

  public static void openFile(Project project, PsiFile file) {
    String path = file.getVirtualFile().getCanonicalPath();
    assert path != null;
    FileEditorManager manager = FileEditorManager.getInstance(project);
    VirtualFile virtFile = LocalFileSystem.getInstance().findFileByPath(path);
    assert virtFile != null;
    manager.openFile(virtFile, true, true);

  }

  @NotNull
  @Override
  public String getText() {
    return "Insert guards for permission usage";
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Insert permission usage getFamilyName()";
  }
}
