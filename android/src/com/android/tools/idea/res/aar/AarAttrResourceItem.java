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
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resource item representing an attr resource.
 */
final class AarAttrResourceItem extends AbstractAarValueResourceItem implements AttrResourceValue {
  @NotNull private final Set<AttributeFormat> myFormats;
  /** The keys are enum or flag names, the values are corresponding numeric values. */
  @NotNull private final Map<String, Integer> myValueMap;
  /** The keys are enum or flag names, the values are the value descriptions. */
  @NotNull private final Map<String, String> myValueDescriptionMap;
  @Nullable private final String myDescription;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param description the description of the attr resource
   * @param formats the allowed attribute formats
   * @param valueMap the enum or flag integer values keyed by the value names. Some of the values in the
   *     map may be null. The map must contain the names of all declared values, even the ones that don't
   *     have corresponding numeric values.
   * @param valueDescriptionMap the the enum or flag value descriptions keyed by the value names
   */
  public AarAttrResourceItem(@NotNull String name,
                             @NotNull AarConfiguration configuration,
                             @NotNull ResourceVisibility visibility,
                             @Nullable String description,
                             @NotNull Set<AttributeFormat> formats,
                             @NotNull Map<String, Integer> valueMap,
                             @NotNull Map<String, String> valueDescriptionMap) {
    super(name, configuration, visibility);
    myDescription = description;
    myFormats = Collections.unmodifiableSet(formats);
    myValueMap = ImmutableMap.copyOf(valueMap);
    myValueDescriptionMap = valueDescriptionMap;
  }

  @Override
  @NotNull
  public ResourceType getResourceType() {
    return ResourceType.ATTR;
  }

  @Override
  @NotNull
  public Set<AttributeFormat> getFormats() {
    return myFormats;
  }

  @Override
  @Nullable
  public Map<String, Integer> getAttributeValues() {
    return myValueMap.isEmpty() ? null : myValueMap;
  }

  @Override
  @Nullable
  public String getValueDescription(@NotNull String valueName) {
    return myValueDescriptionMap.get(valueName);
  }

  @Override
  @Nullable
  public String getDescription() {
    return myDescription;
  }

  @Override
  @Nullable
  public String getGroupName() {
    return null;
  }

  @Override
  public boolean equals(@com.android.annotations.Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarAttrResourceItem other = (AarAttrResourceItem) obj;
    return Objects.equals(myDescription, other.myDescription) &&
           myFormats.equals(other.myFormats) &&
           myValueMap.equals(other.myValueMap) &&
           myValueDescriptionMap.equals(other.myValueDescriptionMap);
  }
}
