/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.common;

import com.google.common.collect.Interner;
import java.nio.file.Path;

/**
 * Stores static interner instances to enable de-duplication of string and other objects in-memory.
 */
public class Interners {

  private Interners() {}

  public static final Interner<String> STRING =
      com.google.common.collect.Interners.newWeakInterner();

  public static final Interner<Path> PATH = com.google.common.collect.Interners.newWeakInterner();

  /** Returns an interned path from the given string. */
  public static Path pathOf(String path) {
    return PATH.intern(Path.of(path));
  }
}
