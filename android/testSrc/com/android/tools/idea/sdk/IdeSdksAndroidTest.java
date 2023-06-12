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
import static com.android.tools.idea.testing.AndroidGradleTests.getEmbeddedJdk8Path;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.utils.FileUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JavaSdkVersionUtil;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.projectRoots.impl.ProjectJdkImpl;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;

/**
 * Tests for {@link IdeSdks}
 */
public class IdeSdksAndroidTest extends AndroidGradleTestCase {
  @Nullable private Path myInitialJdkPath;
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
    Path embeddedPath = EmbeddedDistributionPaths.getInstance().getEmbeddedJdkPath();
    myEmbeddedIsJavaHome = FileUtils.isSameFile(embeddedPath.toFile(), myJavaHomePath);
    cleanJdkTable();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      if (myInitialJdkPath != null) {
        ApplicationManager.getApplication().runWriteAction(() -> {myIdeSdks.setJdkPath(myInitialJdkPath);});
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
    WriteAction.run(() -> {
      Sdk[] jdks = ProjectJdkTable.getInstance().getAllJdks();
      for (Sdk jdk : jdks) {
        ProjectJdkTable.getInstance().removeJdk(jdk);
      }
    });
    ApplicationManager.getApplication().runWriteAction((Runnable)() -> myIdeSdks.setJdkPath(myJavaHomePath.toPath()));
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
    verify(spyIdeSdks).doGetJdk(eq(true));
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
    myIdeSdks.overrideJdkEnvVariable(myInitialJdkPath.toAbsolutePath().toString());
    assertThat(myIdeSdks.isUsingEnvVariableJdk()).isTrue();
    ApplicationManager.getApplication().runWriteAction((Runnable)() -> myIdeSdks.setJdkPath(myJavaHomePath.toPath()));
    assertThat(myIdeSdks.isUsingEnvVariableJdk()).isFalse();
  }

  /**
   * Verify that the embedded JDK8 can be used in setJdk
   */
  public void testSetJdk8() throws IOException {
    File jdkPath = new File(getEmbeddedJdk8Path());
    AtomicReference<Sdk> createdJdkRef = new AtomicReference<>(null);
    ApplicationManager.getApplication().runWriteAction(() -> {createdJdkRef.set(myIdeSdks.setJdkPath(jdkPath.toPath()));});
    Sdk createdJdk = createdJdkRef.get();
    assertThat(createdJdk).isNotNull();
    JavaSdkVersion createdVersion = JavaSdkVersionUtil.getJavaSdkVersion(createdJdk);
    assertThat(createdVersion).isEqualTo(JavaSdkVersion.JDK_1_8);
    assertThat(FileUtils.isSameFile(jdkPath, new File(createdJdk.getHomePath()))).isTrue();
    assertThat(myIdeSdks.getJdk()).isEqualTo(createdJdk);
  }

  /**
   * Confirm that isJdkCompatible returns true with embedded JDK 8
   */
  public void testIsJdkCompatibleJdk8() throws IOException {
    @Nullable Sdk jdk = Jdks.getInstance().createJdk(getEmbeddedJdk8Path());
    assertThat(IdeSdks.getInstance().isJdkCompatible(jdk, myIdeSdks.getRunningVersionOrDefault())).isTrue();
  }

  /**
   * Confirm that isJdkCompatible returns true with embedded JDK
   */
  public void testIsJdkCompatibleEmbedded() {
    @Nullable Sdk jdk = Jdks.getInstance().createJdk(myIdeSdks.getEmbeddedJdkPath().toString());
    assertThat(IdeSdks.getInstance().isJdkCompatible(jdk, myIdeSdks.getRunningVersionOrDefault())).isTrue();
  }

  /**
   * Recreated JDK should have same class roots
   */
  public void testRecreateJdkInTableSameClassRoots() {
    Sdk originalJdk = myIdeSdks.getJdk();
    assertThat(originalJdk).isNotNull();
    assertThat(originalJdk).isInstanceOf(ProjectJdkImpl.class);

    VirtualFile[] originalClassRoots = ((ProjectJdkImpl)originalJdk).getRoots(OrderRootType.CLASSES);
    SdkTypeId sdkType = originalJdk.getSdkType();
    assertThat(sdkType).isInstanceOf(JavaSdk.class);

    ProjectJdkTable spyJdkTable = spy(ProjectJdkTable.getInstance());
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ProjectJdkTable.class, spyJdkTable, getProject());


