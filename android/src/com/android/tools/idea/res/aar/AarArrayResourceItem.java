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

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

/**
 * Resource item representing an array resource.
 */
final class AarArrayResourceItem extends AbstractAarValueResourceItem implements ArrayResourceValue {
  @NotNull private final List<String> myElements;

  /**
   * Initializes the resource.
   *
   * @param name the name of the resource
   * @param configuration the configuration the resource belongs to
   * @param visibility the visibility of the resource
   * @param elements the elements  or the array
   */
  public AarArrayResourceItem(@NotNull String name,
                              @NotNull AarConfiguration configuration,
                              @NotNull ResourceVisibility visibility,
                              @NotNull List<String> elements) {
    super(name, configuration, visibility);
    myElements = elements;
  }

  @Override
  @NotNull
  public ResourceType getResourceType() {
    return ResourceType.ARRAY;
  }

  @Override
  public int getElementCount() {
    return myElements.size();
  }

  @Override
  @NotNull
  public String getElement(int index) {
    return myElements.get(index);
  }

  @Override
  public Iterator<String> iterator() {
    return myElements.iterator();
  }

  @Override
  @Nullable
  public String getValue() {
    return myElements.isEmpty() ? null : myElements.get(0);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    AarArrayResourceItem other = (AarArrayResourceItem) obj;
    return myElements.equals(other.myElements);
  }
}
