/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

public enum StatusCode {
  NO_ERROR,
  HEAP_IS_TOO_BIG,
  CANT_TAG_OBJECTS, // JVM doesn't have a capability of tagging objects configured
  OBJECTS_MAP_IS_TOO_BIG, // The size of the object map exceeded the limit
  CLASS_FIELDS_CACHE_IS_TOO_BIG, // The size of the class fields cache exceeded the limit
  WRONG_ROOT_OBJECT_ID, // Something went wrong: one of the root
  // objects had the wrong id after enumeration
  LOW_MEMORY, // LowMemory state occurred during the heap traversal
  // (and collecting was immediately stopped)
  AGENT_LOAD_FAILED, // Loading object tagging java agent failed
}