/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea.issues;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.sdk.Jdks;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import java.nio.file.Paths;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link JdkImportCheck#validateProjectGradleJdk(Project, String)} and {@link JdkImportCheck#validateDefaultGradleJdk()}.
 */
public class JdkImportCheckTest extends AndroidGradleTestCase {
  private IdeSdks myMockIdeSdks;
  private Jdks myMockJdks;
  private Sdk myValidJdk;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myValidJdk = IdeSdks.getInstance().getJdk();
    myMockIdeSdks = new IdeComponents(getProject()).mockApplicationService(IdeSdks.class);
    myMockJdks = new IdeComponents(getProject()).mockApplicationService(Jdks.class);
    assertSame(myMockIdeSdks, IdeSdks.getInstance());

    StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.override(false);
  }

  @Override
  public void tearDown() throws Exception {
    StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.clearOverride();
    super.tearDown();
  }

  public void testDoCheckCanSyncWithNullJdk() {
    assertThat(runCheckJdkErrorMessage(null)).startsWith("Jdk location is not set");
  }

  public void testDoCheckWithJdkWithoutHomePath() {
    Sdk jdk = mock(Sdk.class);
    when(jdk.getHomePath()).thenReturn(null);

    assertThat(runCheckJdkErrorMessage(jdk)).startsWith("Could not find valid Jdk home from the selected Jdk location");
  }

  public void testDoCheckWithJdkWithIncompatibleVersion() {
    Sdk jdk = mock(Sdk.class);
    String pathToJdk10 = "/path/to/jdk10";

    when(jdk.getHomePath()).thenReturn(pathToJdk10);
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockJdks.findVersion(Paths.get(pathToJdk10))).thenReturn(JavaSdkVersion.JDK_10);

    assertThat(runCheckJdkErrorMessage(jdk)).startsWith(
      "The version of selected Jdk doesn't match the Jdk used by Studio. Please choose a valid Jdk 8 directory.\n" +
      "Selected Jdk location is /path/to/jdk10."
    );
  }

  public void testDoCheckWithJdkWithIncompatibleVersionNoCheck() {
    StudioFlags.ALLOW_DIFFERENT_JDK_VERSION.override(true);
    Sdk jdk = mock(Sdk.class);
    String pathToJdk10 = "/path/to/jdk10";
    when(jdk.getHomePath()).thenReturn(pathToJdk10);
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockJdks.findVersion(Paths.get(pathToJdk10))).thenReturn(JavaSdkVersion.JDK_10);

    assertThat(runCheckJdkErrorMessage(jdk)).startsWith(
      "The Jdk installation is invalid.\n" +
      "Selected Jdk location is /path/to/jdk10."
    );
  }

  public void testDoCheckWithJdkWithInvalidJdkInstallation() {
    Sdk jdk = mock(Sdk.class);
    String pathToJdk8 = "/path/to/jdk8";
    when(jdk.getHomePath()).thenReturn(pathToJdk8);
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(JavaSdkVersion.JDK_1_8);
    when(myMockJdks.findVersion(Paths.get(pathToJdk8))).thenReturn(JavaSdkVersion.JDK_1_8);

    assertThat(runCheckJdkErrorMessage(jdk)).startsWith(
      "The Jdk installation is invalid.\n" +
      "Selected Jdk location is /path/to/jdk8."
    );
  }

  public void testDoCheckWithJdkValidRecreate() {
    StudioFlags.GRADLE_SYNC_RECREATE_JDK.override(true);
    try {
      runCheckWithValid();
      verify(myMockIdeSdks).recreateOrAddJdkInTable(any());
    }
    finally {
      StudioFlags.GRADLE_SYNC_RECREATE_JDK.clearOverride();
    }
  }

  public void testDoCheckWithJdkValidNoRecreate() {
    StudioFlags.GRADLE_SYNC_RECREATE_JDK.override(false);
    try {
      runCheckWithValid();
      verify(myMockIdeSdks, never()).recreateOrAddJdkInTable(any());
    }
    finally {
      StudioFlags.GRADLE_SYNC_RECREATE_JDK.clearOverride();
    }
  }

  private void runCheckWithValid() {
  String versionString = myValidJdk.getVersionString();
    assertThat(versionString).isNotNull();
    JavaSdkVersion javaVersion = JavaSdkVersion.fromVersionString(versionString);
    assertThat(javaVersion).isNotNull();
    when(myMockIdeSdks.getRunningVersionOrDefault()).thenReturn(javaVersion);

    String jdkPath = myValidJdk.getHomePath();
    assertThat(jdkPath).isNotNull();
    when(myMockJdks.findVersion(Paths.get(jdkPath))).thenReturn(javaVersion);

    String message = runCheckJdkErrorMessage(myValidJdk);

    assertThat(message).isEmpty();
  }

  private static String runCheckJdkErrorMessage(@Nullable Sdk jdk) {
    try {
      JdkImportCheck.checkJdkErrorMessage(jdk);
      return ""; // No error
    } catch (JdkImportCheckException e) {
      return e.getMessage();
    }
  }
}
