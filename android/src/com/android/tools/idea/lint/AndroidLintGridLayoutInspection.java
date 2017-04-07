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
package com.android.tools.idea.lint;

import com.android.tools.lint.checks.GridLayoutDetector;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.AUTO_URI;
import static com.android.tools.lint.detector.api.TextFormat.RAW;

public class AndroidLintGridLayoutInspection extends AndroidLintInspectionBase {
  public AndroidLintGridLayoutInspection() {
    super(AndroidBundle.message("android.lint.inspections.grid.layout"), GridLayoutDetector.ISSUE);
  }

  @NotNull
  @Override
  public AndroidLintQuickFix[] getQuickFixes(@NotNull final PsiElement startElement,
                                             @NotNull PsiElement endElement,
                                             @NotNull String message) {
    String obsolete = GridLayoutDetector.getOldValue(message, RAW);
    String available = GridLayoutDetector.getNewValue(message, RAW);
    if (obsolete != null && available != null) {
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Update to " + available, obsolete, available) {
        @Override
        protected void editBefore(@NotNull Document document) {
          Project project = startElement.getProject();
          final XmlFile file = PsiTreeUtil.getParentOfType(startElement, XmlFile.class);
          if (file != null) {
            AndroidResourceUtil.ensureNamespaceImported(file, AUTO_URI, null);
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
          }
        }
      }};
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }
}
