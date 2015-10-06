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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Use the {@link Builder} class for creating a {@link RenderingContext} instance.
 * e.g.
 *
 * <code>
 *   RenderingContext rc = RenderingContext.Builder.newContext(template, project).build();
 *   template.render(rc);
 * </code>
 */
public class RenderingContext {
  private final Project myProject;
  private final Map<String, Object> myParamMap;
  private final File myOutputRoot;
  private final File myModuleRoot;
  private final boolean myGradleSync;
  private final boolean myFindOnlyReferences;
  private final StudioTemplateLoader myLoader;
  private final Configuration myFreemarker;
  private final List<File> myTargetFiles;
  private final List<File> myFilesToOpen;
  private final List<String> myDependencies;
  private final List<File> mySourceFiles;
  private final boolean myDryRun;

  private RenderingContext(@Nullable Project project,
                           @NotNull File templateRootPath,
                           @NotNull Map<String, Object> paramMap,
                           @NotNull File outputRoot,
                           @NotNull File moduleRoot,
                           boolean gradleSyncIfNeeded,
                           boolean findOnlyReferences,
                           boolean dryRun) {
    myProject = useDefaultProjectIfNeeded(project);
    myParamMap = Template.createParameterMap(paramMap);
    myOutputRoot = outputRoot;
    myModuleRoot = moduleRoot;
    myGradleSync = gradleSyncIfNeeded;
    myFindOnlyReferences = findOnlyReferences;
    myDryRun = dryRun;
    myLoader = new StudioTemplateLoader(templateRootPath);
    myFreemarker = new Configuration();
    myFreemarker.setTemplateLoader(myLoader);
    myTargetFiles = Lists.newArrayList();
    myFilesToOpen = Lists.newArrayList();
    myDependencies = Lists.newArrayList();
    mySourceFiles = Lists.newArrayList();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public Map<String, Object> getParamMap() {
    return myParamMap;
  }

  @NotNull
  public File getOutputRoot() {
    return myOutputRoot;
  }

  @NotNull
  public File getModuleRoot() {
    return myModuleRoot;
  }

  public boolean performGradleSync() {
    return myGradleSync;
  }

  @NotNull
  public StudioTemplateLoader getLoader() {
    return myLoader;
  }

  @NotNull
  public Configuration getFreemarkerConfiguration() {
    return myFreemarker;
  }

  /**
   * The list of template outputs.
   */
  @NotNull
  public List<File> getTargetFiles() {
    return myTargetFiles;
  }

  /**
   * List of files to open after the wizard has been created (these are
   * identified by TAG_OPEN elements in the recipe file)
   */
  @NotNull
  public List<File> getFilesToOpen() {
    return myFilesToOpen;
  }

  /**
   * List of dependencies added by a previous template rendering.
   */
  @NotNull
  public List<String> getDependencies() {
    return myDependencies;
  }

  /**
   * List of source files that was/would be used by a previous template rendering.
   */
  @NotNull
  public List<File> getSourceFiles() {
    return mySourceFiles;
  }

  public RecipeExecutor getRecipeExecutor() {
    if (myFindOnlyReferences) {
      return new FindReferencesRecipeExecutor(this);
    }
    else {
      return new DefaultRecipeExecutor(this, myDryRun);
    }
  }

  @NotNull
  private static Project useDefaultProjectIfNeeded(@Nullable Project project) {
    // Project creation: no current project to read code style settings from yet, so use defaults
    return project != null ? project : ProjectManagerEx.getInstanceEx().getDefaultProject();
  }

  public static final class Builder {
    private final File myTemplateRootPath;
    private final Project myProject;
    private Map<String, Object> myParams;
    private File myOutputRoot;
    private File myModuleRoot;
    private boolean myGradleSync;
    private boolean myFindOnlyReferences;
    private boolean myDryRun;

    private Builder(@NotNull File templateRootPath, @NotNull Project project) {
      myTemplateRootPath = templateRootPath;
      myProject = project;
      myParams = Collections.emptyMap();
      myOutputRoot = VfsUtilCore.virtualToIoFile(project.getBaseDir());
      myModuleRoot = myOutputRoot;
      myGradleSync = true;
      myFindOnlyReferences = false;
      myDryRun = false;
    }

    public static Builder newContext(@NotNull File templateRootPath, @NotNull Project project) {
      return new Builder(templateRootPath, project);
    }

    public static Builder newContext(@NotNull Template template, @NotNull Project project) {
      return new Builder(template.getRootPath(), project);
    }

    public Builder withModule(@NotNull Module module) {
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      assert roots.length > 0;
      myModuleRoot = VfsUtilCore.virtualToIoFile(roots[0]);
      return this;
    }

    public Builder withOutputRoot(@NotNull File outputRoot) {
      myOutputRoot = outputRoot;
      return this;
    }

    public Builder withModuleRoot(@NotNull File moduleRoot) {
      myModuleRoot = moduleRoot;
      return this;
    }

    public Builder withParams(@NotNull Map<String, Object> params) {
      myParams = params;
      return this;
    }

    public Builder withGradleSync(boolean gradleSync) {
      myGradleSync = gradleSync;
      return this;
    }

    public Builder withFindOnlyReferences(boolean findOnlyReferences) {
      myFindOnlyReferences = findOnlyReferences;
      return this;
    }

    public Builder withDryRun(boolean dryRun) {
      myDryRun = dryRun;
      return this;
    }

    public RenderingContext build() {
      return new RenderingContext(myProject, myTemplateRootPath, myParams, myOutputRoot, myModuleRoot, myGradleSync, myFindOnlyReferences,
                                  myDryRun);
    }
  }
}
