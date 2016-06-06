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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.repository.testframework.*;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationCategory;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.project.Project;
import junit.framework.TestCase;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.easymock.EasyMock.createMock;

/**
 * Tests for {@link MissingCMakeErrorHandler}
 */
public class MissingCMakeErrorHandlerTest extends TestCase {
  private ExternalSystemException myError;
  private NotificationData myNotification;
  private Project myProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myError = new ExternalSystemException("Test");
    myNotification = new NotificationData("Test", "Testing", NotificationCategory.ERROR, NotificationSource.PROJECT_SYNC);
    myProject = createMock(Project.class);
  }

  public void testHandleError() throws Exception {
    RepositoryPackages packages = new RepositoryPackages();
    Map<String, RemotePackage> remotePackages = Maps.newHashMap();
    Revision fakeCmakeVersion = new Revision(1, 2);
    String fakeCmakePackage = SdkConstants.FD_CMAKE + RepoPackage.PATH_SEPARATOR + fakeCmakeVersion.toString();
    remotePackages.put(fakeCmakePackage, new FakePackage(fakeCmakePackage, fakeCmakeVersion, null));
    packages.setRemotePkgInfos(remotePackages);
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(new File("/sdk"), new MockFileOp(), new FakeRepoManager(packages));

    MissingCMakeErrorHandler errorHandler = new MissingCMakeErrorHandler(sdkHandler, new FakeDownloader(new MockFileOp()),
                                                                         new FakeSettingsController(false));

    List<String> message = ImmutableList.of("Failed to find CMake.", "Install from Android Studio under File/Settings/" +
                                                                     "Appearance & Behavior/System Settings/Android SDK/SDK Tools/CMake.");
    assertTrue(errorHandler.handleError(message, myError, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, "Failed to find CMake.\n<a href=\"install.cmake\">Install CMake and sync project</a>");
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertEquals(ImmutableList.of("install.cmake"), linkIds);
  }

  public void testHandleErrorNoCMakePackage() {
    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(new File("/sdk"), new MockFileOp(), new FakeRepoManager(new RepositoryPackages()));
    MissingCMakeErrorHandler errorHandler = new MissingCMakeErrorHandler(sdkHandler, new FakeDownloader(new MockFileOp()),
                                                                         new FakeSettingsController(false));

    List<String> message = ImmutableList.of("Failed to find CMake.", "Install from Android Studio under File/Settings/" +
                                                                     "Appearance & Behavior/System Settings/Android SDK/SDK Tools/CMake.");
    String originalNotificationMessage = myNotification.getMessage();
    assertFalse(errorHandler.handleError(message, myError, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, originalNotificationMessage);
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertTrue(linkIds.isEmpty());
  }

  public void testHandlerErrorUnrelatedMessage() {
    RepositoryPackages packages = new RepositoryPackages();
    Map<String, RemotePackage> remotePackages = Maps.newHashMap();
    packages.setRemotePkgInfos(remotePackages);

    Revision fakeCmakeVersion = new Revision(1, 2);
    String fakeCmakePackage = SdkConstants.FD_CMAKE + RepoPackage.PATH_SEPARATOR + fakeCmakeVersion.toString();
    remotePackages.put(fakeCmakePackage, new FakePackage(fakeCmakePackage, fakeCmakeVersion, null));
    packages.setRemotePkgInfos(remotePackages);

    AndroidSdkHandler sdkHandler = new AndroidSdkHandler(new File("/sdk"), new MockFileOp(), new FakeRepoManager(packages));
    MissingCMakeErrorHandler errorHandler = new MissingCMakeErrorHandler(sdkHandler, new FakeDownloader(new MockFileOp()),
                                                new FakeSettingsController(false));

    List<String> message = ImmutableList.of("Arbitrary message.", "This has nothing to do with CMake.");
    String originalNotificationMessage = myNotification.getMessage();
    assertFalse(errorHandler.handleError(message, myError, myNotification, myProject));
    String notificationMessage = myNotification.getMessage();
    assertEquals(notificationMessage, originalNotificationMessage);
    List<String> linkIds = myNotification.getRegisteredListenerIds();
    assertTrue(linkIds.isEmpty());
  }
}
