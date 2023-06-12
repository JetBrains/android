/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.asdriver.inject;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a copy of {@link com.intellij.testFramework.MapDataContext} so that we don't have to
 * rely on IntelliJ's {@code testFramework.jar} from our tests.
 */
public class MapDataContext implements DataContext {
  private final Map<String, Object> myMap = new HashMap<>();

  public MapDataContext() { }

  @Override
  public Object getData(@NotNull String dataId) {
    return myMap.get(dataId);
  }

  public void put(@NotNull String dataId, Object data) {
    myMap.put(dataId, data);
  }

  public <T> void put(@NotNull DataKey<T> dataKey, T data) {
    put(dataKey.getName(), data);
  }
}