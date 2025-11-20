/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.qsync.project

/**
 * Data class to hold sets of file extensions and names relevant for project structure analysis.
 * This allows for easy injection and testing.
 *
 * @property jvmExtensions Extensions for Java and Kotlin source files.
 * @property ccSourceExtensions Extensions for C/C++ source files.
 * @property nonSourceExtensions Extensions of files to be completely ignored during source collection.
 */
// TODO(b/463719611): Inject the file extensions from ProjectBuilder instead
data class FileExtensions(
  val jvmExtensions: Set<String> = setOf("java", "kt"),
  // Note: C/C++ header files are included here as bazel query includes them in the BuildGraph's source files.
  val ccSourceExtensions: Set<String> = setOf("c", "C", "cc", "cpp", "cxx", "c++", "h", "hh", "hpp", "hxx", "inc", "inl", "H"),
  val protoSourceExtensions: Set<String> = setOf("proto"),
)
