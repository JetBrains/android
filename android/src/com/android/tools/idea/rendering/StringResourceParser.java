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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LanguageQualifier;
import com.android.resources.ResourceType;
import com.google.common.collect.*;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StringResourceParser {
  public static StringResourceData parse(@NotNull LocalResourceRepository repository) {
    List<String> keys = Lists.newArrayList(repository.getItemsOfType(ResourceType.STRING));
    Collections.sort(keys);

    final Set<String> untranslatableKeys = Sets.newHashSet();
    // Uses a tree set to sort the locales by language code
    final Set<Locale> locales = Sets.newTreeSet(Locale.LANGUAGE_CODE_COMPARATOR);
    Map<String,String> defaultValues = Maps.newHashMapWithExpectedSize(keys.size());
    Table<String, Locale, String> translations = HashBasedTable.create();
    for (String key : keys) {
      List<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, key);
      if (items == null) {
        continue;
      }
      for (ResourceItem item : items) {
        if (item instanceof PsiResourceItem) {
          XmlTag tag = ((PsiResourceItem) item).getTag();
          if (tag != null && String.valueOf(tag.getAttributeValue((SdkConstants.ATTR_TRANSLATABLE))).equals(SdkConstants.VALUE_FALSE)) {
            untranslatableKeys.add(key);
          }
        }
        FolderConfiguration config = item.getConfiguration();
        LanguageQualifier languageQualifier = config.getLanguageQualifier();
        if (languageQualifier == null) {
          ResourceValue value = item.getResourceValue(false);
          defaultValues.put(key, value == null ? "" : value.getRawXmlValue());
        } else {
          Locale locale = Locale.create(languageQualifier, config.getRegionQualifier());
          locales.add(locale);
          ResourceValue value = item.getResourceValue(false);
          translations.put(key, locale, value == null ? "" : value.getRawXmlValue());
        }
      }
    }

    return new StringResourceData(keys, Lists.newArrayList(untranslatableKeys), Lists.newArrayList(locales), defaultValues, translations);
  }
}
