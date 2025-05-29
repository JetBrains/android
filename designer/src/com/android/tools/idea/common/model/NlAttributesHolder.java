/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.model;

import static com.android.SdkConstants.ANDROID_URI;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface for elements dealing with {@link NlComponent} attributes.
 */
public interface NlAttributesHolder extends NlAttributesReader {
  void setAttribute(@Nullable String namespace, @NotNull String attribute, @Nullable String value);

  default void removeAndroidAttribute(@NotNull String name) {
    setAttribute(ANDROID_URI, name, null);
  }

  default void removeAttribute(@NotNull String namespace, @NotNull String name) {
    setAttribute(namespace, name, null);
  }

  /**
   * Sets an attribute value in the "android" namespace.
   */
  default void setAndroidAttribute(@NotNull String name, @Nullable String value) {
    setAttribute(ANDROID_URI, name, value);
  }

  @Nullable
  @Override
  default String getAndroidAttribute(@NotNull String attribute) {
    return getAttribute(ANDROID_URI, attribute);
  }
}
