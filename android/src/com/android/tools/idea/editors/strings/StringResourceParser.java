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
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.resources.ResourceType;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.rendering.Locale;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  @NotNull
  private static StringResourceData parseUnderReadLock(AndroidFacet facet, LocalResourceRepository repository) {
    List<String> keys = Lists.newArrayList(repository.getItemsOfType(ResourceType.STRING));
    Collections.sort(keys);

    Map<String, StringResource> keyToResourceMap = new HashMap<>();

    Project project = facet.getModule().getProject();
    for (String key : keys) {
      List<ResourceItem> items = repository.getResourceItem(ResourceType.STRING, key);
      if (items == null) {
        continue;
      }

      StringResource stringResource = new StringResource(key);
      for (ResourceItem item : items) {
        XmlTag tag = LocalResourceRepository.getItemTag(project, item);
        if (tag != null && SdkConstants.VALUE_FALSE.equals(tag.getAttributeValue(SdkConstants.ATTR_TRANSLATABLE))) {
          stringResource.setTranslatable(false);
        }

        String itemStringRepresentation = resourceToString(project, item);
        FolderConfiguration config = item.getConfiguration();
        LocaleQualifier qualifier = config == null ? null : config.getLocaleQualifier();
        if (qualifier == null) {
          stringResource.setDefaultValue(item, itemStringRepresentation);
        }
        else {
          Locale locale = Locale.create(qualifier);
          stringResource.putTranslation(locale, item, itemStringRepresentation);
        }
      }

      keyToResourceMap.put(key, stringResource);
    }

    return new StringResourceData(facet, keyToResourceMap);
  }

  @NotNull
  private static String resourceToString(@NotNull Project project, @NotNull ResourceItem item) {
    XmlTag tag = LocalResourceRepository.getItemTag(project, item);
    String string;

    if (tag == null) {
      // TODO Make item.getResourceValue(false).getRawXmlValue() work in all cases so we can avoid the LocalResourceRepository
      ResourceValue value = item.getResourceValue(false);

      if (value == null) {
        return "";
      }

      string = value.getRawXmlValue();
    }
    else {
      string = tag.getValue().getText();
    }

    return Strings.nullToEmpty(ValueXmlHelper.unescapeResourceString(string, false, false));
  }
}