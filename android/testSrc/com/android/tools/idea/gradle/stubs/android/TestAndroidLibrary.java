/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.android;

import com.android.ide.common.gradle.model.stubs.AndroidLibraryStub;
import com.android.ide.common.gradle.model.stubs.MavenCoordinatesStub;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class TestAndroidLibrary extends AndroidLibraryStub {
  public TestAndroidLibrary(@NotNull File bundle, @NotNull File jarFile) {
    this(bundle, jarFile, null);
  }

  public TestAndroidLibrary(@NotNull File bundle, @NotNull File jarFile, @Nullable String project) {
    this(bundle, jarFile, project, null);
  }

  public TestAndroidLibrary(@NotNull File bundle, @NotNull File jarFile, @Nullable String project, @Nullable String projectVariant) {
    super(Lists.newArrayList(), new File("proguardRules"), new File("lint"), new File("publicResources"), new MavenCoordinatesStub(),
          bundle, jarFile.getParentFile(), Lists.newArrayList(), Lists.newArrayList(), new File("manifest"),
          jarFile, new File("res"), new File("assets"), project, null, projectVariant, false);
  }

  public void addLocalJar(@NotNull File localJar) {
    getLocalJars().add(localJar);
  }
}
