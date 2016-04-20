/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates.recipe;

import com.android.tools.idea.templates.FreemarkerConfiguration;
import com.android.tools.idea.templates.StudioTemplateLoader;
import com.android.tools.idea.templates.Template;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import freemarker.template.Configuration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Use the {@link Builder} class for creating a {@link RenderingContext} instance.
 * See the documentation for each option below.
 * e.g.
 *
 * <code>
 *   RenderingContext rc = RenderingContext.Builder.newContext(template, project)
 *     .withCommandName("My Action")
 *     .withShowErrors(true)
 *     .withDryRun(true)
 *   template.render(rc);
 * </code>
 */
public class RenderingContext {
  private final Project myProject;
  private final String myTitle;
  private final Map<String, Object> myParamMap;
  private final File myOutputRoot;
  private final File myModuleRoot;
  private final boolean myGradleSync;
  private final boolean myFindOnlyReferences;
  private final StudioTemplateLoader myLoader;
  private final Configuration myFreemarker;
  private final Collection<File> mySourceFiles;
  private final Collection<File> myTargetFiles;
  private final Collection<File> myFilesToOpen;
  private final Collection<String> myClasspathEntries;
  private final Collection<String> myDependencies;
  private final Collection<String> myWarnings;
  private final boolean myDryRun;
  private final boolean myShowErrors;

