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
package com.android.tools.idea.gradle.facet;

import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.collect.Lists;
import com.intellij.openapi.util.io.FileUtil;
import org.gradle.tooling.model.idea.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExtIdeaCompilerOutput;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaModel implements Serializable {
  @NotNull @NonNls private static final String UNRESOLVED_DEPENDENCY_PREFIX = "unresolved dependency - ";

  @NotNull private final List<IdeaContentRoot> myContentRoots;
  @NotNull private final List<IdeaModuleDependency> myModuleDependencies;
  @NotNull private final List<IdeaSingleEntryLibraryDependency> myLibraryDependencies;
  @NotNull private final List<String> myUnresolvedDependencyNames;
  @NotNull private final ExtIdeaCompilerOutput myCompilerOutput;

  @NotNull
  public static JavaModel newJavaModel(@NotNull IdeaModule module, @Nullable ModuleExtendedModel model) {
    List<IdeaContentRoot> contentRoots = Lists.newArrayList();
    for (IdeaContentRoot root : getContentRoots(module, model)) {
      contentRoots.add(root);
    }

    List<IdeaModuleDependency> moduleDependencies = Lists.newArrayList();
    List<IdeaSingleEntryLibraryDependency> libraryDependencies = Lists.newArrayList();
    List<String> unresolvedDependencyNames = Lists.newArrayList();

    for (IdeaDependency dependency : getDependencies(module)) {
      if (dependency instanceof IdeaModuleDependency) {
        moduleDependencies.add((IdeaModuleDependency)dependency);
        continue;
      }
      if (dependency instanceof IdeaSingleEntryLibraryDependency) {
        IdeaSingleEntryLibraryDependency libDependency = (IdeaSingleEntryLibraryDependency)dependency;
        if (isResolved(libDependency)) {
          libraryDependencies.add(libDependency);
          continue;
        }
        String name = getUnresolvedDependencyName(libDependency);
        if (name != null) {
          unresolvedDependencyNames.add(name);
        }
      }
    }
    ExtIdeaCompilerOutput compilerOutput = model != null ? model.getCompilerOutput() : null;
    if (compilerOutput == null) {
      compilerOutput = new IdeaCompilerOutput(contentRoots);
    }
    return new JavaModel(contentRoots, moduleDependencies, libraryDependencies, unresolvedDependencyNames, compilerOutput);
  }

  @NotNull
  private static Collection<? extends IdeaContentRoot> getContentRoots(@NotNull IdeaModule module, @Nullable ModuleExtendedModel model) {
    Collection<? extends IdeaContentRoot> contentRoots = model != null ? model.getContentRoots() : module.getContentRoots();
    if (contentRoots == null) {
      contentRoots = module.getContentRoots();
    }
    return contentRoots != null ? contentRoots : Collections.<IdeaContentRoot>emptyList();
  }

  @NotNull
  private static List<? extends IdeaDependency> getDependencies(IdeaModule module) {
    List<? extends IdeaDependency> dependencies = module.getDependencies().getAll();
    return dependencies != null ? dependencies : Collections.<IdeaDependency>emptyList();
  }

  private static boolean isResolved(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    String libraryName = getFileName(dependency);
    return libraryName != null && !libraryName.startsWith(UNRESOLVED_DEPENDENCY_PREFIX);
  }

  @Nullable
  private static String getUnresolvedDependencyName(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    String libraryName = getFileName(dependency);
    if (libraryName == null) {
      return null;
    }
    // Gradle uses names like 'unresolved dependency - commons-collections commons-collections 3.2' for unresolved dependencies.
    // We report the unresolved dependency as 'commons-collections:commons-collections:3.2'
    return libraryName.substring(UNRESOLVED_DEPENDENCY_PREFIX.length()).replace(' ', ':');
  }

  @Nullable
  private static String getFileName(@NotNull IdeaSingleEntryLibraryDependency dependency) {
    File binaryPath = dependency.getFile();
    return binaryPath != null ? binaryPath.getName() : null;
  }

  public JavaModel(@NotNull List<IdeaContentRoot> contentRoots,
                   @NotNull List<IdeaModuleDependency> moduleDependencies,
                   @NotNull List<IdeaSingleEntryLibraryDependency> libraryDependencies,
                   @NotNull List<String> unresolvedDependencyNames,
                   @NotNull ExtIdeaCompilerOutput compilerOutput) {
    myContentRoots = contentRoots;
    myModuleDependencies = moduleDependencies;
    myLibraryDependencies = libraryDependencies;
    myUnresolvedDependencyNames = unresolvedDependencyNames;
    myCompilerOutput = compilerOutput;
  }

  @NotNull
  public List<IdeaContentRoot> getContentRoots() {
    return myContentRoots;
  }

  @NotNull
  public List<IdeaModuleDependency> getModuleDependencies() {
    return myModuleDependencies;
  }

  @NotNull
  public List<IdeaSingleEntryLibraryDependency> getLibraryDependencies() {
    return myLibraryDependencies;
  }

  @NotNull
  public List<String> getUnresolvedDependencyNames() {
    return myUnresolvedDependencyNames;
  }

  @NotNull
  public ExtIdeaCompilerOutput getCompilerOutput() {
    return myCompilerOutput;
  }

  // We hard-code the paths of the output folders in case we obtain a null ModuleExtendedModel. This is meant as a temporary
  // workaround until this is resolved on the IDEA side.
  // See https://code.google.com/p/android/issues/detail?id=70490
  public static class IdeaCompilerOutput implements ExtIdeaCompilerOutput {
    @NonNls private static final String MAIN_SOURCE_SET_NAME = "main";
    @NonNls private static final String TEST_SOURCE_SET_NAME = "test";

    private File myMainClassesDir;
    private File myMainResourcesDir;
    private File myTestClassesDir;
    private File myTestResourcesDir;

    public IdeaCompilerOutput(@NotNull List<IdeaContentRoot> contentRoots) {
      File buildFolderPath = null;

      for (IdeaContentRoot contentRoot : contentRoots) {
        for (File excluded : contentRoot.getExcludeDirectories()) {
          if (GradleUtil.BUILD_DIR_DEFAULT_NAME.equals(excluded.getName())) {
            buildFolderPath = excluded;
            break;
          }
        }
        if (buildFolderPath != null) {
          break;
        }
      }

      if (buildFolderPath != null) {
        myMainClassesDir = createClassesFolderPath(buildFolderPath, MAIN_SOURCE_SET_NAME);
        myMainResourcesDir = createResourcesFolderPath(buildFolderPath, MAIN_SOURCE_SET_NAME);
        myTestClassesDir = createClassesFolderPath(buildFolderPath, TEST_SOURCE_SET_NAME);
        myTestResourcesDir = createResourcesFolderPath(buildFolderPath, TEST_SOURCE_SET_NAME);
      }
    }

    @NotNull
    private static File createClassesFolderPath(@NotNull File buildFolderPath, @NotNull String sourceSetName) {
      return new File(buildFolderPath, FileUtil.join("classes", sourceSetName));
    }

    @NotNull
    private static File createResourcesFolderPath(@NotNull File buildFolderPath, @NotNull String sourceSetName) {
      return new File(buildFolderPath, FileUtil.join("resources", sourceSetName));
    }

    @Override
    @Nullable
    public File getMainClassesDir() {
      return myMainClassesDir;
    }

    @Override
    @Nullable
    public File getMainResourcesDir() {
      return myMainResourcesDir;
    }

    @Override
    @Nullable
    public File getTestClassesDir() {
      return myTestClassesDir;
    }

    @Override
    @Nullable
    public File getTestResourcesDir() {
      return myTestResourcesDir;
    }
  }
}
