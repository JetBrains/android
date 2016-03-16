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

package com.android.tools.idea.sdk.legacy.remote.internal.archives;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;

/**
 * The Architecture that this archive can be downloaded on.
 */
public enum BitSize {
  _32(32),
  _64(64);

  private final int mSize;

  private BitSize(int size) {
    mSize = size;
  }

  /**
   * Returns the size of the architecture.
   */
  public int getSize() {
    return mSize;
  }

  /**
   * Returns the XML name of the bit size.
   */
  @NonNull
  public String getXmlName() {
    return Integer.toString(mSize);
  }

  /**
   * Returns the enum value matching the given XML name.
   *
   * @return A valid {@link HostOs} constant or null if not a valid XML name.
   */
  @Nullable
  public static BitSize fromXmlName(@Nullable String xmlName) {
    if (xmlName != null) {
      for (BitSize v : values()) {
        if (v.getXmlName().equalsIgnoreCase(xmlName)) {
          return v;
        }
      }
    }
    return null;
  }

}
