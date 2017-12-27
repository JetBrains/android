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

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ImageViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.utils.Pair;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.XmlName;
import com.intellij.xml.NamespaceAwareXmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.android.resourceManagers.ResourceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;

public class NlProperties {
  public static final String STARRED_PROP = "ANDROID.STARRED_PROPERTIES";

  private static NlProperties ourInstance = null;
  private final AndroidDomElementDescriptorProvider myDescriptorProvider = new AndroidDomElementDescriptorProvider();

  public static synchronized NlProperties getInstance() {
    if (ourInstance == null) {
      ourInstance = new NlProperties();
    }
    return ourInstance;
  }

  @NotNull
  public Table<String, String, NlPropertyItem> getProperties(@NotNull NlPropertiesManager propertiesManager,
                                                             @NotNull List<NlComponent> components) {
    AndroidFacet facet = getFacet(components);
    if (facet == null) {
      return ImmutableTable.of();
    }
    return getProperties(facet, propertiesManager, components);
  }

  @VisibleForTesting
  Table<String, String, NlPropertyItem> getProperties(@NotNull AndroidFacet facet,
                                                      @NotNull NlPropertiesManager propertiesManager,
                                                      @NotNull List<NlComponent> components) {
    return ApplicationManager.getApplication().runReadAction((Computable<Table<String, String, NlPropertyItem>>)() ->
      getPropertiesWithReadLock(facet, propertiesManager, components));
  }

  @NotNull
  private Table<String, String, NlPropertyItem> getPropertiesWithReadLock(@NotNull AndroidFacet facet,
                                                                          @NotNull NlPropertiesManager propertiesManager,
                                                                          @NotNull List<NlComponent> components) {
    ModuleResourceManagers resourceManagers = ModuleResourceManagers.getInstance(facet);
    ResourceManager localResourceManager = resourceManagers.getLocalResourceManager();
    ResourceManager systemResourceManager = resourceManagers.getSystemResourceManager();
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
        XmlName name = getXmlName(desc, tag);
        AttributeDefinitions attrDefs = NS_RESOURCES.equals(name.getNamespaceKey()) ? systemAttrDefs : localAttrDefs;
        AttributeDefinition attrDef = attrDefs == null ? null : attrDefs.getAttrDefByName(name.getLocalName());
        NlPropertyItem property = NlPropertyItem.create(name, attrDef, components, propertiesManager);
        properties.put(StringUtil.notNullize(name.getNamespaceKey()), property.getName(), property);
      }

      // Exceptions:
      switch (tag.getName()) {
        case AUTO_COMPLETE_TEXT_VIEW:
          // An AutoCompleteTextView has a popup that is created at runtime.
          // Properties for this popup can be added to the AutoCompleteTextView tag.
          properties.put(ANDROID_URI, ATTR_POPUP_BACKGROUND, NlPropertyItem.create(
            new XmlName(ATTR_POPUP_BACKGROUND, ANDROID_URI),
            systemAttrDefs != null ? systemAttrDefs.getAttrDefByName(ATTR_POPUP_BACKGROUND) : null,
            components,
            propertiesManager));
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
    setUpSrcCompat(combinedProperties, facet, components, propertiesManager);

    initStarState(combinedProperties);

    //noinspection ConstantConditions
    return combinedProperties;
  }

  @Nullable
  private static AndroidFacet getFacet(@NotNull List<NlComponent> components) {
    if (components.isEmpty()) {
      return null;
    }
    return components.get(0).getModel().getFacet();
  }

  private static void initStarState(@NotNull Table<String, String, NlPropertyItem> properties) {
    for (String starredProperty : getStarredProperties()) {
      Pair<String, String> property = split(starredProperty);
      NlPropertyItem item = properties.get(property.getFirst(), property.getSecond());
      if (item != null) {
        item.setInitialStarred();
      }
    }
  }

  public static void saveStarState(@Nullable String propertyNamespace,
                                   @NotNull String propertyName,
                                   boolean starred,
                                   @NotNull NlPropertiesManager propertiesManager) {
    String propertyNameWithPrefix = getPropertyNameWithPrefix(propertyNamespace, propertyName);
    List<String> favorites = new ArrayList<>();
    for (String starredProperty : getStarredProperties()) {
      if (!starredProperty.equals(propertyNameWithPrefix)) {
        favorites.add(starredProperty);
      }
    }
    if (starred) {
      favorites.add(propertyNameWithPrefix);
    }
    PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(STARRED_PROP, Joiner.on(';').join(favorites));
    String added = starred ? propertyNameWithPrefix : "";
    String removed = !starred ? propertyNameWithPrefix : "";
    propertiesManager.logFavoritesChange(added, removed, favorites);
  }

