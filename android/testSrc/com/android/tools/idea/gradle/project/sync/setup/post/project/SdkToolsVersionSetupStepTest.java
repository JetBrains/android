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
package com.android.tools.idea.gradle.project.sync.setup.post.project;

import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.FakePackage;
import com.android.repository.testframework.FakeRepoManager;
import com.android.tools.idea.project.AndroidNotificationStub;
import com.android.tools.idea.project.AndroidNotificationStub.NotificationMessage;
import com.android.tools.idea.gradle.project.sync.setup.post.project.SdkToolsVersionSetupStep.InstallSdkToolsHyperlink;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.android.tools.idea.sdk.IdeSdks;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.testFramework.PlatformTestCase;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import org.mockito.Mock;

import java.io.File;
import java.util.List;

import static com.android.tools.idea.sdk.VersionCheck.MIN_TOOLS_REV;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.notification.NotificationType.INFORMATION;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link SdkToolsVersionSetupStep}.
 */
public class SdkToolsVersionSetupStepTest extends PlatformTestCase {
  @Mock private IdeSdks myIdeSdks;

  private File sdkDir;

  private AndroidNotificationStub myNotification;
  private SdkToolsVersionSetupStep mySetupStep;

  private FakeRepoManager myRepoManager;
  private RepositoryPackages myRepoPackages;

  private ExecutorService myExecutorService = MoreExecutors.newDirectExecutorService();

  private static final String PLATFORM_SOURCE_PROPERTIES = "Pkg.UserSrc=false\n"
    + "Pkg.Revision=26.1.1\n"
    + "Platform.MinPlatformToolsRev=20\n"
    + "Pkg.Dependencies=emulator\n"
    + "Pkg.Path=tools\n"
    + "Pkg.Desc=Android SDK Tools\n";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    sdkDir = getTempDir().createTempDir();

    myNotification = AndroidNotificationStub.replaceSyncMessagesService(getProject());
    mySetupStep = new SdkToolsVersionSetupStep(myIdeSdks, () -> myRepoManager, () -> myExecutorService);

    myRepoPackages = new RepositoryPackages();
    myRepoManager = new FakeRepoManager(myRepoPackages);
  }

  public void testSetUpProject_toolsDirectory() throws Exception {
    File toolsDirectory = new File(sdkDir, "tools");
    toolsDirectory.mkdirs();
    File sourceProperties = new File(toolsDirectory, "source.properties");
    sourceProperties.createNewFile();
    Files.asCharSink(sourceProperties, Charset.forName("UTF-8")).write(PLATFORM_SOURCE_PROPERTIES);

    when(myIdeSdks.getAndroidSdkPath()).thenReturn(toolsDirectory.getParentFile());

    mySetupStep.setUpProject(getProject());

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).isEmpty();

    assertFalse(mySetupStep.isNewSdkVersionToolsInfoAlreadyShown());
  }

  public void testSetUpProject_renamedDirectory() {
    File toolsDirectory = new File(sdkDir, "tools-non-default");
    toolsDirectory.mkdirs();

    // This creates a "tools" package which resides in the "tools-non-default" directory as set above.
    FakePackage.FakeLocalPackage myToolsPackage = new FakePackage.FakeLocalPackage("tools");
    myToolsPackage.setInstalledPath(toolsDirectory);
    myRepoPackages.setLocalPkgInfos(ImmutableList.of(myToolsPackage));

    when(myIdeSdks.getAndroidSdkPath()).thenReturn(toolsDirectory.getParentFile());

    mySetupStep.setUpProject(getProject());

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).isEmpty();

    assertFalse(mySetupStep.isNewSdkVersionToolsInfoAlreadyShown());
  }

  public void testSetUpProject_toolsMissing() {
    when(myIdeSdks.getAndroidSdkPath()).thenReturn(new File("fakePath"));

    mySetupStep.setUpProject(getProject());

    List<NotificationMessage> messages = myNotification.getMessages();
    assertThat(messages).hasSize(1);

    NotificationMessage message = messages.get(0);
    assertEquals("Android SDK Tools", message.getTitle());
    assertEquals("Version " + MIN_TOOLS_REV + " or later is required.", message.getText());
    assertEquals(INFORMATION, message.getType());

    NotificationHyperlink[] hyperlinks = message.getHyperlinks();
    assertThat(hyperlinks).hasLength(1);

    NotificationHyperlink hyperlink = hyperlinks[0];
    assertThat(hyperlink).isInstanceOf(InstallSdkToolsHyperlink.class);
    assertEquals(MIN_TOOLS_REV, ((InstallSdkToolsHyperlink)hyperlink).getVersion());

    assertTrue(mySetupStep.isNewSdkVersionToolsInfoAlreadyShown());

    // We shutdown the executor and try again to make sure we'll not try to recompute the bubble as it has been already shown.
    myExecutorService.shutdown();
    mySetupStep.setUpProject(getProject());
  }

  public void testInvokeOnFailedSync() {
    assertTrue(mySetupStep.invokeOnFailedSync());
  }
}