/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.actions;

import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.gradle.plugin.AndroidPluginVersionUpdater;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModelFeatures;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.run.OutputBuildAction;
import com.android.tools.idea.gradle.stubs.gradle.GradleProjectStub;
import com.android.tools.idea.testing.Facets;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.testFramework.JavaProjectTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildBundleActionTest extends JavaProjectTestCase {
  @Mock private GradleProjectInfo myGradleProjectInfo;
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private ProjectStructure myProjectStructure;
  @Mock private AndroidModuleModel myAndroidModel;
  @Mock private IdeAndroidProject myIdeAndroidProject;
  @Mock private IdeVariant myIdeVariant;
  @Mock private IdeAndroidArtifact myMainArtifact;
  @Mock private AndroidPluginVersionUpdater myAndroidPluginVersionUpdater;
  @Mock private IdeInfo myIdeInfo;
  private BuildBundleAction myAction;
  private TestDialog myDefaultTestDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    ServiceContainerUtil.replaceService(myProject, GradleBuildInvoker.class, myBuildInvoker, getTestRootDisposable());
    ServiceContainerUtil.replaceService(myProject, GradleProjectInfo.class, myGradleProjectInfo, getTestRootDisposable());
    ServiceContainerUtil.replaceService(myProject, ProjectStructure.class, myProjectStructure, getTestRootDisposable());
    ServiceContainerUtil.replaceService(myProject, AndroidPluginVersionUpdater.class, myAndroidPluginVersionUpdater, getTestRootDisposable());
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), IdeInfo.class, myIdeInfo, getTestRootDisposable());
    when(myIdeInfo.isAndroidStudio()).thenReturn(true);
    myAction = new BuildBundleAction();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myDefaultTestDialog != null) {
        Messages.setTestDialog(myDefaultTestDialog);
      }
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testActionPerformed() {
    Module appModule = createModule("app1");
    setUpModuleAsAndroidModule(appModule, myAndroidModel, myIdeAndroidProject, myIdeVariant, myMainArtifact);
    // Ignore return value, as we just want to make sure the "bundle" action does not apply to all modules
    createModule("app2");

    Module[] appModules = {appModule};
    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.copyOf(appModules));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    myAction.actionPerformed(event);

    verify(myBuildInvoker).bundle(eq(appModules), eq(emptyList()), any(OutputBuildAction.class));
  }

  public void testUpdateGradlePluginNotification() {
    Module appModule = createModule("app1");
    setUpModuleAsAndroidModule(appModule, myAndroidModel, myIdeAndroidProject, myIdeVariant, myMainArtifact);
    when(myMainArtifact.getBundleTaskName()).thenReturn(null);
    // Ignore return value, as we just want to make sure the "bundle" action does not apply to all modules
    createModule("app2");

    Module[] appModules = {appModule};
    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.copyOf(appModules));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    @SuppressWarnings("MagicConstant") // Using custom button IDs
    TestMessagesDialog testDialog = new TestMessagesDialog(1 /* Update*/);
    myDefaultTestDialog = Messages.setTestDialog(testDialog);
    myAction.actionPerformed(event);

    assertThat(testDialog.getDisplayedMessage()).isEqualTo(getHtmlUpdateMessage());
    // flush event queue to ensure the update call is processed.
    IdeEventQueue.getInstance().flushQueue();
    verify(myAndroidPluginVersionUpdater).updatePluginVersion(any(), any());
  }

  public void testUpdateGradlePluginCanceledNotification() {
    Module appModule = createModule("app1");
    setUpModuleAsAndroidModule(appModule, myAndroidModel, myIdeAndroidProject, myIdeVariant, myMainArtifact);
    when(myMainArtifact.getBundleTaskName()).thenReturn(null);
    // Ignore return value, as we just want to make sure the "bundle" action does not apply to all modules
    createModule("app2");

    Module[] appModules = {appModule};
    when(myProjectStructure.getAppModules()).thenReturn(ImmutableList.copyOf(appModules));
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());

    @SuppressWarnings("MagicConstant") // Using custom button IDs
    TestMessagesDialog testDialog = new TestMessagesDialog(0 /* Cancel*/);
    myDefaultTestDialog = Messages.setTestDialog(testDialog);
    myAction.actionPerformed(event);

    assertThat(testDialog.getDisplayedMessage()).isEqualTo(getHtmlUpdateMessage());
    verify(myAndroidPluginVersionUpdater, never()).updatePluginVersion(any(), any());
  }

  private static String getHtmlUpdateMessage() {
    return "<html><body>Building Android App Bundles requires you to update to the latest version of the Android Gradle Plugin.<BR/>" +
           "<A HREF=\"https://d.android.com/r/studio-ui/dynamic-delivery/overview.html\">Learn More</A><BR/><BR/>" +
           "App bundles allow you to support multiple device configurations from a single build artifact.<BR/>" +
           "App stores that support the bundle format use it to build and sign your APKs for you, and<BR/>" +
           "serve those APKs to users as needed.<BR/><BR/></body></html>";
  }

  private static void setUpModuleAsAndroidModule(@NotNull Module module,
                                                 @NotNull AndroidModuleModel androidModel,
                                                 @NotNull IdeAndroidProject ideAndroidProject,
                                                 @NotNull IdeVariant ideVariant,
                                                 @NotNull IdeAndroidArtifact mainArtifact) {
    setUpModuleAsGradleModule(module);

    when(androidModel.getAndroidProject()).thenReturn(ideAndroidProject);
    when(androidModel.getSelectedVariant()).thenReturn(ideVariant);
    when(ideVariant.getMainArtifact()).thenReturn(mainArtifact);
    when(mainArtifact.getBundleTaskName()).thenReturn("bundleDebug");

    AndroidModelFeatures androidModelFeatures = mock(AndroidModelFeatures.class);
    when(androidModel.getFeatures()).thenReturn(androidModelFeatures);

    AndroidFacet androidFacet = Facets.createAndAddAndroidFacet(module);
    androidFacet.getConfiguration().setModel(androidModel);
  }

  private static void setUpModuleAsGradleModule(@NotNull Module module) {
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    String gradlePath = GRADLE_PATH_SEPARATOR + module.getName();
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = gradlePath;

    GradleProject gradleProjectStub = new GradleProjectStub(emptyList(), gradlePath, getBaseDirPath(module.getProject()));
    GradleModuleModel model = new GradleModuleModel(module.getName(), gradleProjectStub, emptyList(), null, null);

    gradleFacet.setGradleModuleModel(model);
  }
}
