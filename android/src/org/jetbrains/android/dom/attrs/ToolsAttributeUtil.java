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
package org.jetbrains.android.dom.attrs;

import static com.android.SdkConstants.ATTR_ACTION_BAR_NAV_MODE;
import static com.android.SdkConstants.ATTR_COMPOSABLE_NAME;
import static com.android.SdkConstants.ATTR_CONTEXT;
import static com.android.SdkConstants.ATTR_DISCARD;
import static com.android.SdkConstants.ATTR_IGNORE;
import static com.android.SdkConstants.ATTR_ITEM_COUNT;
import static com.android.SdkConstants.ATTR_KEEP;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LISTFOOTER;
import static com.android.SdkConstants.ATTR_LISTHEADER;
import static com.android.SdkConstants.ATTR_LISTITEM;
import static com.android.SdkConstants.ATTR_LOCALE;
import static com.android.SdkConstants.ATTR_MENU;
import static com.android.SdkConstants.ATTR_OPEN_DRAWER;
import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.ATTR_SHRINK_MODE;
import static com.android.SdkConstants.ATTR_SRC_COMPAT;
import static com.android.SdkConstants.ATTR_TARGET_API;
import static com.android.SdkConstants.ATTR_USE_HANDLER;
import static com.android.SdkConstants.ATTR_VIEW_BINDING_IGNORE;
import static com.android.SdkConstants.ATTR_VIEW_BINDING_TYPE;
import static com.android.SdkConstants.CLASS_VIEW;
import static com.android.SdkConstants.CLASS_VIEWGROUP;
import static com.android.SdkConstants.VALUE_SAFE;
import static com.android.SdkConstants.VALUE_STRICT;
import static com.android.SdkConstants.WIDGET_PKG_PREFIX;
import static java.util.Collections.singletonList;

import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.ResolvingConverter;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.converters.StaticEnumConverter;
import org.jetbrains.android.dom.converters.TargetApiConverter;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class containing utility methods to handle XML attributes in the "tools" namespace.
 * <p/>
 * Tools attributes are described in several documents:
 * <ul>
 *   <li><a href="https://developer.android.com/studio/write/tool-attributes.html#design-time_view_attributes">layout attributes</a></li>
 *   <li><a href="https://developer.android.com/studio/build/manifest-merge.html#merge_rule_markers">manifest attributes</a></li>
 * </ul>
 */
public final class ToolsAttributeUtil {
  private static final ResolvingConverter LAYOUT_REFERENCE_CONVERTER =
    new ResourceReferenceConverter(EnumSet.of(ResourceType.LAYOUT));
  private static final Converter ACTIVITY_CLASS_CONVERTER = new PackageClassConverter.Builder()
    .useManifestBasePackage(true)
    .withExtendClassNames(AndroidUtils.ACTIVITY_BASE_CLASS_NAME)
    .build();
  private static final Converter VIEW_CONVERTER = new PackageClassConverter.Builder()
    .completeLibraryClasses(true)
    .withExtendClassNames(CLASS_VIEW)
    .withExtraBasePackages(WIDGET_PKG_PREFIX)
    .build();
  private static final Converter VIEW_GROUP_CONVERTER = new PackageClassConverter.Builder()
    .completeLibraryClasses(true)
    .withExtendClassNames(CLASS_VIEWGROUP)
    .withExtraBasePackages(WIDGET_PKG_PREFIX)
    .build();
  private static final List<AttributeFormat> NO_FORMATS = Collections.emptyList();

  // Manifest merger attribute names
  public static final String ATTR_NODE = "node";
  public static final String ATTR_STRICT = "strict";
  public static final String ATTR_REMOVE = "remove";
  public static final String ATTR_REPLACE = "replace";
  public static final String ATTR_OVERRIDE_LIBRARY = "overrideLibrary";

