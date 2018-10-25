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
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.ConstraintSetConstraint;
import static com.android.tools.idea.uibuilder.handlers.motion.MotionSceneString.MotionSceneConstraintSet;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property2.api.PropertiesTable;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import java.awt.EventQueue;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
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
public class MotionLayoutPropertyProvider {
  private final MotionLayoutAttributesModel myModel;
  private final Project myProject;
  private final XmlElementDescriptorProvider myDescriptorProvider;
  private final Table<String, String, MotionPropertyItem> myEmptyTable;

  public MotionLayoutPropertyProvider(@NotNull MotionLayoutAttributesModel model) {
    myModel = model;
    myProject = model.getProject();
    myDescriptorProvider = new AndroidDomElementDescriptorProvider();
    myEmptyTable = ImmutableTable.of();
  }

  @NotNull
  public PropertiesTable<MotionPropertyItem> getProperties(@NotNull NlComponent component,
                                                           @NotNull SmartPsiElementPointer<XmlTag> tagPointer)  {
    assert(EventQueue.isDispatchThread() || ApplicationManager.getApplication().isUnitTestMode());

    return DumbService.getInstance(myProject).runReadActionInSmartMode(() ->
      PropertiesTable.Companion.create(getPropertiesImpl(component, tagPointer)));
  }

  private Table<String, String, MotionPropertyItem> getPropertiesImpl(@NotNull NlComponent component,
                                                                      @NotNull SmartPsiElementPointer<XmlTag> tagPointer) {
    XmlTag tag = tagPointer.getElement();
    if (tag == null) {
      return myEmptyTable;
    }
    if (tag.getLocalName().equals(MotionSceneConstraintSet)) {
      // Hack for ConstraintSets. Get the attributes for the Constraint of the current component instead:
      tag = findConstraint(tag, component);
      if (tag == null) {
        return myEmptyTable;
      }
      tagPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(tag);
    }
    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(myModel.getFacet());
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager frameworkResourceManager = resourceManagers.getFrameworkResourceManager();
    if (frameworkResourceManager == null) {
      Logger.getInstance(MotionLayoutPropertyProvider.class).error(
        "No system resource manager for module: " + myModel.getFacet().getModule().getName());
      return myEmptyTable;
    }

    AttributeDefinitions localAttrDefs = localResourceManager.getAttributeDefinitions();
    AttributeDefinitions systemAttrDefs = frameworkResourceManager.getAttributeDefinitions();

    XmlElementDescriptor elementDescriptor = myDescriptorProvider.getDescriptor(tag);
    if (elementDescriptor == null) {
      return myEmptyTable;
    }
    XmlAttributeDescriptor[] descriptors = elementDescriptor.getAttributesDescriptors(tag);
    Table<String, String, MotionPropertyItem> properties = HashBasedTable.create(3, descriptors.length);
    for (XmlAttributeDescriptor descriptor : descriptors) {
      String namespaceUri = getNamespace(descriptor, tag);
      String name = descriptor.getName();
      AttributeDefinitions attrDefs = (ANDROID_URI == namespaceUri) ? systemAttrDefs : localAttrDefs;
      ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
      AttributeDefinition attrDef = (namespace != null && attrDefs != null)
                                    ? attrDefs.getAttrDefinition(ResourceReference.attr(namespace, name)) : null;
      MotionPropertyItem property = new MotionPropertyItem(myModel, namespaceUri, name, attrDef, tagPointer, component);
      properties.put(namespaceUri, name, property);
    }
    return properties;
  }

  @Nullable
  private static XmlTag findConstraint(@NotNull XmlTag constraintSet, @NotNull NlComponent component) {
    String id = component.getId();
    if (id == null) {
      return null;
    }
    for (XmlTag constraint : constraintSet.findSubTags(ConstraintSetConstraint)) {
      if (id.equals(NlComponent.stripId(constraint.getAttributeValue(ATTR_ID, ANDROID_URI)))) {
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
}
