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
package com.android.tools.idea.editors.strings;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_STRING;

/**
 * Enables the Safe Delete refactoring for Android string resource elements (&lt;string>...&lt;/string>)
 */
final class StringResourceSafeDeleteProcessorDelegate extends SafeDeleteProcessorDelegateBase {
  @Override
  public boolean handlesElement(@NotNull PsiElement element) {
    return handlesElementImpl(element);
  }

  static boolean handlesElementImpl(@NotNull PsiElement element) {
    if (element instanceof XmlTag) {
      // The string element: what we actually want to delete
      return AndroidResourceUtil.isStringResource((XmlTag)element);
    }
    else if (element instanceof XmlAttributeValue) {
      // The value of the string element's name attribute. Extracted from the string element and added to the list of PsiElements to search
      // for in the getElementsToSearch method below.
      PsiNamedElement attribute = (PsiNamedElement)element.getParent();
      return ATTR_NAME.equals(attribute.getName()) && TAG_STRING.equals(((PsiNamedElement)attribute.getParent()).getName());
    }
    else {
      return false;
    }
  }

  @NotNull
  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @Nullable Module module,
                                                              @NotNull Collection<PsiElement> elementsToDelete) {
    if (element instanceof XmlTag) {
      XmlTag tag = (XmlTag)element;

      XmlAttribute attribute = tag.getAttribute(ATTR_NAME);
      assert attribute != null;

      Collection<PsiElement> elements = new ArrayList<>();

      // Find usages of the string element's name attribute value, such as @string/string_name references in XML files
      elements.add(attribute.getValueElement());

      // R.string.string_name references in Java files that are not LightElements
      Collection<PsiField> fields = Arrays.stream(AndroidResourceUtil.findResourceFieldsForValueResource(tag, true))
        .filter(field -> !(field instanceof LightElement))
        .collect(Collectors.toList());

      elements.addAll(fields);
      return elements;
    }
    else if (element instanceof XmlAttributeValue) {
      return getElementsToSearch(element.getParent().getParent(), module, elementsToDelete);
    }
    else {
      return Collections.emptyList();
    }
  }

  @NotNull
  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                              @NotNull Collection<PsiElement> elementsToDelete,
                                                              boolean askUser) {
    return element instanceof XmlAttributeValue ? Collections.singletonList(element.getParent().getParent()) : Collections.emptyList();
  }

  @NotNull
  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element,
                                           @NotNull PsiElement[] elementsToDelete,
                                           @NotNull List<UsageInfo> result) {
    SafeDeleteProcessor.findGenericElementUsages(element, result, elementsToDelete);
    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(elementsToDelete), element);
  }

  @NotNull
  @Override
  public UsageInfo[] preprocessUsages(@NotNull Project project, @NotNull UsageInfo[] usages) {
    return usages;
  }

  @Nullable
  @Override
  public Collection<String> findConflicts(@NotNull PsiElement element, @NotNull PsiElement[] elementsToDelete) {
    return null;
  }

  @Override
  public void prepareForDeletion(@NotNull PsiElement element) throws IncorrectOperationException {
  }

  @Override
  public boolean isToSearchInComments(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public void setToSearchInComments(@NotNull PsiElement element, boolean enabled) {
  }

  @Override
  public boolean isToSearchForTextOccurrences(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public void setToSearchForTextOccurrences(@NotNull PsiElement element, boolean enabled) {
  }
}
