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
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.intentions.AndroidAddStringResourceAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidAddStringResourceQuickFix extends AndroidAddStringResourceAction {
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
        defaultName = IdeResourcesUtil.buildResourceNameFromStringValue(value);
      }
    }
    invokeIntention(project, editor, file, defaultName);
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
