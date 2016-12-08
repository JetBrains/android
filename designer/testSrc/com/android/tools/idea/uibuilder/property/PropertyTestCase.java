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

import com.android.tools.adtui.workbench.PropertiesComponentMock;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.inspector.InspectorProvider;
import com.android.util.PropertiesMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.Disposer;
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

import java.util.*;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;

public abstract class PropertyTestCase extends LayoutTestCase {
  private static final String UNKNOWN_TAG = "UnknownTagName";

  protected NlComponent myTextView;
  protected NlComponent myProgressBar;
  protected NlComponent myCheckBox1;
  protected NlComponent myCheckBox2;
  protected NlComponent myCheckBox3;
  protected NlComponent mySwitch;
  protected NlComponent myUnknown;
  protected NlComponent myMerge;
  protected NlComponent myConstraintLayout;
  protected NlComponent myButton;
  protected NlComponent myImageView;
  protected NlComponent myAutoCompleteTextView;
  protected NlComponent myRadioGroup;
  protected NlComponent myButtonInConstraintLayout;
  protected NlModel myModel;
  protected NlPropertiesManager myPropertiesManager;
  protected AndroidDomElementDescriptorProvider myDescriptorProvider;
  protected Map<String, NlComponent> myComponentMap;
  protected PropertiesComponent myPropertiesComponent;
  protected PropertiesComponent myOldPropertiesComponent;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myModel = createModel();
    myComponentMap = createComponentMap();
    myTextView = myComponentMap.get("textView");
    myProgressBar = myComponentMap.get("progress");
    myCheckBox1 = myComponentMap.get("checkBox1");
    myCheckBox2 = myComponentMap.get("checkBox2");
    myCheckBox3 = myComponentMap.get("checkBox3");
    mySwitch = myComponentMap.get("switch");
    myImageView = myComponentMap.get("imageView");
    myUnknown = myComponentMap.get("unknown");
    myAutoCompleteTextView = myComponentMap.get("autoCompleteTextView");
    myRadioGroup = myComponentMap.get("group");
    myButton = myComponentMap.get("button");
    myMerge = myComponentMap.get("merge");
    myConstraintLayout = myComponentMap.get("constraintLayout");
    myButtonInConstraintLayout = myComponentMap.get("button2");
    myPropertiesManager = new NlPropertiesManager(getProject(), null);
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    myPropertiesComponent = new PropertiesComponentMock();
    myOldPropertiesComponent = registerApplicationComponent(PropertiesComponent.class, myPropertiesComponent);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      registerApplicationComponent(PropertiesComponent.class, myOldPropertiesComponent);
      Disposer.dispose(myModel);
      Disposer.dispose(myPropertiesManager);
    }
    finally {
      super.tearDown();
    }
  }

  @NotNull
  private Map<String, NlComponent> createComponentMap() {
    return addToMap(ImmutableMap.builder(), myModel.getComponents()).build();
  }

  private static ImmutableMap.Builder<String, NlComponent> addToMap(@NotNull ImmutableMap.Builder<String, NlComponent> builder,
                                                                    @NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getId() != null) {
        builder.put(component.getId(), component);
      }
      addToMap(builder, component.getChildren());
    }
    return builder;
  }

  @NotNull
  protected NlModel createModel() {
    ModelBuilder builder = model("merge.xml",
                                 component(VIEW_MERGE)
                                   .withBounds(0, 0, 1000, 1500)
                                   .id("@id/merge")
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .withAttribute(TOOLS_URI, ATTR_CONTEXT, "com.example.MyActivity")
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("@id/textView")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("SomeText"),
                                     component(PROGRESS_BAR)
                                       .withBounds(100, 200, 100, 100)
                                       .id("@id/progress")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 300, 100, 100)
                                       .id("@id/checkBox1")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Wifi"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 400, 100, 100)
                                       .id("@id/checkBox2")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("SomeText"),
                                     component(CHECK_BOX)
                                       .withBounds(100, 500, 100, 100)
                                       .id("@id/checkBox3")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(SWITCH)
                                       .withBounds(100, 600, 100, 100)
                                       .id("@id/switch")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("Enable Roaming"),
                                     component(IMAGE_VIEW)
                                       .withBounds(100, 700, 100, 100)
                                       .id("@id/imageView")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(UNKNOWN_TAG)
                                       .withBounds(100, 800, 100, 100)
                                       .id("@id/unknown")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(BUTTON)
                                       .withBounds(400, 900, 100, 100)
                                       .id("@id/button")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .text("SomeButton"),
                                     component(AUTO_COMPLETE_TEXT_VIEW)
                                       .withBounds(100, 1000, 100, 100)
                                       .id("@id/autoCompleteTextView")
                                       .width("wrap_content")
                                       .height("wrap_content"),
                                     component(RADIO_GROUP)
                                       .withBounds(100, 1100, 100, 150)
                                       .id("@id/group")
                                       .width("wrap_content")
                                       .height("wrap_content")
                                       .children(
                                         component(RADIO_BUTTON)
                                           .withBounds(100, 1150, 100, 50)
                                           .id("@id/radio1")
                                           .width("wrap_content")
                                           .height("wrap_content"),
                                         component((RADIO_BUTTON))
                                           .withBounds(100, 1200, 100, 50)
                                           .id("@+id/radio2")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                       ),
                                     component(CONSTRAINT_LAYOUT)
                                       .withBounds(300, 0, 700, 1000)
                                       .id("@id/constraintLayout")
                                       .width("700dp")
                                       .height("1000dp")
                                       .children(
                                         component(BUTTON)
                                           .withBounds(400, 100, 100, 100)
                                           .id("@id/button2")
                                           .width("wrap_content")
                                           .height("wrap_content")
                                           .text("OtherButton")
                                       )));
    return builder.build();
  }

  protected void clearSnapshots() {
    clearSnapshots(myModel.getComponents());
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

    return NlPropertyItem.create(components, myPropertiesManager, descriptor, namespace, definition);
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

  @NotNull
  protected Table<String, String, NlPropertyItem> getPropertyTable(@NotNull List<NlComponent> components) {
    NlProperties propertiesProvider = NlProperties.getInstance();
    return propertiesProvider.getProperties(myPropertiesManager, components);
  }

  @NotNull
  protected Map<String, NlProperty> getPropertyMap(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      return Collections.emptyMap();
    }
    Table<String, String, NlPropertyItem> properties = getPropertyTable(components);
    Map<String, NlProperty> propertiesByName = new HashMap<>();
    for (NlProperty property : properties.row(ANDROID_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row(AUTO_URI).values()) {
      propertiesByName.put(property.getName(), property);
    }
    for (NlProperty property : properties.row("").values()) {
      propertiesByName.put(property.getName(), property);
    }
    // Add access to known design properties
    NlDesignProperties designProperties = new NlDesignProperties();
    for (NlProperty property : designProperties.getKnownProperties(components, myPropertiesManager)) {
      propertiesByName.putIfAbsent(property.getName(), property);
    }
    return propertiesByName;
  }

  @NotNull
  protected NlProperty getProperty(@NotNull NlComponent component, @NotNull String propertyName) {
    Map<String, NlProperty> properties = getPropertyMap(ImmutableList.of(component));
    NlProperty property = properties.get(propertyName);
    assert property != null;
    return property;
  }

  @NotNull
  protected NlProperty getPropertyWithDefaultValue(@NotNull NlComponent component,
                                                   @NotNull String propertyName,
                                                   @NotNull String resource) {
    NlPropertyItem property = (NlPropertyItem)getProperty(component, propertyName);
    property.setDefaultValue(new PropertiesMap.Property(resource, null));
    return property;
  }

  protected boolean isApplicable(@NotNull InspectorProvider provider, @NotNull NlComponent... componentArray) {
    return provider.isApplicable(Arrays.asList(componentArray), getPropertyMap(Arrays.asList(componentArray)), myPropertiesManager);
  }
}
