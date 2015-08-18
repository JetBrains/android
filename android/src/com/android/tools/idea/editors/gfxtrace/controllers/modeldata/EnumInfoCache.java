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
package com.android.tools.idea.editors.gfxtrace.controllers.modeldata;

import com.android.tools.idea.editors.gfxtrace.rpc.EnumInfo;
import com.android.tools.idea.editors.gfxtrace.rpc.Schema;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class EnumInfoCache {
  @NotNull private Map<String, EnumInfo> myEnumLookup;

  public EnumInfoCache(@NotNull Schema schema) {
    myEnumLookup = new HashMap<String, EnumInfo>(schema.getEnums().length);
    for (EnumInfo info : schema.getEnums()) {
      myEnumLookup.put(info.getName(), info);
    }
  }

  public EnumInfo getInfo(@NotNull String enumName) {
    return myEnumLookup.get(enumName);
  }
}
