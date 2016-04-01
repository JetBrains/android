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
package com.android.tools.idea.uibuilder.property;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class NlProperties {
  private static NlProperties ourInstance = null;
  private final AndroidDomElementDescriptorProvider myDescriptorProvider = new AndroidDomElementDescriptorProvider();

  public static synchronized NlProperties getInstance() {
    if (ourInstance == null) {
      ourInstance = new NlProperties();
    }
    return ourInstance;
  }

  @NotNull
  public List<NlPropertyItem> getProperties(@NotNull final NlComponent component) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<NlPropertyItem>>() {
      @Override
      public List<NlPropertyItem> compute() {
        return getPropertiesWithReadLock(component);
      }
    });
  }

  @NotNull
  private List<NlPropertyItem> getPropertiesWithReadLock(@NotNull NlComponent component) {
    XmlTag tag = component.getTag();
    if (!tag.isValid()) {
      return Collections.emptyList();
    }

    AndroidFacet facet = AndroidFacet.getInstance(tag);
    if (facet == null) {
      return Collections.emptyList();
    }

    XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
    if (elementDescriptor == null) {
      return Collections.emptyList();
    }

    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
    List<NlPropertyItem> properties = Lists.newArrayListWithExpectedSize(descriptors.length);

    ResourceManager localResourceManager = facet.getLocalResourceManager();
    ResourceManager systemResourceManager = facet.getSystemResourceManager();
    if (systemResourceManager == null) {
      Logger.getInstance(NlProperties.class).error("No system resource manager for module: " + facet.getModule().getName());
      return Collections.emptyList();
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = systemResourceManager.getAttributeDefinitions();

    for (XmlAttributeDescriptor desc : descriptors) {
      String namespace = getNamespace(desc, tag);
      if (SdkConstants.TOOLS_URI.equals(namespace)) {
        // Skip tools namespace attributes
        continue;
      }
      AttributeDefinitions attrDefs = SdkConstants.NS_RESOURCES.equals(namespace) ? systemAttrDefs : localAttrDefs;
      AttributeDefinition attrDef = attrDefs == null ? null : attrDefs.getAttrDefByName(desc.getName());
      properties.add(NlPropertyItem.create(component, desc, attrDef));
    }

    return properties;
  }

  @Nullable
  private static String getNamespace(@NotNull XmlAttributeDescriptor descriptor, @NotNull XmlTag context) {
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      return ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(context);
    } else {
      return null;
    }
  }
}
