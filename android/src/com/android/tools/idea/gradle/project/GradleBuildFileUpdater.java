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
package com.android.tools.idea.gradle.project;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.parser.*;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GradleBuildFileUpdater listens for module-level events and updates the settings.gradle and build.gradle files to reflect any changes it
 * sees.
 */
public class GradleBuildFileUpdater extends ModuleAdapter implements BulkFileListener, ModuleRootListener {
  private static final Logger LOG = Logger.getInstance(GradleBuildFileUpdater.class);

  private static Map<DependencyScope, Dependency.Scope> SCOPE_CONVERSIONS = ImmutableMap.of(
    DependencyScope.COMPILE, Dependency.Scope.COMPILE,
    DependencyScope.PROVIDED, Dependency.Scope.PROVIDED,
    DependencyScope.RUNTIME, Dependency.Scope.APK,
    DependencyScope.TEST, Dependency.Scope.INSTRUMENT_TEST_COMPILE
  );
  private static final Pattern GRADLE_CACHE_PATTERN = Pattern.compile(".*\\.gradle/caches/[^/]+/[^/]+/([^/]+)/([^/]+)/([^/]+)/[^/]+/[^/]+");
  private static final Pattern INGORE_EXPLODED_BUNDLES_PATTERN = Pattern.compile(".*exploded-bundles+/.*");
  private static final Pattern EXPLODED_AAR_PATTERN = Pattern.compile(".*exploded-aar+/([^/]+)/([^/]+)/([^/]+)/[^/]+");
  private static final Pattern ANDROID_EXTRAS_PATTERN =
      Pattern.compile(".*/sdk/extras/android/m2repository/com/android/support/([^/]+)/([^/]+)/([^/]+)");

  // Module doesn't implement hashCode(); we can't use a map
  private final Collection<Pair<Module, GradleBuildFile>> myBuildFiles = Lists.newArrayList();
  private final GradleSettingsFile mySettingsFile;
  private final Project myProject;
  private final ModulesProvider myModulesProvider;

  public GradleBuildFileUpdater(@NotNull Project project) {
    myProject = project;
    myModulesProvider = DefaultModulesProvider.createForProject(myProject);
    mySettingsFile = GradleSettingsFile.get(project);
    findAndAddAllBuildFiles();
  }

  @Override
  public void moduleAdded(@NotNull final Project project, @NotNull final Module module) {
    VirtualFile vBuildFile = GradleUtil.getGradleBuildFile(module);
    if (vBuildFile != null) {
      put(module, new GradleBuildFile(vBuildFile, project));
    }

    // The module has probably already been added to the settings file but let's call this to be safe.
    if (mySettingsFile != null) {
      mySettingsFile.addModule(module);
    }
  }

  @Override
  public void moduleRemoved(@NotNull Project project, @NotNull final Module module) {
    remove(module);
    if (mySettingsFile != null) {
      mySettingsFile.removeModule(module);
    }
  }

  @Override
  public void before(@NotNull List<? extends VFileEvent> events) {
  }

