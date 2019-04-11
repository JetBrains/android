/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.deserializers;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.lang.reflect.Type;
import org.jetbrains.annotations.NotNull;

/**
 * In the sketch JSON, colors are represented through floating-point values denoting percentages that correspond to the ARGB values.
 * This is a {@link JsonDeserializer} that turns that into a {@link Color} for easy manipulation.
 */
public class ColorDeserializer implements JsonDeserializer<Color> {
  private static final String ALPHA = "alpha";
  private static final String BLUE = "blue";
  private static final String GREEN = "green";
  private static final String RED = "red";

  @Override
  @NotNull
  public Color deserialize(@NotNull JsonElement json,
                           @NotNull Type typeOfT,
                           @NotNull JsonDeserializationContext context) {
    final JsonObject jsonObject = json.getAsJsonObject();

    final int alpha = (int)Math.round(jsonObject.get(ALPHA).getAsDouble() * 255);
    final int blue = (int)Math.round(jsonObject.get(BLUE).getAsDouble() * 255);
    final int green = (int)Math.round(jsonObject.get(GREEN).getAsDouble() * 255);
    final int red = (int)Math.round(jsonObject.get(RED).getAsDouble() * 255);

    return new Color(red, green, blue, alpha);
  }
}
