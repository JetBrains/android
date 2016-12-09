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
import com.android.tools.idea.run.ApkInfo;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.util.LaunchStatus;
import com.google.common.collect.ImmutableList;
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
  @Mock private IDevice myDevice;
  @Mock private LaunchStatus myLaunchStatus;
  @Mock private ConsolePrinter myPrinter;
  @Captor private ArgumentCaptor<String> myShellCommandsCaptor;

  private File createDummyZipFile() throws Exception {
    return myFolder.newFile("dummy.zip");
  }

  @Before
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void perform_uploadsCorrectly() throws Exception {
    ApkInfo dummyInfo = new ApkInfo(createDummyZipFile(), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo));

    assertThat(task.perform(myDevice, myLaunchStatus, myPrinter)).isTrue();

    verify(myDevice, times(1)).pushFile(dummyInfo.getFile().getPath(), "/data/local/tmp/aia/dummy.zip");
    verify(myDevice, times(3)).executeShellCommand(myShellCommandsCaptor.capture(), any(IShellOutputReceiver.class));

    List<String> shellCommands = myShellCommandsCaptor.getAllValues();

    assertThat(shellCommands.get(0))
      .matches("am startservice -a \"com.google.android.instantapps.devman.iapk.LOAD\" " +
               "--es \"com.google.android.instantapps.devman.iapk.IAPK_PATH\" \"/data/local/tmp/aia/dummy.zip\" " +
               "--es \"com.google.android.instantapps.devman.iapk.INSTALL_TOKEN\" \"[^\"]+\" " +
               "--ez \"com.google.android.instantapps.devman.iapk.FORCE\" \"false\" " +
               "-n com.google.android.instantapps.devman/.iapk.IapkLoadService");
    assertThat(shellCommands.get(1)).isEqualTo("rm /data/local/tmp/aia/dummy.zip");
    assertThat(shellCommands.get(2)).isEqualTo("am force-stop com.google.android.instantapps.supervisor");
  }

  @Test
  public void perform_generatesUniqueTokens() throws Exception {
    Pattern tokenExtractor = Pattern.compile(".*TOKEN\" \"([^\"]*)\".*");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(new ApkInfo(createDummyZipFile(), "applicationId")));

    // Generate first token
    task.perform(myDevice, myLaunchStatus, myPrinter);

    verify(myDevice, atLeastOnce()).executeShellCommand(myShellCommandsCaptor.capture(), any(IShellOutputReceiver.class));
    Matcher tokenMatcher = tokenExtractor.matcher(myShellCommandsCaptor.getAllValues().get(0));
    assertThat(tokenMatcher.find()).isTrue();

    reset(myDevice);

    // Generate second token
    task.perform(myDevice, myLaunchStatus, myPrinter);

    verify(myDevice, never()).executeShellCommand(contains(tokenMatcher.group(1)), any(IShellOutputReceiver.class));
  }

  @Test
  public void perform_failsWhenNoTarget() throws Exception {
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of());

    assertThat(task.perform(myDevice, myLaunchStatus, myPrinter)).isFalse();
  }

  @Test
  public void perform_failsWhenMissingTarget() throws Exception {
    ApkInfo dummyInfo = new ApkInfo(new File("fake_path"), "applicationId");
    DeployInstantAppTask task = new DeployInstantAppTask(ImmutableList.of(dummyInfo));

    assertThat(task.perform(myDevice, myLaunchStatus, myPrinter)).isFalse();
  }
}