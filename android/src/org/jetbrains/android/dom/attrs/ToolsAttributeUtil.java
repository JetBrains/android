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
import org.jetbrains.android.dom.converters.LightFlagConverter;
import org.jetbrains.android.dom.converters.PackageClassConverter;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static java.util.Collections.singletonList;

/**
 * Class containing utility methods to handle XML attributes in the "tools" namespace.
 * <p/>
 * See <a href="http://tools.android.com/tech-docs/tools-attributes">tools flags documentation</a>
 */
public class ToolsAttributeUtil {
  private static final ResolvingConverter LAYOUT_REFERENCE_CONVERTER =
    new ResourceReferenceConverter(Collections.singleton(ResourceType.LAYOUT.getName()));
  private static final ResolvingConverter ACTIVITY_CLASS_CONVERTER = new PackageClassConverter(true, AndroidUtils.ACTIVITY_BASE_CLASS_NAME);
  private static final ResolvingConverter ACTION_BAR_MODE_FLAGS_CONVERTER = new LightFlagConverter("standard", "list", "tabs");

  /** List of all the tools namespace attributes and its attribute format */
  private static final ImmutableMap<String, List<AttributeFormat>> ATTRIBUTES = ImmutableMap.<String, List<AttributeFormat>>builder()
    .put(ATTR_ACTION_BAR_NAV_MODE, singletonList(AttributeFormat.Flag))
    .put(ATTR_CONTEXT, ImmutableList.of(AttributeFormat.Reference, AttributeFormat.String))
    .put(ATTR_IGNORE, Collections.<AttributeFormat>emptyList())
    .put(ATTR_LISTFOOTER, singletonList(AttributeFormat.Reference))
    .put(ATTR_LISTHEADER, singletonList(AttributeFormat.Reference))
    .put(ATTR_LISTITEM, singletonList(AttributeFormat.Reference))
    .put(ATTR_LAYOUT, singletonList(AttributeFormat.Reference))
    .put(ATTR_LOCALE, Collections.<AttributeFormat>emptyList())
    .put(ATTR_MENU, Collections.<AttributeFormat>emptyList())
    .put(ATTR_SHOW_IN, singletonList(AttributeFormat.Reference))
    .put(ATTR_TARGET_API, Collections.<AttributeFormat>emptyList())
    .build();
  /** List of converters to be applied to some of the attributes */
  private static final ImmutableMap<String, ResolvingConverter> CONVERTERS = ImmutableMap.<String, ResolvingConverter>builder()
    .put(ATTR_ACTION_BAR_NAV_MODE, ACTION_BAR_MODE_FLAGS_CONVERTER)
    .put(ATTR_CONTEXT, ACTIVITY_CLASS_CONVERTER)
    .put(ATTR_LISTFOOTER, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LISTHEADER, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LISTITEM, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_LAYOUT, LAYOUT_REFERENCE_CONVERTER)
    .put(ATTR_SHOW_IN, LAYOUT_REFERENCE_CONVERTER)
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
   * Returns a set with the names of all the tools namespace atrtibutes.
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
