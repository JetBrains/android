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
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
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
  private static final ImmutableSet<ContentType> TEXT_TYPES = ImmutableSet.of(CSV, HTML, JSON, XML);
  private static final Map<ContentType, FileType> FILE_TYPE_MAP = new ImmutableMap.Builder<ContentType, FileType>()
    .put(CSV, FileTypeManager.getInstance().getStdFileType("CSV"))
    .put(HTML, HtmlFileType.INSTANCE)
    .put(JSON, FileTypeManager.getInstance().getStdFileType("JSON"))
    .put(XML, XmlFileType.INSTANCE)
    .build();

  private String myType = "";

  /**
   * @return the first part of the underlying MIME type.
   */
  @NotNull
  public String getType() {
    return myType;
  }

  private ContentType withType(@NotNull String type) {
    myType = type;
    return this;
  }

  /**
   * @return true if its type is "text" or its subtype is a known text subtype.
   */
  public boolean isSupportedTextType() {
    return "text".equalsIgnoreCase(getType()) || TEXT_TYPES.contains(this);
  }

  public boolean isSupportedImageType() {
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
  private static ContentType get(@NotNull String type, @NotNull String subtype) {
    for (ContentType contentType : ContentType.values()) {
      if (subtype.equalsIgnoreCase(contentType.name())) {
        return contentType.withType(type);
      }
    }
    return DEFAULT.withType(type);
  }

  @NotNull
  public static ContentType fromMimeType(@NotNull String mimeType) {
    String[] typeAndSubType = mimeType.split("/", 2);
    if (typeAndSubType.length < 2) {
      return typeAndSubType.length == 1 ? DEFAULT.withType(typeAndSubType[0]) : DEFAULT;
    }

    String[] subTypeAndSuffix = typeAndSubType[1].split("\\+", 2);
    // Without suffix: json, xml, html, etc.
    // With suffix: vnd.api+json, svg+xml, etc.
    // See also: https://en.wikipedia.org/wiki/Media_type#Suffix
    return get(typeAndSubType[0], subTypeAndSuffix[subTypeAndSuffix.length - 1]);
  }
}
