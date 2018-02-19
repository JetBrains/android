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
package com.android.tools.idea.rendering.parsers;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for parsers that support declaration of inlined {@code aapt:attr} attributes
 */
public interface AaptAttrParser {
  /**
   * Returns a {@link ImmutableMap} that contains all the {@code aapt:attr} elements declared in this or any children parsers. This list
   * can be used to resolve {@code @aapt/_aapt} references into this parser.
   */
  @NotNull
  ImmutableMap<String, TagSnapshot> getAaptDeclaredAttrs();
}
