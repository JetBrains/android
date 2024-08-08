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
package com.google.idea.blaze.base.lang.buildfile.references;

import com.google.idea.blaze.base.lang.buildfile.psi.BuildFile;
import com.google.idea.blaze.base.lang.buildfile.psi.StringLiteral;
import com.google.idea.blaze.base.lang.buildfile.psi.util.PsiUtils;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import javax.annotation.Nullable;

/** The label component preceding the colon. */
public class PackageReferenceFragment extends PsiReferenceBase<StringLiteral> {

  public PackageReferenceFragment(LabelReference labelReference) {
    super(labelReference.getElement(), labelReference.getRangeInElement(), labelReference.isSoft());
  }

  @Nullable
  private WorkspacePath getWorkspacePath(String labelString) {
    if (!labelString.startsWith("//")) {
      return null;
    }
    int colonIndex = labelString.indexOf(':');
    int endIndex = colonIndex != -1 ? colonIndex : labelString.length();
    return WorkspacePath.createIfValid(labelString.substring(2, endIndex));
  }

  @Override
  public TextRange getRangeInElement() {
    String rawText = myElement.getText();
    boolean valid = getWorkspacePath(myElement.getStringContents()) != null;
    if (!valid) {
      return TextRange.EMPTY_RANGE;
    }
    int endIndex = rawText.indexOf(':');
    if (endIndex == -1) {
      endIndex = rawText.length() - 1;
    }
    return new TextRange(1, endIndex);
  }

  @Nullable
  @Override
  public BuildFile resolve() {
    WorkspacePath workspacePath = getWorkspacePath(myElement.getStringContents());
    return BuildReferenceManager.getInstance(myElement.getProject())
        .resolveBlazePackage(workspacePath);
  }

  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myElement; // renaming a BUILD file has no effect on the package label fragments
  }

  @Override
  public PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof BuildFile)) {
      return super.bindToElement(element);
    }
    if (element.equals(resolve())) {
      return myElement;
    }
    Label newPackageLabel = ((BuildFile) element).getPackageLabel();
    if (newPackageLabel == null) {
      return myElement;
    }
    String newPath = newPackageLabel.blazePackage().toString();
    String labelString = myElement.getStringContents();
    int colonIndex = labelString.indexOf(':');
    if (colonIndex != -1) {
      return handleRename("//" + newPath + labelString.substring(colonIndex));
    }
    // need to assume there's an implicit rule name
    return handleRename("//" + newPath + ":" + PathUtil.getFileName(labelString));
  }

  private PsiElement handleRename(String newStringContents) {
    ASTNode node = myElement.getNode();
    node.replaceChild(
        node.getFirstChildNode(),
        PsiUtils.createNewLabel(myElement.getProject(), newStringContents));
    return myElement;
  }
}
