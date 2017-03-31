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

import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/*
 * Create {@link JarLibraryDependency} from IdeaSingleEntryLibraryDependency returned by JetBrain's Plugin.
 */
public class IdeaJarLibraryDependencyFactory {
  @Nullable
  public JarLibraryDependency create(@NotNull IdeaSingleEntryLibraryDependency original) {
    File binaryPath = original.getFile();
    if (binaryPath == null) {
      return null;
    }
    String scope = null;
    IdeaDependencyScope originalScope = original.getScope();
    if (originalScope != null) {
      scope = originalScope.getScope();
    }
    boolean resolved = JarLibraryDependency.isResolved(binaryPath.getName());
    String name = JarLibraryDependency.getDependencyName(binaryPath, resolved);
    return new JarLibraryDependency(name, binaryPath, original.getSource(), original.getJavadoc(), scope,
                                    original.getGradleModuleVersion(), resolved);
  }
}
