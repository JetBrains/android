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
package org.jetbrains.android.refactoring.ui;

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiBinaryFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

public class UsageInfoTreeNode extends CheckedTreeNode implements Comparable<UsageInfoTreeNode> {

  private final int myReferenceCount;
  private final PsiElement myPsiElement;

  public UsageInfoTreeNode(@NotNull UsageInfo usageInfo) {
    this(usageInfo, 0);
  }

  public UsageInfoTreeNode(@NotNull UsageInfo usageInfo, int referenceCount) {
    super(usageInfo);
    myReferenceCount = referenceCount;
    myPsiElement = usageInfo.getElement();
  }

  public int getReferenceCount() {
    return myReferenceCount;
  }

  public PsiElement getPsiElement() {
    return myPsiElement;
  }

  public void render(@NotNull ColoredTreeCellRenderer renderer, @NotNull SimpleTextAttributes inheritedAttributes) {
    if (myPsiElement instanceof PsiBinaryFile) {
      renderer.setIcon(PlatformIcons.FILE_ICON);
      renderer.append(((PsiBinaryFile)myPsiElement).getName(), inheritedAttributes);
      renderQualifiers(ResourceHelper.getFolderConfiguration((PsiFile)myPsiElement), renderer, inheritedAttributes);
      renderReferenceCount(renderer, inheritedAttributes);
    }
    else if (myPsiElement instanceof PsiFile) {
      renderer.setIcon(AllIcons.FileTypes.Xml);
      renderer.append(((PsiFile)myPsiElement).getName(), inheritedAttributes);
      renderQualifiers(ResourceHelper.getFolderConfiguration((PsiFile)myPsiElement), renderer, inheritedAttributes);
      renderReferenceCount(renderer, inheritedAttributes);
    }
    else if (myPsiElement instanceof PsiClass) {
      PsiClass psiClass = (PsiClass)myPsiElement;
      // TODO: use psiClass.getIcon() and figure out how to refresh the presentation once the icon is available.
      if (psiClass.isInterface()) {
        renderer.setIcon(PlatformIcons.INTERFACE_ICON);
      }
      else if (psiClass.isEnum()) {
        renderer.setIcon(PlatformIcons.ENUM_ICON);
      }
      else if (psiClass.isAnnotationType()) {
        renderer.setIcon(PlatformIcons.ANNOTATION_TYPE_ICON);
      }
      else {
        renderer.setIcon(PlatformIcons.CLASS_ICON);
      }
      renderer.append(psiClass.getName() == null ? "<unknown>" : psiClass.getName(), inheritedAttributes);
      renderReferenceCount(renderer, inheritedAttributes);
    }
    else if (myPsiElement instanceof XmlTag) {
      // TODO: use a syntax highlighter? SyntaxHighlighterFactory.getSyntaxHighlighter(psiElement.getLanguage(), null, null)
      renderer.setIcon(PlatformIcons.XML_TAG_ICON);
      renderer.append(myPsiElement.getText(), inheritedAttributes);
    }
    else {
      throw new IllegalArgumentException("Unknown psiElement " + myPsiElement);
    }
  }

  private void renderReferenceCount(ColoredTreeCellRenderer renderer, SimpleTextAttributes inheritedAttributes) {
    if (myReferenceCount > 1) {
      SimpleTextAttributes derivedAttributes = new SimpleTextAttributes(
        inheritedAttributes.getStyle() | SimpleTextAttributes.STYLE_ITALIC | SimpleTextAttributes.STYLE_SMALLER,
        inheritedAttributes.getFgColor());
      renderer.append(" (" + myReferenceCount + " usages)", derivedAttributes);
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

  @Override
  public int compareTo(@NotNull UsageInfoTreeNode o) {
    // TODO: How should we sort these properly?
    return o.getReferenceCount() - myReferenceCount;
  }
}
