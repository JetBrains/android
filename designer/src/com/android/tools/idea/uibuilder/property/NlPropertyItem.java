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
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableGroupItem;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.StarState;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.property.renderer.NlAttributeRenderer;
import com.android.tools.idea.uibuilder.property.renderer.NlPropertyRenderers;
import com.android.util.PropertiesMap;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class NlPropertyItem extends PTableItem implements NlProperty {
  // Certain attributes are special and do not have an attribute definition from attrs.xml
  private static final Set<String> ATTRS_WITHOUT_DEFINITIONS = ImmutableSet.of(
    SdkConstants.ATTR_STYLE, // <View style="..." />
    SdkConstants.ATTR_CLASS, // class is suggested as an attribute for a <fragment>!
    SdkConstants.ATTR_LAYOUT // <include layout="..." />
  );

  @NotNull
  protected final List<NlComponent> myComponents;
  @NotNull
  protected final NlPropertiesManager myPropertiesManager;
  @Nullable
  protected final AttributeDefinition myDefinition;
  @NotNull
  private final String myName;
  @Nullable
  private final String myNamespace;
  @Nullable
  private PropertiesMap.Property myDefaultValue;
  @NotNull
  private StarState myStarState;

  public static NlPropertyItem create(@NotNull XmlName name,
                                      @Nullable AttributeDefinition attributeDefinition,
                                      @NotNull List<NlComponent> components,
                                      @NotNull NlPropertiesManager propertiesManager) {
    if (attributeDefinition != null && attributeDefinition.getFormats().contains(AttributeFormat.Flag)) {
      return new NlFlagPropertyItem(name, attributeDefinition, components, propertiesManager);
    }
    else if (name.getLocalName().equals(SdkConstants.ATTR_ID)) {
      return new NlIdPropertyItem(name, attributeDefinition, components, propertiesManager);
    }
    else {
      return new NlPropertyItem(name, attributeDefinition, components, propertiesManager);
    }
  }

  protected NlPropertyItem(@NotNull XmlName name,
                           @Nullable AttributeDefinition attributeDefinition,
                           @NotNull List<NlComponent> components,
                           @NotNull NlPropertiesManager propertiesManager) {
    assert !components.isEmpty();
    if (attributeDefinition == null &&
        !ATTRS_WITHOUT_DEFINITIONS.contains(name.getLocalName()) &&
        !SdkConstants.TOOLS_URI.equals(name.getNamespaceKey())) {
      throw new IllegalArgumentException("Missing attribute definition for " + name.getLocalName());
    }

    // NOTE: we do not save any PSI data structures as fields as they could go out of date as the user edits the file.
    // Instead, we have a reference to the component, and query whatever information we need from the component, and expect
    // that the component can provide that information by having a shadow copy that is consistent with the rendering
    myComponents = components;
    myPropertiesManager = propertiesManager;
    myName = name.getLocalName();
    myNamespace = name.getNamespaceKey();
    myDefinition = attributeDefinition;
    myStarState = StarState.STAR_ABLE;
  }

  protected NlPropertyItem(@NotNull NlPropertyItem property, @NotNull String namespace) {
    assert !property.myComponents.isEmpty();
    myComponents = property.myComponents;
    myPropertiesManager = property.myPropertiesManager;
    myName = property.myName;
    myNamespace = namespace;
    myDefinition = property.myDefinition;
    myStarState = StarState.STAR_ABLE;
    if (property.getParent() != null) {
      PTableGroupItem group = (PTableGroupItem)property.getParent();
      group.addChild(this, property);
    }
  }

  public boolean sameDefinition(@Nullable NlPropertyItem other) {
    return other != null &&
           Objects.equal(myName, other.myName) &&
           Objects.equal(myNamespace, other.myNamespace) &&
           myDefinition == other.myDefinition;
  }

  @Override
  @NotNull
  public List<NlComponent> getComponents() {
    return myComponents;
  }

  @Override
  @NotNull
  public StarState getStarState() {
    return myStarState;
  }

  public void setInitialStarred() {
    myStarState = StarState.STARRED;
  }

  @Override
  public void setStarState(@NotNull StarState starState) {
    myStarState = starState;
    NlProperties.saveStarState(myNamespace, myName, starState == StarState.STARRED, myPropertiesManager);
    myPropertiesManager.starStateChanged();
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public String getNamespace() {
    return myNamespace;
  }

  public void setDefaultValue(@Nullable PropertiesMap.Property defaultValue) {
    myDefaultValue = defaultValue;
  }

  @Override
  @Nullable
  public String getValue() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    String prev = null;
    for (NlComponent component : myComponents) {
      String value = component.getAttribute(myNamespace, myName);
      if (value == null) {
        return null;
      }
      if (prev == null) {
        prev = value;
      }
      else if (!value.equals(prev)) {
        return null;
      }
    }
    return prev;
  }

  @Override
  @Nullable
  public String getResolvedValue() {
    return resolveValue(getValue());
  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    if (value == null) {
      return true;
    }
    if (myDefaultValue == null) {
      return false;
    }
    return value.equals(myDefaultValue.resource);
  }

  @Override
  @Nullable
  public String resolveValue(@Nullable String value) {
    if (myDefaultValue != null && isDefaultValue(value)) {
      if (myDefaultValue.value == null) {
        myDefaultValue = new PropertiesMap.Property(myDefaultValue.resource, resolveValueUsingResolver(myDefaultValue.resource));
      }
      return myDefaultValue.value;
    }
    return value != null ? resolveValueUsingResolver(value) : null;
  }

  @Override
  public void mouseMoved(@NotNull PTable table, @NotNull MouseEvent event, @NotNull Rectangle rectRightColumn) {
    NlAttributeRenderer renderer = NlPropertyRenderers.getInstance().get(this);
    renderer.mouseMoved(table, event, rectRightColumn);
  }

  public void delete() {
    PTableGroupItem group = (PTableGroupItem)getParent();
    if (group != null) {
      group.deleteChild(this);
    }
  }

  public static boolean isReference(@Nullable String value) {
    return value != null && (value.startsWith("?") || value.startsWith("@") && !isId(value));
  }

  @NotNull
  private String resolveValueUsingResolver(@NotNull String value) {
    if (isReference(value)) {
      ResourceResolver resolver = getResolver();
      if (resolver != null) {
        ResourceValue resource = resolver.findResValue(value, false);
        if (resource == null) {
          resource = resolver.findResValue(value, true);
        }
        if (resource != null) {
          if (resource.getValue() != null) {
            value = resource.getResourceType() == ResourceType.FONT ? resource.getName() : resource.getValue();
            if (resource.isFramework()) {
              value = addAndroidPrefix(value);
            }
          }
          ResourceValue resolved = resolver.resolveResValue(resource);
          if (resolved != null && resolved.getValue() != null) {
            value = resolved.getResourceType() == ResourceType.FONT ? resolved.getName() : resolved.getValue();
            if (resource.isFramework()) {
              value = addAndroidPrefix(value);
            }
          }
        }
      }
    }
    return value;
  }

  @NotNull
  private static String addAndroidPrefix(@NotNull String value) {
    if (value.startsWith("@") && !value.startsWith(SdkConstants.ANDROID_PREFIX)) {
      return SdkConstants.ANDROID_PREFIX + value.substring(1);
    }
    return value;
  }

  private static boolean isId(@NotNull String value) {
    return value.startsWith(SdkConstants.ID_PREFIX) ||
           value.startsWith(SdkConstants.NEW_ID_PREFIX) ||
           value.startsWith(SdkConstants.ANDROID_ID_PREFIX) ||
           value.startsWith(SdkConstants.ANDROID_NEW_ID_PREFIX);
  }

  @NotNull
  @Override
  public NlProperty getChildProperty(@NotNull String itemName) {
    throw new UnsupportedOperationException(itemName);
  }

  @NotNull
  @Override
  public NlPropertyItem getDesignTimeProperty() {
    if (SdkConstants.TOOLS_URI.equals(myNamespace)) {
      return this;
    }
    return new NlPropertyItem(this, SdkConstants.TOOLS_URI);
  }

  @Override
  @NotNull
  public NlModel getModel() {
    return myComponents.get(0).getModel();
  }

  @Override
  @Nullable
  public XmlTag getTag() {
    return myComponents.size() == 1 ? myComponents.get(0).getTag() : null;
  }

  @Override
  @Nullable
  public String getTagName() {
    String tagName = null;
    for (NlComponent component : myComponents) {
      if (tagName == null) {
        tagName = component.getTagName();
      }
      else if (!tagName.equals(component.getTagName())) {
        return null;
      }
    }
    return tagName;
  }

  @Override
  @Nullable
  public ResourceResolver getResolver() {
    Configuration configuration = getModel().getConfiguration();

    // TODO: what happens if this is configuration dependent? (in theory, those should be edited in the theme editor)
    return configuration.getResourceResolver();
  }

  @Override
  public void setValue(@Nullable Object value) {
    String strValue = value == null ? null : value.toString();
    if (StringUtil.isEmpty(strValue) || isDefaultValue(strValue)) {
      strValue = null;
    }
    setValueIgnoreDefaultValue(strValue, null);
  }

  protected void setValueIgnoreDefaultValue(@Nullable String attrValue, @Nullable Runnable valueUpdated) {
    // TODO: Consider making getApplication() a field to avoid statics
    assert ApplicationManager.getApplication().isDispatchThread();
    if (getModel().getProject().isDisposed()) {
      return;
    }
    String oldValue = getValue();
    String componentName = myComponents.size() == 1 ? myComponents.get(0).getTagName() : "Multiple";

    NlWriteCommandAction.run(myComponents, "Set " + componentName + '.' + myName + " to " + attrValue, () -> {
      myComponents.forEach(component -> component.setAttribute(myNamespace, myName, attrValue));
      myPropertiesManager.propertyChanged(this, oldValue, attrValue);

      if (valueUpdated == null) {
        return;
      }

      valueUpdated.run();
    });

    myPropertiesManager.logPropertyChange(this);
  }

  @NotNull
  public List<String> getParentStylables() {
    return myDefinition == null ? Collections.emptyList() : myDefinition.getParentStyleables();
  }

  @Override
  @Nullable
  public AttributeDefinition getDefinition() {
    return myDefinition;
  }

  @Override
  public boolean isEditable(int column) {
    return column == 1;
  }

  @Override
  public String toString() {
    return namespaceToPrefix(myNamespace) + myName;
  }

  @Override
  @NotNull
  public String getTooltipText() {
    StringBuilder sb = new StringBuilder(100);
    sb.append(namespaceToPrefix(myNamespace));
    sb.append(myName);
    if (myDefinition != null) {
      String value = myDefinition.getDocValue(null);

      if (value != null) {
        sb.append(": ");
        sb.append(value);
      }
    }
    return sb.toString();
  }

  @NotNull
  @VisibleForTesting
  static String namespaceToPrefix(@Nullable String namespace) {
    if (namespace == null) {
      return "";
    }

    if (namespace.equalsIgnoreCase(SdkConstants.ANDROID_URI)) {
      return SdkConstants.ANDROID_PREFIX;
    }

    if (namespace.equalsIgnoreCase(SdkConstants.AUTO_URI)) {
      return "@app:";
    }

    return "";
  }
}
