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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.PsiResourceItem;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class StringResourceData {
  private final AndroidFacet myFacet;
  private final List<String> myKeys;
  private final Set<String> myUntranslatableKeys;
  private final List<Locale> myLocales;
  private final Map<String, ResourceItem> myDefaultValues;
  private final HashBasedTable<String, Locale, ResourceItem> myTranslations;

  public StringResourceData(@NotNull AndroidFacet facet,
                            @NotNull List<String> keys,
                            @NotNull Collection<String> untranslatableKeys,
                            @NotNull Collection<Locale> locales,
                            @NotNull Map<String, ResourceItem> defaultValues,
                            @NotNull Table<String, Locale, ResourceItem> translations) {
    myFacet = facet;
    myKeys = Lists.newArrayList(keys);
    myUntranslatableKeys = Sets.newHashSet(untranslatableKeys);
    myLocales = Lists.newArrayList(locales);
    myDefaultValues = Maps.newHashMap(defaultValues);
    myTranslations = HashBasedTable.create(translations);
  }

  @NotNull
  public List<String> getKeys() {
    return myKeys;
  }

  @NotNull
  public Set<String> getUntranslatableKeys() {
    return myUntranslatableKeys;
  }

  @NotNull
  public List<Locale> getLocales() {
    return myLocales;
  }

  @NotNull
  public Map<String, ResourceItem> getDefaultValues() {
    return myDefaultValues;
  }

  @NotNull
  public Table<String, Locale, ResourceItem> getTranslations() {
    return myTranslations;
  }

  @NotNull
  public static String resourceToString(@NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      XmlTag tag = ((PsiResourceItem)item).getTag();
      return tag == null ? "" : XmlTagUtils.unescape(tag).trim();
    }
    else {
      // TODO This is a hack to prevent ClassCastExceptions from ResourceItems that aren't PsiResourceItems (like resources defined in
      // Gradle files). This disables apostrophe unescaping for those resources and reverts to the old behavior. Undo this hack when the
      // final escaping solution is in place.
      ResourceValue value = item.getResourceValue(false);
      return value == null ? "" : value.getRawXmlValue().trim();
    }
  }

  @Nullable
  public static XmlTag resourceToXmlTag(@NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      XmlTag tag = ((PsiResourceItem)item).getTag();
      return tag != null && tag.isValid() ? tag : null;
    }
    return null;
  }

  public void changeKeyName(int index, String name) {
    if (index >= myKeys.size()) {
      throw new IllegalArgumentException(String.format("Cannot change key at index %1$d (# of entries: %2$d)", index, myKeys.size()));
    }

    if (myKeys.contains(name)) {
      throw new IllegalArgumentException("Key " + name + " already exists.");
    }

    String currentName = myKeys.get(index);
    ResourceItem defaultValue = myDefaultValues.get(currentName);
    Map<Locale, ResourceItem> translations = myTranslations.row(currentName);

    myKeys.remove(index);
    myKeys.add(name);
    Collections.sort(myKeys);

    if (defaultValue != null) {
      myDefaultValues.remove(currentName);
      myDefaultValues.put(name, defaultValue);
    }

    if (!translations.isEmpty()) {
      // TODO: can this be done? wouldn't this have to be re-read since the ResourceItems might be different?
      // TODO: Is this whole thing better done as a refactoring operation?
      myTranslations.row(name).putAll(translations);
      translations.clear();
    }
  }

  public boolean setDoNotTranslate(String key, boolean doNotTranslate) {
    ResourceItem item = myDefaultValues.get(key);
    if (item != null) {
      String translatable;
      if (doNotTranslate) {
        translatable = SdkConstants.VALUE_FALSE;
        myUntranslatableKeys.add(key);
      }
      else {
        translatable = null;
        myUntranslatableKeys.remove(key);
      }
      return StringsWriteUtils.setAttributeForItems(myFacet.getModule().getProject(), SdkConstants.ATTR_TRANSLATABLE, translatable,
                                                    Collections.singletonList(item));
    }
    return false;
  }

  public boolean setTranslation(@NotNull String key, @Nullable Locale locale, @NotNull String value) {
    ResourceItem currentItem = locale == null ? myDefaultValues.get(key) : myTranslations.get(key, locale);
    if (currentItem != null) { // modify existing item
      String oldText = resourceToString(currentItem);
      if (!StringUtil.equals(oldText, value)) {
        boolean changed = StringsWriteUtils.setItemText(myFacet.getModule().getProject(), currentItem, value);
        if (changed && value.isEmpty()) {
          if (locale == null) {
            myDefaultValues.remove(key);
          }
          else {
            myTranslations.remove(key, locale);
          }
        }
        return changed;
      }
    }
    else { // create new item
      @SuppressWarnings("deprecation") VirtualFile primaryResourceDir = myFacet.getPrimaryResourceDir();
      assert primaryResourceDir != null;

      ResourceItem item =
        StringsWriteUtils.createItem(myFacet, primaryResourceDir, locale, key, value, !getUntranslatableKeys().contains(key));
      if (item != null) {
        if (locale == null) {
          myDefaultValues.put(key, item);
        }
        else {
          myTranslations.put(key, locale, item);
        }
        return true;
      }
      return false;
    }
    return false;
  }

  @Nullable
  public String validateKey(@NotNull String key) {
    if (!myKeys.contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    Map<Locale, ResourceItem> translationsForKey = myTranslations.row(key);
    if (myUntranslatableKeys.contains(key)) {
      if (!translationsForKey.isEmpty()) {
        Set<Locale> localesWithTranslation = translationsForKey.keySet();
        return String.format("Key '%1$s' is marked as non translatable, but is translated in %2$s %3$s", key,
                             StringUtil.pluralize("locale", localesWithTranslation.size()), summarizeLocales(localesWithTranslation));
      }
    }
    else { // translatable key
      if (myDefaultValues.get(key) == null) {
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
    if (!myKeys.contains(key)) {
      throw new IllegalArgumentException("Key " + key + " does not exist.");
    }

    if (locale == null) {
      ResourceItem item = myDefaultValues.get(key);
      return (item == null) ? String.format("Key '%1$s' is missing the default value", key) : null;
    }

    final boolean translationMissing = isTranslationMissing(key, locale);
    final boolean doNotTranslate = myUntranslatableKeys.contains(key);
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
    for (Locale locale : myLocales) {
      if (isTranslationMissing(key, locale)) {
        missingTranslations.add(locale);
      }
    }

    return missingTranslations;
  }

  @VisibleForTesting
  boolean isTranslationMissing(@NotNull String key, @NotNull Locale locale) {
    ResourceItem item = myTranslations.get(key, locale);
    if (isTranslationMissing(item) && locale.hasRegion()) {
      locale = Locale.create(locale.qualifier.getLanguage());
      item = myTranslations.get(key, locale);
    }

    return isTranslationMissing(item);
  }

  private static boolean isTranslationMissing(@Nullable ResourceItem item) {
    return item == null || resourceToString(item).isEmpty();
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
}
