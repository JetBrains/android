/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.intellij.testFramework.rules.TempDirectory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Test utility for creating JAR files. */
public class JarBuilder {
  private boolean addManifest = false;
  private int bloatBytes = 0;
  private final Map<String, String> files = new HashMap<>();
  private final TempDirectory tempDirectory;

  private JarBuilder(TempDirectory tempDirectory) {
    this.tempDirectory = tempDirectory;
  }

  static JarBuilder newEmptyJar(TempDirectory tempDirectory) {
    return new JarBuilder(tempDirectory);
  }

  /** Increases the size of the JAR by adding {@code numBytes} in junk metadata. */
  @CanIgnoreReturnValue
  JarBuilder bloatBy(int numBytes) {
    bloatBytes = numBytes;
    return this;
  }

  @CanIgnoreReturnValue
  JarBuilder addManifest() {
    addManifest = true;
    return this;
  }

  @CanIgnoreReturnValue
  JarBuilder addDirectory(String path) {
    if (!path.endsWith("/")) {
      path = path + "/";
    }
    files.put(path, null);
    return this;
  }

  @CanIgnoreReturnValue
  JarBuilder addFile(String path, String content) {
    files.put(path, content);
    return this;
  }

  File build() throws IOException {
    File jar = tempDirectory.newFile("test.jar");
    try (OutputStream outputStream = new FileOutputStream(jar);
        JarOutputStream jarOutputStream =
            addManifest
                ? new JarOutputStream(outputStream, createVersionedManifest())
                : new JarOutputStream(outputStream)) {
      if (bloatBytes > 0) {
        // The comment isn't compressed, so we can use it to artificially bloat the JAR.
        jarOutputStream.setComment(stringOfSize(bloatBytes));
      }
      for (Map.Entry<String, String> entry : files.entrySet()) {
        jarOutputStream.putNextEntry(new JarEntry(entry.getKey()));
        String content = entry.getValue();
        if (content != null) {
          jarOutputStream.write(content.getBytes(UTF_8));
        }
        jarOutputStream.closeEntry();
      }
    }
    return jar;
  }

  private static Manifest createVersionedManifest() {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    return manifest;
  }

  private static String stringOfSize(int numChars) {
    StringBuilder sb = new StringBuilder();
    while (numChars > 0) {
      sb.append(' ');
      numChars--;
    }
    return sb.toString();
  }
}
