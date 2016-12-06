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
package com.android.tools.idea.npw.template;

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.npw.platform.AndroidVersionsInfo;
import com.android.tools.idea.npw.project.AndroidProjectPaths;
import com.android.tools.idea.npw.project.AndroidSourceSet;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.wizard.model.WizardModel;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A model responsible for instantiating a FreeMarker {@link Template} into the current project
 * representing an Android component.
 */
public final class RenderTemplateModel extends WizardModel {
  @NotNull private final String myCommandName;
  @NotNull private final OptionalProperty<Project> myProject;
  @NotNull private final ObjectProperty<AndroidSourceSet> mySourceSet;
  @NotNull private final OptionalProperty<AndroidVersionsInfo.VersionItem> myAndroidSdkInfo = new OptionalValueProperty<>();
  @NotNull private final StringProperty myPackageName;

  @NotNull private final Map<String, Object> myTemplateValues = Maps.newHashMap();
  @NotNull private TemplateHandle myTemplateHandle;
  @Nullable private AndroidIconGenerator myIconGenerator;

  public RenderTemplateModel(@NotNull Project project,
                             @NotNull TemplateHandle templateHandle,
                             @NotNull String initialPackageSuggestion,
                             @NotNull AndroidSourceSet sourceSet,
                             @NotNull String commandName) {
    myProject = new OptionalValueProperty<>(project);
    myPackageName = new StringValueProperty(initialPackageSuggestion);
    mySourceSet = new ObjectValueProperty<>(sourceSet);
    myTemplateHandle = templateHandle;
    myCommandName = commandName;
  }

  public RenderTemplateModel(@NotNull NewModuleModel moduleModel,
                             @NotNull TemplateHandle templateHandle,
                             @NotNull AndroidSourceSet sourceSet,
                             @NotNull String commandName) {
    myProject = moduleModel.getProject();
    myPackageName = moduleModel.packageName();
    mySourceSet = new ObjectValueProperty<>(sourceSet);
    myTemplateHandle = templateHandle;
    myCommandName = commandName;
  }

  private static Logger getLog() {
    return Logger.getInstance(RenderTemplateModel.class);
  }

  @NotNull
  public Map<String, Object> getTemplateValues() {
    return myTemplateValues;
  }

  /**
   * Get the current {@link SourceProvider} used by this model (the source provider affects which
   * paths the template's output will be rendered into).
   */
  @NotNull
  public ObjectProperty<AndroidSourceSet> getSourceSet() {
    return mySourceSet;
  }

  /**
   * The package name affects which paths the template's output will be rendered into.
   */
  @NotNull
  public StringProperty packageName() {
    return myPackageName;
  }

  public void setTemplateHandle(@NotNull TemplateHandle templateHandle) {
    myTemplateHandle = templateHandle;
  }

  @NotNull
  public TemplateHandle getTemplateHandle() {
    return myTemplateHandle;
  }

  @NotNull
  public OptionalProperty<Project> getProject() {
    return myProject;
  }

  public OptionalProperty<AndroidVersionsInfo.VersionItem> androidSdkInfo() {
    return myAndroidSdkInfo;
  }

  /**
   * If this template should also generate icon assets, set an icon generator.
   */
  public void setIconGenerator(@NotNull AndroidIconGenerator iconGenerator) {
    myIconGenerator = iconGenerator;
  }

  @Override
  protected void handleFinished() {
    if (!myProject.get().isPresent()) {
      getLog().error("RenderTemplateModel did not collect expected information and will not complete. Please report this error.");
      return;
    }

    AndroidProjectPaths paths = mySourceSet.get().getPaths();
    final Project project = myProject.getValue();

    boolean canRender = renderTemplate(true, project, paths, null, null);
    if (!canRender) {
      // If here, there was a render conflict and the user chose to cancel creating the template
      return;
    }

    final List<File> filesToOpen = Lists.newArrayListWithExpectedSize(3);
    final List<File> filesToReformat = Lists.newArrayList();

    boolean success = new WriteCommandAction<Boolean>(project, myCommandName) {
      @Override
      protected void run(@NotNull Result<Boolean> result) throws Throwable {
        boolean success = renderTemplate(false, project, paths, filesToOpen, filesToReformat);
        if (success && myIconGenerator != null) {
          myIconGenerator.generateImageIconsIntoPath(paths);
        }

        result.setResult(success);
      }
    }.execute().getResultObject();

    if (success) {
      // If this is a new project, we can't selected any file until gradle finishes sync.
      // Cache the value of project.isOpen() in a flag, as it will return true when called later from invokeLater()
      final boolean selectEditor = project.isOpen();
      ApplicationManager.getApplication().invokeLater(() -> TemplateUtils.openEditors(project, filesToOpen, selectEditor));
    }
  }

  private boolean renderTemplate(boolean dryRun,
                                 @NotNull Project project,
                                 @NotNull AndroidProjectPaths paths,
                                 @Nullable List<File> filesToOpen,
                                 @Nullable List<File> filesToReformat) {
    final Template template = myTemplateHandle.getTemplate();
    File moduleRoot = paths.getModuleRoot();
    if (moduleRoot == null) {
      return false;
    }

    // @formatter:off
    final RenderingContext context = RenderingContext.Builder.newContext(template, project)
      .withCommandName(myCommandName)
      .withDryRun(dryRun)
      .withShowErrors(true)
      .withModuleRoot(paths.getModuleRoot())
      .withParams(myTemplateValues)
      .intoOpenFiles(filesToOpen)
      .intoTargetFiles(filesToReformat)
      .build();
    // @formatter:on
    return template.render(context);
  }
}
