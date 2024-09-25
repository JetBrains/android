/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.collect.ImmutableSet;

/** C/C++ file extensions categorized. */
final class CFileExtensions {

  // See https://bazel.build/versions/master/docs/be/c-cpp.html#cc_binary.srcs
  static final ImmutableSet<String> C_FILE_EXTENSIONS = ImmutableSet.of("c");
  static final ImmutableSet<String> CXX_FILE_EXTENSIONS =
      ImmutableSet.of("cc", "cpp", "cxx", "c++", "C");

  static final ImmutableSet<String> CXX_ONLY_HEADER_EXTENSIONS =
      ImmutableSet.of("hh", "hpp", "hxx");
  private static final ImmutableSet<String> SHARED_HEADER_EXTENSIONS = ImmutableSet.of("h", "inc");

  static final ImmutableSet<String> SOURCE_EXTENSIONS =
      ImmutableSet.<String>builder().addAll(C_FILE_EXTENSIONS).addAll(CXX_FILE_EXTENSIONS).build();
  static final ImmutableSet<String> HEADER_EXTENSIONS =
      ImmutableSet.<String>builder()
          .addAll(SHARED_HEADER_EXTENSIONS)
          .addAll(CXX_ONLY_HEADER_EXTENSIONS)
          .build();

  private CFileExtensions() {}
}
