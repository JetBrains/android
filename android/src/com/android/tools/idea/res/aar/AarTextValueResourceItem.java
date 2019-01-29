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

import com.android.ide.common.rendering.api.TextResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.utils.HashCodes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Resource item representing a value resource, e.g. a string or a color.
 */
class AarTextValueResourceItem extends AarValueResourceItem implements TextResourceValue {
  private final String myRawXmlValue;

  /**
   * Initializes the resource.
   *
   * @param type the type of the resource
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param value the value associated with the resource
   */
  public AarTextValueResourceItem(@NotNull ResourceType type,
                                  @NotNull String name,
                                  @NotNull AarConfiguration configuration,
                                  @NotNull ResourceVisibility visibility,
                                  @Nullable String textValue,
                                  @Nullable String rawXmlValue) {
    super(type, name, configuration, visibility, textValue);
    myRawXmlValue = rawXmlValue;
  }

  @Override
  @Nullable
  public String getRawXmlValue() {
    return myRawXmlValue == null ? getValue() : myRawXmlValue;
  }

  @Override
  public boolean equals(@com.android.annotations.Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarTextValueResourceItem other = (AarTextValueResourceItem) obj;
    return Objects.equals(myRawXmlValue, other.myRawXmlValue);
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(super.hashCode(), Objects.hashCode(myRawXmlValue));
  }
}
