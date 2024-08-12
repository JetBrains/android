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
package com.google.idea.blaze.aspect;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.MessageLite;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Provides a BufferedReader for the source java files, and a writer for the output proto */
@VisibleForTesting
public class PackageParserIoProvider {

  static final PackageParserIoProvider INSTANCE = new PackageParserIoProvider();

  void writeProto(MessageLite message, Path file) throws IOException {
    try (OutputStream out = Files.newOutputStream(file)) {
      message.writeTo(out);
    }
  }

  BufferedReader getReader(Path file) throws IOException {
    return Files.newBufferedReader(file, StandardCharsets.UTF_8);
  }
}
