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
import static com.google.common.truth.TruthJUnit.assume;
import static com.intellij.notification.NotificationType.ERROR;
import static java.util.Collections.emptyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.gradle.model.IdeAndroidProjectType;
import com.android.tools.idea.gradle.project.build.invoker.AssembleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.project.build.invoker.GradleMultiInvocationResult;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.project.AndroidNotification;
import com.android.tools.idea.testing.AndroidModuleModelBuilder;
import com.android.tools.idea.testing.AndroidProjectBuilder;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.module.Module;
import com.intellij.testFramework.HeavyPlatformTestCase;
import java.io.File;
import org.mockito.Mock;

/**
 * Tests for {@link GenerateApkAction}.
 */
public class BuildBundleActionTest extends HeavyPlatformTestCase {
  @Mock private GradleBuildInvoker myBuildInvoker;
  @Mock private AndroidNotification myAndroidNotification;
  private GenerateBundleAction myAction;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    new IdeComponents(myProject).replaceProjectService(GradleBuildInvoker.class, myBuildInvoker);
    new IdeComponents(myProject).replaceProjectService(AndroidNotification.class, myAndroidNotification);
    myAction = new GenerateBundleAction();
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
      new AndroidModuleModelBuilder(":feature1", "debug",
                                    new AndroidProjectBuilder().withProjectType(it -> IdeAndroidProjectType.PROJECT_TYPE_DYNAMIC_FEATURE))
    );
    Module[] appModules = new Module[]{gradleModule(getProject(), ":app1")};
    assume().that(appModules).asList().doesNotContain(null);

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());
    when(myBuildInvoker.bundle(any()))
      .thenReturn(
        Futures.immediateFuture(
          new AssembleInvocationResult(
            new GradleMultiInvocationResult(
              ImmutableList.of(
                new GradleInvocationResult(new File("/root"), emptyList(), null)
              )
            ),
            BuildMode.BUNDLE)));
    myAction.actionPerformed(event);

    verify(myBuildInvoker).bundle(eq(appModules));
  }

  public void testNoBundleModulesNotification() {
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

    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getProject()).thenReturn(getProject());
    myAction.actionPerformed(event);

    verify(myAndroidNotification).showBalloon(any(), any(), eq(ERROR));
  }
}
