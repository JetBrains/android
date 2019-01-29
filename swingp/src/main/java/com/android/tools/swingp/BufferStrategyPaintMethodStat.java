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

import com.android.tools.swingp.json.IncludeMethodsSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

@JsonAdapter(IncludeMethodsSerializer.class) // Needed to properly serialize MethodStat fields
public class BufferStrategyPaintMethodStat extends MethodStat {
  @SerializedName("isBufferStrategy")
  private final boolean myIsBufferStrategy;

  public BufferStrategyPaintMethodStat(@NotNull Object owner, boolean isBufferStrategy) {
    super(owner);
    myIsBufferStrategy = isBufferStrategy;
  }
}
