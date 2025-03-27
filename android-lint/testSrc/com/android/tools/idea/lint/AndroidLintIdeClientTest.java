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
package com.android.tools.idea.lint;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.impl.meta.TypeDetails;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.sdklib.AndroidTargetHash;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.meta.DetailsTypes;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.tools.idea.lint.common.LintEditorResult;
import com.android.tools.idea.lint.common.LintIgnoredResult;
import com.android.tools.idea.lint.common.LintResult;
import com.android.tools.idea.model.AndroidModel;
import com.android.tools.idea.model.TestAndroidModel;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.client.api.PlatformLookupKt;
import com.android.tools.lint.detector.api.Project;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.Cancellation;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.concurrency.Semaphore;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.gradle.internal.impldep.org.junit.Rule;
import org.gradle.internal.impldep.org.junit.rules.TemporaryFolder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLintIdeClientTest extends AndroidTestCase {
  private final com.intellij.openapi.project.Project ideaProject = mock(com.intellij.openapi.project.Project.class);
  private final LintResult result = new LintIgnoredResult();
  @Rule private final TemporaryFolder temporaryFolder = new TemporaryFolder();

  public void testFindLintRuleJars_withOverride() throws IOException {
    LintClient client = new AndroidLintIdeClient(ideaProject, result) {
      @Nullable
      protected Module getModule(@NotNull com.android.tools.lint.detector.api.Project project) {
        return myModule;
      }
    };
    File expectedLintRuleJars = new File("com/bar.jar");
    AndroidModel.set(myFacet, TestAndroidModel.lintRuleJars(ImmutableList.of(expectedLintRuleJars)));
    temporaryFolder.create();
    File dir = temporaryFolder.newFolder("foo.aar");
    Project lintProject = new AndroidLintIdeProject(client, dir, dir);
    Iterable<File> actualLintRuleJars = client.findRuleJars(lintProject);
    assertThat(actualLintRuleJars).containsExactly(expectedLintRuleJars);
  }

  public void testFindLintRuleJars_withoutOverride() throws IOException {
    LintClient client = new AndroidLintIdeClient(ideaProject, result) {
      @Nullable
      protected Module getModule(@NotNull com.android.tools.lint.detector.api.Project project) {
        return myModule;
      }

      @Override
      public boolean isGradleProject(@NotNull com.android.tools.lint.detector.api.Project project) {
        return true;
      }
    };
    AndroidModel.set(myFacet, TestAndroidModel.namespaced(myFacet));
    temporaryFolder.create();
    File dir = temporaryFolder.newFolder("foo.aar");
    File expectedLintRuleJar = temporaryFolder.newFile("foo.aar/lint.jar");
    temporaryFolder.newFile(SdkConstants.FN_BUILD_GRADLE);

    Project lintProject = new AndroidLintIdeProject(client, dir, dir);
    Iterable<File> actualLintRuleJars = client.findRuleJars(lintProject);
    assertThat(actualLintRuleJars).containsExactly(expectedLintRuleJar);
  }

  public void testFindCompilationTarget() {
    Path sdkRoot = InMemoryFileSystems.createInMemoryFileSystemAndFolder("sdk");
    LocalPackage platformPackage = getLocalPlatformPackage(sdkRoot, "23", 23);
    LocalPackage previewPlatform = getLocalPlatformPackage(sdkRoot, "O", 26);
    LocalPackage addOnPlatform =
      getLocalAddOnPackage(
        sdkRoot, "google_apis", "Google APIs", "google", "Google Inc.", 23);

    RepositoryPackages packages = new RepositoryPackages();
    packages.setLocalPkgInfos(
      ImmutableList.of(platformPackage, previewPlatform, addOnPlatform));
    RepoManager mgr = new FakeRepoManager(null, packages);
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(sdkRoot, null, mgr);
    LintClient client = new AndroidLintIdeClient(ideaProject, result) {
      @Override
      public AndroidSdkHandler getSdk() {
        return sdkHandler;
      }
    };
    Project platformProject = mock(Project.class);
    when(platformProject.getBuildTargetHash()).thenReturn("android-23");
    when(platformProject.isAndroidProject()).thenReturn(true);
    Project previewProject = mock(Project.class);
    when(previewProject.getBuildTargetHash()).thenReturn("android-O");
    Project addOnProject = mock(Project.class);
    when(addOnProject.getBuildTargetHash()).thenReturn("Google Inc.:Google APIs:23");

    IAndroidTarget platformTarget = client.getCompileTarget(platformProject);
    assertThat(platformTarget).isNotNull();
    assertEquals("android-23", AndroidTargetHash.getTargetHashString(platformTarget));

    when(previewProject.isAndroidProject()).thenReturn(true);
    IAndroidTarget previewTarget = client.getCompileTarget(previewProject);
    assertThat(previewTarget).isNotNull();
    assertEquals("android-O", AndroidTargetHash.getTargetHashString(previewTarget));

    if (PlatformLookupKt.SUPPORTS_ADD_ONS) {
      when(addOnProject.isAndroidProject()).thenReturn(true);
      IAndroidTarget addOnTarget = client.getCompileTarget(addOnProject);
      assertThat(addOnTarget).isNotNull();
      assertEquals(
        "Google Inc.:Google APIs:23",
        AndroidTargetHash.getTargetHashString(addOnTarget));
    }

    // Don't return IAndroidTargets for non-Android projects
    when(previewProject.isAndroidProject()).thenReturn(false);
    previewTarget = client.getCompileTarget(previewProject);
    assertThat(previewTarget).isNull();
  }

  public void testReadActionCancellation() {
    // Regression test for b/382396705: cancellable read actions broken when run inside ExternalToolPass.
    PlatformTestUtil.withSystemProperty("android.lint.use.cancelable.read.actions.in.tests", "true", () -> {
      Application app = ApplicationManager.getApplication();
      LintClient client = new AndroidLintIdeClient(ideaProject, mock(LintEditorResult.class));

      var readActionStarted = new Semaphore(1);
      var readActionEnded = new Semaphore(1);
      var cancelledSuccessfully = new Ref<>(false);
      long msTimeout = TimeUnit.SECONDS.toMillis(10);

      // Start a read action on a background thread.
      app.executeOnPooledThread(() -> {
        // Simulate the behavior of Update.execute() used by ExternalToolPass, i.e., use Cancellation.withNonCancelableSection():
        // https://github.com/JetBrains/intellij-community/blob/ef54fa808e/platform/ide-core/src/com/intellij/util/ui/update/Update.kt#L60
        //noinspection UnstableApiUsage
        try (var ignoredAccessToken = Cancellation.withNonCancelableSection()) {
          client.runReadAction(() -> {
            try {
              readActionStarted.up();
              // Spin for a while, hoping to be cancelled.
              long msDeadline = System.currentTimeMillis() + msTimeout;
              while (System.currentTimeMillis() < msDeadline) {
                ProgressManager.checkCanceled();
                LockSupport.parkNanos(1_000_000);
              }
            }
            catch (ProcessCanceledException ignored) {
              cancelledSuccessfully.set(true);
            }
            finally {
              readActionEnded.up();
            }
          });
        }
      });

      // Once the read action starts, cancel it, wait, and verify ProcessCanceledException was thrown.
      assertThat(readActionStarted.waitFor(msTimeout)).isTrue();
      app.runWriteAction(EmptyRunnable.getInstance());
      assertThat(readActionEnded.waitFor(msTimeout)).isTrue();
      assertThat(cancelledSuccessfully.get()).isTrue();
    });
  }

  @NotNull
  private static LocalPackage getLocalPlatformPackage(Path sdkRoot, String version, int api) {
    InMemoryFileSystems.recordExistingFile(sdkRoot.resolve("android-" + version + "/build.prop"));
    FakePackage.FakeLocalPackage local =
      new FakePackage.FakeLocalPackage("platforms;android-" + version, sdkRoot.resolve("android-" + version));

    DetailsTypes.PlatformDetailsType platformDetails =
      AndroidSdkHandler.getRepositoryModule()
        .createLatestFactory()
        .createPlatformDetailsType();
    platformDetails.setApiLevel(api);
    if (!Character.isDigit(version.charAt(0))) {
      platformDetails.setCodename(version);
    }
    local.setTypeDetails((TypeDetails)platformDetails);
    return local;
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static LocalPackage getLocalAddOnPackage(
    Path sdkRoot,
    String tag,
    String tagDisplay,
    String vendor,
    String vendorDisplay,
    int version) {
    Path packagePath = sdkRoot.resolve("add-ons/addon-" + tag + "-" + vendor + "-" + version);
    // MUST also have platform target of the same version
    InMemoryFileSystems.recordExistingFile(packagePath.resolve("source.properties"));
    FakePackage.FakeLocalPackage local =
      new FakePackage.FakeLocalPackage("add-ons;addon-" + tag + "-" + vendor + "-" + version, packagePath);

    DetailsTypes.AddonDetailsType addOnDetails =
      AndroidSdkHandler.getAddonModule().createLatestFactory().createAddonDetailsType();
    addOnDetails.setVendor(IdDisplay.create(vendor, vendorDisplay));
    addOnDetails.setTag(IdDisplay.create(tag, tagDisplay));
    addOnDetails.setApiLevel(version);
    local.setTypeDetails((TypeDetails)addOnDetails);
    return local;
  }
}