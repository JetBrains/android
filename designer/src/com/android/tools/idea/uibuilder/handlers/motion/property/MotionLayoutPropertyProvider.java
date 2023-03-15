/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.resources.ResourceType;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import com.android.tools.dom.attrs.StyleableDefinition;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionEditorSelector;
import com.android.tools.idea.uibuilder.property.NlFlagsPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertiesModel;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import com.android.tools.idea.uibuilder.property.NlPropertyType;
import com.android.tools.idea.uibuilder.property.PropertiesProvider;
import com.android.tools.idea.uibuilder.property.support.TypeResolver;
import com.android.tools.property.panel.api.PropertiesTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Property provider for motion layout property editor.
 *
 * The properties are retrieved from the attrs.xml supplied with the
 * constraint layout library.
 */
public class MotionLayoutPropertyProvider implements PropertiesProvider {
  private final AndroidFacet myFacet;
  private final XmlElementDescriptorProvider myDescriptorProvider;
  private final Table<String, String, NlPropertyItem> myEmptyTable;

  private static final int EXPECTED_ROWS = 3;
  private static final int EXPECTED_CELLS_PER_ROW = 10;
  private static final List<String> INCLUDE_SUB_TAGS_OF = ImmutableList.of(MotionSceneAttrs.Tags.CONSTRAINT,
                                                                           MotionSceneAttrs.Tags.TRANSITION);
  public MotionLayoutPropertyProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    myEmptyTable = ImmutableTable.of();
  }

  @NotNull
  @Override
  public PropertiesTable<NlPropertyItem> getProperties(@NotNull NlPropertiesModel model,
                                                       @Nullable Object optionalValue,
                                                       @NotNull List<? extends NlComponent> components) {
    return PropertiesTable.Companion.emptyTable();
  }

  @NotNull
  @Override
  public PropertiesTable<NlPropertyItem> createEmptyTable() {
    return PropertiesTable.Companion.create(HashBasedTable.create(EXPECTED_ROWS, EXPECTED_CELLS_PER_ROW));
  }

  @NotNull
  public Map<String, PropertiesTable<NlPropertyItem>> getAllProperties(@NotNull NlPropertiesModel model,
                                                                       @NotNull MotionSelection selection) {
    if (selection.getComponents().isEmpty()) {
      return Collections.emptyMap();
    }

    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(myFacet);
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager frameworkResourceManager = resourceManagers.getFrameworkResourceManager();
    if (frameworkResourceManager == null) {
      Logger.getInstance(MotionLayoutPropertyProvider.class).error(
        "No system resource manager for module: " + myFacet.getModule().getName());
      return Collections.emptyMap();
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = frameworkResourceManager.getAttributeDefinitions();
    if (localAttrDefs == null) {
      return Collections.emptyMap();
    }

    Map<String, PropertiesTable<NlPropertyItem>> allProperties = new LinkedHashMap<>();

    MotionSceneTag motionSceneTag = selection.getMotionSceneTag();
    XmlTag tag = selection.getXmlTag(motionSceneTag);
    if (motionSceneTag != null && tag != null) {
      XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
      if (elementDescriptor == null) {
        return Collections.emptyMap();
      }
      XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
      Table<String, String, NlPropertyItem> properties = HashBasedTable.create(3, descriptors.length);
      for (XmlAttributeDescriptor descriptor : descriptors) {
        String namespaceUri = getNamespace(descriptor, tag);
        String name = descriptor.getName();
        AttributeDefinitions attrDefs = (ANDROID_URI == namespaceUri) ? systemAttrDefs : localAttrDefs;
        ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
        AttributeDefinition attrDef = (namespace != null && attrDefs != null)
                                      ? attrDefs.getAttrDefinition(ResourceReference.attr(namespace, name)) : null;
        NlPropertyItem property = createProperty(namespaceUri, name, attrDef, model, selection, null);
        properties.put(namespaceUri, name, property);
      }
      allProperties.put(tag.getLocalName(), PropertiesTable.Companion.create(properties));

      loadCustomAttributes(model, allProperties, motionSceneTag, selection);

      if (INCLUDE_SUB_TAGS_OF.contains(tag.getLocalName())) {
        XmlElementDescriptor[] subTagDescriptors = elementDescriptor.getElementsDescriptors(tag);
        for (XmlElementDescriptor descriptor : subTagDescriptors) {
          String subTagName = descriptor.getName();
          if (!subTagName.equals(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE)) {
            Table<String, String, NlPropertyItem> subTagProperties =
              loadFromStyleableName(subTagName, localAttrDefs, model, selection);
            allProperties.put(subTagName, PropertiesTable.Companion.create(subTagProperties));
          }
        }
      }
    }
    else if (selection.getType() == MotionEditorSelector.Type.CONSTRAINT) {
      Table<String, String, NlPropertyItem> constraintProperties =
        loadFromStyleableName(MotionSceneAttrs.Tags.CONSTRAINT, localAttrDefs, model, selection);
      NlPropertyItem id = createProperty(ANDROID_URI, ATTR_ID, null, model, selection, null);
      constraintProperties.put(id.getNamespace(), id.getName(), id);
      allProperties.put(MotionSceneAttrs.Tags.CONSTRAINT, PropertiesTable.Companion.create(constraintProperties));

      loadCustomAttributes(model, allProperties, null, selection);
    }
    return allProperties;
  }

  private static void loadCustomAttributes(@NotNull NlPropertiesModel model,
                                           @NotNull Map<String, PropertiesTable<NlPropertyItem>> allProperties,
                                           @Nullable MotionSceneTag motionSceneTag,
                                           @NotNull MotionSelection selection) {
    MTag[] customTags = motionSceneTag != null ? motionSceneTag.getChildTags(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE) : new MTag[0];
    List<MotionAttributes.DefinedAttribute> inheritedAttributes = Collections.emptyList();
    MotionAttributes attributes = selection.getMotionAttributes();
    if (attributes != null) {
      //noinspection SSBasedInspection
      inheritedAttributes = attributes.getAttrMap().values().stream()
        .filter(defined -> defined.isCustomAttribute())
        .collect(Collectors.toList());
    }
    if (customTags.length == 0 && inheritedAttributes.isEmpty()) {
      return;
    }
    Table<String, String, NlPropertyItem> customProperties = HashBasedTable.create(3, customTags.length);
    for (MTag customTag : customTags) {
      String name = customTag.getAttributeValue(MotionSceneAttrs.ATTR_CUSTOM_ATTRIBUTE_NAME);
      if (name == null) {
        continue;
      }
      for (String customType : MotionSceneAttrs.ourCustomAttribute) {
        String customValue = customTag.getAttributeValue(customType);
        if (customValue != null) {
          NlPropertyItem item = createCustomProperty(name, customType, selection, model);
          customProperties.put(item.getNamespace(), item.getName(), item);
          break;
        }
      }
    }
    inheritedAttributes.forEach(
      defined -> {
        NlPropertyItem item = createCustomProperty(defined.getName(), defined.getCustomType(), selection, model);
        customProperties.put(item.getNamespace(), item.getName(), item);
      }
    );

    allProperties.put(MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE, PropertiesTable.Companion.create(customProperties));
  }

  private Table<String, String, NlPropertyItem> loadFromStyleableName(@NotNull String subTagName,
                                                                      @NotNull AttributeDefinitions attrDefs,
                                                                      @NotNull NlPropertiesModel model,
                                                                      @NotNull MotionSelection selection) {
    ResourceReference reference = new ResourceReference(ResourceNamespace.TODO(), ResourceType.STYLEABLE, subTagName);
    StyleableDefinition styleable = attrDefs.getStyleableDefinition(reference);
    if (styleable == null) {
      return myEmptyTable;
    }
    Table<String, String, NlPropertyItem> properties = HashBasedTable.create(3, styleable.getAttributes().size());
    styleable.getAttributes().forEach((AttributeDefinition attr) -> {
      NlPropertyItem property = createProperty(attr.getResourceReference().getNamespace().getXmlNamespaceUri(),
                                               attr.getName(), attr, model, selection, subTagName);
      properties.put(property.getNamespace(), property.getName(), property);
    });
    return properties;
  }

  public static NlPropertyItem createCustomProperty(@NotNull String name,
                                                    @NotNull String customType,
                                                    @NotNull MotionSelection selection,
                                                    @NotNull NlPropertiesModel model) {
    NlPropertyType type = mapFromCustomType(customType);
    List<? extends NlComponent> components = selection.getComponents();
    return new NlPropertyItem("", name, type, null, "", "", model, components, selection, MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE);
  }

  private static NlPropertyItem createProperty(@NotNull String namespace,
                                               @NotNull String name,
                                               @Nullable AttributeDefinition attr,
                                               @NotNull NlPropertiesModel model,
                                               @NotNull MotionSelection selection,
                                               @Nullable String subTag) {
    List<? extends NlComponent> components = selection.getComponents();
    NlPropertyType type = TypeResolver.INSTANCE.resolveType(name, attr, null);
    String libraryName = StringUtil.notNullize(attr != null ? attr.getLibraryName() : null);
    if (namespace.equals(ANDROID_URI) && name.equals(ATTR_ID)) {
      return new MotionIdPropertyItem(model, attr, "", components, selection, subTag);
    }
    if (attr != null && attr.getFormats().contains(AttributeFormat.FLAGS) && attr.getValues().length == 0) {
      return new NlFlagsPropertyItem(namespace, name, type, attr, "", libraryName, model, components, selection, subTag);
    }
    return new NlPropertyItem(namespace, name, type, attr, "", libraryName, model, components, selection, subTag);
  }

  @NotNull
  private static String getNamespace(@NotNull XmlAttributeDescriptor descriptor, @NotNull XmlTag context) {
    String namespace = null;
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(context);
    }
    return namespace != null ? namespace : ANDROID_URI;
  }

  @NotNull
  static NlPropertyType mapFromCustomType(@NotNull String customType) {
    switch (customType) {
      case MotionSceneAttrs.ATTR_CUSTOM_COLOR_VALUE:
        return NlPropertyType.COLOR;

      case MotionSceneAttrs.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE:
        return NlPropertyType.COLOR_STATE_LIST;

      case MotionSceneAttrs.ATTR_CUSTOM_INTEGER_VALUE:
        return NlPropertyType.INTEGER;

      case MotionSceneAttrs.ATTR_CUSTOM_FLOAT_VALUE:
        return NlPropertyType.FLOAT;

      case MotionSceneAttrs.ATTR_CUSTOM_DIMENSION_VALUE:
        return NlPropertyType.DIMENSION;

      case MotionSceneAttrs.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE:
        return NlPropertyType.DIMENSION_PIXEL;

      case MotionSceneAttrs.ATTR_CUSTOM_BOOLEAN_VALUE:
        return NlPropertyType.BOOLEAN;

      case MotionSceneAttrs.ATTR_CUSTOM_STRING_VALUE:
      default:
        return NlPropertyType.STRING;
    }
  }

  @NotNull
  static String mapToCustomType(@NotNull NlPropertyType type) {
    switch (type) {
      case COLOR:
        return MotionSceneAttrs.ATTR_CUSTOM_COLOR_VALUE;

      case COLOR_STATE_LIST:
        return MotionSceneAttrs.ATTR_CUSTOM_COLOR_DRAWABLE_VALUE;

      case INTEGER:
        return MotionSceneAttrs.ATTR_CUSTOM_INTEGER_VALUE;

      case FLOAT:
        return MotionSceneAttrs.ATTR_CUSTOM_FLOAT_VALUE;

      case DIMENSION:
        return MotionSceneAttrs.ATTR_CUSTOM_DIMENSION_VALUE;

      case DIMENSION_PIXEL:
        return MotionSceneAttrs.ATTR_CUSTOM_PIXEL_DIMENSION_VALUE;

      case BOOLEAN:
        return MotionSceneAttrs.ATTR_CUSTOM_BOOLEAN_VALUE;

      case STRING:
      default:
        return MotionSceneAttrs.ATTR_CUSTOM_STRING_VALUE;
    }
  }
}
