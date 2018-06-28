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
package com.android.tools.profilers;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Data type for showing data bytes in UI components.
 */
public enum ContentType {
  BMP,
  CSV,
  GIF,
  HTML,
  JPEG,
  JPG,
  JSON,
  PNG,
  WEBP,
  XML,
  DEFAULT;

  private static final ImmutableSet<ContentType> IMAGE_TYPES = ImmutableSet.of(BMP, GIF, JPEG, JPG, PNG, WEBP);
  private static final Map<ContentType, FileType> FILE_TYPE_MAP = new ImmutableMap.Builder<ContentType, FileType>()
    .put(CSV, FileTypeManager.getInstance().getStdFileType("CSV"))
    .put(HTML, StdFileTypes.HTML)
    .put(JSON, FileTypeManager.getInstance().getStdFileType("JSON"))
    .put(XML, StdFileTypes.XML)
    .build();

  public boolean isImageType() {
    return IMAGE_TYPES.contains(this);
  }

  /**
   * Returns the associated {@link FileType}, if returns null, data is treated as plain text.
   */
  @Nullable
  public FileType getFileType() {
    return FILE_TYPE_MAP.containsKey(this) ? FILE_TYPE_MAP.get(this) : null;
  }

  @NotNull
  public static ContentType get(@NotNull String type) {
    for (ContentType contentType : ContentType.values()) {
      if (type.equalsIgnoreCase(contentType.name())) {
        return contentType;
      }
    }
    return DEFAULT;
  }

  @NotNull
  public static ContentType fromMimeType(@NotNull String mimeType) {
    String[] typeAndSubType = mimeType.split("/", 2);
    return get(typeAndSubType[typeAndSubType.length - 1]);
  }
}
