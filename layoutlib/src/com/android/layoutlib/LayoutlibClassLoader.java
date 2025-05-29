/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.layoutlib;

import android.os._Original_Build;
import com.android.tools.environment.Logger;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.InputStream;
import java.util.Deque;
import java.util.LinkedList;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.commons.ClassRemapper;
import org.jetbrains.org.objectweb.asm.commons.Remapper;

/**
 * {@link ClassLoader} used for Layoutlib. Currently it only generates {@code android.os.Build} dynamically by copying the class in
 * {@link _Original_Build}.
 * By generating {@code android.os.Build} dynamically, we avoid to have it in the classpath of the plugins. Some plugins check for the
 * existence of the class in order to detect if they are running Android. This is just a workaround for that.
 */
public class LayoutlibClassLoader extends ClassLoader {
  private static final Logger LOG = Logger.getInstance(LayoutlibClassLoader.class);

  public LayoutlibClassLoader(@NotNull ClassLoader parent) {
    super(parent);

    // Define the android.os.Build and all inner classes by renaming everything in android.os._Original_Build
    generate(_Original_Build.class, (className, classBytes) -> defineClass(className, classBytes, 0, classBytes.length));
  }

  @NotNull
  private static String toBinaryClassName(@NotNull String name) {
    return name.replace('.', '/');
  }

  @NotNull
  private static String toClassName(@NotNull String name) {
    return name.replace('/', '.');
  }

  /**
   * Creates a copy of the passed class, replacing its name with "android.os.Build".
   */
  @VisibleForTesting
  static void generate(@NotNull Class<?> originalBuildClass, @NotNull BiConsumer<String, byte[]> defineClass) {
    ClassLoader loader = originalBuildClass.getClassLoader();
    String originalBuildClassName = originalBuildClass.getName();
    String originalBuildBinaryClassName = toBinaryClassName(originalBuildClassName);
    Deque<String> pendingClasses = new LinkedList<>();
    pendingClasses.push(originalBuildClassName);

    Remapper remapper = new Remapper() {
      @Override
      public String map(String typeName) {
        if (typeName.startsWith(originalBuildBinaryClassName)) {
          return "android/os/Build" + typeName.substring(originalBuildBinaryClassName.length());
        }

        return typeName;
      }
    };

    while (!pendingClasses.isEmpty()) {
      String name = pendingClasses.pop();
      String trimmedName =
        name.startsWith(originalBuildClassName) ?
        name.substring(originalBuildClassName.length()) :
        name;

      String newName = "android.os.Build" + trimmedName;
      String binaryName = toBinaryClassName(name);

      try (InputStream is = loader.getResourceAsStream(binaryName + ".class")) {
        ClassWriter writer = new ClassWriter(0);
        ClassReader reader = new ClassReader(is);
        ClassRemapper classRemapper = new ClassRemapper(writer, remapper) {
          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            if (outerName != null && outerName.startsWith(binaryName)) {
              pendingClasses.push(toClassName(name));
            }
            super.visitInnerClass(name, outerName, innerName, access);
          }
        };
        reader.accept(classRemapper, 0);
        defineClass.accept(newName, writer.toByteArray());
      }
      catch (IOException e) {
        LOG.warn("Unable to define android.os.Build", e);
      }
    }
  }
}
