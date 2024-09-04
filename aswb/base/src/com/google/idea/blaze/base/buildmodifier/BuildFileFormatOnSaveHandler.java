/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.buildmodifier;

import static com.google.idea.blaze.base.buildmodifier.BuildFileFormatter.getReplacements;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.formatter.FileBasedFormattingSynchronizer;
import com.google.idea.blaze.base.formatter.FileBasedFormattingSynchronizer.Formatter;
import com.google.idea.blaze.base.formatter.FormatUtils;
import com.google.idea.blaze.base.formatter.FormatUtils.FileContentsProvider;
import com.google.idea.blaze.base.formatter.FormatUtils.Replacements;
import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile.BlazeFileType;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/** Runs the buildifier command on file save. */
public class BuildFileFormatOnSaveHandler extends FileDocumentManagerAdapter {

  @Override
  public void beforeDocumentSaving(final Document document) {
    if (!BlazeUserSettings.getInstance().getFormatBuildFilesOnSave() || !document.isWritable()) {
      return;
    }
    VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(document);
    if (virtualFile == null || !virtualFile.getFileType().equals(BuildFileType.INSTANCE)) {
      // Do not guess projects for unrelated files as it might write error messages to log files.
      return;
    }
    for (Project project : ProjectLocator.getInstance().getProjectsForFile(virtualFile)) {
      PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
      if (psiFile != null) {
        formatBuildFile(project, psiFile);
        return;
      }
    }
  }

  private static void formatBuildFile(Project project, PsiFile psiFile) {
    if (!(psiFile instanceof BuildFile)
        || !isProjectValid(project)
        || !canWriteToFile(project, psiFile)) {
      return;
    }
    // DO not use formatter here, instead we format the entire file.
    BlazeFileType type = ((BuildFile) psiFile).getBlazeFileType();
    ListenableFuture<Void> future =
        FileBasedFormattingSynchronizer.applyReplacements(
            psiFile,
            f -> {
              FileContentsProvider fileContents = FileContentsProvider.fromPsiFile(f);
              if (fileContents == null) {
                return new Formatter.Result<>(null, new Replacements());
              }
              ImmutableList<TextRange> toFormat =
                  ImmutableList.of(TextRange.allOf(fileContents.getInitialFileContents()));
              Replacements replacements = getReplacements(type, fileContents, toFormat);
              return new Formatter.Result<>(null, replacements);
            });
    FormatUtils.formatWithProgressDialog(project, "Running buildifier", future);
  }

  private static boolean canWriteToFile(Project project, PsiFile psiFile) {
    VirtualFile vf = psiFile.getVirtualFile();
    return psiFile.isValid()
        && psiFile.isWritable()
        && psiFile.getModificationStamp() != 0
        && vf != null
        && NonProjectFileWritingAccessProvider.isWriteAccessAllowed(vf, project);
  }

  private static boolean isProjectValid(Project project) {
    return project.isInitialized() && !project.isDisposed();
  }
}
