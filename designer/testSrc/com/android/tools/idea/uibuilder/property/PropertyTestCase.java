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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.google.common.collect.ImmutableList;
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

import java.util.List;

import static com.android.SdkConstants.NS_RESOURCES;
import static com.google.common.truth.Truth.assertThat;

public abstract class PropertyTestCase extends LayoutTestCase {
  private AndroidDomElementDescriptorProvider myDescriptorProvider;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
  }

  @NotNull
  protected static NlComponent findComponent(@NotNull NlModel model, @NotNull String id, int y) {
    NlComponent component = model.findLeafAt(100, y, true);
    assertThat(component).isNotNull();
    assertThat(component.getId()).isEqualTo(id);
    return component;
  }

  protected static void clearSnapshots(@NotNull NlModel model) {
    clearSnapshots(model.getComponents());
  }

  protected static void clearSnapshots(@NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      component.setSnapshot(null);
      clearSnapshots(component.getChildren());
    }
  }

  @NotNull
  protected NlPropertyItem createFrom(@NotNull NlComponent component, @NotNull String attributeName) {
    List<NlComponent> components = ImmutableList.of(component);
    XmlAttributeDescriptor descriptor = getDescriptor(component, attributeName);
    assertThat(descriptor).isNotNull();
    AttributeDefinition definition = getDefinition(component, descriptor);
    String namespace = getNamespace(component, descriptor);

    return NlPropertyItem.create(components, descriptor, namespace, definition);
  }

  @Nullable
  protected XmlAttributeDescriptor getDescriptor(@NotNull NlComponent component, @NotNull String attributeName) {
    XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(component.getTag());
    assertThat(elementDescriptor).isNotNull();
    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(component.getTag());
    for (XmlAttributeDescriptor descriptor : descriptors) {
      if (descriptor.getName().equals(attributeName)) {
        return descriptor;
      }
    }
    return null;
  }

  @Nullable
  protected static AttributeDefinition getDefinition(@NotNull NlComponent component, @NotNull XmlAttributeDescriptor descriptor) {
    AndroidFacet facet = component.getModel().getFacet();
    ResourceManager localResourceManager = facet.getLocalResourceManager();
    ResourceManager systemResourceManager = facet.getSystemResourceManager();
    assertThat(systemResourceManager).isNotNull();

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = systemResourceManager.getAttributeDefinitions();

    AttributeDefinitions attrDefs = NS_RESOURCES.equals(getNamespace(component, descriptor)) ? systemAttrDefs : localAttrDefs;
    return attrDefs == null ? null : attrDefs.getAttrDefByName(descriptor.getName());
  }

  @Nullable
  private static String getNamespace(@NotNull NlComponent component, @NotNull XmlAttributeDescriptor descriptor) {
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      return ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(component.getTag());
    }
    return null;
  }
}
