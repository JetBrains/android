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

import com.android.SdkConstants;
import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.*;
import com.google.common.collect.*;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.google.common.base.Predicates.*;
import static com.intellij.openapi.vfs.VfsUtil.createDirectoryIfMissing;
import static com.intellij.openapi.vfs.VfsUtil.findFileByIoFile;
import static com.intellij.openapi.vfs.VfsUtilCore.isAncestor;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

/**
 * Creates new project module from source files with Gradle configuration.
 */
public final class GradleModuleImporter extends ModuleImporter {
  private final Logger LOG = Logger.getInstance(getClass());

  @Nullable private final Project myProject;
  private final boolean myIsWizard;

  public GradleModuleImporter(@NotNull WizardContext context) {
    this(context.getProject(), true);
  }

  public GradleModuleImporter(@NotNull Project project) {
    this(project, false);
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
    catch (ConfigurationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public boolean canImport(@NotNull VirtualFile importSource) {
    try {
      return Projects.canImportAsGradleProject(importSource) && (myIsWizard || findModules(importSource).size() == 1);
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }
  }

  @Override
  @NotNull
  public Set<ModuleToImport> findModules(@NotNull VirtualFile importSource) throws IOException {
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
        dependencyComputer = Suppliers.<Iterable<String>>ofInstance(ImmutableSet.<String>of());
      }
      modulesSet.add(new ModuleToImport(entry.getKey(), location, dependencyComputer));
    }
    return modulesSet;
  }

  @NotNull
  public static Map<String, VirtualFile> getSubProjects(@NotNull final VirtualFile settingsGradle, Project destinationProject) {
    GradleSettingsFile settingsFile = new GradleSettingsFile(settingsGradle, destinationProject);
    Map<String, File> allProjects = settingsFile.getModulesWithLocation();
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
                            @Nullable final GradleSyncListener listener) throws IOException, ConfigurationException {
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
    Throwable throwable = new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        copyAndRegisterModule(requestor, modules, project, listener);
      }

      @Override
      public boolean isSilentExecution() {
        return true;
      }
    }.execute().getThrowable();
    rethrowAsProperlyTypedException(throwable);
  }

  /**
   * Ensures that we know paths for all projects we are trying to import.
   *
   * @return message string if import is not possible or <code>null</code> otherwise
   */
  @Nullable
  private static String validateProjectsForImport(@NotNull Map<String, VirtualFile> modules) {
    Set<String> projects = new TreeSet<String>();
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
   * Recover actual type of the exception.
   */
  private static void rethrowAsProperlyTypedException(Throwable throwable) throws IOException, ConfigurationException {
    if (throwable != null) {
      Throwables.propagateIfPossible(throwable, IOException.class, ConfigurationException.class);
      throw new IllegalStateException(throwable);
    }
  }

  /**
   * Copy modules and adds it to settings.gradle
   */
  private static void copyAndRegisterModule(@NotNull Object requestor,
                                            @NotNull Map<String, VirtualFile> modules,
                                            @NotNull Project project,
                                            @Nullable GradleSyncListener listener) throws IOException, ConfigurationException {
    VirtualFile projectRoot = project.getBaseDir();
    if (projectRoot.findChild(SdkConstants.FN_SETTINGS_GRADLE) == null) {
      projectRoot.createChildData(requestor, SdkConstants.FN_SETTINGS_GRADLE);
    }
    GradleSettingsFile gradleSettingsFile = GradleSettingsFile.get(project);
    assert gradleSettingsFile != null : "File should have been created";
    for (Map.Entry<String, VirtualFile> module : modules.entrySet()) {
      String name = module.getKey();
      File targetFile = GradleUtil.getModuleDefaultPath(projectRoot, name);
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
      gradleSettingsFile.addModule(name, targetFile);
    }
    GradleProjectImporter.getInstance().requestProjectSync(project, false, listener);
  }

  /**
   * Resolves paths that may be either relative to provided directory or absolute.
   */
  private static class ResolvePath implements Function<File, VirtualFile> {
    private final File mySourceDir;

    public ResolvePath(File sourceDir) {
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
}
