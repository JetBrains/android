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
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.SerializedName;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * By default, gson only serializes fields. This serializer adds support for serializing both
 * fields AND methods, if annotated with {@link SerializedName}
 */
public class IncludeMethodsSerializer implements JsonSerializer<Object> {
  @Override
  public JsonElement serialize(Object src, Type typeOfSrc, JsonSerializationContext context) {
    JsonElement jsonResult = JsonNull.INSTANCE;
    try {
      JsonObject jsonObject = null;
      List<Field> fields = new ArrayList<>();
      List<Method> methods = new ArrayList<>();

      Class<?> curr = src.getClass();
      while (curr != null) {
        Collections.addAll(fields, curr.getDeclaredFields());
        Collections.addAll(methods,curr.getDeclaredMethods());

        curr = curr.getSuperclass();
      }

      for (Field field : fields) {
        SerializedName nameAnnotation = field.getAnnotation(SerializedName.class);
        if (nameAnnotation != null) {
          jsonObject = (jsonObject != null) ? jsonObject : new JsonObject();
          field.setAccessible(true);
          jsonObject.add(nameAnnotation.value(), context.serialize(field.get(src)));
        }
      }
      for (Method method : methods) {
        SerializedName nameAnnotation = method.getAnnotation(SerializedName.class);
        if (nameAnnotation != null) {
          jsonObject = (jsonObject != null) ? jsonObject : new JsonObject();
          method.setAccessible(true);
          jsonObject.add(nameAnnotation.value(), context.serialize(method.invoke(src)));
        }
      }

      if (jsonObject != null) {
        jsonResult = jsonObject;
      }
    }
    catch (IllegalAccessException | InvocationTargetException ignored) {
      // If we fail to serialize for any reason, we'll just return JsonNull
    }

    return jsonResult;
  }
}
