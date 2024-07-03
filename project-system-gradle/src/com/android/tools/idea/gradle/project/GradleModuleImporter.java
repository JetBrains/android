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
package com.android.tools.idea.gradle.project;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.notNull;
import static com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_IMPORT_MODULES_COPIED;
import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;
import static com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.GradleProjects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Creates new project module from source files with Gradle configuration.
 */
public final class GradleModuleImporter extends ModuleImporter {
  private final Logger LOG = Logger.getInstance(getClass());

  @Nullable private final Project myProject;
  private final boolean myIsWizard;

  private GradleModuleImporter(@NotNull WizardContext context) {
    this(context.getProject(), true);
  }

  private GradleModuleImporter(@Nullable Project project, boolean isWizard) {
    myIsWizard = isWizard;
    myProject = project;
  }

  @Override
  public boolean isStepVisible(@NotNull ModuleWizardStep step) {
    return false;
  }

  @Override
  @NotNull
  public List<? extends ModuleWizardStep> createWizardSteps() {
    return Collections.emptyList();
  }

  @Override
  public void importProjects(Map<String, VirtualFile> projects) {
    try {
      importModules(this, projects, myProject, null);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canImport(@NotNull VirtualFile importSource) {
    return GradleProjects.canImportAsGradleProject(importSource) && (myIsWizard || findModules(importSource).size() == 1);
  }

  @Override
  @NotNull
  public Set<ModuleToImport> findModules(@NotNull VirtualFile importSource) {
    assert myProject != null;
    return getRelatedProjects(importSource, myProject);
  }

  /**
   * Find related modules that should be imported into Android Studio together with the project user chose so it could be built.
   * <p/>
   * Top-level use-cases:
   * 1. If the user selects top-level project (e.g. the one with settings.gradle) Android Studio will import all its sub-projects.
   * 2. For leaf projects (ones with build.gradle), Android Studio will import selected project and the projects it depends on.
   *
   * @param sourceProject      the destinationProject that user wants to import
   * @param destinationProject destination destinationProject
   * @return mapping from module name to {@code VirtualFile} containing module contents. Values will be null if the module location was not
   * found.
   */
  @NotNull
  public static Set<ModuleToImport> getRelatedProjects(@NotNull VirtualFile sourceProject, @NotNull Project destinationProject) {
    VirtualFile settingsGradle = sourceProject.findFileByRelativePath(SdkConstants.FN_SETTINGS_GRADLE);
    if (settingsGradle != null) {
      return buildModulesSet(getSubProjects(settingsGradle, destinationProject),
                             GradleProjectDependencyParser.newInstance(destinationProject));
    }
    else {
      return getRequiredProjects(sourceProject, destinationProject);
    }
  }

  /**
   * Find direct and transitive dependency projects.
   */
  @NotNull
  private static Set<ModuleToImport> getRequiredProjects(@NotNull VirtualFile sourceProject, @NotNull Project destinationProject) {
    GradleSiblingLookup subProjectLocations = new GradleSiblingLookup(sourceProject, destinationProject);
    Function<VirtualFile, Iterable<String>> parser = GradleProjectDependencyParser.newInstance(destinationProject);
    Map<String, VirtualFile> modules = Maps.newHashMap();
    List<VirtualFile> toAnalyze = Lists.newLinkedList();
    toAnalyze.add(sourceProject);

    while (!toAnalyze.isEmpty()) {
      Set<String> dependencies = Sets.newHashSet(Iterables.concat(Iterables.transform(toAnalyze, parser)));
      Iterable<String> notAnalyzed = Iterables.filter(dependencies, not(in(modules.keySet())));
      // Turns out, Maps#toMap does not allow null values...
      Map<String, VirtualFile> dependencyToLocation = Maps.newHashMap();
      for (String dependency : notAnalyzed) {
        dependencyToLocation.put(dependency, subProjectLocations.apply(dependency));
      }
      modules.putAll(dependencyToLocation);
      toAnalyze = FluentIterable.from(dependencyToLocation.values()).filter(notNull()).toList();
    }
    modules.put(subProjectLocations.getPrimaryProjectName(), sourceProject);
    return buildModulesSet(modules, parser);
  }

  @NotNull
  private static Set<ModuleToImport> buildModulesSet(@NotNull Map<String, VirtualFile> modules,
                                                     @NotNull Function<VirtualFile, Iterable<String>> parser) {
    Set<ModuleToImport> modulesSet = Sets.newHashSetWithExpectedSize(modules.size());
    for (Map.Entry<String, VirtualFile> entry : modules.entrySet()) {
      VirtualFile location = entry.getValue();
      Supplier<Iterable<String>> dependencyComputer;
      if (location != null) {
        dependencyComputer = Suppliers.compose(parser, Suppliers.ofInstance(location));
      }
      else {
        dependencyComputer = Suppliers.ofInstance(ImmutableSet.of());
      }
      modulesSet.add(new ModuleToImport(entry.getKey(), location, dependencyComputer));
    }
    return modulesSet;
  }

  @NotNull
  public static Map<String, VirtualFile> getSubProjects(@NotNull final VirtualFile settingsGradle, Project destinationProject) {
    GradleSettingsModel gradleSettingsModel = GradleSettingsModel.get(settingsGradle, destinationProject);
    Iterable<String> paths = gradleSettingsModel.modulePaths();
    Map<String, File> allProjects = new LinkedHashMap<>();
    for (String path : paths) {
      // Exclude the root path.
      if (path.equals(":")) {
        continue;
      }
      File projectFile = gradleSettingsModel.moduleDirectory(path);
      if (projectFile != null) {
        allProjects.put(path, projectFile);
      }
    }
    return Maps.transformValues(allProjects, new ResolvePath(virtualToIoFile(settingsGradle.getParent())));
  }

  /**
   * Import set of gradle modules into existing Android Studio project. Note that no validation will be performed, modules will
   * be copied as is and settings.xml will be updated for the imported modules. It is callers' responsibility to ensure content
   * can be copied to the target directory and that module list is valid.
   *
   * @param modules  mapping between module names and locations on the filesystem. Neither name nor location should be null
   * @param project  project to import the modules to
   * @param listener optional object that gets notified of operation success or failure
   */
  @VisibleForTesting
  static void importModules(@NotNull final Object requestor,
                            @NotNull final Map<String, VirtualFile> modules,
                            @Nullable final Project project,
                            @Nullable final GradleSyncListener listener) throws IOException {
    String error = validateProjectsForImport(modules);
    if (error != null) {
      if (listener != null && project != null) {
        listener.syncFailed(project, error);
        return;
      }
      else {
        throw new IOException(error);
      }
    }

    assert project != null;
    writeCommandAction(project).run(() -> copyAndRegisterModule(requestor, modules, project, listener));
  }

  /**
   * Ensures that we know paths for all projects we are trying to import.
   *
   * @return message string if import is not possible or <code>null</code> otherwise
   */
  @Nullable
  private static String validateProjectsForImport(@NotNull Map<String, VirtualFile> modules) {
    Set<String> projects = new TreeSet<>();
    for (Map.Entry<String, VirtualFile> mapping : modules.entrySet()) {
      if (mapping.getValue() == null) {
        projects.add(mapping.getKey());
      }
    }
    if (projects.isEmpty()) {
      return null;
    }
    else if (projects.size() == 1) {
      return String.format("Sources for module '%1$s' were not found", Iterables.getFirst(projects, null));
    }
    else {
      String projectsList = Joiner.on("', '").join(projects);
      return String.format("Sources were not found for modules '%1$s'", projectsList);
    }
  }

  /**
   * Copy modules and adds it to settings.gradle
   */
  private static void copyAndRegisterModule(@NotNull Object requestor,
                                            @NotNull Map<String, VirtualFile> modules,
                                            @NotNull Project project,
                                            @Nullable GradleSyncListener listener) throws IOException {
    VirtualFile projectRoot = Objects.requireNonNull(ProjectUtil.guessProjectDir(project));
    GradleSettingsModel gradleSettingsModel = ProjectBuildModel.get(project).getProjectSettingsModel();
    if (gradleSettingsModel == null) {
      projectRoot.createChildData(requestor, SdkConstants.FN_SETTINGS_GRADLE);
      gradleSettingsModel = ProjectBuildModel.get(project).getProjectSettingsModel();
    }

    for (Map.Entry<String, VirtualFile> module : modules.entrySet()) {
      String name = module.getKey();
      File targetFile = GradleProjectSystemUtil.getModuleDefaultPath(projectRoot, name);
      VirtualFile moduleSource = module.getValue();
      if (moduleSource != null) {
        if (!isAncestor(projectRoot, moduleSource, true)) {
          VirtualFile target = createDirectoryIfMissing(targetFile.getAbsolutePath());
          if (target == null) {
            throw new IOException(String.format("Unable to create directory %1$s", targetFile));
          }
          if (target.exists()) {
            target.delete(requestor);
          }
          moduleSource.copy(requestor, target.getParent(), target.getName());
        }
        else {
          targetFile = virtualToIoFile(moduleSource);
        }
      }
      if (gradleSettingsModel != null) {
        gradleSettingsModel.addModulePath(name);
        if (!FileUtil.filesEqual(GradleProjectSystemUtil.getModuleDefaultPath(projectRoot, name), targetFile)) {
          gradleSettingsModel.setModuleDirectory(name, targetFile);
        }
      }
    }

    if (gradleSettingsModel == null) {
      Messages.showErrorDialog(project, "Couldn't add new paths to the Gradle settings file, please add them manually",
                               "Gradle Module Import Error");
    } else {
      gradleSettingsModel.applyChanges();
      GradleSyncInvoker.Request request = new GradleSyncInvoker.Request(TRIGGER_IMPORT_MODULES_COPIED);
      GradleSyncInvoker.getInstance().requestProjectSync(project, request, listener);
    }
  }

  /**
   * Resolves paths that may be either relative to provided directory or absolute.
   */
  private static class ResolvePath implements Function<File, VirtualFile> {
    private final File mySourceDir;

    ResolvePath(File sourceDir) {
      mySourceDir = sourceDir;
    }

    @Override
    public VirtualFile apply(File path) {
      if (!path.isAbsolute()) {
        path = new File(mySourceDir, path.getPath());
      }
      return findFileByIoFile(path, true);
    }
  }

  public static class GradleAndroidModuleImporter implements AndroidModuleImporter {
    @NotNull
    @Override
    public ModuleImporter create(@NotNull WizardContext context) {
      return new GradleModuleImporter(context);
    }
  }
}
