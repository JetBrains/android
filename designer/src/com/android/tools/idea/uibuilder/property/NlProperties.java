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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
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

import java.util.Map;

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
  public Table<String, String, NlPropertyItem> getProperties(@NotNull final NlComponent component) {
    return ApplicationManager.getApplication().runReadAction(
      (Computable<Table<String, String, NlPropertyItem>>)() -> getPropertiesWithReadLock(component));
  }

  @NotNull
  private Table<String, String, NlPropertyItem> getPropertiesWithReadLock(@NotNull NlComponent component) {
    XmlTag tag = component.getTag();
    if (!tag.isValid()) {
      return ImmutableTable.of();
    }

    AndroidFacet facet = AndroidFacet.getInstance(tag);
    if (facet == null) {
      return ImmutableTable.of();
    }

    XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
    if (elementDescriptor == null) {
      return ImmutableTable.of();
    }

    ResourceManager localResourceManager = facet.getLocalResourceManager();
    ResourceManager systemResourceManager = facet.getSystemResourceManager();
    if (systemResourceManager == null) {
      Logger.getInstance(NlProperties.class).error("No system resource manager for module: " + facet.getModule().getName());
      return ImmutableTable.of();
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = systemResourceManager.getAttributeDefinitions();

    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
    Table<String, String, NlPropertyItem>  properties = HashBasedTable.create(3, descriptors.length);

    for (XmlAttributeDescriptor desc : descriptors) {
      String namespace = getNamespace(desc, tag);
      AttributeDefinitions attrDefs = SdkConstants.NS_RESOURCES.equals(namespace) ? systemAttrDefs : localAttrDefs;
      AttributeDefinition attrDef = attrDefs == null ? null : attrDefs.getAttrDefByName(desc.getName());
      NlPropertyItem property = NlPropertyItem.create(component, desc, attrDef);
      properties.put(StringUtil.notNullize(namespace), property.getName(), property);
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
