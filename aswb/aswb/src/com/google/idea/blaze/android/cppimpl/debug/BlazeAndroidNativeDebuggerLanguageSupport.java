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
package com.google.idea.blaze.android.cppimpl.debug;

import com.google.idea.blaze.android.run.BlazeAndroidRunConfigurationHandler;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.jetbrains.cidr.execution.debugger.OCDebuggerLanguageSupport;
import com.jetbrains.cidr.execution.debugger.OCDebuggerTypesHelper;
import com.jetbrains.cidr.lang.OCFileType;
import com.jetbrains.cidr.lang.OCLanguage;
import com.jetbrains.cidr.lang.util.OCElementFactory;
import javax.annotation.Nullable;

class BlazeAndroidNativeDebuggerLanguageSupport extends OCDebuggerLanguageSupport {
  @Override
  public XDebuggerEditorsProvider createEditor(RunProfile profile) {
    if (profile == null) {
      return new DebuggerEditorsProvider();
    }
    BlazeAndroidRunConfigurationHandler handler =
        BlazeAndroidRunConfigurationHandler.getHandlerFrom(profile);
    if (handler != null && handler.getCommonState().isNativeDebuggingEnabled()) {
      return new DebuggerEditorsProvider();
    }
    return null;
  }

  private static class DebuggerEditorsProvider extends XDebuggerEditorsProvider {
    @Override
    public FileType getFileType() {
      return OCFileType.INSTANCE;
    }

    @Override
    public Document createDocument(
        final Project project,
        final String text,
        @Nullable XSourcePosition sourcePosition,
        final EvaluationMode mode) {
      final PsiElement context = OCDebuggerTypesHelper.getContextElement(sourcePosition, project);
      if (context != null && context.getLanguage() == OCLanguage.getInstance()) {
        return WriteAction.compute(
            () -> {
              PsiFile fragment =
                  mode == EvaluationMode.EXPRESSION
                      ? OCElementFactory.expressionCodeFragment(text, project, context, true, false)
                      : OCElementFactory.expressionOrStatementsCodeFragment(
                          text, project, context, true, false);
              return PsiDocumentManager.getInstance(project).getDocument(fragment);
            });
      } else {
        final LightVirtualFile plainTextFile =
            new LightVirtualFile("oc-debug-editor-when-no-source-position-available.txt", text);
        //noinspection ConstantConditions
        return FileDocumentManager.getInstance().getDocument(plainTextFile);
      }
    }
  }
}
