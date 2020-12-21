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
package org.jetbrains.android.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlFile;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class MoveFileQuickFix implements LocalQuickFix {
  @NotNull
  private final String myFolderName;

  @NotNull
  private final SmartPsiElementPointer<XmlFile> myFile;

  public MoveFileQuickFix(@NotNull String folderName, @NotNull XmlFile file) {
    myFolderName = folderName;
    myFile = SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(file);
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return String.format("Move file to \"%s\"", myFolderName);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Move file";
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final XmlFile xmlFile = myFile.getElement();
    if (xmlFile == null) {
      return;
    }

    // Following assertions should be satisfied by xmlFile being passed to the constructor
    final PsiDirectory directory = xmlFile.getContainingDirectory();
    assert directory != null;

    final PsiDirectory parent = directory.getParent();
    assert parent != null;

    PsiDirectory existingDirectory = parent.findSubdirectory(myFolderName);
    final PsiDirectory resultDirectory = existingDirectory == null ? parent.createSubdirectory(myFolderName) : existingDirectory;

    final boolean removeFirst;
    if (resultDirectory.findFile(xmlFile.getName()) != null) {
      final String message =
        String.format("File %s already exists in directory %s. Overwrite it?", xmlFile.getName(), resultDirectory.getName());
      removeFirst = Messages.showOkCancelDialog(project, message, "Move Resource File", Messages.getWarningIcon()) == Messages.OK;

      if (!removeFirst) {
        // User pressed "Cancel", do nothing
        return;
      }
    }
    else {
      removeFirst = false;
    }

    WriteCommandAction.writeCommandAction(project, xmlFile).run(() -> {
      if (removeFirst) {
        final PsiFile file = resultDirectory.findFile(xmlFile.getName());
        if (file != null) {
          file.delete();
        }
      }
      MoveFilesOrDirectoriesUtil.doMoveFile(xmlFile, resultDirectory);
    });
  }
}
