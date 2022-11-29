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

import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.ATTR_NAME;

/**
 * Enables the Safe Delete refactoring for Android string resource elements (&lt;string>...&lt;/string>)
 */
final class StringResourceSafeDeleteProcessorDelegate extends SafeDeleteProcessorDelegateBase {
  @Override
  public boolean handlesElement(@NotNull PsiElement element) {
    return handlesElementImpl(element);
  }

  static boolean handlesElementImpl(@NotNull PsiElement element) {
    return element instanceof XmlTag && IdeResourcesUtil.isStringResource((XmlTag)element);
  }

  @NotNull
  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @Nullable Module module,
                                                              @NotNull Collection<? extends PsiElement> elementsToDelete) {
    return Collections.singletonList(element);
  }

  @NotNull
  @Override
  public Collection<PsiElement> getAdditionalElementsToDelete(@NotNull PsiElement element,
                                                              @NotNull Collection<? extends PsiElement> elementsToDelete,
                                                              boolean askUser) {
    return Collections.emptyList();
  }

  @NotNull
  @Override
  public NonCodeUsageSearchInfo findUsages(@NotNull PsiElement element,
                                           @NotNull PsiElement[] elementsToDelete,
                                           @NotNull List<? super UsageInfo> result) {
    Collection<PsiElement> elements = new ArrayList<>();

    XmlTag tag = (XmlTag)element;
    XmlAttribute attribute = tag.getAttribute(ATTR_NAME);
    assert attribute != null;

    elements.add(attribute.getValueElement());
    elements.addAll(Arrays.asList(IdeResourcesUtil.findResourceFieldsForValueResource(tag, true)));
    elements.forEach(e -> SafeDeleteProcessor.findGenericElementUsages(e, result, elementsToDelete));

    return new NonCodeUsageSearchInfo(SafeDeleteProcessor.getDefaultInsideDeletedCondition(elementsToDelete), elements);
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
