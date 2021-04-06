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

import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.android.tools.idea.testing.JavaModuleModelBuilder.getRootModuleBuilder;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.upgrade.AndroidPluginVersionUpdater;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.IdeComponents;
import com.android.tools.idea.testing.TestMessagesDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.PlatformTestCase;
import java.io.File;
import org.mockito.Mock;

/**
 * Tests for {@link BuildApkAction}.
 */
public class BuildBundleActionTest extends PlatformTestCase {
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private AndroidPluginVersionUpdater myAndroidPluginVersionUpdater;
  private BuildBundleAction myAction;
  private TestDialog myDefaultTestDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(myProject).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);
    new IdeComponents(myProject).replaceProjectService(AndroidPluginVersionUpdater.class, myAndroidPluginVersionUpdater);
    myAction = new BuildBundleAction();
  }

  @Override
  protected void tearDown() throws Exception {
    if (myDefaultTestDialog != null) {
      TestDialogManager.setTestDialog(myDefaultTestDialog);
    }
    super.tearDown();
  }

  public void testActionPerformed() {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      getRootModuleBuilder(),
      new AndroidModuleModelBuilder(
        ":app1", "debug",
        new AndroidProjectBuilder()
          .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_APP)
          .withDynamicFeatures(it -> ImmutableList.of(":feature1"))
      ),
      new AndroidModuleModelBuilder(":feature1", "debug", new AndroidProjectBuilder().withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE))
    );
    Module[] appModules = new Module[]{gradleModule(getProject(), ":app1")};
    assume().that(appModules).asList().doesNotContain(null);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());
    myAction.actionPerformed(event);

    verify(myBuildInvoker).bundle(eq(appModules), eq(null));
  }

  public void testUpdateGradlePluginNotification() {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      getRootModuleBuilder(),
      new AndroidModuleModelBuilder(
        ":app1", "debug",
        new AndroidProjectBuilder()
          .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_APP)
          .withSupportsBundleTask(it -> false)
      ),
      new AndroidModuleModelBuilder(
        ":app2", "debug",
        new AndroidProjectBuilder()
          .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_APP)
          .withSupportsBundleTask(it -> false)
      )
    );
    Module[] appModules = new Module[]{gradleModule(getProject(), ":app1"), gradleModule(getProject(), ":app2")};
    assume().that(appModules).asList().doesNotContain(null);


    @SuppressWarnings("MagicConstant") // Using custom button IDs
      TestMessagesDialog testDialog = new TestMessagesDialog(1 /* Update*/);
    myDefaultTestDialog = TestDialogManager.setTestDialog(testDialog);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());
    myAction.actionPerformed(event);

    assertThat(testDialog.getDisplayedMessage()).isEqualTo(getHtmlUpdateMessage());
    // flush event queue to ensure the update call is processed.
    IdeEventQueue.getInstance().flushQueue();
    verify(myAndroidPluginVersionUpdater).updatePluginVersion(any(), any(), any());
  }

  public void testUpdateGradlePluginCanceledNotification() throws InterruptedException {
    setupTestProjectFromAndroidModel(
      getProject(),
      new File(getProject().getBasePath()),
      getRootModuleBuilder(),
      new AndroidModuleModelBuilder(
        ":app1", "debug",
        new AndroidProjectBuilder()
          .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_APP)
          .withSupportsBundleTask(it -> false)
      ),
      new AndroidModuleModelBuilder(
        ":app2", "debug",
        new AndroidProjectBuilder()
          .withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_APP)
          .withSupportsBundleTask(it -> false)
      )
    );
    Module[] appModules = new Module[]{gradleModule(getProject(), ":app1"), gradleModule(getProject(), ":app2")};
    assume().that(appModules).asList().doesNotContain(null);

    @SuppressWarnings("MagicConstant") // Using custom button IDs
      TestMessagesDialog testDialog = new TestMessagesDialog(0 /* Cancel*/);
    myDefaultTestDialog = TestDialogManager.setTestDialog(testDialog);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());
    myAction.actionPerformed(event);

    assertThat(testDialog.getDisplayedMessage()).isEqualTo(getHtmlUpdateMessage());
    verify(myAndroidPluginVersionUpdater, never()).updatePluginVersion(any(), any(), any());
  }

  private static String getHtmlUpdateMessage() {
    return "<html><body>Building Android App Bundles requires you to update to the latest version of the Android Gradle Plugin.<BR/>" +
           "<A HREF=\"https://d.android.com/r/studio-ui/dynamic-delivery/overview.html\">Learn More</A><BR/><BR/>" +
           "App bundles allow you to support multiple device configurations from a single build artifact.<BR/>" +
           "App stores that support the bundle format use it to build and sign your APKs for you, and<BR/>" +
           "serve those APKs to users as needed.<BR/><BR/></body></html>";
  }
}
