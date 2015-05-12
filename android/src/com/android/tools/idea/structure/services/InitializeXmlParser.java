/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.structure.services;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.List;

import static com.android.tools.idea.templates.parse.SaxUtils.requireAttr;

/**
 * Handles parsing an initialize.xml file. An initialize file specifies a bunch of instructions
 * which search a project for values which, if found, indicate that a service is already
 * installed.
 */
/* package */ final class InitializeXmlParser extends DefaultHandler {
  private static final Logger LOG = Logger.getInstance(InitializeXmlParser.class);

  public static final String INITIALIZE_TAG = "initialize";
  public static final String RESOURCE_TAG = "resource";
  public static final String RESOURCE_ATTR_NAME = "name";
  public static final String RESOURCE_ATTR_VAR = "var";

  @NotNull private final Module myModule;
  @NotNull private final ServiceContext myContext;

  public InitializeXmlParser(@NotNull Module module, @NotNull ServiceContext serviceContext) {
    myModule = module;
    myContext = serviceContext;
  }

  @Override
  public void startElement(String uri, String localName, @NotNull String tagName, @NotNull Attributes attributes) throws SAXException {
    if (tagName.equals(RESOURCE_TAG)) {
      String resourceUrl = requireAttr(tagName, attributes, RESOURCE_ATTR_NAME);
      String targetVar = requireAttr(tagName, attributes, RESOURCE_ATTR_VAR);

      AppResourceRepository appResources = AppResourceRepository.getAppResources(myModule, true);
      assert appResources != null;
      ResourceUrl url = ResourceUrl.parse(resourceUrl);

      boolean resourceFound = false;
      if (url != null) {
        List<ResourceItem> resourceItem = appResources.getResourceItem(url.type, url.name);
        if (resourceItem != null && !resourceItem.isEmpty()) {
          ResourceValue resValue = resourceItem.get(0).getResourceValue(url.framework);
          if (resValue != null) {
            initializeVarFromResource(resValue, targetVar);
            resourceFound = true;
          }
        }
      }

      if (!resourceFound) {
        LOG.warn(String.format("Could not initialize property %1$s from resource url %2$s, resource not found!", targetVar, resourceUrl));
      }
    }
    else if (!tagName.equals(INITIALIZE_TAG)) {
      LOG.warn("WARNING: Unknown initialize directive " + tagName);
    }
  }

  private void initializeVarFromResource(@NotNull ResourceValue resValue, @NotNull final String targetVar) {
    ResourceType type = resValue.getResourceType();
    String value = resValue.getValue();
    switch (type) {
      case STRING:
        StringProperty stringProperty = (StringProperty)myContext.getValue(targetVar);
        stringProperty.set(value);
        break;

      default:
        throw new RuntimeException(String.format("Can't initialize variable to unsupported type %1$s (value = \"%2$s\")", type, value));
    }
  }
}
