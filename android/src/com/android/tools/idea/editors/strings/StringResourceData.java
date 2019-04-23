/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceItem;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.facet.Facet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.rename.RenameProcessor;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StringResourceData {
  private static final int MAX_LOCALE_LABEL_COUNT = 3;

  private final Facet myFacet;
  private final Map<StringResourceKey, StringResource> myKeyToResourceMap;
  private final StringResourceRepository myRepository;

  StringResourceData(@NotNull Facet facet,
                     @NotNull Map<StringResourceKey, StringResource> keyToResourceMap,
                     @NotNull StringResourceRepository repository) {
    myFacet = facet;
    myKeyToResourceMap = keyToResourceMap;
    myRepository = repository;
  }

  public void setKeyName(@NotNull StringResourceKey key, @NotNull String name) {
    if (key.getName().equals(name)) {
      return;
    }

    boolean mapContainsName = myKeyToResourceMap.keySet().stream()
      .map(k -> k.getName())
      .anyMatch(n -> n.equals(name));

    if (mapContainsName) {
      return;
    }

    ResourceItem value = getStringResource(key).getDefaultValueAsResourceItem();

    if (value == null) {
      return;
    }

    Project project = myFacet.getModule().getProject();

    XmlTag stringElement = LocalResourceRepository.getItemTag(project, value);
    assert stringElement != null;

    XmlAttribute nameAttribute = stringElement.getAttribute(SdkConstants.ATTR_NAME);
    assert nameAttribute != null;

    PsiElement nameAttributeValue = nameAttribute.getValueElement();
    assert nameAttributeValue != null;

    new RenameProcessor(project, nameAttributeValue, name, false, false).run();

    myKeyToResourceMap.remove(key);
    key = new StringResourceKey(name, key.getDirectory());
    myKeyToResourceMap.put(key, new StringResource(key, myRepository, project));
  }

  public boolean setTranslatable(@NotNull StringResourceKey key, boolean translatable) {
    StringResource stringResource = getStringResource(key);
    ResourceItem item = stringResource.getDefaultValueAsResourceItem();
    if (item != null) {
      String translatableAsString;
      if (translatable) {
        translatableAsString = null;
        stringResource.setTranslatable(true);
      }
      else {
        translatableAsString = SdkConstants.VALUE_FALSE;
        stringResource.setTranslatable(false);
      }

      return StringsWriteUtils.setAttributeForItems(myFacet.getModule().getProject(), SdkConstants.ATTR_TRANSLATABLE, translatableAsString,
                                                    Collections.singletonList(item));
    }
    return false;
  }

  @Nullable
  public String validateKey(@NotNull StringResourceKey key) {
    if (!myKeyToResourceMap.keySet().contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    StringResource stringResource = getStringResource(key);
    if (!stringResource.isTranslatable()) {
      Collection<Locale> localesWithTranslation = stringResource.getTranslatedLocales();
      if (!localesWithTranslation.isEmpty()) {
        return String.format("Key '%1$s' is marked as non translatable, but is translated in %2$s %3$s", key.getName(),
                             StringUtil.pluralize("locale", localesWithTranslation.size()), summarizeLocales(localesWithTranslation));
      }
    }
    else { // translatable key
      if (stringResource.getDefaultValueAsResourceItem() == null) {
        return "Key '" + key.getName() + "' missing default value";
      }

      Collection<Locale> missingTranslations = getMissingTranslations(key);
      if (!missingTranslations.isEmpty()) {
        return String.format("Key '%1$s' has translations missing for %2$s %3$s", key.getName(),
                             StringUtil.pluralize("locale", missingTranslations.size()), summarizeLocales(missingTranslations));
      }
    }
    return null;
  }

  @NotNull
  @VisibleForTesting
  Collection<Locale> getMissingTranslations(@NotNull StringResourceKey key) {
    Set<Locale> missingTranslations = Sets.newHashSet();
    for (Locale locale : getLocaleSet()) {
      StringResource stringResource = getStringResource(key);
      if (stringResource.isTranslationMissing(locale)) {
        missingTranslations.add(locale);
      }
    }

    return missingTranslations;
  }

  @VisibleForTesting
  @NotNull
  static String summarizeLocales(@NotNull Collection<Locale> locales) {
    if (locales.isEmpty()) {
      return "";
    }

    final int size = locales.size();

    if (size == 1) {
      return getLabel(Iterables.getFirst(locales, null));
    }

    List<Locale> sorted = getLowest(locales);

    if (size <= MAX_LOCALE_LABEL_COUNT) {
      return getLabels(sorted.subList(0, size - 1)) + " and " + getLabel(sorted.get(size - 1));
    }
    else {
      return getLabels(sorted) + " and " + (size - MAX_LOCALE_LABEL_COUNT) + " more";
    }
  }

  @NotNull
  private static List<Locale> getLowest(@NotNull Collection<Locale> locales) {
    return locales.stream()
      .limit(MAX_LOCALE_LABEL_COUNT)
      .sorted(Comparator.comparing(StringResourceData::getLabel))
      .collect(Collectors.toList());
  }

  private static String getLabels(Collection<Locale> locales) {
    return locales.stream()
      .map(StringResourceData::getLabel)
      .collect(Collectors.joining(", "));
  }

  private static String getLabel(@Nullable Locale locale) {
    return locale == null ? "" : LocaleMenuAction.getLocaleLabel(locale, false);
  }

  boolean containsKey(@NotNull StringResourceKey key) {
    return myKeyToResourceMap.containsKey(key);
  }

  @NotNull
  public StringResource getStringResource(@NotNull StringResourceKey key) {
    StringResource resource = myKeyToResourceMap.get(key);

    if (resource == null) {
      throw new IllegalArgumentException(key.toString());
    }

    return resource;
  }

  @NotNull
  public Collection<StringResource> getResources() {
    return myKeyToResourceMap.values();
  }

  @NotNull
  public List<StringResourceKey> getKeys() {
    return myKeyToResourceMap.keySet().stream()
      .sorted()
      .collect(Collectors.toList());
  }

  @NotNull
  public List<Locale> getLocaleList() {
    return getTranslatedLocaleStream()
      .distinct()
      .sorted(Locale.LANGUAGE_NAME_COMPARATOR)
      .collect(Collectors.toList());
  }

  @NotNull
  Set<Locale> getLocaleSet() {
    return getTranslatedLocaleStream().collect(Collectors.toSet());
  }

  @NotNull
  private Stream<Locale> getTranslatedLocaleStream() {
    return myKeyToResourceMap.values().stream().flatMap(resource -> resource.getTranslatedLocales().stream());
  }
}
