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
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StringResourceData {
  private final AndroidFacet myFacet;
  private final Map<String, StringResource> myKeyToResourceMap;

  public StringResourceData(@NotNull AndroidFacet facet,
                            @NotNull Map<String, StringResource> keyToResourceMap) {
    myFacet = facet;
    myKeyToResourceMap = keyToResourceMap;
  }

  public void changeKeyName(@NotNull String oldKey, @NotNull String newKey) {
    Set<String> keys = myKeyToResourceMap.keySet();
    if (!keys.contains(oldKey)) {
      throw new IllegalArgumentException("The old key \"" + oldKey + "\" doesn't exist.");
    }

    if (keys.contains(newKey)) {
      throw new IllegalArgumentException("The new key \"" + newKey + "\" already exists.");
    }

    StringResource stringResource = myKeyToResourceMap.remove(oldKey);
    assert stringResource != null;
    stringResource.setKey(newKey);
    myKeyToResourceMap.put(newKey, stringResource);
  }

  public boolean setTranslatable(String key, boolean translatable) {
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

  public boolean setTranslation(@NotNull String key, @Nullable Locale locale, @NotNull String value) {
    StringResource stringResource = getStringResource(key);
    ResourceItem currentItem = locale == null ? stringResource.getDefaultValueAsResourceItem() : stringResource.getTranslationAsResourceItem(locale);
    if (currentItem != null) { // modify existing item
      CharSequence oldText = locale == null ? stringResource.getDefaultValueAsString() : stringResource.getTranslationAsString(locale);

      if (!StringUtil.equals(oldText, value)) {
        boolean changed = StringsWriteUtils.setItemText(myFacet.getModule().getProject(), currentItem, value);
        if (changed) {
          if (value.isEmpty()) {
            if (locale == null) {
              stringResource.removeDefaultValue();
            }
            else {
              stringResource.removeTranslation(locale);
            }
          } else {
            if (locale == null) {
              stringResource.setDefaultValue(currentItem, value);
            }
            else {
              stringResource.putTranslation(locale, currentItem, value);
            }
          }
        }
        return changed;
      }
    }
    else { // create new item
      @SuppressWarnings("deprecation") VirtualFile primaryResourceDir = myFacet.getPrimaryResourceDir();
      assert primaryResourceDir != null;

      boolean translatable = stringResource.isTranslatable();
      ResourceItem item = StringsWriteUtils.createItem(myFacet, primaryResourceDir, locale, key, value, translatable);
      if (item != null) {
        if (locale == null) {
          stringResource.setDefaultValue(item, value);
        }
        else {
          stringResource.putTranslation(locale, item, value);
        }
        return true;
      }
      return false;
    }
    return false;
  }

  @Nullable
  public String validateKey(@NotNull String key) {
    if (!myKeyToResourceMap.keySet().contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    StringResource stringResource = getStringResource(key);
    if (!stringResource.isTranslatable()) {
      Collection<Locale> localesWithTranslation = stringResource.getTranslatedLocales();
      if (!localesWithTranslation.isEmpty()) {
        return String.format("Key '%1$s' is marked as non translatable, but is translated in %2$s %3$s", key,
                             StringUtil.pluralize("locale", localesWithTranslation.size()), summarizeLocales(localesWithTranslation));
      }
    }
    else { // translatable key
      if (stringResource.getDefaultValueAsResourceItem() == null) {
        return "Key '" + key + "' missing default value";
      }

      Collection<Locale> missingTranslations = getMissingTranslations(key);
      if (!missingTranslations.isEmpty()) {
        return String
          .format("Key '%1$s' has translations missing for %2$s %3$s", key, StringUtil.pluralize("locale", missingTranslations.size()),
                  summarizeLocales(missingTranslations));
      }
    }
    return null;
  }

  @Nullable
  public String validateTranslation(@NotNull String key, @Nullable Locale locale) {
    if (!myKeyToResourceMap.keySet().contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    StringResource stringResource = getStringResource(key);

    if (locale == null) {
      ResourceItem item = stringResource.getDefaultValueAsResourceItem();
      return (item == null) ? String.format("Key '%1$s' is missing the default value", key) : null;
    }

    final boolean translationMissing = stringResource.isTranslationMissing(locale);
    final boolean doNotTranslate = !stringResource.isTranslatable();
    if (translationMissing && !doNotTranslate) {
      return String.format("Key '%1$s' is missing %2$s translation", key, getLabel(locale));
    }
    else if (doNotTranslate && !translationMissing) {
      return String.format("Key '%1$s' is marked as non-localizable, and should not be translated to %2$s", key, getLabel(locale));
    }
    return null;
  }

  @NotNull
  @VisibleForTesting
  Collection<Locale> getMissingTranslations(@NotNull String key) {
    Set<Locale> missingTranslations = Sets.newHashSet();
    for (Locale locale : getLocales()) {
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

    final int max = 3;
    List<Locale> sorted = getLowest(locales, max);
    if (size <= max) {
      return String.format("%1$s and %2$s", getLabels(Iterables.limit(sorted, size - 1)), getLabel(sorted.get(size - 1)));
    }
    else {
      return String.format("%1$s and %2$d more", getLabels(sorted), size - max);
    }
  }

  private static List<Locale> getLowest(Collection<Locale> locales, int n) {
    List<Locale> result = Lists.newArrayListWithExpectedSize(n);
    List<Locale> input = Lists.newArrayList(locales);

    Comparator<Locale> comparator = new Comparator<Locale>() {
      @Override
      public int compare(Locale l1, Locale l2) {
        return getLabel(l1).compareTo(getLabel(l2));
      }
    };

    // rather than sorting the whole list, we just extract the first n
    for (int i = 0; i < locales.size() && i < n; i++) {
      Locale min = Collections.min(input, comparator);
      result.add(min);
      input.remove(min);
    }

    return result;
  }

  private static String getLabels(Iterable<Locale> locales) {
    return Joiner.on(", ").join(Iterables.transform(locales, new Function<Locale, String>() {
      @Override
      public String apply(Locale locale) {
        return getLabel(locale);
      }
    }));
  }

  private static String getLabel(@Nullable Locale locale) {
    return locale == null ? "" : LocaleMenuAction.getLocaleLabel(locale, false);
  }

  @NotNull
  public StringResource getStringResource(@NotNull String key) {
    StringResource stringResource = myKeyToResourceMap.get(key);
    if (stringResource != null) {
      return stringResource;
    }
    throw new IllegalArgumentException("No StringResource for key " + key);
  }

  @NotNull
  public Collection<StringResource> getResources() {
    return myKeyToResourceMap.values();
  }

  @NotNull
  public List<String> getKeys() {
    List<String> keys = new ArrayList<>(myKeyToResourceMap.keySet());
    Collections.sort(keys);
    return keys;
  }

  @NotNull
  public List<Locale> getLocales() {
    Set<Locale> locales = new TreeSet<>(Locale.LANGUAGE_CODE_COMPARATOR);
    for (StringResource stringResource : myKeyToResourceMap.values()) {
      locales.addAll(stringResource.getTranslatedLocales());
    }
    return new ArrayList<>(locales);
  }
}
