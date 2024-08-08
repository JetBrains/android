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
package com.google.idea.blaze.base.lang.projectview.psi.util;

import com.google.idea.blaze.base.lang.projectview.language.ProjectViewFileType;
import com.google.idea.blaze.base.lang.projectview.language.ProjectViewLanguage;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiElement;
import com.google.idea.blaze.base.lang.projectview.psi.ProjectViewPsiSectionItem;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.PsiFileFactoryImpl;
import com.intellij.testFramework.LightVirtualFile;
import javax.annotation.Nullable;

/** Creates dummy BuildElements, e.g. for renaming purposes. */
public class ProjectViewElementGenerator {

  private static final String DUMMY_FILENAME = "dummy.bazelproject";

  private static PsiFile createDummyFile(Project project, String contents) {
    PsiFileFactory factory = PsiFileFactory.getInstance(project);
    LightVirtualFile virtualFile =
        new LightVirtualFile(DUMMY_FILENAME, ProjectViewFileType.INSTANCE, contents);
    PsiFile psiFile =
        ((PsiFileFactoryImpl) factory)
            .trySetupPsiForFile(virtualFile, ProjectViewLanguage.INSTANCE, false, true);
    assert psiFile != null;
    return psiFile;
  }

  @Nullable
  public static ASTNode createReplacementItemNode(
      ProjectViewPsiSectionItem sectionItem, String newStringContents) {
    TextRange itemRange = sectionItem.getTextRange();
    ProjectViewPsiElement parent = (ProjectViewPsiElement) sectionItem.getParent();
    if (parent == null) {
      return sectionItem.getNode();
    }
    int startOffset = sectionItem.getStartOffsetInParent();
    String originalSectionText = parent.getText();
    String newSectionText =
        StringUtil.replaceSubstring(
            originalSectionText,
            new TextRange(startOffset, startOffset + itemRange.getLength()),
            newStringContents);
    PsiFile dummyFile = createDummyFile(sectionItem.getProject(), newSectionText);
    PsiElement leafElement = dummyFile.findElementAt(startOffset);
    return leafElement != null ? leafElement.getParent().getNode() : null;
  }
}
