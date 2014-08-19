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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.PsiResourceItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class StringResourceData {
  private final List<String> myKeys;
  private final List<String> myUntranslatableKeys;
  private final List<Locale> myLocales;
  private final Map<String, ResourceItem> myDefaultValues;
  private final Table<String, Locale, ResourceItem> myTranslations;

  public StringResourceData(@NotNull List<String> keys,
                            @NotNull Collection<String> untranslatableKeys,
                            @NotNull Collection<Locale> locales,
                            @NotNull Map<String, ResourceItem> defaultValues,
                            @NotNull Table<String, Locale, ResourceItem> translations) {
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
  public static XmlTag resourceToXmlTag(@NotNull ResourceItem item) {
    if (item instanceof PsiResourceItem) {
      XmlTag tag = ((PsiResourceItem) item).getTag();
      return tag != null && tag.isValid() ? tag : null;
    }
    return null;
  }
}
