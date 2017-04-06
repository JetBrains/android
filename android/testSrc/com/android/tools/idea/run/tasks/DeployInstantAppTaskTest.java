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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.tools.idea.instantapp.provision.ProvisionPackageTests;
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.ZipUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class DeployInstantAppTaskTest {

  @Rule public TemporaryFolder myFolder = new TemporaryFolder();
  @Mock private LaunchStatus myLaunchStatus;
  @Mock private ConsolePrinter myPrinter;
  @Mock private Project myProject;
  @Captor private ArgumentCaptor<String> myShellCommandsCaptor;

  private File createDummyZipFile() throws Exception {
    File apk = myFolder.newFile("dummy.apk");
    File zip = myFolder.newFile("dummy.zip");
    ZipUtil.compressFile(apk, zip);
    return zip;
  }

  @Before
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(myLaunchStatus.isLaunchTerminated()).thenReturn(false);
  }

  @Test
  public void perform_uploadsCorrectly() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setGoogleAccountLogged().setOsBuildType("dev-keys").setApiLevel(24).getDevice();

    ApkInfo dummyInfo = new ApkInfo(createDummyZipFile(), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo), myProject);

    assertThat(task.perform(device, myLaunchStatus, myPrinter)).isTrue();

    verify(device, times(1)).pushFile(dummyInfo.getFile().getPath(), "/data/local/tmp/aia/dummy.zip");
    verify(device, times(5)).executeShellCommand(myShellCommandsCaptor.capture(), any(IShellOutputReceiver.class));

    List<String> shellCommands = myShellCommandsCaptor.getAllValues();

    assertThat(shellCommands.get(1)).matches("su shell mkdir -p /data/local/tmp/aia/");
    assertThat(shellCommands.get(2))
      .matches("am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" " +
               "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" \"/data/local/tmp/aia/dummy.zip\" " +
               "--es \"com.google.android.instantapps.devman.iapk.INSTALL_TOKEN\" \"[^\"]+\" " +
               "--ez \"com.google.android.instantapps.devman.iapk.FORCE\" \"false\" " +
               "-n com.google.android.instantapps.devman/.iapk.IapkLoadService");
    assertThat(shellCommands.get(3)).isEqualTo("rm -f /data/local/tmp/aia/dummy.zip");
    assertThat(shellCommands.get(4)).isEqualTo("am force-stop com.google.android.instantapps.supervisor");
  }

  @Test
  public void perform_uploadsCorrectlyPostO() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setGoogleAccountLogged().setOsBuildType("dev-keys").setApiLevel(26).getDevice();

    ApkInfo dummyInfo = new ApkInfo(createDummyZipFile(), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo), myProject);

    assertThat(task.perform(device, myLaunchStatus, myPrinter)).isTrue();

    verify(device, times(0)).pushFile(any(), any());
    verify(device, times(1)).installPackages(anyList(), eq(true), eq(Lists.newArrayList("-t", "--ephemeral")), anyLong(), any());
  }

  @Test
  public void perform_generatesUniqueTokens() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setGoogleAccountLogged().setOsBuildType("dev-keys").setApiLevel(24).getDevice();

    Pattern tokenExtractor = Pattern.compile(".*TOKEN\" \"([^\"]*)\".*");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(new ApkInfo(createDummyZipFile(), "applicationId")), myProject);

    // Generate first token
    task.perform(device, myLaunchStatus, myPrinter);

    verify(device, atLeastOnce()).executeShellCommand(myShellCommandsCaptor.capture(), any(IShellOutputReceiver.class));
    Matcher tokenMatcher = tokenExtractor.matcher(myShellCommandsCaptor.getAllValues().get(2));
    assertThat(tokenMatcher.find()).isTrue();

    IDevice device2 = new ProvisionPackageTests.DeviceGenerator().setGoogleAccountLogged().setOsBuildType("dev-keys").setApiLevel(24).getDevice();

    // Generate second token
    task.perform(device2, myLaunchStatus, myPrinter);

    verify(device2, never()).executeShellCommand(contains(tokenMatcher.group(1)), any(IShellOutputReceiver.class));
  }

  @Test
  public void perform_failsWhenNoTarget() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setGoogleAccountLogged().setOsBuildType("dev-keys").setApiLevel(24).getDevice();

    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(), myProject);

    assertThat(task.perform(device, myLaunchStatus, myPrinter)).isFalse();
  }

  @Test
  public void perform_failsWhenMissingTarget() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setGoogleAccountLogged().setOsBuildType("dev-keys").setApiLevel(24).getDevice();

    ApkInfo dummyInfo = new ApkInfo(new File("fake_path"), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo), myProject);

    assertThat(task.perform(device, myLaunchStatus, myPrinter)).isFalse();
  }
}