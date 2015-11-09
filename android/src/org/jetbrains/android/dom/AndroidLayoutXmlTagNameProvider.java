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

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameVariantCollector;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlExtension;
import com.intellij.xml.XmlTagNameProvider;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.android.dom.layout.LayoutDomFileDescription;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
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

    final Set<String> addedNames = new HashSet<String>();
    for (XmlElementDescriptor descriptor : variants) {
      String qualifiedName = descriptor.getName(tag);
      if (!addedNames.add(qualifiedName)) {
        continue;
      }

      final String simpleName = AndroidUtils.getUnqualifiedName(qualifiedName);
      if (simpleName == null) {
        // If tag name is not a qualified name, we're not interested in it.
        // It would be handled by DefaultXmlTagNameProvider and shown nonetheless
        continue;
      }

      // Creating LookupElementBuilder with PsiElement gives an ability to show documentation during completion time
      PsiElement declaration = descriptor.getDeclaration();
      LookupElementBuilder lookupElement =
        declaration == null ? LookupElementBuilder.create(qualifiedName) : LookupElementBuilder.create(declaration, qualifiedName);

      lookupElement = lookupElement.withLookupString(simpleName);

      // Using insert handler is required for, e.g. automatic insertion of required fields in Android layout XMLs
      if (xmlExtension.useXmlTagInsertHandler()) {
        lookupElement = lookupElement.withInsertHandler(XmlTagInsertHandler.INSTANCE);
      }

      // DefaultXmlTagNameProvider uses PrioritizedLookupElement with priority 1.0 for most elements
      // We're using 0.5 here because those elements that would be processed by DefaultXmlTagNameProvider
      // are views available by short names and thus should have higher priority. Otherwise because this
      // provider kicks in first they would be lower.
      elements.add(PrioritizedLookupElement.withPriority(lookupElement, 0.5));
    }
  }
}
