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
package com.android.tools.idea.databinding;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.LightBindingClass.LightDataBindingField;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.NameUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.NameSuggester;
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.*;

/**
 * Renames resource IDs when Java fields derived from those resources are renamed.
 */
public class DataBindingRenamer extends AutomaticRenamer {
  /**
   * Initializes the renamer.
   *
   * @param field the field to be renamed
   * @param newName the new name of the field
   */
  public DataBindingRenamer(@NotNull LightDataBindingField field, @NotNull String newName) {
    PsiNamedElement resourceId = getResourceIdElement(field);
    if (resourceId != null) {
      myElements.add(resourceId);
    }

    suggestAllNames(field.getName(), newName);
  }

  @Nullable
  private static PsiNamedElement getResourceIdElement(@NotNull PsiElement element) {
    PsiElement xmlElement = element.getNavigationElement();
    if (!(xmlElement instanceof XmlTag)) {
      return null;
    }
    XmlAttribute xmlAttribute = ((XmlTag)xmlElement).getAttribute(ATTR_ID, ANDROID_URI);
    if (xmlAttribute == null) {
      return null;
    }
    XmlAttributeValue valueElement = xmlAttribute.getValueElement();
    if (valueElement == null) {
      return null;
    }
    String id = valueElement.getValue();
    if (!id.startsWith(NEW_ID_PREFIX)) {
      return null;
    }

    return new ValueResourceElementWrapper(valueElement);
  }

  @Override
  @NotNull
  protected String suggestNameForElement(@NotNull PsiNamedElement element, @NotNull NameSuggester suggester,
                                         @NotNull String newFieldName, @NotNull String oldFieldName) {
    if (element instanceof ValueResourceElementWrapper) {
      String[] words = NameUtil.splitNameIntoWords(newFieldName);
      return SdkConstants.NEW_ID_PREFIX + String.join("_", words).toLowerCase();
    }
    return super.suggestNameForElement(element, suggester, newFieldName, oldFieldName);
  }

  @Override
  public boolean isSelectedByDefault() {
    return true;
  }

  @Override
  @Nls
  @NotNull
  public String getDialogTitle() {
    return "Rename Resource IDs";
  }

  @Override
  @Nls
  @NotNull
  public String getDialogDescription() {
    return "Rename resources with the following IDs to:";
  }

  @Override
  @NotNull
  public String entityName() {
    return "Resource ID";
  }
}
