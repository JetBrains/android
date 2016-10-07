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
package org.jetbrains.android.dom;

import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.dom.xml.PreferenceElement;
import org.jetbrains.android.dom.xml.XmlResourceElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Utility functions for enumerating available children tag types in the context of a given XML tag.
 *
 * Entry point is {@link #processSubtags(AndroidFacet, AndroidDomElement, SubtagProcessor)}, look for a
 * Javadoc there.
 */
public class SubtagsProcessingUtil {
  private SubtagsProcessingUtil() {
  }

  private static boolean isPreference(@NotNull Map<String, PsiClass> preferenceClassMap, @Nullable PsiClass psiClass) {
    if (psiClass == null) {
      return false;
    }
    PsiClass preferenceClass = preferenceClassMap.get("Preference");
    return preferenceClass != null && (preferenceClass == psiClass || psiClass.isInheritor(preferenceClass, true));
  }

  /**
   * Provides information about available tags for resources in xml/ folder
   */
  public static void registerXmlResourcesSubtags(AndroidFacet facet,
                                                 XmlTag tag,
                                                 XmlResourceElement element,
                                                 SubtagProcessor subtagProcessor) {
    final String tagName = tag.getName();

    switch (tagName) {
      case "searchable":
        subtagProcessor.processSubtag("actionkey", XmlResourceElement.class);
        break;

      // for keyboard api
      case "Keyboard":
        subtagProcessor.processSubtag("Row", XmlResourceElement.class);
        break;
      case "Row":
        subtagProcessor.processSubtag("Key", XmlResourceElement.class);
        break;

      // for device-admin api
      case "device-admin":
        subtagProcessor.processSubtag("uses-policies", XmlResourceElement.class);
        break;
      case "uses-policies":
        subtagProcessor.processSubtag("limit-password", XmlResourceElement.class);
        subtagProcessor.processSubtag("watch-login", XmlResourceElement.class);
        subtagProcessor.processSubtag("reset-password", XmlResourceElement.class);
        subtagProcessor.processSubtag("force-lock", XmlResourceElement.class);
        subtagProcessor.processSubtag("wipe-data", XmlResourceElement.class);
        subtagProcessor.processSubtag("set-global-proxy", XmlResourceElement.class);
        subtagProcessor.processSubtag("expire-password", XmlResourceElement.class);
        subtagProcessor.processSubtag("encrypted-storage", XmlResourceElement.class);
        subtagProcessor.processSubtag("disable-camera", XmlResourceElement.class);
        subtagProcessor.processSubtag("disable-keyguard-features", XmlResourceElement.class);
        break;

      // DevicePolicyManager API
      case "preference-headers":
        subtagProcessor.processSubtag("header", PreferenceElement.class);
        break;
    }

    // for preferences
    Map<String, PsiClass> prefClassMap = AttributeProcessingUtil.getPreferencesClassMap(facet);
    String prefClassName = element.getXmlTag().getName();
    PsiClass psiClass = prefClassMap.get(prefClassName);

    if (isPreference(prefClassMap, psiClass)) {
      registerClassNameSubtags(tag, prefClassMap, PreferenceElement.class, subtagProcessor);
    }
  }

  private static void registerClassNameSubtags(XmlTag tag,
                                               Map<String, PsiClass> classMap,
                                               Type type,
                                               SubtagProcessor subtagProcessor) {
    final Set<String> allAllowedTags = new HashSet<>();
    final Map<String, String> class2Name = new HashMap<>();

    for (Map.Entry<String, PsiClass> entry : classMap.entrySet()) {
      final String tagName = entry.getKey();
      final PsiClass aClass = entry.getValue();

      if (!AndroidUtils.isAbstract(aClass)) {
        allAllowedTags.add(tagName);
        final String qName = aClass.getQualifiedName();
        final String prevTagName = class2Name.get(qName);

        if (prevTagName == null || tagName.indexOf('.') == -1) {
          class2Name.put(qName, tagName);
        }
      }
    }
    registerSubtags(tag, allAllowedTags, class2Name.values(), type, subtagProcessor);
  }

  private static void registerSubtags(XmlTag tag,
                                      final Set<String> allowedTags,
                                      Collection<String> tagsToComplete,
                                      Type type,
                                      SubtagProcessor subtagProcessor) {
    for (String tagName : tagsToComplete) {
      subtagProcessor.processSubtag(tagName, type);
    }
    registerExistingSubtags(tag, allowedTags::contains, type, subtagProcessor);
  }

  /**
   * Enumerate children types that are valid inside a given XML tag, represented by {@link AndroidDomElement}.
   * Proceeds by dispatching on element type by instanceof checks, "returns" information about available tags
   * via {@code subtagCallback}.
   */
  public static void processSubtags(@NotNull AndroidFacet facet,
                                    @NotNull AndroidDomElement element,
                                    @NotNull SubtagProcessor subtagProcessor) {
    if (element instanceof LayoutElement) {
      registerClassNameSubtags(element.getXmlTag(), AttributeProcessingUtil.getViewClassMap(facet), LayoutViewElement.class,
                               subtagProcessor);
    }
    else if (element instanceof XmlResourceElement) {
      registerXmlResourcesSubtags(facet, element.getXmlTag(), (XmlResourceElement)element, subtagProcessor);
    }
  }

  /**
   * Enumerate types of XML tags that are already are children of a tag, via {@code subtagProcessor}
   */
  private static void registerExistingSubtags(@NotNull XmlTag tag,
                                              @NotNull Predicate<String> filter,
                                              @NotNull Type type,
                                              @NotNull SubtagProcessor subtagProcessor) {
    XmlTag[] subtags = tag.getSubTags();
    for (XmlTag subtag : subtags) {
      String localName = subtag.getLocalName();
      if (filter.test(localName)) {
        // Skip child tag for which code completion is invoked at the moment
        if (!localName.endsWith(CompletionUtil.DUMMY_IDENTIFIER_TRIMMED)) {
          subtagProcessor.processSubtag(new XmlName(localName), type);
        }
      }
    }
  }

  public interface SubtagProcessor {
    void processSubtag(@NotNull XmlName xmlName, @NotNull Type type);

    default void processSubtag(@NotNull String xmlName, @NotNull Type type) {
      processSubtag(new XmlName(xmlName), type);
    }
  }
}
