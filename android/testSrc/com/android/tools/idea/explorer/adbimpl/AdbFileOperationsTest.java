/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.ListenableFuture;
import org.hamcrest.core.IsInstanceOf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class AdbFileOperationsTest {
  private static final long TIMEOUT_MILLISECONDS = 30_000;
  @NotNull private static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
  @NotNull private static final String COMMAND_ERROR_CHECK_SUFFIX = " || echo " + ERROR_LINE_MARKER;

  @NotNull private Consumer<TestShellCommands> mySetupCommands;

  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[]{
      (Consumer<TestShellCommands>)AdbFileOperationsTest::addEmulatorApi10Commands,
      (Consumer<TestShellCommands>)AdbFileOperationsTest::addNexus7Api23Commands,
    };
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  public AdbFileOperationsTest(@NotNull Consumer<TestShellCommands> setupCommands) {
    mySetupCommands = setupCommands;
  }

  @NotNull
  private AdbFileOperations setupMockDevice() throws Exception {
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    return new AdbFileOperations(device, new AdbDeviceCapabilities(device), taskExecutor);
  }

  @Test
  public void testCreateNewFileSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewFile("/sdcard", "foo.txt"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewFileRunAsSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewFileRunAs("/data/data/com.example.rpaquay.myapplication",
                                                                  "NewTextFile.txt",
                                                                  "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewFileInvalidFileNameError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "fo/o.txt"));
  }

  @Test
  public void testCreateNewFileReadOnlyError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "foo.txt"));
  }

  @Test
  public void testCreateNewFilePermissionError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/system", "foo.txt"));
  }

  @Test
  public void testCreateNewFileExistError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "default.prop"));
  }

  @Test
  public void testCreateNewDirectorySuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewDirectory("/sdcard", "foo-dir"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewDirectoryRunAsSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewDirectoryRunAs("/data/data/com.example.rpaquay.myapplication",
                                                                       "foo-dir",
                                                                       "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewDirectoryInvalidNameError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "fo/o-dir"));
  }

  @Test
  public void testCreateNewDirectoryReadOnlyError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryPermissionError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/system", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryExistError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "data"));
  }

  @Test
  public void testDeleteExistingFileSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteFile("/sdcard/foo.txt"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingFileRunAsSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteFileRunAs("/data/data/com.example.rpaquay.myapplication/NewTextFile.txt",
                                                               "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryAsFileError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/sdcard/foo-dir"));
  }

  @Test
  public void testDeleteExistingReadOnlyFileError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/system/bin/sh"));
  }

  @Test
  public void testDeleteExistingDirectorySucceeds() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteRecursive("/sdcard/foo-dir"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryRunAsSucceeds() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteRecursiveRunAs("/data/data/com.example.rpaquay.myapplication/foo-dir",
                                                                    "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryPermissionError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteRecursive("/config"));
  }

  @Test
  public void testListPackages() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    List<String> result = waitForFuture(fileOperations.listPackages());

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).contains("com.example.rpaquay.myapplication");
  }

  /**
   * These are command + result as run on a Nexus 7, Android 6.0.1, API 23
   */
  private static void addNexus7Api23Commands(@NotNull TestShellCommands commands) {
    commands.setDescription("Nexus 7, Android 6.0.1, API 23");

    // "su" capability detection
    addFailedCommand(commands, "su 0 sh -c 'id'", "/system/bin/sh: su: not found\n");

    // "test" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");
    addCommand(commands, "test -e /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");

    // "rm -f" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");
    addCommand(commands, "rm -f /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");

    // "touch" capability detection
    addCommand(commands, "touch /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "");

    addFailedCommand(commands, "test -e /foo.txt");

    addFailedCommand(commands, "touch /foo.txt", "touch: '/foo.txt': Read-only file system\n");

    addCommand(commands, "test -e /default.prop", "");

    addFailedCommand(commands, "test -e /sdcard/foo.txt");
    addCommand(commands, "touch /sdcard/foo.txt", "");

    addFailedCommand(commands, "test -e /system/foo.txt");
    addFailedCommand(commands, "touch /system/foo.txt", "touch: '/system/foo.txt': Read-only file system\n");

    addFailedCommand(
      commands,
      "run-as com.example.rpaquay.myapplication sh -c 'test -e /data/data/com.example.rpaquay.myapplication/NewTextFile.txt'",
      "");
    addCommand(commands,
               "run-as com.example.rpaquay.myapplication sh -c 'touch /data/data/com.example.rpaquay.myapplication/NewTextFile.txt'",
               "");

    addCommand(commands,
               "run-as com.example.rpaquay.myapplication sh -c 'mkdir /data/data/com.example.rpaquay.myapplication/foo-dir'",
               "");
    addCommand(commands,
                     "run-as com.example.rpaquay.myapplication sh -c 'rm -f /data/data/com.example.rpaquay.myapplication/NewTextFile.txt'",
                     "");
    addCommand(commands,
               "run-as com.example.rpaquay.myapplication sh -c 'rm -r -f /data/data/com.example.rpaquay.myapplication/foo-dir'",
               "");

    addCommand(commands,
               "pm list packages",
               "package:com.google.android.youtube\n" +
               "package:com.android.providers.telephony\n" +
               "package:com.google.android.gallery3d\n" +
               "package:com.google.android.googlequicksearchbox\n" +
               "package:com.android.providers.calendar\n" +
               "package:com.android.providers.media\n" +
               "package:com.google.android.apps.docs.editors.docs\n" +
               "package:com.google.android.onetimeinitializer\n" +
               "package:com.android.wallpapercropper\n" +
               "package:com.example.rpaquay.myapplication\n" +
               "package:com.android.launcher\n" +
               "package:com.weather.Weather\n" +
               "package:com.android.documentsui\n" +
               "package:com.android.externalstorage\n" +
               "package:com.google.android.apps.enterprise.dmagent\n" +
               "package:com.android.htmlviewer\n" +
               "package:com.android.mms.service\n" +
               "package:com.google.android.apps.docs.editors.sheets\n" +
               "package:com.google.android.apps.docs.editors.slides\n" +
               "package:com.android.providers.downloads\n" +
               "package:com.google.android.apps.currents\n" +
               "package:com.google.android.configupdater\n" +
               "package:com.android.defcontainer\n" +
               "package:org.zwanoo.android.speedtest\n" +
               "package:com.android.providers.downloads.ui\n" +
               "package:com.android.vending\n" +
               "package:com.android.pacprocessor\n" +
               "package:com.android.certinstaller\n" +
               "package:com.google.android.marvin.talkback\n" +
               "package:android\n" +
               "package:com.android.nfc\n" +
               "package:com.android.backupconfirm\n" +
               "package:com.googleplex.android.apps.dogfood.frick\n" +
               "package:com.google.android.launcher\n" +
               "package:com.google.android.deskclock\n" +
               "package:com.android.statementservice\n" +
               "package:com.google.android.gm\n" +
               "package:com.android.wallpaper.holospiral\n" +
               "package:com.android.phasebeam\n" +
               "package:com.google.android.setupwizard\n" +
               "package:com.android.providers.settings\n" +
               "package:com.android.sharedstoragebackup\n" +
               "package:com.google.android.music\n" +
               "package:com.android.printspooler\n" +
               "package:com.android.dreams.basic\n" +
               "package:com.google.android.backup\n" +
               "package:com.android.inputdevices\n" +
               "package:com.google.android.apps.cloudprint\n" +
               "package:com.android.musicfx\n" +
               "package:com.google.android.apps.docs\n" +
               "package:com.google.android.apps.maps\n" +
               "package:com.google.android.apps.plus\n" +
               "package:com.google.android.nfcprovision\n" +
               "package:com.google.android.webview\n" +
               "package:com.google.android.contacts\n" +
               "package:com.android.server.telecom\n" +
               "package:com.google.android.syncadapters.contacts\n" +
               "package:com.android.facelock\n" +
               "package:com.android.keychain\n" +
               "package:com.google.android.gm.exchange\n" +
               "package:com.android.chrome\n" +
               "package:com.google.android.gms\n" +
               "package:com.google.android.gsf\n" +
               "package:com.google.android.tag\n" +
               "package:com.google.android.tts\n" +
               "package:com.google.android.partnersetup\n" +
               "package:com.android.packageinstaller\n" +
               "package:com.google.android.videos\n" +
               "package:com.android.proxyhandler\n" +
               "package:com.google.android.feedback\n" +
               "package:com.google.android.apps.photos\n" +
               "package:com.google.android.calendar\n" +
               "package:com.android.managedprovisioning\n" +
               "package:com.android.noisefield\n" +
               "package:com.android.providers.partnerbookmarks\n" +
               "package:com.google.android.gsf.login\n" +
               "package:com.android.wallpaper.livepicker\n" +
               "package:com.google.android.inputmethod.korean\n" +
               "package:com.android.settings\n" +
               "package:com.google.android.inputmethod.pinyin\n" +
               "package:com.android.calculator2\n" +
               "package:com.google.android.apps.books\n" +
               "package:com.nuance.xt9.input\n" +
               "package:com.android.wallpaper\n" +
               "package:com.android.vpndialogs\n" +
               "package:com.google.android.ears\n" +
               "package:com.google.android.keep\n" +
               "package:com.google.android.talk\n" +
               "package:com.android.phone\n" +
               "package:com.android.shell\n" +
               "package:com.android.providers.userdictionary\n" +
               "package:jp.co.omronsoft.iwnnime.ml\n" +
               "package:com.android.location.fused\n" +
               "package:com.android.systemui\n" +
               "package:com.android.bluetoothmidiservice\n" +
               "package:com.google.android.play.games\n" +
               "package:com.google.android.apps.magazines\n" +
               "package:com.google.android.apps.gcs\n" +
               "package:com.android.bluetooth\n" +
               "package:com.android.providers.contacts\n" +
               "package:com.android.captiveportallogin\n" +
               "package:com.google.android.GoogleCamera\n" +
               "package:com.google.earth\n" +
               "package:com.hp.android.printservice\n" +
               "package:com.google.android.inputmethod.latin\n");

    addCommand(commands, "mkdir /sdcard/foo-dir", "");
    addFailedCommand(commands, "mkdir /foo-dir", "mkdir: '/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir /system/foo-dir", "mkdir: '/system/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir /data", "mkdir: '/data': File exists\n");

    addCommand(commands, "rm -f /sdcard/foo.txt", "");
    addFailedCommand(commands, "rm -f /sdcard/foo-dir", "rm: sdcard/foo-dir: is a directory\n");
    addFailedCommand(commands, "rm -f /system/bin/sh", "rm: /system/bin/sh: Read-only file system\n");

    addCommand(commands, "rm -r -f /sdcard/foo-dir", "");
    addFailedCommand(commands, "rm -r -f /config", "rm: /config: Permission denied\n");
  }

  /**
   * Add commands from a Nexus emulator, Android 2.3.7, API 10
   */
  private static void addEmulatorApi10Commands(@NotNull TestShellCommands commands) {
    commands.setDescription("Nexus 5, Android 2.3.7, API 10");

    // "su" capability detection
    addCommand(commands, "su 0 sh -c 'id'", "uid=0(root) gid=0(root)\n");

    // "test" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");
    addFailedCommand(commands, "test -e /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "test: not found\n");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");

    // "touch" capability detection
    addFailedCommand(commands, "touch /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "touch: not found\n");

    // "rm -f" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");
    addCommand(commands, "rm -f /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");
    //addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");

    addFailedCommand(commands, "su 0 sh -c 'ls -d -a /foo.txt'", "/foo.txt: No such file or directory\n");

    addFailedCommand(commands, "su 0 sh -c 'cat </dev/null >/foo.txt'", "cannot create /foo.txt: read-only file system\n");

    addCommand(commands, "su 0 sh -c 'ls -d -a /default.prop'", "/default.prop\n");

    addFailedCommand(commands, "su 0 sh -c 'ls -d -a /sdcard/foo.txt'", "/sdcard/foo.txt: No such file or directory\n");
    addCommand(commands, "su 0 sh -c 'cat </dev/null >/sdcard/foo.txt'", "");

    addFailedCommand(commands,
                     "su 0 sh -c 'ls -d -a /data/data/com.example.rpaquay.myapplication/NewTextFile.txt'",
                     "/data/data/com.example.rpaquay.myapplication/NewTextFile.txt: No such file or directory\n");
    addCommand(commands, "su 0 sh -c 'cat </dev/null >/data/data/com.example.rpaquay.myapplication/NewTextFile.txt'", "");
    addCommand(commands, "su 0 sh -c 'mkdir /data/data/com.example.rpaquay.myapplication/foo-dir'", "");
    addCommand(commands, "su 0 sh -c 'rm -f /data/data/com.example.rpaquay.myapplication/NewTextFile.txt'", "");
    addCommand(commands, "su 0 sh -c 'rm -r -f /data/data/com.example.rpaquay.myapplication/foo-dir'", "");
    addCommand(commands,
               "su 0 sh -c 'pm list packages'",
               "package:com.android.smoketest\n" +
               "package:com.android.cts.priv.ctsshim\n" +
               "package:com.example.android.livecubes\n" +
               "package:com.android.providers.telephony\n" +
               "package:com.google.android.googlequicksearch\n" +
               "package:com.android.providers.calendar\n" +
               "package:com.android.providers.media\n" +
               "package:com.android.protips\n" +
               "package:com.example.rpaquay.myapplication\n" +
               "package:com.android.documentsui\n" +
               "package:com.android.gallery\n" +
               "package:com.android.externalstorage\n" +
               "package:com.android.htmlviewer\n" +
               "package:com.android.mms.service\n" +
               "package:com.android.providers.downloads\n" +
               "package:com.google.android.apps.messaging\n" +
               "package:com.android.defcontainer\n" +
               "package:com.android.providers.downloads.ui\n" +
               "package:com.android.vending\n" +
               "package:com.android.pacprocessor\n" +
               "package:com.android.certinstaller\n" +
               "package:android\n" +
               "package:com.android.contacts\n" +
               "package:com.android.egg\n" +
               "package:com.android.mtp\n" +
               "package:com.android.backupconfirm\n" +
               "package:com.android.statementservice\n" +
               "package:com.android.calendar\n" +
               "package:com.android.providers.settings\n" +
               "package:com.android.sharedstoragebackup\n" +
               "package:com.android.printspooler\n" +
               "package:com.android.dreams.basic\n" +
               "package:com.android.webview\n" +
               "package:com.android.inputdevices\n" +
               "package:google.simpleapplication\n" +
               "package:com.android.sdksetup\n" +
               "package:com.google.android.apps.maps\n" +
               "package:android.ext.shared\n" +
               "package:com.android.server.telecom\n" +
               "package:com.google.android.syncadapters.cont\n" +
               "package:com.android.keychain\n" +
               "package:com.android.camera\n" +
               "package:com.android.chrome\n" +
               "package:com.android.printservice.recommendat\n" +
               "package:com.android.dialer\n" +
               "package:com.android.emulator.smoketests\n" +
               "package:com.google.android.gms\n" +
               "package:com.google.android.gsf\n" +
               "package:android.ext.services\n" +
               "package:com.ustwo.lwp\n" +
               "package:com.google.android.apps.wallpaper.ne\n" +
               "package:com.android.packageinstaller\n" +
               "package:com.google.android.apps.nexuslaunche\n" +
               "package:com.svox.pico\n" +
               "package:com.example.android.apis\n" +
               "package:com.android.proxyhandler\n" +
               "package:com.android.fallback\n" +
               "package:com.breel.geswallpapers\n" +
               "package:com.android.inputmethod.latin\n" +
               "package:org.chromium.webview_shell\n" +
               "package:com.android.managedprovisioning\n" +
               "package:com.android.providers.partnerbookmar\n" +
               "package:com.google.android.gsf.login\n" +
               "package:com.android.wallpaper.livepicker\n" +
               "package:com.android.netspeed\n" +
               "package:com.android.storagemanager\n" +
               "package:jp.co.omronsoft.openwnn\n" +
               "package:com.android.bookmarkprovider\n" +
               "package:com.android.settings\n" +
               "package:com.android.calculator2\n" +
               "package:com.android.gesture.builder\n" +
               "package:com.android.cts.ctsshim\n" +
               "package:com.android.vpndialogs\n" +
               "package:com.google.android.apps.wallpaper\n" +
               "package:com.android.email\n" +
               "package:com.android.music\n" +
               "package:com.android.phone\n" +
               "package:com.android.shell\n" +
               "package:com.android.wallpaperbackup\n" +
               "package:com.android.providers.blockednumber\n" +
               "package:com.android.providers.userdictionary\n" +
               "package:com.android.emergency\n" +
               "package:com.android.location.fused\n" +
               "package:com.android.deskclock\n" +
               "package:com.android.systemui\n" +
               "package:com.android.smoketest.tests\n" +
               "package:com.android.customlocale2\n" +
               "package:com.example.android.softkeyboard\n" +
               "package:com.google.android.nexusicons\n" +
               "package:com.android.development\n" +
               "package:com.android.providers.contacts\n" +
               "package:com.android.captiveportallogin\n" +
               "package:com.android.widgetpreview\n");
    addFailedCommand(commands, "su 0 sh -c 'ls -d -a /system/foo.txt'", "/system/foo.txt: No such file or directory\n");
    addFailedCommand(commands, "su 0 sh -c 'cat </dev/null >/system/foo.txt'", "cannot create /system/foo.txt: read-only file system\n");

    addCommand(commands, "su 0 sh -c 'mkdir /sdcard/foo-dir'", "");
    addFailedCommand(commands, "su 0 sh -c 'mkdir /foo-dir'", "mkdir failed for /foo-dir, Read-only file system\n");
    addFailedCommand(commands, "su 0 sh -c 'mkdir /system/foo-dir'", "mkdir failed for /system/foo-dir, Read-only file system\n");
    addFailedCommand(commands, "su 0 sh -c 'mkdir /data'", "mkdir failed for /data, File exists\n");

    addCommand(commands, "su 0 sh -c 'rm -f /sdcard/foo.txt'", "");
    addFailedCommand(commands, "su 0 sh -c 'rm -f /sdcard/foo-dir'", "rm failed for /sdcard/foo-dir, Is a directory\n");
    addFailedCommand(commands, "su 0 sh -c 'rm -f /system/bin/sh'", "rm failed for /system/bin/sh, Read-only file system\n");

    addCommand(commands, "su 0 sh -c 'rm -r -f /sdcard/foo-dir'", "");
    addFailedCommand(commands, "su 0 sh -c 'rm -r -f /config'", "rm failed for /config, Read-only file system\n");
  }

  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command) {
    addFailedCommand(commands, command, "");
  }

  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    addCommand(commands, command, result + ERROR_LINE_MARKER + "\n");
  }

  private static void addCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    commands.add(command + COMMAND_ERROR_CHECK_SUFFIX, result);
  }

  private static <V> V waitForFuture(@NotNull ListenableFuture<V> future) throws Exception {
    assert !EventQueue.isDispatchThread();
    return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}
