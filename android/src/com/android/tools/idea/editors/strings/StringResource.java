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

import com.android.tools.idea.rendering.Locale;
import com.android.ide.common.res2.ResourceItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single entry in the translations editor.
 */
public final class StringResource {
  @NotNull
  private String myKey;
  @NotNull
  private ResourceItemEntry myDefaultValue;
  private boolean myTranslatable = true;
  @NotNull
  private final Map<Locale, ResourceItemEntry> myLocaleToTranslationMap = new HashMap<>();

  public StringResource(@NotNull String key) {
    myKey = key;
    myDefaultValue = new ResourceItemEntry();
  }

  @NotNull
  String getKey() {
    return myKey;
  }

  void setKey(@NotNull String key) {
    myKey = key;
  }

  @Nullable
  ResourceItem getDefaultValueAsResourceItem() {
    return myDefaultValue.myResourceItem;
  }

  @NotNull
  public String getDefaultValueAsString() {
    return myDefaultValue.myString;
  }

  void setDefaultValue(@NotNull ResourceItem resourceItem, @NotNull String string) {
    myDefaultValue = new ResourceItemEntry(resourceItem, string);
  }

  void removeDefaultValue() {
    myDefaultValue = new ResourceItemEntry();
  }

  public boolean isTranslatable() {
    return myTranslatable;
  }

  public void setTranslatable(boolean translatable) {
    myTranslatable = translatable;
  }

  @Nullable
  ResourceItem getTranslationAsResourceItem(@NotNull Locale locale) {
    ResourceItemEntry resourceItemEntry = myLocaleToTranslationMap.get(locale);
    return resourceItemEntry == null ? null : resourceItemEntry.myResourceItem;
  }

  @NotNull
  public String getTranslationAsString(@NotNull Locale locale) {
    ResourceItemEntry resourceItemEntry = myLocaleToTranslationMap.get(locale);
    return resourceItemEntry == null ? "" : resourceItemEntry.myString;
  }

  void putTranslation(@NotNull Locale locale, @NotNull ResourceItem resourceItem, @NotNull String string) {
    myLocaleToTranslationMap.put(locale, new ResourceItemEntry(resourceItem, string));
  }

  void removeTranslation(@NotNull Locale locale) {
    myLocaleToTranslationMap.remove(locale);
  }

  @NotNull
  Collection<Locale> getTranslatedLocales() {
    return myLocaleToTranslationMap.keySet();
  }

  boolean isTranslationMissing(@NotNull Locale locale) {
    ResourceItemEntry item = myLocaleToTranslationMap.get(locale);

    if (isTranslationMissing(item) && locale.hasRegion()) {
      locale = Locale.create(locale.qualifier.getLanguage());
      item = myLocaleToTranslationMap.get(locale);
    }

    return isTranslationMissing(item);
  }

  private static boolean isTranslationMissing(@Nullable ResourceItemEntry item) {
    return item == null || item.myString.isEmpty();
  }

  private static final class ResourceItemEntry {
    @Nullable
    private final ResourceItem myResourceItem;
    @NotNull
    private final String myString;

    public ResourceItemEntry() {
      myResourceItem = null;
      myString = "";
    }

    private ResourceItemEntry(@NotNull ResourceItem resourceItem, @NotNull String string) {
      myResourceItem = resourceItem;
      myString = string;
    }
  }
}