  /**
   * This gets called on all file system changes, but we're interested in changes to module root directories. When we see them, we'll update
   * the settings.gradle file. Note that users can also refactor modules by renaming them, which just changes their display name and not
   * the filesystem directory -- when that happens, this class gets a
   * {@link ModuleAdapter#modulesRenamed(com.intellij.openapi.project.Project, java.util.List)} callback. However, it's not appropriate to
   * update settings.gradle in that case since Gradle doesn't case about IJ's display name of the module.
   */
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    for (VFileEvent event : events) {
      if (!(event instanceof VFilePropertyChangeEvent)) {
        continue;
      }
      VFilePropertyChangeEvent propChangeEvent = (VFilePropertyChangeEvent) event;
      if (!(VirtualFile.PROP_NAME.equals(propChangeEvent.getPropertyName())) || propChangeEvent.getFile() == null) {
        continue;
      }

      VirtualFile eventFile = propChangeEvent.getFile();
      if (!eventFile.isDirectory()) {
        continue;
      }

      // If this listener is installed, it is because that the project is a Gradle-based project. If we don't have any build.gradle files
      // registered, it is because this listener was created before a project was fully created. This is common during creation of new
      // Gradle-based Android projects. ProjectComponent#projectOpened is called when the project is created, instead of when the project
      // is actually opened. It may be a bug in IJ.
      if (myBuildFiles.isEmpty()) {
        findAndAddAllBuildFiles();
      }

      // Dig through our modules and find the one that matches the change event's path (the module will already have its path updated by
      // now).
      Module module = null;
      for (Pair<Module, GradleBuildFile> pair : myBuildFiles) {
        VirtualFile moduleFile = pair.first.getModuleFile();
        if (moduleFile == null || moduleFile.getParent() == null) {
          continue;
        }

        VirtualFile moduleDir = moduleFile.getParent();
        if (FileUtil.pathsEqual(eventFile.getPath(), moduleDir.getPath())) {
          module = pair.first;
          break;
        }
      }

      // If we found the module, then remove the old reference from the settings.gradle file and from our data structures, and put in new
      // references.
      if (module != null) {
        remove(module);
        AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
        if (androidGradleFacet == null) {
          continue;
        }
        String oldPath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        String newPath = updateProjectNameInGradlePath(androidGradleFacet, eventFile);

        if (oldPath.equals(newPath)) {
          continue;
        }

        mySettingsFile.removeModule(oldPath);

        File modulePath = new File(newPath);
        VirtualFile vBuildFile = GradleUtil.getGradleBuildFile(modulePath);
        if (vBuildFile != null) {
          put(module, new GradleBuildFile(vBuildFile, myProject));
        }

        mySettingsFile.addModule(newPath);
      }
    }
  }

  private void findAndAddAllBuildFiles() {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      VirtualFile file = GradleUtil.getGradleBuildFile(module);
      if (file != null) {
        put(module, new GradleBuildFile(file, myProject));
      }
      else {
        LOG.warn("Unable to find build.gradle file for module " + module.getName());
      }
    }
  }

  @NotNull
  private static String updateProjectNameInGradlePath(@NotNull AndroidGradleFacet androidGradleFacet, @NotNull VirtualFile moduleDir) {
    String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
    if (gradlePath.equals(SdkConstants.GRADLE_PATH_SEPARATOR)) {
      // This is root project, renaming folder does not affect it since the path is just ":".
      return gradlePath;
    }
    List<String> pathSegments = GradleUtil.getPathSegments(gradlePath);
    pathSegments.remove(pathSegments.size() - 1);
    pathSegments.add(moduleDir.getName());

    String newPath = Joiner.on(SdkConstants.GRADLE_PATH_SEPARATOR).join(pathSegments);
    androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH = newPath;
    return newPath;
  }

  private void put(@NotNull Module module, @NotNull GradleBuildFile file) {
    remove(module);
    myBuildFiles.add(new Pair<Module, GradleBuildFile>(module, file));
  }

  private void remove(@NotNull Module module) {
    for (Pair<Module, GradleBuildFile> pair : myBuildFiles) {
      if (pair.first == module) {
        myBuildFiles.remove(pair);
        return;
      }
    }
  }

  @Nullable
  private GradleBuildFile getGradleBuildFile(@NotNull Module module) {
    for (Pair<Module, GradleBuildFile> pair : myBuildFiles) {
      if (pair.first == module) {
        return pair.second;
      }
    }
    return null;
  }

  @Override
  public void beforeRootsChange(ModuleRootEvent event) {
  }

  @Override
  public void rootsChanged(ModuleRootEvent event) {
    for (Module module : myModulesProvider.getModules()) {
      // Get the OrderEntries from the module's model and convert them to Dependency objects

      ModuleRootModel rootModel = myModulesProvider.getRootModel(module);
      final GradleBuildFile buildFile = getGradleBuildFile(module);
      List<BuildFileStatement> currentDeps;
      try {
        currentDeps = (List<BuildFileStatement>)buildFile.getValue(BuildFileKey.DEPENDENCIES);
      } catch (IllegalStateException e) {
        // This can happen during project import, when the build file hasn't had a chance to parse its PSI yet. Just bail out for now.
        return;
      }
      if (currentDeps == null) {
        currentDeps = Lists.newArrayList();
      }
      List<BuildFileStatement> newDeps = Lists.newArrayList();
      for (OrderEntry orderEntry : rootModel.getOrderEntries()) {
        Dependency newDep = convertDependency(buildFile, orderEntry);
        if (newDep != null) {
          newDeps.add(newDep);
        }
      }

      // Compare those Dependencies to the existing build.gradle to see if there's anything we need to add.
      // TODO: Try to delete unused dependencies.

      boolean changed = false;
      for (BuildFileStatement dependency : newDeps) {
        if (!buildFile.hasDependency(dependency)) {
          currentDeps.add(dependency);
          changed = true;
        }
      }

      // TODO: This sometimes gets a stale version of existing dependencies and writes it back out. Investigate, fix, and re-enable.
      // TODO: The tests still pass when this is commented out because they don't confirm the changes get written all the way out to
      // the file. Make the tests do better testing.
      //if (changed) {
      //  final List<BuildFileStatement> finalCurrentDeps = currentDeps;
      //  WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      //    @Override
      //    public void run() {
      //      buildFile.setValue(BuildFileKey.DEPENDENCIES, finalCurrentDeps);
      //    }
      //  });
      //}
    }
  }

  @Nullable
  private Dependency convertDependency(@NotNull GradleBuildFile buildFile, @NotNull OrderEntry orderEntry) {
    if (orderEntry instanceof LibraryOrderEntry) {
      LibraryOrderEntry loe = (LibraryOrderEntry)orderEntry;
      if (loe.getLibrary() == null) {
        return null;
      }
      Dependency.Scope scope = SCOPE_CONVERSIONS.get(loe.getScope());
      VirtualFile[] files = loe.getLibrary().getFiles(OrderRootType.CLASSES);
      for (VirtualFile file : files) {
        if (file.getFileSystem() instanceof JarFileSystem) {
          file = ((JarFileSystem)file.getFileSystem()).getLocalVirtualFileFor(file);
        }
        return convertLibraryPathToDependency(buildFile, scope, file);
      }
    } else if (orderEntry instanceof ModuleOrderEntry) {
      ModuleOrderEntry moe = (ModuleOrderEntry)orderEntry;
      return new Dependency(SCOPE_CONVERSIONS.get(moe.getScope()), Dependency.Type.MODULE,
                            GradleSettingsFile.getModuleGradlePath(moe.getModule()));
    }
    return null;
  }

  @Nullable
  @VisibleForTesting
  Dependency convertLibraryPathToDependency(@NotNull GradleBuildFile buildFile, @NotNull Dependency.Scope scope,
                                            @NotNull VirtualFile file) {
    String path = FileUtil.toSystemIndependentName(file.getPath());
    Matcher m;
    if ((m = GRADLE_CACHE_PATTERN.matcher(path)).matches()) {
      return new Dependency(scope, Dependency.Type.EXTERNAL, m.group(1) + ":" + m.group(2) + ":" + m.group(3));
    } else if ((m = EXPLODED_AAR_PATTERN.matcher(path)).matches()) {
      return new Dependency(scope, Dependency.Type.EXTERNAL, m.group(1) + ":" + m.group(2) + ":" + m.group(3) + "@aar");
    } else if ((m = ANDROID_EXTRAS_PATTERN.matcher(path)).matches()) {
      return new Dependency(scope, Dependency.Type.EXTERNAL, "com.android.support:" + m.group(1) + ":" + m.group(2));
    } else if (!INGORE_EXPLODED_BUNDLES_PATTERN.matcher(path).matches()) {
      VirtualFile parent = buildFile.getFile().getParent();
      if (parent == null) {
        return null;
      }
      String relativePath = VfsUtilCore.getRelativePath(file, parent, File.separatorChar);
      if (relativePath == null) {
        return null;
      }
      if (!FileUtil.isAbsolute(relativePath)) {
        return new Dependency(scope, Dependency.Type.FILES, FileUtil.toSystemIndependentName(relativePath));
      }
    }
    return null;
  }
}