    myIdeSdks.recreateProjectJdkTable();
    // JDK should be updated
    ArgumentCaptor<Sdk> sdkCaptor = ArgumentCaptor.forClass(Sdk.class);
    verify(spyJdkTable).updateJdk(eq(originalJdk), sdkCaptor.capture());

    // Jdk used to update should not be the same but must have same class roots
    Sdk newSdk = sdkCaptor.getValue();
    assertThat(newSdk).isNotNull();
    assertThat(newSdk).isNotSameAs(originalJdk);
    VirtualFile[] newClassRoots = ((ProjectJdkImpl)newSdk).getRoots(OrderRootType.CLASSES);
    assertThat(newClassRoots).isEqualTo(originalClassRoots);

    // Jdk should be the same as it was updated, not replaced
    Sdk recreatedJdk = myIdeSdks.getJdk();
    assertThat(recreatedJdk).isNotNull();
    assertThat(recreatedJdk).isSameAs(originalJdk);
    VirtualFile[] recreatedClassRoots = ((ProjectJdkImpl)recreatedJdk).getRoots(OrderRootType.CLASSES);
    assertThat(recreatedClassRoots).isEqualTo(originalClassRoots);
  }

  /**
   * Recreating JDK should revert changes done in the root classes
   */
  public void testRecreateOrAddJdkInTableRevertsClassRootsChanges() {
    Sdk originalJdk = myIdeSdks.getJdk();
    assertThat(originalJdk).isNotNull();

    assertThat(originalJdk).isInstanceOf(ProjectJdkImpl.class);
    VirtualFile[] originalClassRoots = ((ProjectJdkImpl)originalJdk).getRoots(OrderRootType.CLASSES);
    assertThat(originalClassRoots).isNotEmpty();

    SdkTypeId sdkType = originalJdk.getSdkType();
    assertThat(sdkType).isInstanceOf(JavaSdk.class);

    ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), ProjectJdkTable.class, jdkTable, getProject());

    // Created a modified JDK by removing a root class and update the jdkTable with it.
    ProjectJdkImpl finalModifiedJdk = ((ProjectJdkImpl)originalJdk).clone();
    finalModifiedJdk.removeRoot(originalClassRoots[0], OrderRootType.CLASSES);
    WriteAction.runAndWait(() -> jdkTable.updateJdk(originalJdk, finalModifiedJdk));

    // Verify a root was removed
    ProjectJdkImpl modifiedJdk = (ProjectJdkImpl)myIdeSdks.getJdk();
    assertThat(modifiedJdk).isNotNull();
    assertThat(modifiedJdk).isInstanceOf(ProjectJdkImpl.class);
    VirtualFile[] modifiedClassRoots = modifiedJdk.getRoots(OrderRootType.CLASSES);
    assertThat(modifiedClassRoots).hasLength(originalClassRoots.length - 1);

    // Recreate Jdk
    myIdeSdks.recreateOrAddJdkInTable(modifiedJdk.getHomePath(), modifiedJdk.getName());

    // Jdk roots should be the same as original after recreating
    Sdk recreatedJdk = myIdeSdks.getJdk();
    assertThat(recreatedJdk).isNotNull();
    assertThat(recreatedJdk).isInstanceOf(ProjectJdkImpl.class);
    VirtualFile[] recreatedClassRoots = ((ProjectJdkImpl)recreatedJdk).getRoots(OrderRootType.CLASSES);
    assertThat(recreatedClassRoots).isEqualTo(originalClassRoots);
  }

  private void cleanJdkTable() {
    WriteAction.run(() -> Arrays.stream(ProjectJdkTable.getInstance().getAllJdks()).forEach(
      sdk -> ProjectJdkTable.getInstance().removeJdk(sdk)
    ));
  }
}
