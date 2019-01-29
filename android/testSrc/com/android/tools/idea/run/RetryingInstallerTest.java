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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.StopWatchTimeSource;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

public class RetryingInstallerTest {
  private static final String APPLICATION_ID = "com.foo.pkg";
  private IDevice myDevice;
  private RetryingInstaller.Installer myInstaller;
  private RetryingInstaller.Prompter myPrompter;
  private LaunchStatus myLaunchStatus;
  private RetryingInstaller myRetryingInstaller;
  private TestTimeSource myStopWatchTimeSource;

  @Before
  public void setUp() {
    myDevice = mock(IDevice.class);
    myInstaller = mock(RetryingInstaller.Installer.class);
    myPrompter = mock(RetryingInstaller.Prompter.class);

    myLaunchStatus = mock(LaunchStatus.class);
    myStopWatchTimeSource = new TestTimeSource();
    StopWatchTimeSource.INSTANCE.overrideDefault(myStopWatchTimeSource);

    myRetryingInstaller =
      new RetryingInstaller(myDevice, myInstaller, APPLICATION_ID, myPrompter, mock(ConsolePrinter.class), myLaunchStatus);
  }

  @After
  public void cleanUp() {
    StopWatchTimeSource.INSTANCE.resetDefault();
  }

  @Test
  public void singleInstallOnSuccess() {
    // if the installer succeeds on the first try...
    when(myInstaller.installApp(myDevice, myLaunchStatus))
      .thenAnswer(invocationOnMock -> {
        myStopWatchTimeSource.advance(20);
        return new InstallResult(InstallResult.FailureCode.NO_ERROR, null, null);
      });
    RetryingInstallerResult installResult = myRetryingInstaller.install();
    assertThat(installResult.isSuccess()).isTrue();
    assertThat(installResult.getRetryCount()).isEqualTo(1);
    assertThat(installResult.getTotalDuration().toMillis()).isEqualTo(20);
    assertThat(installResult.getLastInstallDuration().toMillis()).isEqualTo(20);

    // then we should only have called it once...
    verify(myInstaller, times(1)).installApp(myDevice, myLaunchStatus);

    // and never prompted the user...
    verify(myPrompter, never()).showQuestionPrompt(anyString());
  }

  @Test
  public void notifyUserIfDeviceDisconnectedDuringInstall() {
    // on an install request, return a failure on first install and a success on the 2nd install
    when (myInstaller.installApp(myDevice, myLaunchStatus))
      .thenAnswer(invocation -> {
        myStopWatchTimeSource.advance(50);
        return new InstallResult(InstallResult.FailureCode.DEVICE_NOT_FOUND, null, null);
      });

    when (myDevice.getName())
      .thenReturn("Test Device");

    // perform the installation
    RetryingInstallerResult installResult = myRetryingInstaller.install();
    assertThat(installResult.isSuccess()).isFalse();
    assertThat(installResult.getRetryCount()).isEqualTo(1);
    assertThat(installResult.getTotalDuration().toMillis()).isEqualTo(50);
    assertThat(installResult.getLastInstallDuration().toMillis()).isEqualTo(50);

    // verify that we got only 1 install requests (there is no retry for this error)
    verify(myInstaller, times(1)).installApp(myDevice, myLaunchStatus);

    // verify that we prompted the user
    verify(myPrompter).showErrorMessage(AndroidBundle.message(
      "deployment.failed.reason.devicedisconnected", myDevice.getName()));
  }

  @Test
  public void promptUserOnIncompatibleUpdate() {
    // on an install request, return a failure on first install and a success on the 2nd install
    when (myInstaller.installApp(myDevice, myLaunchStatus))
      .thenAnswer(invocation -> {
        myStopWatchTimeSource.advance(100);
        return new InstallResult(InstallResult.FailureCode.INSTALL_FAILED_VERSION_DOWNGRADE, null, null);
      })
      .thenAnswer(invocation -> {
        myStopWatchTimeSource.advance(200);
        return new InstallResult(InstallResult.FailureCode.NO_ERROR, null, null);
      });
    try {
      when (myDevice.uninstallPackage(APPLICATION_ID)).thenReturn(null);
    }
    catch (InstallException ignored) {
    }

    // answer true to any prompts
    when (myPrompter.showQuestionPrompt(anyString())).thenAnswer(invocation -> {
      myStopWatchTimeSource.advance(5000);
      return true;
    });

    // perform the installation
    RetryingInstallerResult installResult = myRetryingInstaller.install();
    assertThat(installResult.isSuccess()).isTrue();
    assertThat(installResult.getRetryCount()).isEqualTo(2);
    assertThat(installResult.getTotalDuration().toMillis()).isEqualTo(5300);
    assertThat(installResult.getLastInstallDuration().toMillis()).isEqualTo(200);

    // verify that we got 2 install requests (the first one must've failed, then we retried after prompting the user)
    verify(myInstaller, times(2)).installApp(myDevice, myLaunchStatus);

    // verify that we prompted the user
    verify(myPrompter).showQuestionPrompt(AndroidBundle.message(
      "deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.version.downgrade")));
  }

  private static class TestTimeSource implements StopWatchTimeSource.StopWatchTimeSourceOverride {
    private long myTicks;

    @Override
    public long getCurrentTimeMillis() {
      return myTicks;
    }

    public void advance(long ticks) {
      myTicks += ticks;
    }
  }
}
