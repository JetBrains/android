/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import com.android.annotations.NonNull;
import com.android.builder.model.AndroidLibrary;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.model.LintModelModule;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.GeneratedSourcesFilter;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * An {@linkplain LintIdeProject} represents a lint project, which typically corresponds to a {@link Module},
 * but can also correspond to a library "project" such as an {@link AndroidLibrary}.
 */
public class LintIdeProject extends Project {

  protected LintIdeProject(@NonNull LintClient client,
                           @NonNull File dir,
                           @NonNull File referenceDir) {
    super(client, dir, referenceDir);
  }

  /**
   * Creates a set of projects for the given IntelliJ modules
   */
  @NonNull
  static List<Project> create(@NonNull LintIdeClient client, @Nullable List<VirtualFile> files, @NonNull Module... modules) {
    List<Project> projects = new ArrayList<>();

    Map<Project, Module> projectMap = Maps.newHashMap();
    Map<Module, Project> moduleMap = Maps.newHashMap();
    if (files != null && !files.isEmpty()) {
      // Wrap list with a mutable list since we'll be removing the files as we see them
      files = Lists.newArrayList(files);
    }
    for (Module module : modules) {
      addProjects(client, module, files, moduleMap, projectMap, projects);
    }

    client.setModuleMap(projectMap);

    if (projects.size() > 1) {
      // Partition the projects up such that we only return projects that aren't
      // included by other projects (e.g. because they are library projects)
      Set<Project> roots = new HashSet<>(projects);
      for (Project project : projects) {
        roots.removeAll(project.getAllLibraries());
      }
      return Lists.newArrayList(roots);
    }
    else {
      return projects;
    }
  }

  /**
   * Creates a project for a single file. Also optionally creates a main project for the file, if applicable.
   *
   * @param client the lint client
   * @param file   the file to create a project for
   * @param module the module to create a project for
   * @return a project for the file, as well as a project (or null) for the main Android module
   */
  @NonNull
  static Pair<Project, Project> createForSingleFile(@NonNull LintIdeClient client,
                                                    @Nullable VirtualFile file,
                                                    @NonNull Module module) {
    // TODO: Can make this method even more lightweight: we don't need to initialize anything in the project (source paths etc)
    // other than the metadata necessary for this file's type
    LintModuleProject project = createModuleProject(client, module);
    LintModuleProject main = null;
    Map<Project, Module> projectMap = Maps.newHashMap();
    if (project != null) {
      project.setDirectLibraries(Collections.emptyList());
      if (file != null) {
        project.addFile(VfsUtilCore.virtualToIoFile(file));
      }
      projectMap.put(project, module);
    }
    client.setModuleMap(projectMap);

    //noinspection ConstantConditions
    return Pair.create(project, main);
  }

  /**
   * Recursively add lint projects for the given module, and any other module or library it depends on, and also
   * populate the reverse maps so we can quickly map from a lint project to a corresponding module/library (used
   * by the lint client
   */
  private static void addProjects(@NonNull LintClient client,
                                  @NonNull Module module,
                                  @Nullable List<VirtualFile> files,
                                  @NonNull Map<Module, Project> moduleMap,
                                  @NonNull Map<Project, Module> projectMap,
                                  @NonNull List<Project> projects) {
    if (moduleMap.containsKey(module)) {
      return;
    }

    LintModuleProject project = createModuleProject(client, module);

    if (project == null) {
      return;
    }

    project.setIdeaProject(module.getProject());

    projects.add(project);
    moduleMap.put(module, project);
    projectMap.put(project, module);

    if (processFileFilter(module, files, project)) {
      // No need to process dependencies when doing single file analysis
      return;
    }

    List<Project> dependencies = new ArrayList<>();
    OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    // Loop in the reverse order to resolve dependencies on the libraries, so that if a library
    // is required by two higher level libraries it can be inserted in the correct place.

    List<Module> deps = new ArrayList<>();
    for (int i = entries.length; --i >= 0; ) {
      OrderEntry orderEntry = entries[i];
      if (orderEntry instanceof ModuleOrderEntry) {
        ModuleOrderEntry moduleOrderEntry = (ModuleOrderEntry)orderEntry;

        if (moduleOrderEntry.getScope() == DependencyScope.COMPILE) {
          Module depModule = moduleOrderEntry.getModule();

          if (depModule != null) {
            deps.add(depModule);
          }
        }
      }
    }
    for (Module depModule : deps) {
      Project p = moduleMap.get(depModule);
      if (p != null) {
        dependencies.add(p);
      }
      else {
        addProjects(client, depModule, files, moduleMap, projectMap, dependencies);
      }
    }

    project.setDirectLibraries(dependencies);
  }

  /**
   * Checks whether we have a file filter (e.g. a set of specific files to check in the module rather than all files,
   * and if so, and if all the files have been found, returns true)
   */
  public static boolean processFileFilter(@NonNull Module module, @Nullable List<VirtualFile> files, @NonNull Project project) {
    if (files != null && !files.isEmpty()) {
      ListIterator<VirtualFile> iterator = files.listIterator();
      while (iterator.hasNext()) {
        VirtualFile file = iterator.next();
        if (module.getModuleContentScope().accept(file)) {
          project.addFile(VfsUtilCore.virtualToIoFile(file));
          iterator.remove();
        }
      }
      if (files.isEmpty()) {
        // We're only scanning a subset of files (typically the current file in the editor);
        // in that case, don't initialize all the libraries etc
        project.setDirectLibraries(Collections.emptyList());
        return true;
      }
    }
    return false;
  }

