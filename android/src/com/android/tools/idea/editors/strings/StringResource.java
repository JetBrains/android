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
import com.android.annotations.concurrency.UiThread;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.escape.xml.CharacterDataEscaper;
import com.android.ide.common.util.PathString;
import com.android.tools.idea.editors.strings.model.StringResourceKey;
import com.android.tools.idea.editors.strings.model.StringResourceRepository;
import com.android.tools.idea.res.DynamicValueResourceItem;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.PsiResourceItem;
import com.android.tools.idea.res.StringResourceWriter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.SameThreadExecutor;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a single entry in the translations editor.
 */
public final class StringResource {
  private static final Logger LOGGER = Logger.getInstance(StringResource.class);
  @NotNull
  private final StringResourceKey myKey;

  @NotNull
  private final StringResourceData myData;

  private boolean myTranslatable;

  /** Holds the String default value we're in the process of assigning, to prevent duplicates. */
  @Nullable
  private String myTentativeDefaultValue = null;
  @Nullable
  private ResourceItemEntry myDefaultValue;

  @NotNull
  private final Map<Locale, ResourceItemEntry> myLocaleToTranslationMap;

  private final StringResourceWriter myStringResourceWriter = StringResourceWriter.INSTANCE;

  public StringResource(@NotNull StringResourceKey key, @NotNull StringResourceData data) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    myKey = key;
    myData = data;
    boolean translatable = true;
    @Nullable ResourceItemEntry defaultValue = null;
    Map<Locale, ResourceItemEntry> localeToTranslationMap = new HashMap<>();

    for (ResourceItem item : data.getRepository().getItems(key)) {
      if (!(item instanceof PsiResourceItem || item instanceof DynamicValueResourceItem)) {
       LOGGER.warn(item + " has an unexpected class " + item.getClass().getName());
      }

      XmlTag tag = IdeResourcesUtil.getItemTag(data.getProject(), item);

      if (tag != null && "false".equals(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))) {
        translatable = false;
      }

      LocaleQualifier qualifier = item.getConfiguration().getLocaleQualifier();