  /** List of all the tools namespace attributes and their formats. */
  private static final ImmutableMap<String, List<AttributeFormat>> ATTRIBUTES = ImmutableMap.<String, List<AttributeFormat>>builder()
    // Layout files attributes
    .put(ATTR_ACTION_BAR_NAV_MODE, singletonList(AttributeFormat.FLAGS))
    .put(ATTR_COMPOSABLE_NAME, NO_FORMATS)
    .put(ATTR_CONTEXT, ImmutableList.of(AttributeFormat.REFERENCE, AttributeFormat.STRING))
    .put(ATTR_IGNORE, NO_FORMATS)
    .put(ATTR_ITEM_COUNT, singletonList(AttributeFormat.INTEGER))
    .put(ATTR_LISTFOOTER,  singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LISTHEADER, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LISTITEM, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LAYOUT, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_LOCALE, NO_FORMATS)
    .put(ATTR_MENU, NO_FORMATS)
    .put(ATTR_OPEN_DRAWER, singletonList(AttributeFormat.ENUM))
    .put(ATTR_PARENT_TAG, singletonList(AttributeFormat.STRING))
    .put(ATTR_SHOW_IN, singletonList(AttributeFormat.REFERENCE))
    .put(ATTR_TARGET_API, NO_FORMATS)
    // Manifest merger attributes
    .put(ATTR_NODE, singletonList(AttributeFormat.ENUM))
    .put(ATTR_STRICT, NO_FORMATS)
    .put(ATTR_REMOVE, NO_FORMATS)
    .put(ATTR_REPLACE, NO_FORMATS)
    .put(ATTR_OVERRIDE_LIBRARY, NO_FORMATS)
    // Raw files attributes
    .put(ATTR_SHRINK_MODE, singletonList(AttributeFormat.ENUM))
    .put(ATTR_KEEP, NO_FORMATS)
    .put(ATTR_DISCARD, NO_FORMATS)
    .put(ATTR_USE_HANDLER, singletonList(AttributeFormat.REFERENCE))
    // AppCompatImageView srcCompat attribute
    // TODO: Remove this definition and make sure the app namespace attributes are handled by AndroidDomUtil#getAttributeDefinition
    .put(ATTR_SRC_COMPAT, singletonList(AttributeFormat.REFERENCE))
    // View binding attributes
    .put(ATTR_VIEW_BINDING_IGNORE, singletonList(AttributeFormat.BOOLEAN))
    .put(ATTR_VIEW_BINDING_TYPE, singletonList(AttributeFormat.REFERENCE))
    .build();
  /** List of converters to be applied to some of the attributes */
  private static final ImmutableMap<String, Converter> CONVERTERS = ImmutableMap.<String, Converter>builder()
    .put(ATTR_ACTION_BAR_NAV_MODE, new StaticEnumConverter("standard", "list", "tabs"))
    .put(ATTR_OPEN_DRAWER, new StaticEnumConverter("start", "end", "left", "right"))
    .put(ATTR_CONTEXT, ACTIVITY_CLASS_CONVERTER)
    .put(ATTR_LISTFOOTER, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LISTHEADER, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LISTITEM, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LAYOUT, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_SHOW_IN, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_NODE, new StaticEnumConverter("merge", "replace", "strict", "merge-only-attributes", "remove", "removeAll"))
    .put(ATTR_TARGET_API, new TargetApiConverter())
    .put(ATTR_SHRINK_MODE, new StaticEnumConverter(VALUE_STRICT, VALUE_SAFE))
    .put(ATTR_USE_HANDLER, VIEW_CONVERTER)
    .put(ATTR_PARENT_TAG, VIEW_GROUP_CONVERTER)
    .put(ATTR_VIEW_BINDING_TYPE, VIEW_CONVERTER)
    .build();

  /**
   * Returns a {@link Converter} for the given attribute definition
   */
  @Nullable
  public static Converter getConverter(@NotNull AttributeDefinition attrDef) {
    String name = attrDef.getName();
    Converter converter = CONVERTERS.get(name);

    return converter != null ? converter : AndroidDomUtil.getConverter(attrDef);
  }

  /**
   * Returns a set with the names of all the tools namespace attributes.
   */
  @NotNull
  public static Set<String> getAttributeNames() {
    return ATTRIBUTES.keySet();
  }

  /**
   * Returns an {@link AttributeDefinition} for the attribute with the given name. If the attribute is not defined
   * in the tools namespace, null will be returned.
   */
  @Nullable
  public static AttributeDefinition getAttrDefByName(@NotNull String name) {
    if (!ATTRIBUTES.containsKey(name)) {
      return null;
    }

    Collection<AttributeFormat> formats = ATTRIBUTES.get(name);
    return new AttributeDefinition(ResourceNamespace.TOOLS, name, null, formats);
  }
}
