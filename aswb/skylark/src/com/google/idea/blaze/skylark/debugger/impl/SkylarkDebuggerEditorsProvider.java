/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.skylark.debugger.impl;

import com.google.idea.blaze.base.lang.buildfile.language.BuildFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import javax.annotation.Nullable;

/** Provides the editor environment used for debugger evaluation. */
class SkylarkDebuggerEditorsProvider extends XDebuggerEditorsProvider {

  @Override
  public Document createDocument(
      Project project,
      String expression,
      @Nullable XSourcePosition sourcePosition,
      EvaluationMode mode) {
    PsiElement context = null;
    if (sourcePosition != null) {
      context = getContextElement(sourcePosition.getFile(), sourcePosition.getOffset(), project);
    }
    PsiFile codeFragment =
        createExpressionCodeFragment(project, expression, sourcePosition, context);
    Document document = PsiDocumentManager.getInstance(project).getDocument(codeFragment);
    assert document != null;
    return document;
  }

  private PsiFile createExpressionCodeFragment(
      Project project,
      String text,
      @Nullable XSourcePosition sourcePosition,
      @Nullable PsiElement context) {
    text = text.trim();
    SkylarkExpressionCodeFragment fragment =
        new SkylarkExpressionCodeFragment(
            project, codeFragmentFileName(context), text, /* isPhysical= */ true);
    // inject the debug frame context into the file
    if (sourcePosition instanceof SkylarkSourcePosition) {
      fragment.setDebugEvaluationContext((SkylarkSourcePosition) sourcePosition);
    }
    return fragment;
  }

  @Override
  public FileType getFileType() {
    return BuildFileType.INSTANCE;
  }

  @Nullable
  private static PsiElement getContextElement(
      VirtualFile virtualFile, int offset, Project project) {
    return XDebuggerUtil.getInstance().findContextElement(virtualFile, offset, project, false);
  }

  private static String codeFragmentFileName(@Nullable PsiElement context) {
    if (context == null) {
      return "fragment.bzl";
    }
    PsiFile contextFile = context.getContainingFile();
    return contextFile != null ? contextFile.getName() : "fragment.bzl";
  }
}
