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
package com.android.tools.idea.structure.developerServices;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceUrl;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.parser.BuildFileStatement;
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.parser.GradleBuildFile;
import com.android.tools.idea.rendering.AppResourceRepository;
import com.android.tools.idea.ui.properties.core.StringProperty;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.List;
import java.util.regex.Pattern;

import static com.android.tools.idea.templates.parse.SaxUtils.requireAttr;

/**
 * Handles parsing an initialize.xml file. An initialize file specifies a bunch of instructions
 * which search a project for values which, if found, indicate that a service is already
 * installed.
 */
/* package */ final class InitializeXmlParser extends DefaultHandler {
  private static final Logger LOG = Logger.getInstance(InitializeXmlParser.class);

  public static final String INITIALIZE_TAG = "initialize";
  public static final String DEPENDENCY_TAG = "dependency";
  public static final String DEPENDENCY_ATTR_NAME = "name";
  public static final String RESOURCE_TAG = "resource";
  public static final String RESOURCE_ATTR_NAME = "name";
  public static final String RESOURCE_ATTR_VAR = "var";

  @NotNull private final Module myModule;
  @NotNull private final ServiceContext myContext;
  @NotNull private final List<String> myDependencies = Lists.newArrayList();

  private boolean myIsInstalled = true; // Assume true, but change to false if we fail any condition

  public InitializeXmlParser(@NotNull Module module, @NotNull ServiceContext serviceContext) {
    myModule = module;
    myContext = serviceContext;

    GradleBuildFile gradleBuildFile = GradleBuildFile.get(myModule);
    if (gradleBuildFile != null) {
      for (BuildFileStatement dependency : gradleBuildFile.getDependencies()) {
        if (dependency instanceof Dependency) {
          Object data = ((Dependency)dependency).data;
          if (data instanceof String) {
            myDependencies.add(((String)data));
          }
        }
      }
    }
  }

  @Override
  public void startElement(String uri, String localName, @NotNull String tagName, @NotNull Attributes attributes) throws SAXException {
    if (tagName.equals(DEPENDENCY_TAG)) {
      boolean dependencyFound = false;
      Pattern dependencyPattern = Pattern.compile(requireAttr(tagName, attributes, DEPENDENCY_ATTR_NAME));
      for (String dependency : myDependencies) {
        if (dependencyPattern.matcher(dependency).matches()) {
          dependencyFound = true;
          break;
        }
      }

      if (!dependencyFound) {
        myIsInstalled = false;
      }
    }
    else if (tagName.equals(RESOURCE_TAG)) {
      boolean resourceFound = false;
      String resourceUrl = requireAttr(tagName, attributes, RESOURCE_ATTR_NAME);
      String targetVar = requireAttr(tagName, attributes, RESOURCE_ATTR_VAR);

      AppResourceRepository appResources = AppResourceRepository.getAppResources(myModule, true);
      assert appResources != null;
      ResourceUrl url = ResourceUrl.parse(resourceUrl);
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
        myIsInstalled = false;
      }
    }
    else if (!tagName.equals(INITIALIZE_TAG)) {
      LOG.warn("WARNING: Unknown initialize directive " + tagName);
    }
  }

  @Override
  public void endElement(String uri, String localName, @NotNull String tagName) throws SAXException {
    if (tagName.equals(INITIALIZE_TAG)) {
      myContext.isInstalled().set(myIsInstalled);
      myContext.snapshot();
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
