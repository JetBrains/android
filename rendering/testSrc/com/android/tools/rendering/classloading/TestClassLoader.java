/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.rendering.classloading;

import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader;
import com.android.tools.rendering.classloading.loaders.StaticLoader;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Class loader that stores a number of .class files loaded in memory and loads the classes.
 */
public class TestClassLoader extends DelegatingClassLoader {
  /**
   * Creates a new {@link TestClassLoader}.
   * @param parent the parent {@link ClassLoader} or null if no parent class loader is defined.
   * @param definedClasses a map with the class name to define and the .class file contents.
   */
  TestClassLoader(@Nullable ClassLoader parent, @NotNull Map<String, byte[]> definedClasses) {
    super(parent, new StaticLoader(definedClasses));
  }

  /**
   * Creates a new {@link TestClassLoader} with no parent.
   * @param definedClasses a map with the class name to define and the .class file contents.
   */
  TestClassLoader(@NotNull Map<String, byte[]> definedClasses) {
    this(null, definedClasses);
  }
}
