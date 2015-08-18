/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.util;

import com.google.common.base.Charsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Properties;

import static com.google.common.io.Closeables.close;
import static com.intellij.openapi.util.io.FileUtilRt.createParentDirs;

public final class PropertiesUtil {
  private PropertiesUtil() {
  }

  @NotNull
  public static Properties getProperties(@NotNull File filePath) throws IOException {
    if (filePath.isDirectory()) {
      throw new IllegalArgumentException(String.format("The path '%1$s' belongs to a directory!", filePath.getPath()));
    }
    if (!filePath.exists()) {
      return new Properties();
    }
    Properties properties = new Properties();
    Reader reader = null;
    try {
      reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(filePath)), Charsets.UTF_8);
      properties.load(reader);
    }
    finally {
      close(reader, true);
    }
    return properties;
  }

  public static void savePropertiesToFile(@NotNull Properties properties, @NotNull File filePath, @Nullable String comments)
    throws IOException {
    createParentDirs(filePath);
    FileOutputStream out = null;
    try {
      //noinspection IOResourceOpenedButNotSafelyClosed
      out = new FileOutputStream(filePath);
      // Note that we don't write the properties files in UTF-8; this will *not* write the
      // files with the default platform encoding; instead, it will write it using ISO-8859-1 and
      // \\u escaping syntax for other characters. This will work with older versions of the Gradle
      // plugin which does not read the .properties file with UTF-8 encoding. In the future when
      // nobody is using older (0.7.x) versions of the Gradle plugin anymore we can upgrade this
      properties.store(out, comments);
    }
    finally {
      close(out, true);
    }
  }
}
