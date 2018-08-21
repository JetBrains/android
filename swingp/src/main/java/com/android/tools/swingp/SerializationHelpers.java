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
package com.android.tools.swingp;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

public class SerializationHelpers {
  @NotNull
  public static JsonArray arrayToJsonArray(@NotNull int[] values) {
    JsonArray jsonArray = new JsonArray();
    IntStream.of(values).forEach(value -> jsonArray.add(value));
    return jsonArray;
  }

  @NotNull
  public static JsonArray arrayToJsonArray(@NotNull double[] values) {
    JsonArray jsonArray = new JsonArray();
    DoubleStream.of(values).forEach(value -> jsonArray.add(value));
    return jsonArray;
  }

  @NotNull
  public static JsonArray listToJsonArray(@NotNull List<String> values) {
    JsonArray jsonArray = new JsonArray();
    values.forEach(value -> {
      if (value == null) {
        jsonArray.add(JsonNull.INSTANCE);
      }
      else {
        jsonArray.add(value);
      }
    });
    return jsonArray;
  }
}