  public static String getStarredPropertiesAsString() {
    String starredProperties = PropertiesComponent.getInstance().getValue(STARRED_PROP);
    if (starredProperties == null) {
      starredProperties = ATTR_VISIBILITY;
    }
    return starredProperties;
  }

  public static Iterable<String> getStarredProperties() {
    return Splitter.on(';').trimResults().omitEmptyStrings().split(getStarredPropertiesAsString());
  }

  @NotNull
  private static String getPropertyNameWithPrefix(@Nullable String namespace, @NotNull String propertyName) {
    if (namespace == null) {
      return propertyName;
    }
    switch (namespace) {
      case TOOLS_URI:
        return TOOLS_NS_NAME_PREFIX + propertyName;
      case ANDROID_URI:
        return propertyName;
      default:
        return PREFIX_APP + propertyName;
    }
  }

  @NotNull
  private static Pair<String, String> split(@NotNull String propertyNameWithPrefix) {
    if (propertyNameWithPrefix.startsWith(TOOLS_NS_NAME_PREFIX)) {
      return Pair.of(TOOLS_URI, propertyNameWithPrefix.substring(TOOLS_NS_NAME_PREFIX.length()));
    }
    if (propertyNameWithPrefix.startsWith(PREFIX_APP)) {
      return Pair.of(AUTO_URI, propertyNameWithPrefix.substring(PREFIX_APP.length()));
    }
    return Pair.of(ANDROID_URI, propertyNameWithPrefix);
  }

  @NotNull
  private static XmlName getXmlName(@NotNull XmlAttributeDescriptor descriptor, @NotNull XmlTag context) {
    String namespace = null;
    if (descriptor instanceof NamespaceAwareXmlAttributeDescriptor) {
      namespace = ((NamespaceAwareXmlAttributeDescriptor)descriptor).getNamespace(context);
    }
    return new XmlName(descriptor.getName(), namespace);
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

  private static void setUpDesignProperties(@NotNull Table<String, String, NlPropertyItem> properties) {
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

  // If the src property is available and AppCompat is used then fabricate another property: srcCompat.
  // This is how appCompat is supporting vector drawables in older versions of Android.
  private static void setUpSrcCompat(@NotNull Table<String, String, NlPropertyItem> properties,
                                     @NotNull AndroidFacet facet,
                                     @NotNull List<NlComponent> components,
                                     @NotNull NlPropertiesManager propertiesManager) {
    NlPropertyItem srcProperty = properties.get(ANDROID_URI, ATTR_SRC);
    if (srcProperty != null && shouldAddSrcCompat(facet, components)) {
      AttributeDefinition srcDefinition = srcProperty.getDefinition();
      assert srcDefinition != null;
      AttributeDefinition srcCompatDefinition =
        new AttributeDefinition(ATTR_SRC_COMPAT, SUPPORT_LIB_ARTIFACT, null, srcDefinition.getFormats());
      srcCompatDefinition.getParentStyleables().addAll(srcDefinition.getParentStyleables());
      NlPropertyItem srcCompatProperty =
        NlPropertyItem.create(new XmlName(ATTR_SRC_COMPAT, AUTO_URI), srcCompatDefinition, components, propertiesManager);
      properties.put(AUTO_URI, ATTR_SRC_COMPAT, srcCompatProperty);
    }
  }

  private static boolean shouldAddSrcCompat(@NotNull AndroidFacet facet,
                                            @NotNull List<NlComponent> components) {

    return !components.isEmpty() &&
           allComponentsNeedSrcCompat(facet, components);
  }

  private static boolean allComponentsNeedSrcCompat(@NotNull AndroidFacet facet, @NotNull List<NlComponent> components) {
    NlModel model = components.get(0).getModel();

    ViewHandlerManager manager = ViewHandlerManager.get(facet);
    Set<String> knownTagNames = new HashSet<>();
    for (NlComponent component : components) {
      String tagName = component.getTagName();
      if (knownTagNames.add(tagName)) {
        ViewHandler handler = manager.getHandler(component.getTagName());
        if (!(handler instanceof ImageViewHandler)) {
          return false;
        }
        ImageViewHandler imageViewHandler = (ImageViewHandler)handler;
        if (!imageViewHandler.shouldUseSrcCompat(model)) {
          return false;
        }
      }
    }
    return true;
  }
}
