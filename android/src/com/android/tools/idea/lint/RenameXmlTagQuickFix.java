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

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.annotations.NotNull;

/**
 * QuickFix for renaming an XmlTag
 */
class RenameXmlTagQuickFix extends DefaultLintQuickFix {

  private final String myNewName;

  RenameXmlTagQuickFix(@NotNull String newName) {
    super("Use " + newName);
    myNewName = newName;
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement,
                    @NotNull AndroidQuickfixContexts.Context context) {
    XmlTag currentTag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);

    if (currentTag == null) {
      return;
    }

    XmlTag parentTag = currentTag.getParentTag();

    if (parentTag == null) {
      return;
    }

    XmlTag newTag = parentTag.createChildTag(myNewName, null,
                                             currentTag.isEmpty() ? null : currentTag.getValue().getText(),
                                             true);
    // copy attributes.
    for (XmlAttribute attr : currentTag.getAttributes()) {
      newTag.setAttribute(attr.getLocalName(), attr.getNamespace(), attr.getValue());
    }

    currentTag.replace(newTag);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return startElement.isValid()
           && endElement.isValid()
           && PsiTreeUtil.getParentOfType(startElement, XmlTag.class) != null;
  }
}
