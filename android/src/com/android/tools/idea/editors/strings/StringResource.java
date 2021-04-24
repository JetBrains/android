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
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.StringResourceUnescaper;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.DynamicValueResourceItem;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.PsiResourceItem;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.SameThreadExecutor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  @NotNull
  private final StringResourceData myData;

  public StringResource(@NotNull StringResourceKey key, @NotNull StringResourceData data) {
    Project project = data.getProject();
    boolean translatable = true;
    ResourceItemEntry defaultValue = new ResourceItemEntry();
    StringResourceUnescaper unescaper = data.getUnescaper();
    Map<Locale, ResourceItemEntry> localeToTranslationMap = new HashMap<>();

    for (ResourceItem item : data.getRepository().getItems(key)) {
      if (!(item instanceof PsiResourceItem || item instanceof DynamicValueResourceItem)) {
        Logger.getInstance(StringResource.class).warn(item + " has an unexpected class " + item.getClass().getName());
      }

      XmlTag tag = IdeResourcesUtil.getItemTag(project, item);

      if (tag != null && "false".equals(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))) {
        translatable = false;
      }

      LocaleQualifier qualifier = item.getConfiguration().getLocaleQualifier();

      if (qualifier == null) {
        defaultValue = new ResourceItemEntry(item, unescaper);
      }
      else {
        localeToTranslationMap.put(Locale.create(qualifier), new ResourceItemEntry(item, unescaper));
      }
    }

    myKey = key;

    VirtualFile folder = key.getDirectory();
    myResourceFolder = folder == null ? "" : VirtualFiles.toString(folder, project);

    myTranslatable = translatable;
    myDefaultValue = defaultValue;
    myLocaleToTranslationMap = localeToTranslationMap;
    myData = data;
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

  public @NotNull ListenableFuture<@NotNull Boolean> setDefaultValue(@NotNull String defaultValue) {
    if (myDefaultValue.myResourceItem == null) {
      ListenableFuture<ResourceItem> futureItem = createDefaultValue(defaultValue);
      return Futures.transform(futureItem, item -> {
        if (item == null) {
          return false;
        }

        myDefaultValue = new ResourceItemEntry(item, myData.getUnescaper());
        return true;
      }, SameThreadExecutor.INSTANCE);
    }

    if (myDefaultValue.myString.equals(defaultValue)) {
      return Futures.immediateFuture(false);
    }

    boolean changed = StringsWriteUtils.setItemText(myData.getProject(), myDefaultValue.myResourceItem, defaultValue);

    if (!changed) {
      return Futures.immediateFuture(false);
    }

    if (defaultValue.isEmpty()) {
      myDefaultValue = new ResourceItemEntry();
      return Futures.immediateFuture(true);
    }

    ResourceItem item = myData.getRepository().getDefaultValue(myKey);
    assert item != null;

    myDefaultValue = new ResourceItemEntry(item, myData.getUnescaper());
    return Futures.immediateFuture(true);
  }

  private @NotNull ListenableFuture<@Nullable ResourceItem> createDefaultValue(@NotNull String value) {
    if (value.isEmpty()) {
      return Futures.immediateFuture(null);
    }

    Project project = myData.getProject();
    XmlFile file = StringPsiUtils.getDefaultStringResourceFile(project, myKey);

    if (file == null) {
      return Futures.immediateFuture(null);
    }

    WriteCommandAction.runWriteCommandAction(project, null, null, () -> StringPsiUtils.addString(file, myKey, myTranslatable, value));

    SettableFuture<ResourceItem> futureItem = SettableFuture.create();
    StringResourceRepository stringRepository = myData.getRepository();
    stringRepository.invokeAfterPendingUpdatesFinish(myKey, () -> futureItem.set(stringRepository.getDefaultValue(myKey)));
    return futureItem;
  }

  @Nullable
  public String validateDefaultValue() {
    if (myDefaultValue.myResourceItem == null) {
      return "Key \"" + myKey.getName() + "\" is missing its default value";
    }

    if (!myDefaultValue.myStringValid) {
      return "Invalid XML";
    }

    return null;
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

  public @NotNull ListenableFuture<@NotNull Boolean> putTranslation(@NotNull final Locale locale, @NotNull String translation) {
    if (getTranslationAsResourceItem(locale) == null) {
      return Futures.transform(createTranslation(locale, translation), item -> {
        if (item == null) {
          return false;
        }

        myLocaleToTranslationMap.put(locale, new ResourceItemEntry(item, myData.getUnescaper()));
        return true;
      }, SameThreadExecutor.INSTANCE);
    }

    if (getTranslationAsString(locale).equals(translation)) {
      return Futures.immediateFuture(false);
    }

    ResourceItem item = getTranslationAsResourceItem(locale);
    assert item != null;

    boolean changed = StringsWriteUtils.setItemText(myData.getProject(), item, translation);

    if (!changed) {
      return Futures.immediateFuture(false);
    }

    if (translation.isEmpty()) {
      myLocaleToTranslationMap.remove(locale);
      return Futures.immediateFuture(true);
    }

    item = myData.getRepository().getTranslation(myKey, locale);
    assert item != null;

    myLocaleToTranslationMap.put(locale, new ResourceItemEntry(item, myData.getUnescaper()));
    return Futures.immediateFuture(true);
  }

  private @NotNull ListenableFuture<@Nullable ResourceItem> createTranslation(@NotNull Locale locale, @NotNull String value) {
    if (value.isEmpty()) {
      return Futures.immediateFuture(null);
    }

    Project project = myData.getProject();
    XmlFile file = myData.getDefaultLocaleXml(locale);

    if (file == null) {
      file = StringPsiUtils.getStringResourceFile(project, myKey, locale);
      if (file == null) {
        return Futures.immediateFuture(null);
      }
    }

    XmlFile finalFile = file;
    WriteCommandAction.runWriteCommandAction(project, null, null, () -> StringPsiUtils.addString(finalFile, myKey, myTranslatable, value));

    SettableFuture<ResourceItem> futureItem = SettableFuture.create();
    StringResourceRepository stringRepository = myData.getRepository();
    stringRepository.invokeAfterPendingUpdatesFinish(myKey, () -> futureItem.set(stringRepository.getTranslation(myKey, locale)));
    return futureItem;
  }

  @Nullable
  public String validateTranslation(@NotNull Locale locale) {
    ResourceItemEntry entry = myLocaleToTranslationMap.get(locale);

    if (entry != null && !entry.myStringValid) {
      return "Invalid XML";
    }

    if (myTranslatable && isTranslationMissing(locale)) {
      return "Key \"" + myKey.getName() + "\" is missing its " + LocaleMenuAction.getLocaleLabel(locale, false) + " translation";
    }
    else if (!myTranslatable && !isTranslationMissing(locale)) {
      return "Key \"" + myKey.getName() + "\" is untranslatable and should not be translated to " +
             LocaleMenuAction.getLocaleLabel(locale, false);
    }
    else {
      return null;
    }
  }

  @NotNull
  Collection<Locale> getTranslatedLocales() {
    return myLocaleToTranslationMap.keySet();
  }

  boolean isTranslationMissing(@NotNull Locale locale) {
    ResourceItemEntry item = myLocaleToTranslationMap.get(locale);

    if (isTranslationMissing(item) && locale.hasRegion()) {
      String language = locale.qualifier.getLanguage();
      assert language != null; // qualifiers from Locale objects have the language set.
      locale = Locale.create(language);
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

    private final boolean myStringValid;

    private ResourceItemEntry() {
      myResourceItem = null;
      myString = "";
      myStringValid = true;
    }

    private ResourceItemEntry(@NotNull ResourceItem resourceItem, @NotNull StringResourceUnescaper unescaper) {
      myResourceItem = resourceItem;
      ResourceValue value = resourceItem.getResourceValue();

      if (value == null) {
        myString = "";
        myStringValid = true;

        return;
      }

      String string = value.getRawXmlValue();
      assert string != null;

      boolean stringValid;

      try {
        string = unescaper.unescapeCharacterData(string);
        stringValid = true;
      }
      catch (IllegalArgumentException exception) {
        stringValid = false;
      }

      myString = string;
      myStringValid = stringValid;
    }
  }
}
