/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.util;

import com.google.common.io.Closeables;
import com.intellij.UtilBundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;

/** Utils for serialization. */
public class SerializationUtil {

  /**
   * Write {@link Serializable} to disk.
   *
   * @throws IOException if serialization fails.
   */
  public static void saveToDisk(File file, Serializable serializable) throws IOException {
    ensureExists(file.getParentFile());
    FileOutputStream fos = null;
    try {
      fos = new FileOutputStream(file);
      ObjectOutputStream oos = new ObjectOutputStream(fos);
      try {
        oos.writeObject(serializable);
      } finally {
        Closeables.close(oos, false);
      }
    } finally {
      Closeables.close(fos, false);
    }
  }

  /**
   * Read the serialized objects from disk. Returns null if the file doesn't exist or is empty.
   *
   * @throws IOException if deserialization fails.
   */
  public static Object loadFromDisk(File file, final Iterable<ClassLoader> classLoaders)
      throws IOException {
    try (FileInputStream fin = new FileInputStream(file)) {
      ObjectInputStream ois =
          new ObjectInputStream(fin) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc)
                throws IOException, ClassNotFoundException {
              String name = desc.getName();
              for (ClassLoader loader : classLoaders) {
                try {
                  return Class.forName(name, false, loader);
                } catch (ClassNotFoundException e) {
                  // Ignore - will throw eventually in super
                }
              }
              return super.resolveClass(desc);
            }
          };
      try {
        return ois.readObject();
      } finally {
        Closeables.close(ois, false);
      }
    } catch (ClassNotFoundException | ClassCastException | IllegalStateException e) {
      // rethrow as an IOException, handled by callers
      throw new IOException(e);
    }
  }

  private static void ensureExists(File dir) throws IOException {
    if (!dir.exists() && !dir.mkdirs()) {
      throw new IOException(
          UtilBundle.message("exception.directory.can.not.create", dir.getPath()));
    }
  }
}
