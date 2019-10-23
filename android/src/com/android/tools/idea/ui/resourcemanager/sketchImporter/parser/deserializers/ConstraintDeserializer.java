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

import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ResizingConstraint;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import java.lang.reflect.Type;
import org.jetbrains.annotations.NotNull;

/**
 * INCOMPLETE! Not all cases for constraints have been treated and the constant values below might not be accurate
 * <p>
 * In the sketch JSON, resizing constraints are set by an integer number that represents the
 * sum of powers of two for each constraint that is NOT applied on the shape.
 * If there are no constraints applied, the {@code resizingConstraint} value equals 63.
 * This is a {@link JsonDeserializer} that turns this number into a {@link ResizingConstraint} for easy manipulation.
 */
public class ConstraintDeserializer implements JsonDeserializer<ResizingConstraint> {
  public static final int WIDTH_HEIGHT_CONSTRAINT = 44;
  public static final int HEIGHT_CONSTRAINT = 47;
  public static final int WIDTH_CONSTRAINT = 60;
  public static final int NO_CONSTRAINT = 63;

  @Override
  public ResizingConstraint deserialize(@NotNull JsonElement json, @NotNull Type typeOfT, @NotNull JsonDeserializationContext context) {

    final int constraint = json.getAsInt();
    boolean constrainWidth;
    boolean constrainHeight;

    switch (constraint) {
      case WIDTH_HEIGHT_CONSTRAINT:
        constrainWidth = true;
        constrainHeight = true;
        break;
      case HEIGHT_CONSTRAINT:
        constrainWidth = false;
        constrainHeight = true;
        break;
      case WIDTH_CONSTRAINT:
        constrainWidth = true;
        constrainHeight = false;
        break;
      case NO_CONSTRAINT:
      default:
        constrainWidth = false;
        constrainHeight = false;
    }

    return new ResizingConstraint(constrainWidth, constrainHeight);
  }
}