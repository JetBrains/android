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
import org.jetbrains.android.dom.AndroidAnyAttributeDescriptor;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.*;

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
  public Table<String, String, NlPropertyItem> getProperties(@NotNull final List<NlComponent> components) {
    return ApplicationManager.getApplication().runReadAction(
      (Computable<Table<String, String, NlPropertyItem>>)() -> getPropertiesWithReadLock(components));
  }

  @NotNull
  private Table<String, String, NlPropertyItem> getPropertiesWithReadLock(@NotNull List<NlComponent> components) {
    assert !components.isEmpty();
    NlComponent first = components.get(0);
    XmlTag firstTag = first.getTag();
    if (!firstTag.isValid()) {
      return ImmutableTable.of();
    }

    AndroidFacet facet = AndroidFacet.getInstance(firstTag);
    if (facet == null) {
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

    Table<String, String, NlPropertyItem> combinedProperties = null;

    for (NlComponent component : components) {
      XmlTag tag = component.getTag();
      if (!tag.isValid()) {
        return ImmutableTable.of();
      }

      XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
      if (elementDescriptor == null) {
        return ImmutableTable.of();
      }

      XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
      Table<String, String, NlPropertyItem> properties = HashBasedTable.create(3, descriptors.length);

      for (XmlAttributeDescriptor desc : descriptors) {
        String namespace = getNamespace(desc, tag);
        AttributeDefinitions attrDefs = NS_RESOURCES.equals(namespace) ? systemAttrDefs : localAttrDefs;
        AttributeDefinition attrDef = attrDefs == null ? null : attrDefs.getAttrDefByName(desc.getName());
        NlPropertyItem property = NlPropertyItem.create(components, desc, namespace, attrDef);
        properties.put(StringUtil.notNullize(namespace), property.getName(), property);
      }

      // Exceptions:
      switch (tag.getName()) {
        case AUTO_COMPLETE_TEXT_VIEW:
          // An AutoCompleteTextView has a popup that is created at runtime.
          // Properties for this popup can be added to the AutoCompleteTextView tag.
          properties.put(ANDROID_URI, ATTR_POPUP_BACKGROUND, NlPropertyItem.create(
            components,
            new AndroidAnyAttributeDescriptor(ATTR_POPUP_BACKGROUND),
            ANDROID_URI,
            systemAttrDefs != null ? systemAttrDefs.getAttrDefByName(ATTR_POPUP_BACKGROUND) : null));
          break;
      }

      combinedProperties = combine(properties, combinedProperties);
    }

    // The following properties are deprecated in the support library and can be ignored by tools:
    assert combinedProperties != null;
    combinedProperties.remove(AUTO_URI, ATTR_PADDING_START);
    combinedProperties.remove(AUTO_URI, ATTR_PADDING_END);
    combinedProperties.remove(AUTO_URI, ATTR_THEME);

    setUpDesignProperties(combinedProperties);

    //noinspection ConstantConditions
    return combinedProperties;
  }

  @Nullable
  private static String getNamespace(@NotNull XmlAttributeDescriptor descriptor, @NotNull XmlTag context) {
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      return ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(context);
    } else {
      return null;
    }
  }

  private static Table<String, String, NlPropertyItem> combine(@NotNull Table<String, String, NlPropertyItem> properties,
                                                               @Nullable Table<String, String, NlPropertyItem> combinedProperties) {
    if (combinedProperties == null) {
      return properties;
    }
    List<String> namespaces = new ArrayList<>(combinedProperties.rowKeySet());
    List<String> propertiesToRemove = new ArrayList<>();
    for (String namespace : namespaces) {
      propertiesToRemove.clear();
      for (Map.Entry<String, NlPropertyItem> entry : combinedProperties.row(namespace).entrySet()) {
        NlPropertyItem other = properties.get(namespace, entry.getKey());
        if (!entry.getValue().sameDefinition(other)) {
          propertiesToRemove.add(entry.getKey());
        }
      }
      for (String propertyName : propertiesToRemove) {
        combinedProperties.remove(namespace, propertyName);
      }
    }
    // Never include the ID attribute when looking at multiple components:
    combinedProperties.remove(ANDROID_URI, ATTR_ID);
    return combinedProperties;
  }

  private static void setUpDesignProperties(Table<String, String, NlPropertyItem> properties) {
    List<String> designProperties = new ArrayList<>(properties.row(TOOLS_URI).keySet());
    for (String propertyName : designProperties) {
      NlPropertyItem item = properties.get(AUTO_URI, propertyName);
      if (item == null) {
        item = properties.get(ANDROID_URI, propertyName);
      }
      if (item != null) {
        NlPropertyItem designItem = item.getDesignTimeProperty();
        properties.put(TOOLS_URI, propertyName, designItem);
      }
    }
  }
}
