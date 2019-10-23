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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.parser.pages;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

/**
 * Mimics the JSON element with attribute <code>"_class": "MSJSONFileReference"</code> contained within a sketch file.
 */
public class SketchFileReference {
  /**
   * "MSImmutablePage" or "MSImageData"
   */
  @SerializedName("_ref_class")
  private final String refClassType;
  /**
   * Refers to a path within the .sketch archive (e.g. "images/my_image.pdf")
   */
  @SerializedName("_ref")
  private final String ref;

  public SketchFileReference(@NotNull String type, @NotNull String ref) {
    refClassType = type;
    this.ref = ref;
  }

  @NotNull
  public String getRefClassType() {
    return refClassType;
  }

  @NotNull
  public String getRef() {
    return ref;
  }
}
