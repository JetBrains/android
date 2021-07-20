/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class InfoSection {
  private final @NotNull String myHeading;
  private final @NotNull Map<@NotNull String, @Nullable Object> myInfo;

  InfoSection(@NotNull String heading) {
    myHeading = heading;
    myInfo = new LinkedHashMap<>();
  }

  @NotNull String getHeading() {
    return myHeading;
  }

  @NotNull InfoSection putInfo(@NotNull String name, @Nullable Object value) {
    myInfo.put(name, value);
    return this;
  }

  void forEachInfo(@NotNull BiConsumer<@NotNull String, @Nullable Object> consumer) {
    myInfo.forEach(consumer);
  }
}
