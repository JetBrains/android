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
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.tools.idea.configurations.LocaleMenuAction;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.LocalResourceRepository;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
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

  @NotNull
  private final StringResourceRepository myRepository;

  @NotNull
  private final Project myProject;

  public StringResource(@NotNull StringResourceKey key, @NotNull StringResourceRepository repository, @NotNull Project project) {
    boolean translatable = true;
    ResourceItemEntry defaultValue = new ResourceItemEntry();
    Map<Locale, ResourceItemEntry> localeToTranslationMap = new HashMap<>();

    for (ResourceItem item : repository.getItems(key)) {
      XmlTag tag = LocalResourceRepository.getItemTag(project, item);

      if (tag != null && "false".equals(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))) {
        translatable = false;
      }

      LocaleQualifier qualifier = item.getConfiguration().getLocaleQualifier();

      if (qualifier == null) {
        defaultValue = new ResourceItemEntry(item);
      }
      else {
        localeToTranslationMap.put(Locale.create(qualifier), new ResourceItemEntry(item));
      }
    }

    myKey = key;

    VirtualFile folder = key.getDirectory();
    myResourceFolder = folder == null ? "" : VirtualFiles.toString(folder, project);

    myTranslatable = translatable;
    myDefaultValue = defaultValue;
    myLocaleToTranslationMap = localeToTranslationMap;
    myRepository = repository;
    myProject = project;
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

  public boolean setDefaultValue(@NotNull String defaultValue) {
    if (myDefaultValue.myResourceItem == null) {
      ResourceItem item = createDefaultValue(defaultValue);

      if (item == null) {
        return false;
      }

      myDefaultValue = new ResourceItemEntry(item);
      return true;
    }

    if (myDefaultValue.myString.equals(defaultValue)) {
      return false;
    }

    boolean changed = StringsWriteUtils.setItemText(myProject, myDefaultValue.myResourceItem, defaultValue);

    if (!changed) {
      return false;
    }

    if (defaultValue.isEmpty()) {
      myDefaultValue = new ResourceItemEntry();
      return true;
    }

    ResourceItem item = myRepository.getDefaultValue(myKey);
    assert item != null;

    myDefaultValue = new ResourceItemEntry(item);
    return true;
  }

  @Nullable
  private ResourceItem createDefaultValue(@NotNull String value) {
    if (value.isEmpty()) {
      return null;
    }

    XmlFile file = StringPsiUtils.getDefaultStringResourceFile(myProject, myKey);

    if (file == null) {
      return null;
    }

    WriteCommandAction.runWriteCommandAction(myProject, () -> StringPsiUtils.addString(file, myKey, myTranslatable, value));
    return myRepository.getDefaultValue(myKey);
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

  public boolean putTranslation(@NotNull Locale locale, @NotNull String translation) {
    if (getTranslationAsResourceItem(locale) == null) {
      ResourceItem item = createTranslation(locale, translation);

      if (item == null) {
        return false;
      }

      myLocaleToTranslationMap.put(locale, new ResourceItemEntry(item));
      return true;
    }

    if (getTranslationAsString(locale).equals(translation)) {
      return false;
    }

    ResourceItem item = getTranslationAsResourceItem(locale);
    assert item != null;

    boolean changed = StringsWriteUtils.setItemText(myProject, item, translation);

    if (!changed) {
      return false;
    }

    if (translation.isEmpty()) {
      myLocaleToTranslationMap.remove(locale);
      return true;
    }

    item = myRepository.getTranslation(myKey, locale);
    assert item != null;

    myLocaleToTranslationMap.put(locale, new ResourceItemEntry(item));
    return true;
  }

  @Nullable
  private ResourceItem createTranslation(@NotNull Locale locale, @NotNull String value) {
    if (value.isEmpty()) {
      return null;
    }

    XmlFile file = StringPsiUtils.getStringResourceFile(myProject, myKey, locale);

    if (file == null) {
      return null;
    }

    WriteCommandAction.runWriteCommandAction(myProject, () -> StringPsiUtils.addString(file, myKey, myTranslatable, value));
    return myRepository.getTranslation(myKey, locale);
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

    private final boolean myStringValid;

    public ResourceItemEntry() {
      myResourceItem = null;
      myString = "";
      myStringValid = true;
    }

    private ResourceItemEntry(@NotNull ResourceItem resourceItem) {
      myResourceItem = resourceItem;
      ResourceValue value = resourceItem.getResourceValue(false);

      if (value == null) {
        myString = "";
        myStringValid = true;

        return;
      }

      String string = value.getRawXmlValue();
      boolean stringValid;

      try {
        string = ValueXmlHelper.unescapeResourceStringAsXml(string);
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
