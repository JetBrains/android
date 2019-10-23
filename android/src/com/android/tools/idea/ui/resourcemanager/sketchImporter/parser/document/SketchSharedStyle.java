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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.document;

import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages.SketchStyle;
import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

public class SketchSharedStyle {
  @SerializedName("do_objectID")
  private final String objectId;
  private final String name;
  private final SketchStyle value;

  public SketchSharedStyle(@NotNull String objectId,
                           @NotNull String name,
                           @NotNull SketchStyle value) {
    this.objectId = objectId;
    this.name = name;
    this.value = value;
  }

  @NotNull
  public String getObjectId() {
    return objectId;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public SketchStyle getValue() {
    return value;
  }
}
