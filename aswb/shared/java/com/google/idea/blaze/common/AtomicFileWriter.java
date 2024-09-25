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

import com.google.errorprone.annotations.MustBeClosed;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes files atomically by using a temporary file.
 *
 * <p>While writing to the given file, we create a temporary file alongside it with the extension
 * {@code .tmp} added. If writing completes successfully, we atomically rename the temporary file to
 * the final name, overwriting the existing one. Otherwise, we just delete the temporary file.
 *
 * <p>The parent directory is created if necessary when this class is instantiated.
 *
 * Pattern for usage:
 *
 * <pre>
 *   try (AtomicFileWriter writer = AtomicFileWriter.create(destPath)) {
 *     writer.getOutputStream().write(myData); // or otherwise write to the stream
 *     writer.onWriteComplete();
 *   }
 * </pre>
 *
 * If {@code #onWriteComplete} is not called (due to an exception being thrown, for example), the
 * existing file is left unmodified.
 */
public class AtomicFileWriter implements Closeable {
  private final Path destination;
  private final Path tmpFile;
  private final OutputStream outputStream;

  @MustBeClosed
  public static AtomicFileWriter create(Path destination) throws IOException {
    return new AtomicFileWriter(destination);
  }

  private AtomicFileWriter(Path destination) throws IOException {
    this.destination = destination;
    this.tmpFile = destination.resolveSibling(destination.getFileName().toString() + ".tmp");
    if (!Files.exists(tmpFile.getParent())) {
      Files.createDirectories(tmpFile.getParent());
    }
    this.outputStream = new FileOutputStream(tmpFile.toFile());
  }

  public OutputStream getOutputStream() {
    return outputStream;
  }

  public void onWriteComplete() throws IOException {
    outputStream.close();
    Files.move(
        tmpFile, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
  }

  @Override
  public void close() throws IOException {
    outputStream.close();
    Files.deleteIfExists(tmpFile);
  }
}
