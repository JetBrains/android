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
}
