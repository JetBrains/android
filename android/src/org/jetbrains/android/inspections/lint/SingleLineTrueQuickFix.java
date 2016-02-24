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
package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * A quick fix for replacing deprecated singleLine="true" attribute to maxLines="1"
 */
public class SingleLineTrueQuickFix implements AndroidLintQuickFix {
  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    tag.setAttribute("singleLine", SdkConstants.NS_RESOURCES, null);
    tag.setAttribute("maxLines", SdkConstants.NS_RESOURCES, "1");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    if (!(startElement instanceof XmlToken)) {
      return false;
    }
    if (((XmlToken)startElement).getTokenType() != XmlTokenType.XML_NAME) {
      return false;
    }

    final XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
    if (attribute == null) {
      return false;
    }
    return "singleLine".equals(attribute.getLocalName()) && "true".equals(attribute.getValue());
  }

  @NotNull
  @Override
  public String getName() {
    return "Replace singleLine=\"true\" with maxLines=\"1\"";
  }
}
