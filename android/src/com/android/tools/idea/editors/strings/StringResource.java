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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
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
  private final StringResourceKey myKey;

  @NotNull
  private final String myResourceFolder;

  private boolean myTranslatable;

  @NotNull
  private ResourceItemEntry myDefaultValue;

  @NotNull
  private final Map<Locale, ResourceItemEntry> myLocaleToTranslationMap;

  public StringResource(@NotNull StringResourceKey key, @NotNull Iterable<ResourceItem> items, @NotNull Project project) {
    boolean translatable = true;
    ResourceItemEntry defaultValue = new ResourceItemEntry();
    Map<Locale, ResourceItemEntry> localeToTranslationMap = new HashMap<>();

    for (ResourceItem item : items) {
      XmlTag tag = LocalResourceRepository.getItemTag(project, item);

      if (tag != null && "false".equals(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))) {
        translatable = false;
      }

      LocaleQualifier qualifier = item.getConfiguration().getLocaleQualifier();

      String value = toString(item);
      if (qualifier == null) {
        defaultValue = new ResourceItemEntry(item, value);
      }
      else {
        localeToTranslationMap.put(Locale.create(qualifier), new ResourceItemEntry(item, value));
      }
    }

    myKey = key;

    VirtualFile folder = key.getDirectory();
    myResourceFolder = folder == null ? "" : VirtualFiles.toString(folder, project);

    myTranslatable = translatable;
    myDefaultValue = defaultValue;
    myLocaleToTranslationMap = localeToTranslationMap;
  }

  @NotNull
  @VisibleForTesting
  static String toString(@NotNull ResourceItem item) {
    // THIS GETTER WITH SIDE EFFECTS that registers we have taken an interest in this value
    // so that if the value changes we will get a resource changed event fire.
    ResourceValue value = item.getResourceValue(false);

    return value == null ? "" : ValueXmlHelper.unescapeResourceStringAsXml(value.getRawXmlValue());
  }

  @NotNull
  StringResourceKey getKey() {
    return myKey;
  }

  @NotNull
  public String getResourceFolder() {
    return myResourceFolder;
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