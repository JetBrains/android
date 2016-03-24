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

import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.converters.StaticEnumConverter;
import org.jetbrains.android.dom.converters.TargetApiConverter;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;
import static java.util.Collections.singletonList;

/**
 * Class containing utility methods to handle XML attributes in the "tools" namespace.
 * <p/>
 * Tools attributes are described in several documents:
 * <ul>
 *   <li><a href="http://tools.android.com/tech-docs/tools-attributes">layout attributes</a></li>
 *   <li><a href="http://tools.android.com/tech-docs/new-build-system/user-guide/manifest-merger#TOC-Markers">manifest attributes</a></li>
 * </ul>
 */
public class ToolsAttributeUtil {
  private static final ResolvingConverter LAYOUT_REFERENCE_CONVERTER =
    new ResourceReferenceConverter(EnumSet.of(ResourceType.LAYOUT));
  private static final ResolvingConverter ACTIVITY_CLASS_CONVERTER = new PackageClassConverter(true, AndroidUtils.ACTIVITY_BASE_CLASS_NAME);

  private static final List<AttributeFormat> NO_FORMATS = Collections.emptyList();

  // Manifest merger attribute names
  public static final String ATTR_NODE = "node";
  public static final String ATTR_STRICT = "strict";
  public static final String ATTR_REMOVE = "remove";
  public static final String ATTR_REPLACE = "replace";
  public static final String ATTR_OVERRIDE_LIBRARY = "overrideLibrary";

  /** List of all the tools namespace attributes and its attribute format */
  private static final ImmutableMap<String, List<AttributeFormat>> ATTRIBUTES = ImmutableMap.<String, List<AttributeFormat>>builder()
    // Layout files attributes
    .put(ATTR_ACTION_BAR_NAV_MODE, singletonList(AttributeFormat.Flag))
    .put(ATTR_CONTEXT, ImmutableList.of(AttributeFormat.Reference, AttributeFormat.String))
    .put(ATTR_IGNORE, NO_FORMATS)
    .put(ATTR_LISTFOOTER,  singletonList(AttributeFormat.Reference))
    .put(ATTR_LISTHEADER, singletonList(AttributeFormat.Reference))
    .put(ATTR_LISTITEM, singletonList(AttributeFormat.Reference))
    .put(ATTR_LAYOUT, singletonList(AttributeFormat.Reference))
    .put(ATTR_LOCALE, NO_FORMATS)
    .put(ATTR_MENU, NO_FORMATS)
    .put(ATTR_OPEN_DRAWER, singletonList(AttributeFormat.Enum))
    .put(ATTR_SHOW_IN, singletonList(AttributeFormat.Reference))
    .put(ATTR_TARGET_API, NO_FORMATS)
    // Manifest merger attributes
    .put(ATTR_NODE, singletonList(AttributeFormat.Enum))
    .put(ATTR_STRICT, NO_FORMATS)
    .put(ATTR_REMOVE, NO_FORMATS)
    .put(ATTR_REPLACE, NO_FORMATS)
    .put(ATTR_OVERRIDE_LIBRARY, NO_FORMATS)
    // Raw files attributes
    .put(ATTR_SHRINK_MODE, singletonList(AttributeFormat.Enum))
    .put(ATTR_KEEP, NO_FORMATS)
    .put(ATTR_DISCARD, NO_FORMATS)
    .build();
  /** List of converters to be applied to some of the attributes */
  private static final ImmutableMap<String, ResolvingConverter> CONVERTERS = ImmutableMap.<String, ResolvingConverter>builder()
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
    .build();

  /**
   * Returns a {@link ResolvingConverter} for the given attribute definition
   */
  @Nullable
  public static ResolvingConverter getConverter(@NotNull AttributeDefinition attrDef) {
    String name = attrDef.getName();
    ResolvingConverter converter = CONVERTERS.get(name);

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
    AttributeDefinition def = new AttributeDefinition(name);
    def.addFormats(formats);

    return def;
  }
}
