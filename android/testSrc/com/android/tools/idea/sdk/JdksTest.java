/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import com.android.tools.idea.gradle.project.sync.hyperlink.DownloadAndroidStudioHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.DownloadJdk8Hyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.UseEmbeddedJdkHyperlink;
import com.android.tools.idea.gradle.project.sync.hyperlink.UseJavaHomeAsJdkHyperlink;
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.PlatformTestCase;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link Jdks}.
 */
public class JdksTest extends PlatformTestCase {
  private IdeSdks mySpyIdeSdks;
  private EmbeddedDistributionPaths mySpyEmbeddedDistributionPaths;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeComponents ideComponents = new IdeComponents(myProject);
    mySpyIdeSdks = spy(IdeSdks.getInstance());
    mySpyEmbeddedDistributionPaths = spy(EmbeddedDistributionPaths.getInstance());
    ideComponents.replaceApplicationService(IdeSdks.class, mySpyIdeSdks);
    ideComponents.replaceApplicationService(EmbeddedDistributionPaths.class, mySpyEmbeddedDistributionPaths);
  }

  // These tests verify that LanguageLevel#isAtLeast does what we think it does (this is IntelliJ code.) Leaving these tests here as a way
  // ensure that regressions are not introduced later.
  public void testHasMatchingLangLevelWithLangLevel1dot6AndJdk7() {
    assertTrue(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_7, LanguageLevel.JDK_1_6));
  }

  public void testHasMatchingLangLevelWithLangLevel1dot7AndJdk7() {
    assertTrue(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_7, LanguageLevel.JDK_1_7));
  }

  public void testHasMatchingLangLevelWithLangLevel1dot7AndJdk6() {
    assertFalse(Jdks.hasMatchingLangLevel(JavaSdkVersion.JDK_1_6, LanguageLevel.JDK_1_7));
  }

  /**
   * Confirm {@link UseJavaHomeAsJdkHyperlink} is offered when JavaHome is not used
   */
  public void testGetWrongJdkQuickFixesNotUsingJavaHome() {
    doReturn(false).when(mySpyIdeSdks).isUsingJavaHomeJdk();
    verifyGetWrongJdkQuickFixes(UseJavaHomeAsJdkHyperlink.class);
  }

  /**
   * Confirm {@link UseEmbeddedJdkHyperlink} is offered when JavaHome is not used but is not valid
   */
  public void testGetWrongJdkQuickFixesNotUsingJavaHomeInvalid() {
    doReturn(false).when(mySpyIdeSdks).isUsingJavaHomeJdk();
    doReturn(null).when(mySpyIdeSdks).validateJdkPath(any());
    verifyGetWrongJdkQuickFixes(UseEmbeddedJdkHyperlink.class);
  }

  /**
   * Confirm {@link UseEmbeddedJdkHyperlink} is offered when JavaHome is already in use
   */
  public void testGetWrongJdkQuickFixesUsingJavaHome() {
    doReturn(true).when(mySpyIdeSdks).isUsingJavaHomeJdk();
    verifyGetWrongJdkQuickFixes(UseEmbeddedJdkHyperlink.class);
  }

  /**
   * Confirm {@link DownloadAndroidStudioHyperlink} is offered when JavaHome and Embedded jdk cannot be used
   */
  public void testGetWrongJdkQuickFixesUsingJavaHomeWithOutEmbedded() {
    doReturn(true).when(mySpyIdeSdks).isUsingJavaHomeJdk();
    doReturn(null).when(mySpyEmbeddedDistributionPaths).tryToGetEmbeddedJdkPath();
    verifyGetWrongJdkQuickFixes(DownloadAndroidStudioHyperlink.class);
  }

  private void verifyGetWrongJdkQuickFixes(@NotNull Class<? extends NotificationHyperlink> hyperlinkClass) {
    Jdks jdks = Jdks.getInstance();
    List<NotificationHyperlink> quickFixes = jdks.getWrongJdkQuickFixes(myProject);
    assertThat(quickFixes).hasSize(2);
    assertThat(quickFixes.get(0)).isInstanceOf(hyperlinkClass);
    assertThat(quickFixes.get(1)).isInstanceOf(DownloadJdk8Hyperlink.class);
  }
}
