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
    mySyncMessagesStub = GradleSyncMessagesStub.replaceSyncMessagesService(getProject(), getTestRootDisposable());
  }

  /**
   * @param cmakeVersion The CMake version to use for the created local package.
   * @return A fake local cmake package with the given version.
   */
  @NotNull
  private static LocalPackage createLocalPackage(@NotNull String cmakeVersion) {
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
  private static RemotePackage createRemotePackage(@NotNull String cmakeVersion) {
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
  private static MissingCMakeErrorHandler createHandler(@NotNull List<String> localPackages, @NotNull List<String> remotePackages) {
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

  @NotNull
  MissingCMakeErrorHandler.RevisionOrHigher createRevision(String revision, boolean orHigher) {
    return new MissingCMakeErrorHandler.RevisionOrHigher(Revision.parseRevision(revision), orHigher);
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

  public void testFailedToInstallCMakeFailedLicenceCheck() {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());
    String errMsg =
      "Failed to install the following Android SDK packages as some licences have not been accepted. blah blah CMake blah blah";
    assertEquals(
      errMsg,
      handler.findErrorMessage(
        new ExternalSystemException(errMsg), DummyProject.getInstance()));
  }

  public void testFailedToInstallCMake() {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());
    String errMsg = "Failed to install the following SDK components: blah blah cmake blah blah";
    assertEquals(
      errMsg,
      handler.findErrorMessage(
        new ExternalSystemException(errMsg), DummyProject.getInstance()));
  }

  public void testFindErrorMessageFailedToFindCmake() {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());

    String expectedMsg = "Failed to find CMake.";
    assertEquals(
      expectedMsg,
      handler.findErrorMessage(
        new ExternalSystemException("Failed to find CMake."), DummyProject.getInstance()));
  }

  public void testFindErrorMessageUnableToGetCmakeVersion() {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());

    String expectedMsg = "Failed to find CMake.";
    assertEquals(
      expectedMsg,
      handler.findErrorMessage(
        new ExternalSystemException("Unable to get the CMake version located at: /Users/alruiz/Library/Android/sdk/cmake/bin"),
        DummyProject.getInstance()));
  }

  public void testFindErrorMessageWithCmakeVersion() {
    MissingCMakeErrorHandler handler = createHandler(Collections.emptyList(), Collections.emptyList());
    String msg = "CMake '1.2.3' was not found in PATH or by cmake.dir property\n" +
                 "- CMake '4.5.6' was found on PATH\n";
    assertEquals(msg, handler.findErrorMessage(new ExternalSystemException(msg), DummyProject.getInstance()));
  }

  public void testDefaultInstall() {
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

  public void testCannotParseCmakeVersion() {
    String errMsg = "CMake 'x.y.z' was not found in PATH or by cmake.dir property.";
    MissingCMakeErrorHandler handler = createHandler(
      Collections.emptyList(),
      Collections.emptyList()
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(0);
  }

  public void testRemotePackageNotFound() {
    String errMsg = "CMake '3.7.0' was not found in PATH or by cmake.dir property.";
    MissingCMakeErrorHandler handler = createHandler(
      Collections.emptyList(),
      Collections.emptyList()
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(0);
  }

  public void testAlreadyInstalledRemote() {
    String errMsg = "CMake '3.10.2' was not found in PATH or by cmake.dir property.";
    MissingCMakeErrorHandler handler = createHandler(
      Collections.singletonList("3.10.2"),
      Collections.singletonList("3.10.2")
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(0);
  }

  public void testInstallFromRemote() {
    String errMsg = "CMake '3.10.2' was not found in PATH or by cmake.dir property.";

    MissingCMakeErrorHandler handler = createHandler(

      Collections.singletonList("3.8.2"),
      Collections.singletonList("3.10.2")
    );

    List<NotificationHyperlink> quickFixes = handler.getQuickFixHyperlinks(DummyProject.getInstance(), errMsg);
    assertThat(quickFixes).hasSize(1);
    assertThat(quickFixes.get(0)).isInstanceOf(InstallCMakeHyperlink.class);
    assertThat(((InstallCMakeHyperlink)quickFixes.get(0)).getCmakeVersion()).isEqualTo(Revision.parseRevision("3.10.2"));
  }

  public void testFindBestMatchVersionNotFound() {
    assertNull(
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.6.2"), createRemotePackage("3.8.2")),
        createRevision("3.7.2", false)));
  }

  public void testFindBestMatchVersionMatch() {
    assertEquals(
      Revision.parseRevision("3.8.2"),
      MissingCMakeErrorHandler.findBestMatch(
        Collections.singletonList(createRemotePackage("3.8.2")),
        createRevision("3.8.2", false)));
  }

  public void testFindBestMatchHigherVersionDownstream() {
    assertEquals(
      Revision.parseRevision("3.8.4"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8.2"), createRemotePackage("3.8.4")),
        createRevision("3.8.4", false)));
  }

  public void testFindBestMatchSelectsFirstMatch() {
    // Matches both available versions (preview version is ignored). The first match is selected.
    assertEquals(
      Revision.parseRevision("3.8.2-rc1"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8.2-rc1"), createRemotePackage("3.10.2-rc2")),
        createRevision("3.8.2", false)));

    assertEquals(
      Revision.parseRevision("3.8.2-rc1"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8.2-rc1"), createRemotePackage("3.10.2-rc2")),
        createRevision("3.8.2-rc3", false)));
  }

  public void testFindBestMatchRejectsPrefixMatch() {
    assertNull(
      MissingCMakeErrorHandler.findBestMatch(
        Collections.singletonList(createRemotePackage("3.8.2")),
        createRevision("3", false)));

    assertNull(
      MissingCMakeErrorHandler.findBestMatch(
        Collections.singletonList(createRemotePackage("3.8.2")),
        createRevision("3.8", false)));
  }

  public void testFindBestMatchWithPlusExactMatch() {
    assertEquals(
      Revision.parseRevision("3.8.2"),
      MissingCMakeErrorHandler.findBestMatch(
        Collections.singletonList(createRemotePackage("3.8.2")),
        createRevision("3.8.2", true)));
  }

  public void testFindBestMatchWithPlusMatchesHigherVersion() {
    assertEquals(
      Revision.parseRevision("3.8.2"),
      MissingCMakeErrorHandler.findBestMatch(
        Collections.singletonList(createRemotePackage("3.8.2")),
        createRevision("3.6.2", true)));
  }

  public void testFindBestMatchWithPlusSelectsFirstMatch() {
    // Plus matches both available versions (preview version is ignored). The first match is selected.
    assertEquals(
      Revision.parseRevision("3.8.2-rc1"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8.2-rc1"), createRemotePackage("3.8.2-rc2")),
        createRevision("3.8.2", true)));
    assertEquals(
      Revision.parseRevision("3.8.2-rc1"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.8.2-rc1"), createRemotePackage("3.8.2-rc2")),
        createRevision("3.8.2-rc3", true)));
  }

  public void testFindBestMatchRejectForkVersionInput() {
    // We don't want the user to put "3.6.4111459" as input.
    assertNull(
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.6.0")),
        createRevision("3.6.4111459", false)));
  }

  public void testFindBestMatchTranslateForkVersionFromSdk() {
    // If the SDK contains "3.6.4111459", then we translate it before matching.
    assertEquals(
      Revision.parseRevision("3.6.0"),
      MissingCMakeErrorHandler.findBestMatch(
        Arrays.asList(createRemotePackage("3.6.4111459")),
        createRevision("3.6.0", false)));
  }

  public void testVersionSatisfiesExactMatch() {
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0", false)));
  }

  public void testVersionSatisfiesIgnoresPreview() {
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0", false)));
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0-rc2", false)));
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0-rc2", false)));
  }

  public void testVersionSatisfiesMismatch() {
    assertFalse(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.10.0", false)));
  }

  public void testVersionSatisfiesWithPlusExactMatch() {
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0", true)));
  }

  public void testVersionSatisfiesWithPlusMatchesHigherVersion() {
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.10.0"), createRevision("3.8.0", true)));
  }

  public void testVersionSatisfiesWithPlusIgnoresPreview() {
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0", true)));
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.8.0-rc2", true)));
    assertTrue(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0-rc1"), createRevision("3.8.0-rc2", true)));
  }

  public void testVersionSatisfiesWithPlusMismatch() {
    assertFalse(MissingCMakeErrorHandler.versionSatisfies(
      Revision.parseRevision("3.8.0"), createRevision("3.10.0", true)));
  }

  public void testExtractCmakeVersionFromErrorValidInput() {
    MissingCMakeErrorHandler.RevisionOrHigher rev1 = MissingCMakeErrorHandler.extractCmakeVersionFromError("prefix '1.2.3' suffix");
    assertEquals("1.2.3", rev1.revision.toString());
    assertFalse(rev1.orHigher);

    MissingCMakeErrorHandler.RevisionOrHigher rev2 = MissingCMakeErrorHandler.extractCmakeVersionFromError("prefix'1.2.3'suffix");
    assertEquals("1.2.3", rev2.revision.toString());
    assertFalse(rev2.orHigher);

    MissingCMakeErrorHandler.RevisionOrHigher rev3 = MissingCMakeErrorHandler.extractCmakeVersionFromError("'1.2.3'");
    assertEquals("1.2.3", rev3.revision.toString());
    assertFalse(rev3.orHigher);

    MissingCMakeErrorHandler.RevisionOrHigher rev4 = MissingCMakeErrorHandler.extractCmakeVersionFromError("'1.2.3' or higher");
    assertEquals("1.2.3", rev4.revision.toString());
    assertTrue(rev4.orHigher);
  }

  public void testExtractCmakeVersionFromErrorInvalidInput() {
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError(""));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("does not have quoted substring"));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("missing matching ' single quote"));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("'"));
    assertNull(MissingCMakeErrorHandler.extractCmakeVersionFromError("'a.b.c'"));
  }
}