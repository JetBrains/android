/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.errors;

import com.android.repository.Revision;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.tools.idea.gradle.project.sync.hyperlink.InstallCMakeHyperlink;
import com.android.tools.idea.gradle.project.sync.messages.GradleSyncMessagesStub;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.openapi.command.impl.DummyProject;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.project.sync.SimulatedSyncErrors.registerSyncErrorToSimulate;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link MissingCMakeErrorHandler}.
 */
public class MissingCMakeErrorHandlerTest extends AndroidGradleTestCase {
  private GradleSyncMessagesStub mySyncMessagesStub;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject());
  }

  /**
   * @param cmakeVersion The CMake version to use for the created local package.
   * @return A fake local cmake package with the given version.
   */
  @NotNull
  private LocalPackage createLocalPackage(@NotNull String cmakeVersion) {
    Revision revision = Revision.parseRevision(cmakeVersion);
    FakePackage.FakeLocalPackage pkg = new FakePackage.FakeLocalPackage("cmake;" + cmakeVersion);
    pkg.setRevision(revision);
    return pkg;
  }

  /**
   * @param cmakeVersion The CMake version to use for the created remote package.
   * @return A fake remote cmake package with the given version.
   */
  @NotNull
  private RemotePackage createRemotePackage(@NotNull String cmakeVersion) {
    Revision revision = Revision.parseRevision(cmakeVersion);
    FakePackage.FakeRemotePackage pkg = new FakePackage.FakeRemotePackage("cmake;" + cmakeVersion);
    pkg.setRevision(revision);
    return pkg;
  }

  /**
   * @param localPackages  The local packages to install to the fake SDK.
   * @param remotePackages The remote packages to install to the fake SDK.
   * @return An error handler with a fake SDK that contains the provided local and remote packages.
   */
  @NotNull
  private MissingCMakeErrorHandler createHandler(@NotNull List<String> localPackages, @NotNull List<String> remotePackages) {
    return new MissingCMakeErrorHandler() {
      @Override
      @NotNull
      public RepoManager getSdkManager() {
        return new FakeRepoManager(
          null,
          new RepositoryPackages(
            localPackages.stream().map(p -> createLocalPackage(p)).collect(Collectors.toList()),
            remotePackages.stream().map(p -> createRemotePackage(p)).collect(Collectors.toList()))
        );
      }
    };
  }

  public void testIntegration() throws Exception {
    // Verifies the integration of findErrorMessage and getQuickFixHyperlinks methods with gradle.
    String errMsg = "Failed to find CMake.";
    registerSyncErrorToSimulate(errMsg);
    loadProjectAndExpectSyncError(SIMPLE_APPLICATION);

    GradleSyncMessagesStub.NotificationUpdate notificationUpdate = mySyncMessagesStub.getNotificationUpdate();
    assertNotNull(notificationUpdate);

    assertThat(notificationUpdate.getText()).isEqualTo("Failed to find CMake.");
  }

  public void testFindErrorMessageNoCMakeVersion() throws Exception {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());

    String expectedMsg = "Failed to find CMake.";
    assertEquals(
      expectedMsg,
      handler.findErrorMessage(
        new ExternalSystemException("Failed to find CMake."), DummyProject.getInstance()));
    assertEquals(
      expectedMsg,
      handler.findErrorMessage(
        new ExternalSystemException("Unable to get the CMake version located at: /Users/alruiz/Library/Android/sdk/cmake/bin"),
        DummyProject.getInstance()));
  }

  public void testFindErrorMessageWithCmakeVersion() throws Exception {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());
    String msg = "CMake '1.2.3' was not found in PATH or by cmake.dir property\n" +
                 "- CMake '4.5.6' was found on PATH\n";
    assertEquals(msg, handler.findErrorMessage(new ExternalSystemException(msg), DummyProject.getInstance()));
  }

  public void testDefaultInstall() throws Exception {
    String errMsg = "Failed to find CMake";
    MissingCMakeErrorHandler handler = createHandler(
      Collections.emptyList(),
      Collections.emptyList()
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(1);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallCMakeHyperlink.class);
    assertThat(((InstallCMakeHyperlink)quickFixes.get(0)).getCmakeVersion()).isEqualTo(null);
  }

  public void testCannotParseCmakeVersion() throws Exception {
    String errMsg = "CMake 'x.y.z' was not found in PATH or by cmake.dir property.";
    MissingCMakeErrorHandler handler = createHandler(
      Collections.emptyList(),
      Collections.emptyList()
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(0);
  }

  public void testRemotePackageNotFound() throws Exception {
    String errMsg = "CMake '3.7.0' was not found in PATH or by cmake.dir property.";
    MissingCMakeErrorHandler handler = createHandler(
      Collections.emptyList(),
      Collections.emptyList()
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(0);
  }

  public void testAlreadyInstalledRemote() throws Exception {
    String errMsg = "CMake '3.10.2' was not found in PATH or by cmake.dir property.";
    MissingCMakeErrorHandler handler = createHandler(
      Arrays.asList("3.10.2"),
      Arrays.asList("3.10.2")
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(0);
  }

  public void testInstallFromRemote() throws Exception {
    String errMsg = "CMake '3.10.2' was not found in PATH or by cmake.dir property.";

    MissingCMakeErrorHandler handler = createHandler(
      Arrays.asList("3.8"),
      Arrays.asList("3.10.2")
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(1);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallCMakeHyperlink.class);
    assertThat(((InstallCMakeHyperlink)quickFixes.get(0)).getCmakeVersion()).isEqualTo(Revision.parseRevision("3.10.2"));
  }

  public void testFindBestMatch() throws Exception {
    // Requested version not found.
    assertEquals(
      null,
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.6"), createRemotePackage("3.8")),
        Revision.parseRevision("3.7")));

    // Exact match.
    assertEquals(
      Revision.parseRevision("3.8"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8")),
        Revision.parseRevision("3.8")));

    // A more specific version found.
    assertEquals(
      Revision.parseRevision("3.8"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8")),
        Revision.parseRevision("3")));

    // More specific match downstream.
    assertEquals(
      Revision.parseRevision("3.8.2"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8"), createRemotePackage("3.8.2")),
        Revision.parseRevision("3.8")));

    // Request is too specific. Cannot satisfy.
    assertEquals(
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8"), createRemotePackage("3.10")),
        Revision.parseRevision("3.8.2")),
      null);
  }

  public void testVersionSatisfies() throws Exception {
    // Exact match.
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(new int[]{3, 8, 0}, new int[]{3, 8, 0}));

    // Request longer.
    assertFalse(MissingCMakeErrorHandler.versionSatisfies(new int[]{3, 8}, new int[]{3, 8, 2}));

    // Request shorter.
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(new int[]{3, 8, 0}, new int[]{3, 8}));

    // Version mismatch.
    assertFalse(MissingCMakeErrorHandler.versionSatisfies(new int[]{3, 8, 0}, new int[]{3, 10, 0}));
  }

  public void testExtractCmakeVersionFromError() throws Exception {
    assertEquals(Revision.parseRevision("1.2.3-rc1"), MissingCMakeErrorHandler.extractCmakeVersionFromError("prefix '1.2.3-rc1' suffix"));
    assertEquals(Revision.parseRevision("1.2.3-rc1"), MissingCMakeErrorHandler.extractCmakeVersionFromError("prefix'1.2.3-rc1'suffix"));
    assertEquals(Revision.parseRevision("1.2.3-rc1"), MissingCMakeErrorHandler.extractCmakeVersionFromError("'1.2.3-rc1'"));

    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError(""));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("does not have quoted substring"));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("missing matching ' single quote"));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("'"));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("''"));
  }
}