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
package com.android.tools.idea.sdk;

import static com.android.tools.idea.sdk.IdeSdks.getJdkFromJavaHome;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.utils.FileUtils;
import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link IdeSdks}
 */
public class IdeSdksAndroidTest extends AndroidGradleTestCase {
  @Nullable private File myInitialJdkPath;
  private IdeSdks myIdeSdks;
  private boolean myEmbeddedIsJavaHome;
  private File myJavaHomePath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeSdks = IdeSdks.getInstance();
    myInitialJdkPath = myIdeSdks.getJdkPath();
    String javaHome = getJdkFromJavaHome();
    assertThat(javaHome).isNotEmpty();
    myJavaHomePath = new File(javaHome);
    File embeddedPath = EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath();
    myEmbeddedIsJavaHome = FileUtils.isSameFile(embeddedPath, myJavaHomePath);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myInitialJdkPath != null) {
        ApplicationManager.getApplication().runWriteAction(() -> myIdeSdks.setJdkPath(myInitialJdkPath));
      }
    }
    finally {
      super.tearDown();
    }
  }

  /**
   * Verify that {@link IdeSdks#isUsingJavaHomeJdk} and {@link IdeSdks#isUsingEmbeddedJdk} return correct values when using JAVA_HOME
   */
  public void testJavaHomeJdk() {
    ApplicationManager.getApplication().runWriteAction(() -> myIdeSdks.setJdkPath(myJavaHomePath));
    assertTrue(myIdeSdks.isUsingJavaHomeJdk(false /* do not assume it is uint test */));
    assertEquals(myIdeSdks.isUsingEmbeddedJdk(), myEmbeddedIsJavaHome);
  }

  /**
   * Verify that {@link IdeSdks#isUsingJavaHomeJdk} and {@link IdeSdks#isUsingEmbeddedJdk} return correct values when using embedded JDK
   */
  public void testEmbeddedJdk() {
    ApplicationManager.getApplication().runWriteAction(() -> myIdeSdks.setUseEmbeddedJdk());
    assertTrue(myIdeSdks.isUsingEmbeddedJdk());
    assertEquals(myIdeSdks.isUsingJavaHomeJdk(false /* do not assume it is uint test */), myEmbeddedIsJavaHome);
  }

  /**
   * Verify that {@link IdeSdks#isUsingJavaHomeJdk} calls to {@link IdeSdks#getJdkPath} (b/131297172)
   */
  public void testIsUsingJavaHomeJdkCallsGetJdk() {
    IdeSdks spyIdeSdks = spy(myIdeSdks);
    spyIdeSdks.isUsingJavaHomeJdk(false /* do not assume it is uint test */);
    verify(spyIdeSdks).getJdkPath();
  }

  /**
   * Calling doGetJdkFromPathOrParent should not result in NPE if it is set to "/" (b/132219284)
   */
  public void testDoGetJdkFromPathOrParentRoot() {
    String path = IdeSdks.doGetJdkFromPathOrParent("/");
    assertThat(path).isNull();
  }

  /**
   * Calling doGetJdkFromPathOrParent should not result in NPE if it is set to "" (b/132219284)
   */
  public void testDDoGetJdkFromPathOrParentEmpty() {
    String path = IdeSdks.doGetJdkFromPathOrParent("");
    assertThat(path).isNull();
  }

  /**
   * Calling doGetJdkFromPathOrParent should not result in NPE if it is not a valid path (b/132219284)
   */
  public void testDoGetJdkFromPathOrParentSpaces() {
    String path = IdeSdks.doGetJdkFromPathOrParent("  ");
    assertThat(path).isNull();
  }

  /**
   * Confirm that setting Jdk path also changes the result of isUsingEnvVariableJdk
   */
  public void testIsUsingEnvVariableJdk() {
    myIdeSdks.cleanJdkEnvVariableInitialization();
    myIdeSdks.initializeJdkEnvVariable(myInitialJdkPath.getAbsolutePath());
    assertThat(myIdeSdks.isUsingEnvVariableJdk()).isTrue();
    ApplicationManager.getApplication().runWriteAction(() -> myIdeSdks.setJdkPath(myJavaHomePath));
    assertThat(myIdeSdks.isUsingEnvVariableJdk()).isFalse();
  }
}
