/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom;

import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.meta.PsiPresentableMetaData;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlTagNameProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Tag name provider that is supposed to work with Java-style fully-qualified names
 * and adds simple name (suffix after the last dot, if present) as a lookup string.
 * This makes it possible to do completions like "TvV" to "android.media.tv.TvView".
 *
 * Implementation is derived from {@link com.intellij.psi.impl.source.xml.DefaultXmlTagNameProvider}
 * with described above LookupElementBuilder modification, unnecessary bits stripped out
 * and adding some comments on implementation.
 */
public class AndroidLayoutXmlTagNameProvider implements XmlTagNameProvider {
  // List of namespaces this tag name provider would provide names for.
  private static final List<String> NAMESPACES = Collections.singletonList(XmlUtil.EMPTY_URI);

  @Override
  public void addTagNameVariants(List<LookupElement> elements, @NotNull XmlTag tag, String prefix) {
    final PsiFile file = tag.getContainingFile();
    if (!(file instanceof XmlFile && LayoutDomFileDescription.isLayoutFile((XmlFile)file))) {
      // Only use this provider for Android layout files
      return;
    }

    XmlExtension xmlExtension = XmlExtension.getExtension(file);
    List<XmlElementDescriptor> variants =
      TagNameVariantCollector.getTagDescriptors(tag, NAMESPACES, null);

    // Find the framework widgets that have a support library alternative
    Set<String> supportAlternatives = Sets.newHashSet();
    for (XmlElementDescriptor descriptor : variants) {
      String qualifiedName = descriptor.getName(tag);

      if (qualifiedName.startsWith("android.support.")) {
        supportAlternatives.add(AndroidUtils.getUnqualifiedName(qualifiedName));
      }
    }

    final Set<String> addedNames = Sets.newHashSet();
    for (XmlElementDescriptor descriptor : variants) {
      String qualifiedName = descriptor.getName(tag);
      if (!addedNames.add(qualifiedName)) {
        continue;
      }

      final String simpleName = AndroidUtils.getUnqualifiedName(qualifiedName);

      // Creating LookupElementBuilder with PsiElement gives an ability to show documentation during completion time
      PsiElement declaration = descriptor.getDeclaration();
      LookupElementBuilder lookupElement =
        declaration == null ? LookupElementBuilder.create(qualifiedName) : LookupElementBuilder.create(declaration, qualifiedName);

      final boolean isDeprecated = isDeclarationDeprecated(declaration);
      if (isDeprecated) {
        lookupElement = lookupElement.withStrikeoutness(true);
      }

      if (simpleName != null) {
        lookupElement = lookupElement.withLookupString(simpleName);
      }

      // For some standard widgets available by short names, icons are available.
      // This statement preserves them in autocompletion.
      if (descriptor instanceof PsiPresentableMetaData) {
        lookupElement = lookupElement.withIcon(((PsiPresentableMetaData)descriptor).getIcon());
      }

      // Using insert handler is required for, e.g. automatic insertion of required fields in Android layout XMLs
      if (xmlExtension.useXmlTagInsertHandler()) {
        lookupElement = lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE);
      }

      // Deprecated tag names are supposed to be shown below non-deprecated tags
      int priority = isDeprecated ? 10 : 100;
      if (simpleName == null) {
        if (supportAlternatives.contains(qualifiedName)) {
          // This component has a support library alternative so lower the priority so the support component is shown at the top.
          priority -= 1;
        }
        else {
          // The component doesn't have an alternative in the support library so push it to the top.
          priority += 10;
        }
      }

      elements.add(PrioritizedLookupElement.withPriority(lookupElement, priority));
    }
  }

  private static boolean isDeclarationDeprecated(@Nullable PsiElement declaration) {
    if (!(declaration instanceof PsiClass)) {
      return false;
    }

    final PsiClass aClass = (PsiClass)declaration;
    final PsiModifierList modifierList = aClass.getModifierList();
    if (modifierList == null) {
      return false;
    }

    return modifierList.findAnnotation("java.lang.Deprecated") != null;
  }
}
