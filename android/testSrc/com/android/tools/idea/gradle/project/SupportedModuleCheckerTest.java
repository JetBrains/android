/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.notification.NotificationType.ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

/**
 * Tests for {@link SupportedModuleChecker}.
 */
public class SupportedModuleCheckerTest extends PlatformTestCase {
  @Mock private GradleProjectInfo myGradleProjectInfo;
  private SupportedModuleChecker myModuleChecker;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    Project project = getProject();
    ServiceContainerUtil.replaceService(project, GradleProjectInfo.class, myGradleProjectInfo, getTestRootDisposable());
    myModuleChecker = new SupportedModuleChecker();
  }

  public void testCheckForSupportedModulesWithNonGradleProject() {
    Project project = getProject();
    AndroidNotification androidNotification = mock(AndroidNotification.class);
    ServiceContainerUtil.replaceService(project, AndroidNotification.class, androidNotification, getTestRootDisposable());
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(false);

    myModuleChecker.checkForSupportedModules(project);

    verify(androidNotification, never()).showBalloon(any(), any(), any());
  }

  private void setupMixedGradleJpsProject(Project project) {
    // These will be the "unsupported" modules, since they are not marked as "Gradle" modules.
    doCreateRealModuleIn("lib1", project, StdModuleTypes.JAVA);
    doCreateRealModuleIn("lib2", project, StdModuleTypes.JAVA);

    Module supportedModule = doCreateRealModuleIn("gradleModule", project, StdModuleTypes.JAVA);
    ExternalSystemModulePropertyManager.getInstance(supportedModule).setExternalId(GRADLE_SYSTEM_ID);
  }

  public void testCheckForSupportedModulesWithNonGradleModulesInAndroidStudio() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    Project project = getProject();
    AndroidNotificationStub androidNotification = new AndroidNotificationStub(project);
    IdeInfo androidIdeInfo = mock(IdeInfo.class);
    when(androidIdeInfo.isAndroidStudio()).thenReturn(true);

    ServiceContainerUtil.replaceService(project, AndroidNotification.class, androidNotification, getTestRootDisposable());
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), IdeInfo.class, androidIdeInfo, getTestRootDisposable());

    setupMixedGradleJpsProject(project);

    myModuleChecker.checkForSupportedModules(project);

    assertEquals("Unsupported Modules Detected", androidNotification.displayedTitle);
    assertThat(androidNotification.displayedText).contains("lib1");
    assertThat(androidNotification.displayedText).contains("lib2");
    assertEquals(ERROR, androidNotification.displayedType);
  }

  public void testCheckForSupportedModulesWithNonGradleModulesInIdea() {
    when(myGradleProjectInfo.isBuildWithGradle()).thenReturn(true);

    Project project = getProject();
    AndroidNotification androidNotification = mock(AndroidNotification.class);
    IdeInfo androidIdeInfo = mock(IdeInfo.class);
    when(androidIdeInfo.isAndroidStudio()).thenReturn(false);
    ServiceContainerUtil.replaceService(project, AndroidNotification.class, androidNotification, getTestRootDisposable());
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), IdeInfo.class, androidIdeInfo, getTestRootDisposable());

    setupMixedGradleJpsProject(project);

    myModuleChecker.checkForSupportedModules(project);

    // it is allowed to have Android module as a linked Gradle project in JPS/maven/whatever project
    verify(androidNotification, never()).showBalloon(any(), any(), any());
  }

  private static class AndroidNotificationStub extends AndroidNotification {
    private String displayedTitle;
    private String displayedText;
    private NotificationType displayedType;

    AndroidNotificationStub(@NotNull Project project) {
      super(project);
    }

    @Override
    public void showBalloon(@NotNull String title, @NotNull String text, @NotNull NotificationType type) {
      displayedTitle = title;
      displayedText = text;
      displayedType = type;
    }
  }
}
