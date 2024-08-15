/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.sync.importer.emptylibrary;

import com.intellij.openapi.extensions.ExtensionPointName;

/** Extension interface for modifying empty library filtering */
public interface EmptyLibraryFilterSettings {
  ExtensionPointName<EmptyLibraryFilterSettings> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.EmptyLibraryFilterSettings");

  /** Return false to disable empty jar filtering */
  boolean isEnabled();
}
