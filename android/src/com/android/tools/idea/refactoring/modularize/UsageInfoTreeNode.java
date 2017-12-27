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
package com.android.tools.idea.refactoring.modularize;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class UsageInfoTreeNode extends DependencyTreeNode {

  private final PsiElement myPsiElement;

  public UsageInfoTreeNode(@NotNull UsageInfo usageInfo, int referenceCount) {
    super(usageInfo, referenceCount);
    myPsiElement = usageInfo.getElement();
  }

  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.setIcon(ApplicationManager.getApplication().runReadAction(new Computable<Icon>() {
      @Override
      public Icon compute() {
        return myPsiElement.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS);
      }
    }));

    SimpleTextAttributes inheritedAttributes = getTextAttributes();
    if (myPsiElement instanceof PsiFile) {
      renderer.append(((PsiFile)myPsiElement).getName(), inheritedAttributes);
      renderQualifiers(ResourceHelper.getFolderConfiguration((PsiFile)myPsiElement), renderer, inheritedAttributes);
      renderReferenceCount(renderer, inheritedAttributes);
    }
    else if (myPsiElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)myPsiElement;
      renderer.append(psiClass.getName() == null ? "<unknown>" : psiClass.getName(), inheritedAttributes);
      renderReferenceCount(renderer, inheritedAttributes);
    }
    else if (myPsiElement instanceof XmlTag) {
      // TODO: use a syntax highlighter? SyntaxHighlighterFactory.getSyntaxHighlighter(psiElement.getLanguage(), null, null)
      renderer.append(myPsiElement.getText(), inheritedAttributes);
    }
    else {
      throw new IllegalArgumentException("Unknown psiElement " + myPsiElement);
    }
  }

  private static void renderQualifiers(FolderConfiguration folderConfig,
                                       ColoredTreeCellRenderer renderer,
                                       SimpleTextAttributes inheritedAttributes) {
    String config = folderConfig.getQualifierString();
    if (!StringUtil.isEmptyOrSpaces(config)) {
      SimpleTextAttributes derivedAttributes = new SimpleTextAttributes(
        inheritedAttributes.getStyle() | SimpleTextAttributes.STYLE_SMALLER,
        inheritedAttributes.getFgColor());
      renderer.append(" (" + config + ")", derivedAttributes);
    }
  }
}
