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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.ConstraintSetConstraint;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.MotionSceneConstraintSet;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString;
import com.android.tools.idea.uibuilder.property2.NeleFlagsPropertyItem;
import com.android.tools.idea.uibuilder.property2.NeleIdPropertyItem;
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.android.tools.idea.uibuilder.property2.NelePropertyType;
import com.android.tools.idea.uibuilder.property2.PropertiesProvider;
import com.android.tools.idea.uibuilder.property2.support.TypeResolver;
import com.android.tools.property.panel.api.PropertiesTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import java.awt.EventQueue;
import java.util.List;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
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
  private final Project myProject;
  private final XmlElementDescriptorProvider myDescriptorProvider;
  private final Table<String, String, NelePropertyItem> myEmptyTable;

  private static final int EXPECTED_ROWS = 3;
  private static final int EXPECTED_CELLS_PER_ROW = 10;

  public MotionLayoutPropertyProvider(@NotNull AndroidFacet facet) {
    myFacet = facet;
    myProject = facet.getModule().getProject();
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    myEmptyTable = ImmutableTable.of();
  }

  @NotNull
  @Override
  public PropertiesTable<NelePropertyItem> getProperties(@NotNull NelePropertiesModel model,
                                                         @Nullable Object optionalValue,
                                                         @NotNull List<? extends NlComponent> components) {
    assert (!EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode());

    @SuppressWarnings("unchecked")
    SmartPsiElementPointer<XmlTag> tagPointer = (SmartPsiElementPointer<XmlTag>)optionalValue;
    if (tagPointer == null) {
      return PropertiesTable.Companion.emptyTable();
    }
    return DumbService.getInstance(myProject).runReadActionInSmartMode(
      () -> PropertiesTable.Companion.create(getPropertiesImpl(model, tagPointer, components)));
  }

  @NotNull
  @Override
  public PropertiesTable<NelePropertyItem> createEmptyTable() {
    return PropertiesTable.Companion.create(HashBasedTable.create(EXPECTED_ROWS, EXPECTED_CELLS_PER_ROW));
  }

  private Table<String, String, NelePropertyItem> getPropertiesImpl(@NotNull NelePropertiesModel model,
                                                                    @Nullable SmartPsiElementPointer<XmlTag> tagPointer,
                                                                    @NotNull List<? extends NlComponent> components) {
    if (tagPointer == null || components.isEmpty()) {
      return myEmptyTable;
    }
    XmlTag tag = tagPointer.getElement();
    if (tag == null) {
      return myEmptyTable;
    }
    if (tag.getLocalName().equals(MotionSceneConstraintSet)) {
      // Hack for ConstraintSets. Get the attributes for the Constraint of the current component instead:
      tag = findConstraint(tag, components.get(0));
      if (tag == null) {
        return myEmptyTable;
      }
      tagPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(tag);
    }
    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(myFacet);
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager frameworkResourceManager = resourceManagers.getFrameworkResourceManager();
    if (frameworkResourceManager == null) {
      Logger.getInstance(MotionLayoutPropertyProvider.class).error(
        "No system resource manager for module: " + myFacet.getModule().getName());
      return myEmptyTable;
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = frameworkResourceManager.getAttributeDefinitions();

    XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
    if (elementDescriptor == null) {
      return myEmptyTable;
    }
    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
    Table<String, String, NelePropertyItem> properties = HashBasedTable.create(3, descriptors.length);
    for (XmlAttributeDescriptor descriptor : descriptors) {
      String namespaceUri = getNamespace(descriptor, tag);
      String name = descriptor.getName();
      AttributeDefinitions attrDefs = (ANDROID_URI == namespaceUri) ? systemAttrDefs : localAttrDefs;
      ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
      AttributeDefinition attrDef = (namespace != null && attrDefs != null)
                                    ? attrDefs.getAttrDefinition(ResourceReference.attr(namespace, name)) : null;
      NelePropertyItem property = createProperty(namespaceUri, name, attrDef, model, tagPointer, components);
      properties.put(namespaceUri, name, property);
    }
    for (XmlTag custom : tag.findSubTags(MotionSceneString.KeyAttributes_customAttribute)) {
      String name = custom.getAttributeValue(MotionSceneString.CustomAttributes_attributeName, AUTO_URI);
      if (name == null) {
        continue;
      }
      for (String customType : MotionSceneString.CustomAttributes_types) {
        String customValue = custom.getAttributeValue(customType, AUTO_URI);
        if (customValue != null) {
          properties.put("", name, createCustomProperty(name, customType, custom, model, components));
          break;
        }
      }
    }
    return properties;
  }

  public static NelePropertyItem createCustomProperty(@NotNull String name,
                                                      @NotNull String customType,
                                                      @NotNull XmlTag customTag,
                                                      @NotNull NelePropertiesModel model,
                                                      @NotNull List<? extends NlComponent> components) {
    NelePropertyType type = mapFromCustomType(customType);
    SmartPsiElementPointer<XmlTag> tagPointer =
      SmartPointerManager.getInstance(model.getProject()).createSmartPsiElementPointer(customTag);
    return new NelePropertyItem("", name, type, null, "", "", model, components, tagPointer, null);
  }

  private static NelePropertyItem createProperty(@NotNull String namespace,
                                                 @NotNull String name,
                                                 @Nullable AttributeDefinition attr,
                                                 @NotNull NelePropertiesModel model,
                                                 @NotNull SmartPsiElementPointer<XmlTag> tagPointer,
                                                 @NotNull List<? extends NlComponent> components) {
    NelePropertyType type = TypeResolver.INSTANCE.resolveType(name, attr);
    String libraryName = StringUtil.notNullize(attr != null ? attr.getLibraryName() : null);
    if (namespace == ANDROID_URI && name == ATTR_ID) {
      return new NeleIdPropertyItem(model, attr, "", components, tagPointer, null);
    }
    if (attr != null && attr.getFormats().contains(AttributeFormat.FLAGS) && attr.getValues().length == 0) {
      return new NeleFlagsPropertyItem(namespace, name, type, attr, "", libraryName, model, components, tagPointer, null);
    }
    return new NelePropertyItem(namespace, name, type, attr, "", libraryName, model, components, tagPointer, null);
  }

  @Nullable
  private static XmlTag findConstraint(@NotNull XmlTag constraintSet, @NotNull NlComponent component) {
    String id = component.getId();
    if (id == null) {
      return null;
    }
    for (XmlTag constraint : constraintSet.findSubTags(ConstraintSetConstraint)) {
      String idValue = constraint.getAttributeValue(ATTR_ID, ANDROID_URI);
      if (idValue != null && id.equals(stripPrefixFromId(idValue))) {
        return constraint;
      }
    }
    return null;
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
  static NelePropertyType mapFromCustomType(@NotNull String customType) {
    switch (customType) {
      case MotionSceneString.CustomAttributes_customColorValue:
        return NelePropertyType.COLOR;

      case MotionSceneString.CustomAttributes_customIntegerValue:
        return NelePropertyType.INTEGER;

      case MotionSceneString.CustomAttributes_customFloatValue:
        return NelePropertyType.FLOAT;

      case MotionSceneString.CustomAttributes_customStringValue:
        return NelePropertyType.STRING;

      case MotionSceneString.CustomAttributes_customDimensionValue:
        return NelePropertyType.DIMENSION;

      case MotionSceneString.CustomAttributes_customBooleanValue:
        return NelePropertyType.BOOLEAN;

      default:
        return NelePropertyType.STRING;
    }
  }

  @NotNull
  static String mapToCustomType(@NotNull NelePropertyType type) {
    switch (type) {
      case COLOR:
        return MotionSceneString.CustomAttributes_customColorValue;

      case INTEGER:
        return MotionSceneString.CustomAttributes_customIntegerValue;

      case FLOAT:
        return MotionSceneString.CustomAttributes_customFloatValue;

      case STRING:
        return MotionSceneString.CustomAttributes_customStringValue;

      case DIMENSION:
        return MotionSceneString.CustomAttributes_customDimensionValue;

      case BOOLEAN:
        return MotionSceneString.CustomAttributes_customBooleanValue;

      default:
        return MotionSceneString.CustomAttributes_customStringValue;
    }
  }
}