  /**
   * Creates a new module project
   */
  @Nullable
  private static LintModuleProject createModuleProject(@NonNull LintClient client, @NonNull Module module) {
    File dir = getLintProjectDirectory(module);
    if (dir == null) return null;
    LintModuleProject project = new LintModuleProject(client, dir, dir, module);
    project.setIdeaProject(module.getProject());
    client.registerProject(dir, project);
    return project;
  }

  /**
   * Returns the  directory lint would use for a project wrapping the given module
   */
  @Nullable
  public static File getLintProjectDirectory(@NonNull Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    if (contentRoots.length == 1) {
      return VfsUtilCore.virtualToIoFile(contentRoots[0]);
    }
    else {
      return LintIdeSupportKt.getModuleDir(module);
    }
  }

  @Override
  protected void initialize() {
    // NOT calling super: super performs ADT/ant initialization. Here we want to use
    // the gradle data instead
  }

  @Nullable
  @Override
  public com.intellij.openapi.project.Project getIdeaProject() {
    if (client instanceof LintIdeClient) {
      return ((LintIdeClient)client).myProject;
    }
    return super.getIdeaProject();
  }

  public static class LintModuleProject extends LintIdeProject {
    private final Module myModule;

    public LintModuleProject(@NonNull LintClient client, @NonNull File dir, @NonNull File referenceDir, Module module) {
      super(client, dir, referenceDir);
      myModule = module;
    }

    @Override
    public boolean isAndroidProject() {
      return false;
    }

    protected boolean includeTests() {
      LintModelModule model = getBuildModule();
      if (model != null) {
        return model.getLintOptions().getCheckTestSources();
      }
      return false;
    }

    @NonNull
    @Override
    public List<File> getJavaSourceFolders() {
      if (javaSourceFolders == null) {
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myModule).getSourceRoots(false);
        List<File> dirs = new ArrayList<>(sourceRoots.length);
        com.intellij.openapi.project.Project project = myModule.getProject();
        for (VirtualFile root : sourceRoots) {
          if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(root, project)) {
            // Skip generated sources; they're supposed to be returned by getGeneratedSourceFolders()
            continue;
          }
          dirs.add(VfsUtilCore.virtualToIoFile(root));
        }
        javaSourceFolders = dirs;
      }

      return javaSourceFolders;
    }

    @NonNull
    @Override
    public List<File> getGeneratedSourceFolders() {
      if (generatedSourceFolders == null) {
        VirtualFile[] sourceRoots = ModuleRootManager.getInstance(myModule).getSourceRoots(includeTests());
        List<File> dirs = new ArrayList<>(sourceRoots.length);
        com.intellij.openapi.project.Project project = myModule.getProject();
        for (VirtualFile root : sourceRoots) {
          if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(root, project)) {
            dirs.add(VfsUtilCore.virtualToIoFile(root));
          }
        }
        generatedSourceFolders = dirs;
      }

      return generatedSourceFolders;
    }

    @NonNull
    @Override
    public List<File> getTestSourceFolders() {
      if (testSourceFolders == null) {
        ModuleRootManager manager = ModuleRootManager.getInstance(myModule);
        VirtualFile[] sourceRoots = manager.getSourceRoots(false);
        VirtualFile[] sourceAndTestRoots = manager.getSourceRoots(true);
        com.intellij.openapi.project.Project project = myModule.getProject();
        List<File> dirs = new ArrayList<>(sourceAndTestRoots.length);
        for (VirtualFile root : sourceAndTestRoots) {
          if (!ArrayUtil.contains(root, sourceRoots)) {
            if (GeneratedSourcesFilter.isGeneratedSourceByAnyFilter(root, project)) {
              // Skip generated sources
              continue;
            }
            dirs.add(VfsUtilCore.virtualToIoFile(root));
          }
        }
        testSourceFolders = dirs;
      }
      return testSourceFolders;
    }

    @NonNull
    @Override
    public List<File> getJavaClassFolders() {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        if (javaClassFolders == null) {
          CompilerModuleExtension extension = CompilerModuleExtension.getInstance(myModule);
          VirtualFile folder = extension != null ? extension.getCompilerOutputPath() : null;
          if (folder != null) {
            javaClassFolders = Collections.singletonList(VfsUtilCore.virtualToIoFile(folder));
          }
          else {
            javaClassFolders = Collections.emptyList();
          }
        }

        return javaClassFolders;
      }

      return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<File> getJavaLibraries(boolean includeProvided) {
      if (LintIdeClient.SUPPORT_CLASS_FILES) {
        if (javaLibraries == null) {
          javaLibraries = new ArrayList<>();

          final OrderEntry[] entries = ModuleRootManager.getInstance(myModule).getOrderEntries();
          // loop in the inverse order to resolve dependencies on the libraries, so that if a library
          // is required by two higher level libraries it can be inserted in the correct place

          for (int i = entries.length - 1; i >= 0; i--) {
            final OrderEntry orderEntry = entries[i];
            if (orderEntry instanceof LibraryOrderEntry) {
              LibraryOrderEntry libraryOrderEntry = (LibraryOrderEntry)orderEntry;
              VirtualFile[] classes = libraryOrderEntry.getRootFiles(OrderRootType.CLASSES);
              for (VirtualFile file : classes) {
                javaLibraries.add(VfsUtilCore.virtualToIoFile(file));
              }
            }
          }
        }

        return javaLibraries;
      }

      return Collections.emptyList();
    }
  }
}
