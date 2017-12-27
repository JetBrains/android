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
package com.android.tools.idea.navigator.nodes.other;

import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import javax.swing.*;

enum NonAndroidSourceType {
  JAVA(JavaSourceRootType.SOURCE, "java", AllIcons.Modules.SourceRoot),
  TESTS(JavaSourceRootType.TEST_SOURCE, "tests", AllIcons.Modules.SourceRoot),
  RESOURCES(JavaResourceRootType.RESOURCE, "resources", AllIcons.Modules.ResourcesRoot),
  TEST_RESOURCES(JavaResourceRootType.TEST_RESOURCE, "test-resources", AllIcons.Modules.TestResourcesRoot);

  @NotNull public final JpsModuleSourceRootType rootType;
  @NotNull public final String presentableName;
  @NotNull public final Icon icon;

  NonAndroidSourceType(@NotNull JpsModuleSourceRootType rootType, @NotNull String name, @NotNull Icon icon) {
    this.rootType = rootType;
    this.presentableName = name;
    this.icon = icon;
  }
}