      String tagText = getTextOfTag(tag);
      if (qualifier == null) {
        defaultValue = new ResourceItemEntry(item, tagText);
      }
      else {
        localeToTranslationMap.put(Locale.create(qualifier), new ResourceItemEntry(item, tagText));
      }
    }

    myTranslatable = translatable;
    myDefaultValue = defaultValue;
    myLocaleToTranslationMap = localeToTranslationMap;
  }

  @NotNull
  String getTagText(@Nullable Locale locale) {
    @Nullable ResourceItemEntry resourceItemEntry = myDefaultValue;
    if (locale != null) {
      resourceItemEntry = myLocaleToTranslationMap.get(locale);
    }
    return resourceItemEntry == null ? "" : resourceItemEntry.myTagText;
  }

  @Nullable
  ResourceItem getDefaultValueAsResourceItem() {
    return myDefaultValue == null ? null : myDefaultValue.myResourceItem;
  }

  @NotNull
  public String getDefaultValueAsString() {
    return myDefaultValue == null ? "" : myDefaultValue.myString;
  }

  @UiThread
  @NotNull
  public ListenableFuture<@NotNull Boolean> setDefaultValue(@NotNull String defaultValue) {
    if (myDefaultValue == null) {
      if (defaultValue.equals(myTentativeDefaultValue)) {
        return Futures.immediateFuture(false);
      }
      myTentativeDefaultValue = defaultValue;
      ListenableFuture<ResourceItem> futureItem = createDefaultValue(defaultValue);
      return Futures.transform(futureItem, item -> {
        myTentativeDefaultValue = null;
        if (item == null) {
          return false;
        }

        myDefaultValue = new ResourceItemEntry(item, getTextOfTag(IdeResourcesUtil.getItemTag(myData.getProject(), item)));
        return true;
      }, SameThreadExecutor.INSTANCE);
    }

    if (myDefaultValue.myString.equals(defaultValue)) {
      return Futures.immediateFuture(false);
    }

    boolean changed = myStringResourceWriter.setItemText(myData.getProject(), myDefaultValue.myResourceItem, defaultValue);

    if (!changed) {
      return Futures.immediateFuture(false);
    }

    if (defaultValue.isEmpty()) {
      myDefaultValue = null;
      return Futures.immediateFuture(true);
    }

    ResourceItem item = myData.getRepository().getDefaultValue(myKey);
    assert item != null;

    myDefaultValue = new ResourceItemEntry(item, getTextOfTag(IdeResourcesUtil.getItemTag(myData.getProject(), item)));
    return Futures.immediateFuture(true);
  }

  private @NotNull ListenableFuture<@Nullable ResourceItem> createDefaultValue(@NotNull String value) {
    if (value.isEmpty()) {
      return Futures.immediateFuture(null);
    }

    Project project = myData.getProject();

    StringResourceWriter.INSTANCE.addDefault(project, myKey, value, myTranslatable);

    SettableFuture<ResourceItem> futureItem = SettableFuture.create();
    StringResourceRepository stringRepository = myData.getRepository();
    stringRepository.invokeAfterPendingUpdatesFinish(myKey, () -> futureItem.set(stringRepository.getDefaultValue(myKey)));
    return futureItem;
  }

  @Nullable
  public String validateDefaultValue() {
    if (myDefaultValue == null) {
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
      List<StringResourceKey> keys = myData.getKeys();
      int index = keys.indexOf(myKey);
      StringResourceKey anchor = null;
      if (index != -1) {
        // This translation exists in default translation. Find the anchor
        while (++index < keys.size()) {
          StringResourceKey next = keys.get(index);
          StringResource nextResource = myData.getStringResource(next);
          // If we're into another file already, we're not going to find the anchor here.
          if (!hasSameDefaultValueFile(nextResource)) {
            break;
          }
          // Check if this resource exists in the given Locale file.
          if (nextResource.getTranslationAsResourceItem(locale) != null) {
            anchor = next;
            break;
          }
        }
      }

      return Futures.transform(createTranslationBefore(locale, translation, anchor), item -> {
        if (item == null) {
          return false;
        }

        myLocaleToTranslationMap
          .put(locale, new ResourceItemEntry(item, getTextOfTag(IdeResourcesUtil.getItemTag(myData.getProject(), item))));
        return true;
      }, SameThreadExecutor.INSTANCE);
    }

    if (getTranslationAsString(locale).equals(translation)) {
      return Futures.immediateFuture(false);
    }

    ResourceItem item = getTranslationAsResourceItem(locale);
    assert item != null;

    boolean changed = myStringResourceWriter.setItemText(myData.getProject(), item, translation);

    if (!changed) {
      return Futures.immediateFuture(false);
    }

    if (translation.isEmpty()) {
      myLocaleToTranslationMap.remove(locale);
      return Futures.immediateFuture(true);
    }

    item = myData.getRepository().getTranslation(myKey, locale);
    assert item != null;

    myLocaleToTranslationMap
      .put(locale, new ResourceItemEntry(item, getTextOfTag(IdeResourcesUtil.getItemTag(myData.getProject(), item))));
    return Futures.immediateFuture(true);
  }

  private @NotNull ListenableFuture<@Nullable ResourceItem> createTranslationBefore(@NotNull Locale locale, @NotNull String value,
                                                                                    @Nullable StringResourceKey anchor) {
    if (value.isEmpty()) {
      return Futures.immediateFuture(null);
    }

    VirtualFile resourceDirectory = myKey.getDirectory();
    if (resourceDirectory == null) {
      return Futures.immediateFuture(null);
    }

    Project project = myData.getProject();
    // If there is only one file that all translations of string resources are in, get that file.
    @Nullable XmlFile file = myData.getDefaultLocaleXml(locale);
    if (file != null) {
      StringResourceWriter.INSTANCE.addTranslationToFile(project, file, myKey, value, anchor);
    }
    else {
      StringResourceWriter.INSTANCE.addTranslation(project, myKey, value, locale, getDefaultValueFileName(), anchor);
    }

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
      return "Key \"" + myKey.getName() + "\" is missing its " + Locale.getLocaleLabel(locale, false) + " translation";
    }
    else if (!myTranslatable && !isTranslationMissing(locale)) {
      return "Key \"" + myKey.getName() + "\" is untranslatable and should not be translated to " +
             Locale.getLocaleLabel(locale, false);
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

  private boolean hasSameDefaultValueFile(StringResource other) {
    return Objects.equals(getDefaultValueFileName(), other.getDefaultValueFileName());
  }

  @NotNull
  private String getDefaultValueFileName() {
    ResourceItem resourceItem = getDefaultValueAsResourceItem();
    if (resourceItem != null) {
      PathString pathString = resourceItem.getOriginalSource();
      if (pathString != null) {
        String fileName = pathString.getFileName();
        assert !fileName.isEmpty(); // Only empty if pathString is a file system root.
        return fileName;
      }
    }
    return IdeResourcesUtil.DEFAULT_STRING_RESOURCE_FILE_NAME;
  }

  private static boolean isTranslationMissing(@Nullable ResourceItemEntry item) {
    return item == null || item.myString.isEmpty();
  }

  private static @NotNull String getTextOfTag(@Nullable XmlTag tag) {
    return tag == null ? "" : tag.getText();
  }

  private static final class ResourceItemEntry {
    @NotNull
    private final ResourceItem myResourceItem;

    @NotNull
    private final String myTagText;

    @NotNull
    private final String myString;

    private final boolean myStringValid;

    private ResourceItemEntry(@NotNull ResourceItem resourceItem, @NotNull String tagText) {
      myResourceItem = resourceItem;
      myTagText = tagText;
      ResourceValue value = resourceItem.getResourceValue();

      if (value == null) {
        myString = "";
        myStringValid = true;

        return;
      }

      String string = value.getRawXmlValue();
      assert string != null;

      boolean stringValid = true;

      try {
        string = CharacterDataEscaper.unescape(string);
      }
      catch (IllegalArgumentException exception) {
        stringValid = false;
      }

      myString = string;
      myStringValid = stringValid;
    }
  }
}
