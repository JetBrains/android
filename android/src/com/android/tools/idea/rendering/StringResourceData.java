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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class StringResourceData {
  // The string names
  private final List<String> myKeys;
  // A key is present in this set if and only if it is tagged translatable = false
  private final List<String> myUntranslatableKeys;
  // The locales for which a translation of at least one string exists
  private final List<Locale> myLocales;
  // Map from string names to default values.  Does not contain entries for strings without default values.
  private final Map<String, ResourceItem> myDefaultValues;
  // Map from (string name, locale) pairs to translations.  Does not contain entries for missing translations.
  private final Table<String, Locale, ResourceItem> myTranslations;

  public StringResourceData(@NotNull List<String> keys, @NotNull List<String> untranslatableKeys, @NotNull List<Locale> locales,
                            @NotNull Map<String, ResourceItem> defaultValues, @NotNull Table<String, Locale, ResourceItem> translations) {
    myKeys = ImmutableList.copyOf(keys);
    myUntranslatableKeys = ImmutableList.copyOf(untranslatableKeys);
    myLocales = ImmutableList.copyOf(locales);
    myDefaultValues = ImmutableMap.copyOf(defaultValues);
    myTranslations = ImmutableTable.copyOf(translations);
  }

  @NotNull
  public List<String> getKeys() {
    return myKeys;
  }

  @NotNull
  public List<String> getUntranslatableKeys() {
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
    ResourceValue value = item.getResourceValue(false);
    return value == null ? "" : value.getRawXmlValue().trim();
  }

  @Nullable
  static XmlTag resourceToXmlTag(@NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      XmlTag tag = ((PsiResourceItem) item).getTag();
      return tag != null && tag.isValid() ? tag : null;
    }
    return null;
  }
}
