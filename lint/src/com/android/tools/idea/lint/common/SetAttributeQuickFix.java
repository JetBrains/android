/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetAttributeQuickFix extends DefaultLintQuickFix {

  private final String myAttributeName;
  private final String myValue;
  private final String myNamespace;
  private final int myDot;
  private final int myMark;

  public SetAttributeQuickFix(@NotNull String name,
                              @Nullable String familyName,
                              @NotNull String attributeName,
                              @Nullable String namespace,
                              // 'null' value means asking
                              @Nullable String value) {
    this(name, familyName, attributeName, namespace, value,
         // The default was to select the whole text range
         value != null ? 0 : Integer.MIN_VALUE,
         value != null ? value.length() : Integer.MIN_VALUE);
  }

  public SetAttributeQuickFix(@NotNull String name,
                              @Nullable String familyName,
                              @NotNull String attributeName,
                              @Nullable String namespace,
                              @Nullable String value,
                              int dot,
                              int mark) {
    super(name, familyName);
    myAttributeName = attributeName;
    myValue = value;
    myNamespace = namespace;
    myDot = dot;
    myMark = mark;
  }

  @NotNull
  @Override
  public String getName() {
    return super.getName();
  }

  @Nullable
  @Override
  public String getFamilyName() {
    return super.getFamilyName();
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);

    if (tag == null) {
      return;
    }
    String value = myValue;

    if (value == null && context instanceof AndroidQuickfixContexts.DesignerContext) {
      value = LintIdeSupport.get().askForAttributeValue(myAttributeName, tag);
      if (value == null) {
        return;
      }
    }

    if (myNamespace != null) {
      XmlFile file = PsiTreeUtil.getParentOfType(tag, XmlFile.class);
      if (file != null) {
        LintIdeSupport.get().ensureNamespaceImported(file, myNamespace, null);
      }
    }

    final XmlAttribute attribute = ApplicationManager.getApplication().runWriteAction(
      (Computable<XmlAttribute>)() -> myNamespace != null
                                      ? tag.setAttribute(myAttributeName, myNamespace, "")
                                      : tag.setAttribute(myAttributeName, ""));

    if (attribute != null) {
      if (value != null && !value.isEmpty()) {
        String finalValue = value;
        ApplicationManager.getApplication().runWriteAction(() -> attribute.setValue(finalValue));
      }
      if (context instanceof AndroidQuickfixContexts.EditorContext) {
        final Editor editor = ((AndroidQuickfixContexts.EditorContext)context).getEditor();
        final XmlAttributeValue valueElement = attribute.getValueElement();
        final TextRange valueTextRange = attribute.getValueTextRange();

        if (valueElement != null) {
          final int valueElementStart = valueElement.getTextRange().getStartOffset();
          if (myDot != Integer.MIN_VALUE) {
            int end = valueElementStart + valueTextRange.getStartOffset() + myDot;
            if (myMark != Integer.MIN_VALUE && myMark != myDot) {
              int start = valueElementStart + valueTextRange.getStartOffset() + myMark;
              editor.getCaretModel().moveToOffset(end);
              editor.getSelectionModel().setSelection(start, end);
            }
            else {
              editor.getCaretModel().moveToOffset(end);
            }
          }
        }
      }
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    if (myValue == null && contextType == AndroidQuickfixContexts.BatchContext.TYPE) {
      return false;
    }
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
    if (tag == null) {
      return false;
    }

    XmlAttribute attribute;
    if (myNamespace != null) {
      attribute = tag.getAttribute(myAttributeName, myNamespace);
    }
    else {
      attribute = tag.getAttribute(myAttributeName);
    }
    return attribute == null || !StringUtil.notNullize(myValue).equals(StringUtil.notNullize(attribute.getValue()));
  }
}
