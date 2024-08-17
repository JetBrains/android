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
package com.google.idea.common.experiments;

import com.intellij.UtilBundle;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import javax.annotation.Nullable;

/** Utils for serialization. */
public final class SerializationUtil {

  private SerializationUtil() {}

  public static void saveToDisk(File file, Serializable serializable) throws IOException {
    ensureExists(file.getParentFile());
    try (FileOutputStream fos = new FileOutputStream(file);
        ObjectOutputStream oos = new ObjectOutputStream(fos)) {
      oos.writeObject(serializable);
    }
  }

  @Nullable
  public static Object loadFromDisk(File file) throws IOException {
    if (!file.exists()) {
      return null;
    }
    try (FileInputStream fis = new FileInputStream(file);
        ObjectInputStream ois = new ObjectInputStream(fis)) {
      return ois.readObject();
    } catch (ClassNotFoundException e) {
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
