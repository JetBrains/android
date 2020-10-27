/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.navigation;

import com.google.common.base.Splitter;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class Places {
  private Places() {
  }

  @NotNull
  public static String serialize(@NotNull Place place) {
    StringBuilder buffer = new StringBuilder();
    //noinspection unchecked
    Map<String, Object> paths = (Map<String, Object>)place.getEqualityObjects()[0];
    for (Map.Entry<String, Object> entry : paths.entrySet()) {
      buffer.append(entry.getKey()).append('=').append(entry.getValue()).append("|");
    }
    return buffer.toString();
  }

  @NotNull
  public static Place deserialize(@NotNull String text) {
    Place place = new Place();
    List<String> entries = Splitter.on('|').omitEmptyStrings().splitToList(text);
    for (String entry : entries) {
      List<String> path = Splitter.on('=').splitToList(entry);
      place.putPath(path.get(0), path.get(1));
    }
    return place;
  }
}