  private RenderingContext(@Nullable Project project,
                           @NotNull File initialTemplatePath,
                           @NotNull String commandName,
                           @NotNull Map<String, Object> paramMap,
                           @NotNull File outputRoot,
                           @NotNull File moduleRoot,
                           boolean gradleSyncIfNeeded,
                           boolean findOnlyReferences,
                           boolean dryRun,
                           boolean showErrors,
                           @Nullable Collection<File> outSourceFiles,
                           @Nullable Collection<File> outTargetFiles,
                           @Nullable Collection<File> outOpenFiles,
                           @Nullable Collection<String> outClasspathEntries,
                           @Nullable Collection<String> outDependencies) {
    myProject = useDefaultProjectIfNeeded(project);
    myTitle = commandName;
    myParamMap = Template.createParameterMap(paramMap);
    myOutputRoot = outputRoot;
    myModuleRoot = moduleRoot;
    myGradleSync = gradleSyncIfNeeded;
    myFindOnlyReferences = findOnlyReferences;
    myDryRun = dryRun;
    myShowErrors = showErrors;
    myLoader = new StudioTemplateLoader(initialTemplatePath);
    myFreemarker = new FreemarkerConfiguration();
    myFreemarker.setTemplateLoader(myLoader);
    mySourceFiles = outSourceFiles != null ? outSourceFiles : Lists.newArrayList();
    myTargetFiles = outTargetFiles != null ? outTargetFiles : Lists.newArrayList();
    myFilesToOpen = outOpenFiles != null ? outOpenFiles : Lists.newArrayList();
    myClasspathEntries = outClasspathEntries != null ? outClasspathEntries : Lists.newArrayList();
    myDependencies = outDependencies != null ? outDependencies : Lists.newArrayList();
    myWarnings = Lists.newArrayList();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * The title of the operation.
   * This title is used in error dialogs and in the name of the Undo operation created.
   */
  @NotNull
  public String getCommandName() {
    return myTitle;
  }

  /**
   * Key/Value pairs that are fed into the input parameters for the template.
   */
  @NotNull
  public Map<String, Object> getParamMap() {
    return myParamMap;
  }

  /**
   * The root directory where the template will be expanded.
   */
  @NotNull
  public File getOutputRoot() {
    return myOutputRoot;
  }

  /**
   * The root of the IDE project module for the template being expanded.
   * This can be useful for finding build files.
   */
  @NotNull
  public File getModuleRoot() {
    return myModuleRoot;
  }

  /**
   * If true perform a Gradle sync at the end of the template execution.
   * A false means do NOT perform a Gradle sync since we plan to do this later.
   */
  public boolean performGradleSync() {
    return myGradleSync;
  }

  /**
   * If true show errors.
   * A false means errors are thrown as a {@link RuntimeException} for the IDE to handle.
   */
  public boolean showErrors() {
    return myShowErrors;
  }

  /**
   * If true show warnings.
   * A false means warnings will be ignored.
   */
  public boolean showWarnings() {
    return myShowErrors && myDryRun;
  }

  /**
   * Used internally.
   */
  @NotNull
  public StudioTemplateLoader getLoader() {
    return myLoader;
  }

  /**
   * Used internally.
   */
  @NotNull
  public Configuration getFreemarkerConfiguration() {
    return myFreemarker;
  }

  /**
   * The list of template outputs.
   */
  @NotNull
  public Collection<File> getTargetFiles() {
    return myTargetFiles;
  }

  /**
   * List of files to open after the wizard has been created (these are
   * identified by TAG_OPEN elements in the recipe file)
   */
  @NotNull
  public Collection<File> getFilesToOpen() {
    return myFilesToOpen;
  }

  /**
   * List of classpath entries added by a previous template rendering.
   */
  @NotNull
  public Collection<String> getClasspathEntries() {
    return myClasspathEntries;
  }

  /**
   * List of dependencies added by a previous template rendering.
   */
  @NotNull
  public Collection<String> getDependencies() {
    return myDependencies;
  }

  /**
   * List of source files that was/would be used by a previous template rendering.
   */
  @NotNull
  public Collection<File> getSourceFiles() {
    return mySourceFiles;
  }

  /**
   * List of warnings that were issued during a dry run.
   */
  @NotNull
  public Collection<String> getWarnings() {
    return myWarnings;
  }

  /**
   * Used internally.
   */
  public RecipeExecutor getRecipeExecutor() {
    if (myFindOnlyReferences) {
      return new FindReferencesRecipeExecutor(this);
    }
    else {
      return new DefaultRecipeExecutor(this, myDryRun);
    }
  }

  /**
   * @return true if the target files should be reformatted after the template is rendered
   */
  public boolean shouldReformat() {
    return !myDryRun && myProject.isInitialized();
  }

  /**
   * If there is an error, can it cause a project that is partially rendered?
   */
  public boolean canCausePartialRendering() {
    return !myDryRun;
  }

  @NotNull
  private static Project useDefaultProjectIfNeeded(@Nullable Project project) {
    // Project creation: no current project to read code style settings from yet, so use defaults
    return project != null ? project : ProjectManagerEx.getInstanceEx().getDefaultProject();
  }

  public static final class Builder {
    private final File myInitialTemplatePath;
    private final Project myProject;
    private String myCommandName;
    private Map<String, Object> myParams;
    private File myOutputRoot;
    private File myModuleRoot;
    private boolean myGradleSync;
    private boolean myFindOnlyReferences;
    private boolean myDryRun;
    private boolean myShowErrors;
    private Collection<File> mySourceFiles;
    private Collection<File> myTargetFiles;
    private Collection<File> myOpenFiles;
    private Collection<String> myClasspathEntries;
    private Collection<String> myDependencies;

    private Builder(@NotNull File initialTemplatePath, @NotNull Project project) {
      myInitialTemplatePath = initialTemplatePath;
      myProject = project;
      myCommandName = "Instantiate Template";
      myParams = Collections.emptyMap();
      myOutputRoot = VfsUtilCore.virtualToIoFile(project.getBaseDir());
      myModuleRoot = myOutputRoot;
      myGradleSync = true;
      myFindOnlyReferences = false;
      myDryRun = false;
      myShowErrors = false;
    }

    /**
     * Create a {@link Builder} that uses the project base dir as the template output and module dir.
     * Recommended version.
     */
    public static Builder newContext(@NotNull Template template, @NotNull Project project) {
      return new Builder(template.getRootPath(), project);
    }

    /**
     * Create a {@link Builder} that uses the project base dir as the template output and module dir.
     * Use this version if there is no {@link Template} instance available.
     */
    public static Builder newContext(@NotNull File templateRootPath, @NotNull Project project) {
      return new Builder(templateRootPath, project);
    }

    /**
     * Specify the command name used in an undo event and in error and warning dialogs.
     */
    public Builder withCommandName(@NotNull String commandName) {
      myCommandName = commandName;
      return this;
    }

    /**
     * Specify the module.
     * This can be useful for finding build files.
     */
    public Builder withModule(@NotNull Module module) {
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      assert roots.length > 0;
      myModuleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
      return this;
    }

    /**
     * Specify the output folder.
     * This is where the generated files are placed.
     */
    public Builder withOutputRoot(@NotNull File outputRoot) {
      myOutputRoot = outputRoot;
      return this;
    }

    /**
     * Specify the module root folder.
     * This can be useful for finding build files.
     */
    public Builder withModuleRoot(@NotNull File moduleRoot) {
      myModuleRoot = moduleRoot;
      return this;
    }

    /**
     * Specify the parameters that are passed to the Freemarker template engine.
     */
    public Builder withParams(@NotNull Map<String, Object> params) {
      myParams = params;
      return this;
    }

    /**
     * Specify if a Gradle sync should be performed at the end of the template execution.
     * A false means do NOT perform a Gradle sync since we plan to do this later.
     * Default: true.
     */
    public Builder withGradleSync(boolean gradleSync) {
      myGradleSync = gradleSync;
      return this;
    }

    /**
     * With this option the template rendering will not create files but only gather
     * references.
     * Default: false
     */
    public Builder withFindOnlyReferences(boolean findOnlyReferences) {
      myFindOnlyReferences = findOnlyReferences;
      return this;
    }

    /**
     * With this option the template rendering will not create files, but all templates
     * are executed and all input files are checked.
     * Use this option to find errors and warnings without modifying the project into a
     * state where it may not compile.
     * Default: false
     */
    public Builder withDryRun(boolean dryRun) {
      myDryRun = dryRun;
      return this;
    }

    /**
     * Display user errors found. And in a dry run display warnings.
     * Default: false
     */
    public Builder withShowErrors(boolean showErrors) {
      myShowErrors = showErrors;
      return this;
    }

    /**
     * Collect all source files into the specified collection.
     */
    public Builder intoSourceFiles(@Nullable Collection<File> sourceFiles) {
      mySourceFiles = sourceFiles;
      return this;
    }

    /**
     * Collect all generated target files into the specified collection.
     */
    public Builder intoTargetFiles(@Nullable Collection<File> targetFiles) {
      myTargetFiles = targetFiles;
      return this;
    }

    /**
     * Collect all the generated files that should be opened after the template rendering into
     * the specified collection.
     */
    public Builder intoOpenFiles(@Nullable Collection<File> openFiles) {
      myOpenFiles = openFiles;
      return this;
    }

    /**
     * Collect all classpath entries required for the template in the specified collection.
     */
    public Builder intoClasspathEntries(@Nullable Collection<String> classpathEntries) {
      myClasspathEntries = classpathEntries;
      return this;
    }

    /**
     * Collect all dependencies required for the template in the specified collection.
     */
    public Builder intoDependencies(@Nullable Collection<String> dependencies) {
      myDependencies = dependencies;
      return this;
    }

    /**
     * Create a {@link RenderingContext} based on the options given.
     */
    public RenderingContext build() {
      if (myDryRun) {
        // Ignore outputs if this is a dry run:
        mySourceFiles = null;
        myTargetFiles = null;
        myOpenFiles = null;
        myClasspathEntries = null;
        myDependencies = null;
      }
      return new RenderingContext(myProject, myInitialTemplatePath, myCommandName, myParams, myOutputRoot, myModuleRoot, myGradleSync,
                                  myFindOnlyReferences, myDryRun, myShowErrors, mySourceFiles, myTargetFiles, myOpenFiles,
                                  myClasspathEntries, myDependencies);
    }
  }
}
