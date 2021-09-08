/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint.intentions;

import com.android.resources.ResourceType;
import com.google.common.base.CharMatcher;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.intentions.AndroidAddStringResourceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.CharMatcher.inRange;

public class AndroidAddStringResourceQuickFix extends AndroidAddStringResourceAction {
  private static final CharMatcher DISALLOWED_CHARS = inRange('a', 'z').or(inRange('A', 'Z')).or(inRange('0', '9')).negate();
  private final PsiElement myStartElement;

  public AndroidAddStringResourceQuickFix(@NotNull PsiElement startElement) {
    myStartElement = startElement;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myStartElement.isValid()) {
      return false;
    }
    final XmlAttributeValue value = getAttributeValue(myStartElement);
    return value != null && getStringLiteralValue(project, value, file, ResourceType.STRING) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String defaultName = null;
    final PsiElement parent = myStartElement.getParent();
    if (parent instanceof XmlAttribute) {
      final String value = ((XmlAttribute)parent).getValue();
      if (value != null) {
        defaultName = buildResourceName(value);
      }
    }
    invokeIntention(project, editor, file, defaultName);
  }

  @NotNull
  public static String buildResourceName(@NotNull String value) {
    final String result = StringUtil.toLowerCase(DISALLOWED_CHARS.trimAndCollapseFrom(value, '_'));
    if (!result.isEmpty() && CharMatcher.javaDigit().matches(result.charAt(0))) {
      return "_" + result;
    }
    return result;
  }

  public void invokeIntention(Project project, Editor editor, PsiFile file, @Nullable String resName) {
    final XmlAttributeValue attributeValue = getAttributeValue(myStartElement);
    if (attributeValue != null) {
      doInvoke(project, editor, file, resName, attributeValue, ResourceType.STRING);
    }
  }

  @Nullable
  private static XmlAttributeValue getAttributeValue(@NotNull PsiElement element) {
    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
    return attribute != null ? attribute.getValueElement() : null;
  }
}
