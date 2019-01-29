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
package com.android.tools.idea.gradle.model.java;

import com.android.java.model.JavaLibrary;
import com.android.java.model.LibraryVersion;
import java.io.Serializable;
import org.gradle.tooling.model.GradleModuleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/*
 * Create {@link JarLibraryDependency} from JavaLibrary returned by Java Library Plugin.
 */
public class NewJarLibraryDependencyFactory {
  @Nullable
  public JarLibraryDependency create(@NotNull JavaLibrary original, @Nullable String scope) {
    File binaryPath = original.getJarFile();
    if (binaryPath == null) {
      return null;
    }
    boolean resolved = JarLibraryDependency.isResolved(binaryPath.getName());
    String name = JarLibraryDependency.getDependencyName(binaryPath, resolved);
    GradleModuleVersionImpl version = null;
    if (resolved && original.getLibraryVersion() != null) {
      version = new GradleModuleVersionImpl(original.getLibraryVersion());
    }
    return new JarLibraryDependency(name, binaryPath, original.getSource(), original.getJavadoc(), scope, version, resolved);
  }

  private static class GradleModuleVersionImpl implements GradleModuleVersion, Serializable {
    @NotNull private final String myGroup;
    @NotNull private final String myName;
    @NotNull private final String myVersion;

    GradleModuleVersionImpl(@NotNull LibraryVersion version) {
      myGroup = version.getGroup();
      myName = version.getName();
      myVersion = version.getVersion();
    }

    @Override
    @NotNull
    public String getGroup() {
      return myGroup;
    }

    @Override
    @NotNull
    public String getName() {
      return myName;
    }

    @Override
    @NotNull
    public String getVersion() {
      return myVersion;
    }
  }
}
