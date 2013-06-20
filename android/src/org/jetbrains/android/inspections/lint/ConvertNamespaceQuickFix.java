/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.tools.lint.checks.TypographyDetector;
import com.android.tools.lint.detector.api.Issue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.URI_PREFIX;

class ConvertNamespaceQuickFix implements AndroidLintQuickFix {
  public ConvertNamespaceQuickFix() {
  }

  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class);
    if (tag == null) {
      return;
    }

    for (XmlAttribute attribute : tag.getAttributes()) {
      if (attribute.getName().startsWith(SdkConstants.XMLNS)) {
        String uri = attribute.getValue();
        if (uri != null && !uri.isEmpty() && uri.startsWith(URI_PREFIX) && !uri.equals(ANDROID_URI)) {
          attribute.setValue(AUTO_URI);
        }
      }
    }
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, XmlTag.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidBundle.message("android.lint.fix.replace.namespace");
  }
}
