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
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.PsiResourceItem;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StringResourceParser {
  public static StringResourceData parse(@NotNull final AndroidFacet facet, @NotNull final LocalResourceRepository repository) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      return parseUnderReadLock(facet, repository);
    } else {
      return ApplicationManager.getApplication().runReadAction(new Computable<StringResourceData>() {
        @Override
        public StringResourceData compute() {
          return parseUnderReadLock(facet, repository);
        }
      });
    }
  }

  private static StringResourceData parseUnderReadLock(AndroidFacet facet, LocalResourceRepository repository) {
    List<String> keys = Lists.newArrayList(repository.getItemsOfType(ResourceType.STRING));
    Collections.sort(keys);

    final Set<String> untranslatableKeys = Sets.newHashSet();
    final Set<Locale> locales = Sets.newTreeSet(Locale.LANGUAGE_CODE_COMPARATOR); // tree set to sort the locales by language code
    Map<String, ResourceItem> defaultValues = Maps.newHashMapWithExpectedSize(keys.size());
    Table<String, Locale, ResourceItem> translations = HashBasedTable.create();

    for (String key : keys) {
      List<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, key);
      if (items == null) {
        continue;
      }

      for (ResourceItem item : items) {
        if (item instanceof PsiResourceItem) {
          XmlTag tag = ((PsiResourceItem)item).getTag();
          if (tag != null && SdkConstants.VALUE_FALSE.equals(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))) {
            untranslatableKeys.add(key);
          }
        }

        FolderConfiguration config = item.getConfiguration();
        LocaleQualifier qualifier = config == null ? null : config.getLocaleQualifier();
        if (qualifier == null) {
          defaultValues.put(key, item);
        }
        else {
          Locale locale = Locale.create(qualifier);
          locales.add(locale);
          translations.put(key, locale, item);
        }
      }
    }

    return new StringResourceData(facet, keys, untranslatableKeys, locales, defaultValues, translations);
  }
}
