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
package com.android.tools.idea.editors.gfxtrace.schema;

import com.android.tools.idea.editors.gfxtrace.rpc.EnumEntry;
import com.android.tools.idea.editors.gfxtrace.rpc.EnumInfo;

/**
 * A single enum value unpacked using a schema description.
 */
public class EnumValue {
  public final EnumInfo info;
  public final long value;

  public EnumValue(EnumInfo info, long value) {
    this.info = info;
    this.value = value;
  }

  @Override
  public String toString() {
    for (EnumEntry entry : info.getEntries()) {
      if (entry.getValue() == value) {
        return entry.getName();
      }
    }
    return String.format("%s<%d>", info.getName(), value);
  }
}
