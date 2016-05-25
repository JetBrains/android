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

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Changes the attribute name.
 */
public class RenameAttributeQuickFix implements AndroidLintQuickFix {
  private final String myNamespace;
  private final String myLocalName;

  public RenameAttributeQuickFix(@NotNull String namespace, @NotNull String localName) {
    myNamespace = namespace;
    myLocalName = localName;
  }


  @Override
  public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull AndroidQuickfixContexts.Context context) {
    XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class, false);
    if (attribute == null) {
      return;
    }

    XmlTag tag = attribute.getParent();
    if (tag == null) {
      return;
    }

    XmlFile xmlFile = PsiTreeUtil.getParentOfType(tag, XmlFile.class);
    if (xmlFile == null) {
      return;
    }

    String prefix = AndroidResourceUtil.ensureNamespaceImported(xmlFile, myNamespace, null);
    attribute.setName(prefix + ":" + myLocalName);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement startElement,
                              @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.ContextType contextType) {
    return PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Use " + myLocalName;
  }
}
