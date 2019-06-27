/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.layoutlib;

import com.google.common.collect.ImmutableSet;

import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.DOT_CLASS;

public class LayoutlibNativeClassLoader extends ClassLoader {
  private static Logger LOG = Logger.getInstance(LayoutlibClassLoader.class);
  private final ImmutableSet<String> myJarClasses;

  public LayoutlibNativeClassLoader(@NotNull URL url) {
    super(LayoutlibNativeClassLoader.class.getClassLoader());
    myJarClasses = getJarClasses(url);
  }

  @Override
  protected Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
    String lookupName = name;
    int dollarIdx = lookupName.indexOf('$');
    if (dollarIdx != -1) {
      // If it's an inner class, filter it if the parent class is contained in the list
      lookupName = lookupName.substring(0, dollarIdx);
    }
    if (myJarClasses.contains(lookupName)) {
      throw new ClassNotFoundException();
    }
    return super.loadClass(name, resolve);
  }

  @NotNull
  private static ImmutableSet<String> getJarClasses(@NotNull URL url) {
    ImmutableSet.Builder<String> builder = ImmutableSet.builder();
    try (ZipInputStream zipStream = new ZipInputStream(url.openStream())) {
      for (ZipEntry entry = zipStream.getNextEntry(); entry != null; entry = zipStream.getNextEntry()) {
        String name = entry.getName();
        if (name != null && name.endsWith(DOT_CLASS)) {
          // Transform the class file path to the class name
          String newName = name.substring(0, name.length() - DOT_CLASS.length()).replace("/", ".");
          if (!newName.startsWith("org.xmlpull.v1") && !newName.contains("$")) {
            // Filter kxml classes (so we use the ones in studio) and the inner classes. The inner classes will be filtered by
            // using the parent class name.
            builder.add(newName);
          }
        }
      }
    }
    catch (IOException e) {
      LOG.warn("Failed to read layoutlib native jar", e);
    }
    return builder.build();
  }
}
