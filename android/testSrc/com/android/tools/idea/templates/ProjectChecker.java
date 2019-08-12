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
package com.android.tools.idea.templates;

import static com.android.SdkConstants.GRADLE_LATEST_VERSION;
import static com.android.testutils.TestUtils.getSdk;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MIN_API;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_MODULE_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_SOURCE_PROVIDER_NAME;
import static com.android.tools.idea.templates.TemplateMetadata.ATTR_TOP_OUT;
import static com.android.tools.idea.templates.TemplateTestBase.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.templates.TemplateTestUtils.addIconsIfNecessary;
import static com.android.tools.idea.templates.TemplateTestUtils.cleanupProjectFiles;
import static com.android.tools.idea.templates.TemplateTestUtils.createRenderingContext;
import static com.android.tools.idea.templates.TemplateTestUtils.getModifiedProjectName;
import static com.android.tools.idea.templates.TemplateTestUtils.invokeGradleForProjectDir;
import static com.android.tools.idea.templates.TemplateTestUtils.lintIfNeeded;
import static com.android.tools.idea.templates.TemplateTestUtils.setUpFixtureForProject;
import static com.android.tools.idea.templates.TemplateTestUtils.verifyLanguageFiles;
import static com.android.tools.idea.templates.TemplateTestUtils.verifyLastLoggedUsage;
import static com.android.tools.idea.testing.AndroidGradleTests.getLocalRepositoriesForGroovy;
import static com.android.tools.idea.testing.AndroidGradleTests.updateLocalRepositories;
import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.testing.Helpers.assertEmpty;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.jetbrains.android.AndroidTestBase.refreshProjectFiles;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import com.android.SdkConstants;
import com.android.tools.analytics.TestUsageTracker;
import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate;
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor;
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.android.tools.idea.npw.assetstudio.IconGenerator;
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.platform.Language;
import com.android.tools.idea.npw.project.AndroidGradleModuleUtils;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectChecker {
  boolean syncProject;
  @NotNull TestNewProjectWizardState projectState;
  @Nullable TestTemplateWizardState activityState;
  @NotNull TestUsageTracker usageTracker;
  @NotNull Language language;

  public ProjectChecker(
    boolean syncProject,
    @NotNull TestNewProjectWizardState projectState,
    @Nullable TestTemplateWizardState activityState,
    @NotNull TestUsageTracker usageTracker,
    @NotNull Language language
  ) {
    this.syncProject = syncProject;
    this.projectState = projectState;
    this.activityState = activityState;
    this.usageTracker = usageTracker;
    this.language = language;
  }

  void checkProjectNow(@NotNull String projectName) throws Exception {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    String modifiedProjectName = getModifiedProjectName(projectName, activityState);

    moduleState.put(ATTR_MODULE_NAME, modifiedProjectName);
    JavaCodeInsightTestFixture fixture = setUpFixtureForProject(modifiedProjectName);
    @NotNull Project project = Objects.requireNonNull(fixture.getProject());
    new IdeComponents(project).replaceProjectService(PostProjectBuildTasksExecutor.class, mock(PostProjectBuildTasksExecutor.class));
    AndroidGradleTests.setUpSdks(fixture, getSdk());
    @NotNull File projectDir = getBaseDirPath(project);
    moduleState.put(ATTR_TOP_OUT, projectDir.getPath());

    System.out.println("Checking project " + projectName + " in " + ProjectUtil.guessProjectDir(project));
    try {
      createProject(fixture);

      File projectRoot = virtualToIoFile(ProjectUtil.guessProjectDir(project));
      if (activityState != null && !moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
        activityState.put(ATTR_TOP_OUT, projectDir.getPath());
        ApplicationManager.getApplication().runWriteAction(() -> {
          Template template = activityState.getTemplate();
          assert template != null;
          File moduleRoot = new File(projectRoot, modifiedProjectName);
          activityState.put(ATTR_MODULE_NAME, moduleRoot.getName());
          activityState.put(ATTR_SOURCE_PROVIDER_NAME, "main");
          activityState.populateDirectoryParameters();
          RenderingContext context = createRenderingContext(template, project, moduleRoot, moduleRoot, activityState.getParameters());
          template.render(context, false);

          addIconsIfNecessary(activityState);
        });
      }

      if (language == Language.KOTLIN) {
        verifyLanguageFiles(projectDir, Language.KOTLIN);
      }

      invokeGradleForProjectDir(projectDir);
      lintIfNeeded(project);
    }
    finally {
      if (fixture != null) {
        fixture.tearDown();
      }

      Project[] openProjects = ProjectManagerEx.getInstanceEx().getOpenProjects();
      assert (openProjects.length <= 1); // 1: the project created by default by the test case

      cleanupProjectFiles(projectDir);
    }
  }

  private void createProject(@NotNull JavaCodeInsightTestFixture myFixture) throws Exception {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    ApplicationManager.getApplication().runWriteAction(() -> {
      int minSdkVersion = Integer.parseInt((String)moduleState.get(ATTR_MIN_API));
      IconGenerator iconGenerator = new LauncherIconGenerator(myFixture.getProject(), minSdkVersion, null);
      try {
        iconGenerator.outputName().set("ic_launcher");
        iconGenerator.sourceAsset().setValue(new ImageAsset());
        createProject(myFixture.getProject(), iconGenerator);
      }
      finally {
        Disposer.dispose(iconGenerator);
      }
      FileDocumentManager.getInstance().saveAllDocuments();
    });

    // Update to latest plugin / gradle and sync model
    File projectRoot = new File(moduleState.getString(ATTR_TOP_OUT));
    assertEquals(projectRoot, virtualToIoFile(ProjectUtil.guessProjectDir(myFixture.getProject())));
    AndroidGradleTests.createGradleWrapper(projectRoot, GRADLE_LATEST_VERSION);

    File gradleFile = new File(projectRoot, SdkConstants.FN_BUILD_GRADLE);
    String origContent = com.google.common.io.Files.asCharSource(gradleFile, UTF_8).read();
    String newContent = updateLocalRepositories(origContent, getLocalRepositoriesForGroovy());
    if (!newContent.equals(origContent)) {
      com.google.common.io.Files.asCharSink(gradleFile, UTF_8).write(newContent);
    }

    @NotNull Project project = myFixture.getProject();

    refreshProjectFiles();
    if (syncProject) {
      assertEquals(moduleState.getString(ATTR_MODULE_NAME), project.getName());
      assertEquals(projectRoot, getBaseDirPath(project));
      AndroidGradleTests.importProject(project, GradleSyncInvoker.Request.testRequest());
    }
  }

  private void createProject(@NotNull Project project, @Nullable IconGenerator iconGenerator) {
    TestTemplateWizardState moduleState = projectState.getModuleTemplateState();
    List<String> errors = new ArrayList<>();
    try {
      moduleState.populateDirectoryParameters();
      String moduleName = moduleState.getString(ATTR_MODULE_NAME);
      String projectPath = moduleState.getString(ATTR_TOP_OUT);
      File projectRoot = new File(projectPath);
      AndroidModuleTemplate paths = GradleAndroidModuleTemplate.createDefaultTemplateAt(projectPath, moduleName).getPaths();
      if (FileUtilRt.createDirectory(projectRoot)) {
        if (iconGenerator != null) {
          // TODO test the icon generator
        }
        projectState.updateParameters();

        File moduleRoot = paths.getModuleRoot();
        assert moduleRoot != null;

        // If this is a new project, instantiate the project-level files
        Template projectTemplate = projectState.getProjectTemplate();
        final RenderingContext projectContext =
          createRenderingContext(projectTemplate, project, projectRoot, moduleRoot, moduleState.getParameters());
        projectTemplate.render(projectContext, false);
        // check usage tracker after project render
        verifyLastLoggedUsage(usageTracker, Template.titleToTemplateRenderer(projectTemplate.getMetadata().getTitle()),
                              projectContext.getParamMap());
        AndroidGradleModuleUtils.setGradleWrapperExecutable(projectRoot);

        final RenderingContext moduleContext =
          createRenderingContext(moduleState.getTemplate(), project, projectRoot, moduleRoot, moduleState.getParameters());
        Template moduleTemplate = moduleState.getTemplate();
        moduleTemplate.render(moduleContext, false);
        // check usage tracker after module render
        verifyLastLoggedUsage(usageTracker, Template.titleToTemplateRenderer(moduleTemplate.getMetadata().getTitle()),
                              moduleContext.getParamMap());
        if (moduleState.getBoolean(ATTR_CREATE_ACTIVITY)) {
          TestTemplateWizardState activityTemplateState = projectState.getActivityTemplateState();
          Template activityTemplate = activityTemplateState.getTemplate();
          assert activityTemplate != null;
          final RenderingContext activityContext =
            createRenderingContext(activityTemplate, project, moduleRoot, moduleRoot, activityTemplateState.getParameters());
          activityTemplate.render(activityContext, false);
          // check usage tracker after activity render
          verifyLastLoggedUsage(usageTracker, Template.titleToTemplateRenderer(activityTemplate.getMetadata().getTitle()),
                                activityContext.getParamMap());
          moduleContext.getFilesToOpen().addAll(activityContext.getFilesToOpen());
        }
      }
      else {
        errors.add(String.format("Unable to create directory '%1$s'.", projectRoot.getPath()));
      }
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
    assertEmpty(errors);
  }
}
