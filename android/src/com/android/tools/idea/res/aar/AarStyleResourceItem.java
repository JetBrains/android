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
package com.android.tools.idea.res.aar;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Resource item representing a style resource.
 */
final class AarStyleResourceItem extends AbstractAarValueResourceItem implements StyleResourceValue {
  @Nullable private final String myParentStyle;
  @NotNull private final List<StyleItemResourceValue> myStyleItems;
  /** Style items keyed by the namespace and name of the attribute they define. */
  @NotNull private final Table<ResourceNamespace, String, StyleItemResourceValue> myStyleItemTable;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param parentStyle the parent style reference (package:type/entry)
   * @param styleItems the items of the style
   */
  public AarStyleResourceItem(@NotNull String name,
                              @NotNull AarConfiguration configuration,
                              @NotNull ResourceVisibility visibility,
                              @Nullable String parentStyle,
                              @NotNull Collection<StyleItemResourceValue> styleItems) {
    super(name, configuration, visibility);
    myParentStyle = parentStyle;
    myStyleItems = ImmutableList.copyOf(styleItems);
    myStyleItemTable = HashBasedTable.create();
    for (StyleItemResourceValue item : styleItems) {
      myStyleItemTable.put(item.getNamespace(), item.getAttrName(), item);
    }
  }

  @Override
  @NotNull
  public ResourceType getResourceType() {
    return ResourceType.STYLE;
  }

  @Override
  @Nullable
  public String getParentStyleName() {
    return myParentStyle;
  }

  @Override
  @Nullable
  public StyleItemResourceValue getItem(@NotNull ResourceNamespace namespace, @NotNull String name) {
    return myStyleItemTable.get(namespace, name);
  }

  @Override
  @Nullable
  public StyleItemResourceValue getItem(@NotNull ResourceReference attr) {
    assert attr.getResourceType() == ResourceType.ATTR;
    return myStyleItemTable.get(attr.getNamespace(), attr.getName());
  }

  @Override
  @NotNull
  public Collection<StyleItemResourceValue> getDefinedItems() {
    return myStyleItems;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarStyleResourceItem other = (AarStyleResourceItem) obj;
    return Objects.equals(myParentStyle, other.myParentStyle) && myStyleItemTable.equals(other.myStyleItemTable);
  }
}
