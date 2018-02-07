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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

public class StyleEnumSupport extends EnumSupport {

  protected final StyleFilter myStyleFilter;
  protected final ResourceRepositoryManager myResourceManager;

  public StyleEnumSupport(@NotNull NlProperty property) {
    this(property.getModel().getFacet(), property);
  }

  private StyleEnumSupport(@NotNull AndroidFacet facet, @NotNull NlProperty property) {
    this(property, new StyleFilter(facet, property.getResolver()), ResourceRepositoryManager.getOrCreateInstance(facet));
  }

  @VisibleForTesting
  StyleEnumSupport(@NotNull NlProperty property, @NotNull StyleFilter styleFilter, @NotNull ResourceRepositoryManager resourceManager) {
    super(property);
    myStyleFilter = styleFilter;
    myResourceManager = resourceManager;
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    String tagName = myProperty.getTagName();
    assert tagName != null;
    return convertStylesToDisplayValues(myStyleFilter.getWidgetStyles(tagName));
  }

  @Override
  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    if (value != null && !value.startsWith(PREFIX_RESOURCE_REF) && !value.startsWith(PREFIX_THEME_REF)) {
      // The user did not specify a proper style value.
      // Lookup the value specified to see if there is a matching style.
      ResourceNamespace currentNamespace = myResourceManager.getNamespace();

      // Prefer the users styles:
      StyleResourceValue styleFound = resolve(currentNamespace, value);

      // Otherwise try each of the namespaces defined in the XML file:
      if (styleFound == null) {
        for (String namespaceUri : findKnownNamespaces()) {
          ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
          if (namespace == null) {
            continue;
          }
          StyleResourceValue resource = resolve(namespace, value);
          if (resource != null) {
            styleFound = resource;
            break;
          }
        }
      }

      value = styleFound != null ?
              styleFound.asReference().getRelativeResourceUrl(currentNamespace, getResolver()).toString() : STYLE_RESOURCE_PREFIX + value;
    }
    String shortDisplay = StringUtil.substringAfter(resolvedValue, REFERENCE_STYLE);
    String display = shortDisplay != null ? shortDisplay : resolvedValue;
    return new ValueWithDisplayString(display, value, generateHint(display, value));
  }

  @NotNull
  protected String[] findKnownNamespaces() {
    XmlTag tag = getTagOfFirstComponent();
    return tag != null ? tag.knownNamespaces() : new String[0];
  }

  @NotNull
  protected ResourceNamespace.Resolver getResolver() {
    XmlTag tag = getTagOfFirstComponent();
    return tag != null ? ResourceHelper.getNamespaceResolver(tag) : ResourceNamespace.Resolver.EMPTY_RESOLVER;
  }

  @Nullable
  private XmlTag getTagOfFirstComponent() {
    List<NlComponent> components = myProperty.getComponents();
    return !components.isEmpty() ? components.get(0).getTag() : null;
  }

  @Nullable
  protected StyleResourceValue resolve(@NotNull ResourceNamespace namespace, @NotNull String styleName) {
    ResourceResolver resolver = myProperty.getResolver();
    if (resolver == null) {
      return null;
    }
    ResourceReference reference = new ResourceReference(namespace, ResourceType.STYLE, styleName);
    return resolver.getStyle(reference);
  }

  @Nullable
  protected String generateHint(@NotNull String display, @Nullable String value) {
    if (value == null) {
      return "default";
    }
    if (value.endsWith(display)) {
      return null;
    }
    return value;
  }

  @NotNull
  protected List<ValueWithDisplayString> convertStylesToDisplayValues(@NotNull List<StyleResourceValue> styles) {
    List<ValueWithDisplayString> values = new ArrayList<>();
    StyleResourceValue previousStyle = null;
    for (StyleResourceValue style : styles) {
      if (previousStyle != null && (!previousStyle.getNamespace().equals(style.getNamespace()) ||
                                    previousStyle.isUserDefined() != style.isUserDefined())) {
        values.add(ValueWithDisplayString.SEPARATOR);
      }
      previousStyle = style;
      values.add(createFromResolvedValue(
        style.getName(),
        style.asReference().getRelativeResourceUrl(ResourceNamespace.TODO, getResolver()).toString(), null));
    }
    return values;
  }
}
