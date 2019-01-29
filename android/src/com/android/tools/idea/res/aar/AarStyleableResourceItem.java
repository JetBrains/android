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

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resource item representing a styleable resource.
 */
final class AarStyleableResourceItem extends AbstractAarValueResourceItem implements StyleableResourceValue {
  @NotNull private final List<AttrResourceValue> myAttrs;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param attrs the attributes of the styleable
   */
  public AarStyleableResourceItem(@NotNull String name,
                                  @NotNull AarConfiguration configuration,
                                  @NotNull ResourceVisibility visibility,
                                  @NotNull List<AttrResourceValue> attrs) {
    super(name, configuration, visibility);
    myAttrs = ImmutableList.copyOf(attrs);
  }

  @Override
  @NotNull
  public ResourceType getResourceType() {
    return ResourceType.STYLEABLE;
  }

  @Override
  @NotNull
  public List<AttrResourceValue> getAllAttributes() {
    return myAttrs;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarStyleableResourceItem other = (AarStyleableResourceItem) obj;
    return myAttrs.equals(other.myAttrs);
  }
}
