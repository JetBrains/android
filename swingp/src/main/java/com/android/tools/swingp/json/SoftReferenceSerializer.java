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
package com.android.tools.swingp.json;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.ref.SoftReference;
import java.lang.reflect.Type;

public final class SoftReferenceSerializer implements JsonSerializer<SoftReference<?>> {
  @Override
  public JsonElement serialize(SoftReference<?> reference, Type typeOfSrc, JsonSerializationContext context) {
    Object value = reference.get();
    return new JsonPrimitive(value == null ? "<gc>" : value.getClass().getSimpleName());
  }
}
