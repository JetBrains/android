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

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("SpellCheckingInspection")
public class TestDevices {
  @NotNull private static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
  @NotNull public static final String COMMAND_ERROR_CHECK_SUFFIX = " || echo " + ERROR_LINE_MARKER;

  /**
   * Add commands from a Nexus 5 emulator, runnign Android 2.3.7, API 10
   */
  public static void addEmulatorApi10Commands(@NotNull TestShellCommands commands) {
    commands.setDescription("Nexus 5 Emulator, Android 2.3.7, API 10");

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

    addFailedCommand(commands, "su 0 sh -c 'ls -d -a /foo.txt'", "/foo.txt: No such file or directory\n");

    addFailedCommand(commands, "su 0 sh -c 'echo -n >/foo.txt'", "cannot create /foo.txt: read-only file system\n");

    addCommand(commands, "su 0 sh -c 'ls -d -a /default.prop'", "/default.prop\n");

    addFailedCommand(commands, "su 0 sh -c 'ls -d -a /sdcard/foo.txt'", "/sdcard/foo.txt: No such file or directory\n");
    addCommand(commands, "su 0 sh -c 'echo -n >/sdcard/foo.txt'", "");

    addFailedCommand(commands,
                     "su 0 sh -c 'ls -d -a /data/data/com.example.rpaquay.myapplication/NewTextFile.txt'",
                     "/data/data/com.example.rpaquay.myapplication/NewTextFile.txt: No such file or directory\n");
    addCommand(commands, "su 0 sh -c 'echo -n >/data/data/com.example.rpaquay.myapplication/NewTextFile.txt'", "");
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
    addFailedCommand(commands, "su 0 sh -c 'echo -n >/system/foo.txt'", "cannot create /system/foo.txt: read-only file system\n");

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

  /**
   * Add commands from a Nexus 7 device, running Android 6.0.1, API 23
   */
  public static void addNexus7Api23Commands(@NotNull TestShellCommands commands) {
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

    // Listing commands
    addCommand(commands, "ls -l /", "drwxr-xr-x root     root              2016-11-21 12:09 acct\r\n" +
                                    "drwxrwx--- system   cache             2016-08-26 12:12 cache\r\n" +
                                    "lrwxrwxrwx root     root              1969-12-31 16:00 charger -> /sbin/healthd\r\n" +
                                    "dr-x------ root     root              2016-11-21 12:09 config\r\n" +
                                    "lrwxrwxrwx root     root              2016-11-21 12:09 d -> /sys/kernel/debug\r\n" +
                                    "drwxrwx--x system   system            2016-11-21 12:10 data\r\n" +
                                    "-rw-r--r-- root     root          564 1969-12-31 16:00 default.prop\r\n" +
                                    "drwxr-xr-x root     root              2016-11-21 14:04 dev\r\n" +
                                    "lrwxrwxrwx root     root              2016-11-21 12:09 etc -> /system/etc\r\n" +
                                    "-rw-r--r-- root     root        21429 1969-12-31 16:00 file_contexts\r\n" +
                                    "drwxrwx--x system   system            2016-11-21 12:09 firmware\r\n" +
                                    "-rw-r----- root     root         3447 1969-12-31 16:00 fstab.flo\r\n" +
                                    "lstat '//init' failed: Permission denied\r\n" +
                                    "-rwxr-x--- root     root          852 1969-12-31 16:00 init.environ.rc\r\n" +
                                    "-rwxr-x--- root     root           79 1969-12-31 16:00 init.flo.diag.rc\r\n" +
                                    "-rwxr-x--- root     root        15962 1969-12-31 16:00 init.flo.rc\r\n" +
                                    "-rwxr-x--- root     root         8086 1969-12-31 16:00 init.flo.usb.rc\r\n" +
                                    "-rwxr-x--- root     root        26830 1969-12-31 16:00 init.rc\r\n" +
                                    "-rwxr-x--- root     root         1921 1969-12-31 16:00 init.trace.rc\r\n" +
                                    "-rwxr-x--- root     root         9283 1969-12-31 16:00 init.usb.configfs.rc\r\n" +
                                    "-rwxr-x--- root     root         5339 1969-12-31 16:00 init.usb.rc\r\n" +
                                    "-rwxr-x--- root     root          342 1969-12-31 16:00 init.zygote32.rc\r\n" +
                                    "drwxr-xr-x root     system            2016-11-21 12:09 mnt\r\n" +
                                    "drwxr-xr-x root     root              1969-12-31 16:00 oem\r\n" +
                                    "lstat '//persist' failed: Permission denied\r\n" +
                                    "dr-xr-xr-x root     root              1969-12-31 16:00 proc\r\n" +
                                    "-rw-r--r-- root     root         3405 1969-12-31 16:00 property_contexts\r\n" +
                                    "drwxr-xr-x root     root              1969-12-31 16:00 res\r\n" +
                                    "drwx------ root     root              2016-07-01 17:00 root\r\n" +
                                    "drwxr-x--- root     root              1969-12-31 16:00 sbin\r\n" +
                                    "lrwxrwxrwx root     root              2016-11-21 12:09 sdcard -> /storage/self/primary\r\n" +
                                    "-rw-r--r-- root     root          596 1969-12-31 16:00 seapp_contexts\r\n" +
                                    "-rw-r--r-- root     root           51 1969-12-31 16:00 selinux_version\r\n" +
                                    "-rw-r--r-- root     root       149405 1969-12-31 16:00 sepolicy\r\n" +
                                    "-rw-r--r-- root     root         9769 1969-12-31 16:00 service_contexts\r\n" +
                                    "drwxr-xr-x root     root              2016-11-21 12:10 storage\r\n" +
                                    "dr-xr-xr-x root     root              2016-11-21 12:09 sys\r\n" +
                                    "drwxr-xr-x root     root              2016-08-26 12:02 system\r\n" +
                                    "lrwxrwxrwx root     root              2016-11-21 12:09 tombstones -> /data/tombstones\r\n" +
                                    "-rw-r--r-- root     root         2195 1969-12-31 16:00 ueventd.flo.rc\r\n" +
                                    "-rw-r--r-- root     root         4587 1969-12-31 16:00 ueventd.rc\r\n" +
                                    "lrwxrwxrwx root     root              2016-11-21 12:09 vendor -> /system/vendor\r\n");

    commands.add("ls -l -d /charger/", "/charger/: Permission denied\r\n");
    commands.add("ls -l -d /d/", "drwxr-xr-x root     root              1969-12-31 16:00\r\n");
    commands.add("ls -l -d /etc/", "drwxr-xr-x root     root              2016-08-26 12:00\r\n");
    commands.add("ls -l -d /sdcard/", "drwxrwx--x root     sdcard_rw          2014-02-10 17:16\r\n");
    commands.add("ls -l -d /tombstones/", "/tombstones/: Permission denied\r\n");
    commands.add("ls -l -d /vendor/", "drwxr-xr-x root     shell             2013-06-15 12:54\r\n");

    addCommand(commands, "ls -l /system/", "drwxr-xr-x root     root              2016-05-17 12:04 app\n\n" +
                                           "drwxr-xr-x root     shell             2016-08-26 12:00 bin\n\n" +
                                           "-rw-r--r-- root     root         3870 2016-08-26 12:02 build.prop\n\n" +
                                           "drwxr-xr-x root     root              2016-08-26 12:00 etc\n\n" +
                                           "drwxr-xr-x root     root              2016-05-27 13:49 fonts\n\n" +
                                           "drwxr-xr-x root     root              2016-08-26 12:02 framework\n\n" +
                                           "drwxr-xr-x root     root              2016-08-26 12:00 lib\n\n" +
                                           "drwxr-xr-x root     root              1969-12-31 16:00 lost+found\n\n" +
                                           "drwxr-xr-x root     root              2016-05-17 12:01 media\n\n" +
                                           "drwxr-xr-x root     root              2016-05-17 12:04 priv-app\n\n" +
                                           "-rw-r--r-- root     root       103290 2008-08-01 05:00 recovery-from-boot.p\n\n" +
                                           "drwxr-xr-x root     root              2016-05-17 12:04 usr\n\n" +
                                           "drwxr-xr-x root     shell             2013-06-15 12:54 vendor\n\n" +
                                           "drwxr-xr-x root     shell             2016-08-24 15:40 xbin\n\n");

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

    addCommand(commands,
               "pm list packages -f",
               "package:/data/app/com.google.android.youtube-1/base.apk=com.google.android.youtube\n" +
               "package:/system/priv-app/TelephonyProvider/TelephonyProvider.apk=com.android.providers.telephony\n" +
               "package:/system/app/MediaShortcuts/MediaShortcuts.apk=com.google.android.gallery3d\n" +
               "package:/data/app/com.google.android.googlequicksearchbox-2/base.apk=com.google.android.googlequicksearchbox\n" +
               "package:/system/priv-app/CalendarProvider/CalendarProvider.apk=com.android.providers.calendar\n" +
               "package:/system/priv-app/MediaProvider/MediaProvider.apk=com.android.providers.media\n" +
               "package:/data/app/com.google.android.apps.docs.editors.docs-2/base.apk=com.google.android.apps.docs.editors.docs\n" +
               "package:/system/priv-app/GoogleOneTimeInitializer/GoogleOneTimeInitializer.apk=com.google.android.onetimeinitializer\n" +
               "package:/system/priv-app/WallpaperCropper/WallpaperCropper.apk=com.android.wallpapercropper\n" +
               "package:/data/app/com.example.rpaquay.myapplication-2/base.apk=com.example.rpaquay.myapplication\n" +
               "package:/system/priv-app/Launcher2/Launcher2.apk=com.android.launcher\n" +
               "package:/data/app/com.weather.Weather-2/base.apk=com.weather.Weather\n" +
               "package:/system/app/DocumentsUI/DocumentsUI.apk=com.android.documentsui\n" +
               "package:/system/priv-app/ExternalStorageProvider/ExternalStorageProvider.apk=com.android.externalstorage\n" +
               "package:/data/app/com.google.android.apps.enterprise.dmagent-1/base.apk=com.google.android.apps.enterprise.dmagent\n" +
               "package:/system/app/HTMLViewer/HTMLViewer.apk=com.android.htmlviewer\n" +
               "package:/system/priv-app/MmsService/MmsService.apk=com.android.mms.service\n" +
               "package:/data/app/com.google.android.apps.docs.editors.sheets-1/base.apk=com.google.android.apps.docs.editors.sheets\n" +
               "package:/data/app/com.google.android.apps.docs.editors.slides-1/base.apk=com.google.android.apps.docs.editors.slides\n" +
               "package:/system/priv-app/DownloadProvider/DownloadProvider.apk=com.android.providers.downloads\n" +
               "package:/data/app/com.google.android.apps.currents-2.apk=com.google.android.apps.currents\n" +
               "package:/system/priv-app/ConfigUpdater/ConfigUpdater.apk=com.google.android.configupdater\n" +
               "package:/system/priv-app/DefaultContainerService/DefaultContainerService.apk=com.android.defcontainer\n" +
               "package:/data/app/org.zwanoo.android.speedtest-1/base.apk=org.zwanoo.android.speedtest\n" +
               "package:/system/app/DownloadProviderUi/DownloadProviderUi.apk=com.android.providers.downloads.ui\n" +
               "package:/data/app/com.android.vending-1/base.apk=com.android.vending\n" +
               "package:/system/app/PacProcessor/PacProcessor.apk=com.android.pacprocessor\n" +
               "package:/system/app/CertInstaller/CertInstaller.apk=com.android.certinstaller\n" +
               "package:/data/app/com.google.android.marvin.talkback-1/base.apk=com.google.android.marvin.talkback\n" +
               "package:/system/framework/framework-res.apk=android\n" +
               "package:/system/app/NfcNci/NfcNci.apk=com.android.nfc\n" +
               "package:/system/priv-app/BackupRestoreConfirmation/BackupRestoreConfirmation.apk=com.android.backupconfirm\n" +
               "package:/data/app/com.googleplex.android.apps.dogfood.frick-2.apk=com.googleplex.android.apps.dogfood.frick\n" +
               "package:/data/app/com.google.android.launcher-1/base.apk=com.google.android.launcher\n" +
               "package:/data/app/com.google.android.deskclock-2/base.apk=com.google.android.deskclock\n" +
               "package:/system/priv-app/StatementService/StatementService.apk=com.android.statementservice\n" +
               "package:/data/app/com.google.android.gm-1/base.apk=com.google.android.gm\n" +
               "package:/system/app/HoloSpiralWallpaper/HoloSpiralWallpaper.apk=com.android.wallpaper.holospiral\n" +
               "package:/system/app/PhaseBeam/PhaseBeam.apk=com.android.phasebeam\n" +
               "package:/data/app/com.google.android.instantapps.supervisor-1/base.apk=com.google.android.instantapps.supervisor\n" +
               "package:/system/priv-app/SetupWizard/SetupWizard.apk=com.google.android.setupwizard\n" +
               "package:/system/priv-app/SettingsProvider/SettingsProvider.apk=com.android.providers.settings\n" +
               "package:/system/priv-app/SharedStorageBackup/SharedStorageBackup.apk=com.android.sharedstoragebackup\n" +
               "package:/data/app/com.google.android.music-1/base.apk=com.google.android.music\n" +
               "package:/system/app/PrintSpooler/PrintSpooler.apk=com.android.printspooler\n" +
               "package:/system/app/BasicDreams/BasicDreams.apk=com.android.dreams.basic\n" +
               "package:/system/priv-app/GoogleBackupTransport/GoogleBackupTransport.apk=com.google.android.backup\n" +
               "package:/system/priv-app/InputDevices/InputDevices.apk=com.android.inputdevices\n" +
               "package:/data/app/com.google.android.apps.cloudprint-2/base.apk=com.google.android.apps.cloudprint\n" +
               "package:/system/priv-app/MusicFX/MusicFX.apk=com.android.musicfx\n" +
               "package:/data/app/com.google.android.apps.docs-1/base.apk=com.google.android.apps.docs\n" +
               "package:/data/app/com.google.android.apps.maps-1/base.apk=com.google.android.apps.maps\n" +
               "package:/data/app/com.google.android.apps.plus-1/base.apk=com.google.android.apps.plus\n" +
               "package:/system/priv-app/NfcProvision/NfcProvision.apk=com.google.android.nfcprovision\n" +
               "package:/data/app/com.google.android.webview-2/base.apk=com.google.android.webview\n" +
               "package:/data/app/com.google.android.contacts-2/base.apk=com.google.android.contacts\n" +
               "package:/system/priv-app/Telecom/Telecom.apk=com.android.server.telecom\n" +
               "package:/system/app/GoogleContactsSyncAdapter/GoogleContactsSyncAdapter.apk=com.google.android.syncadapters.contacts\n" +
               "package:/system/app/FaceLock/FaceLock.apk=com.android.facelock\n" +
               "package:/system/app/KeyChain/KeyChain.apk=com.android.keychain\n" +
               "package:/data/app/com.google.android.gm.exchange-2/base.apk=com.google.android.gm.exchange\n" +
               "package:/data/app/com.android.chrome-1/base.apk=com.android.chrome\n" +
               "package:/data/app/com.google.android.gms-1/base.apk=com.google.android.gms\n" +
               "package:/system/priv-app/GoogleServicesFramework/GoogleServicesFramework.apk=com.google.android.gsf\n" +
               "package:/system/priv-app/TagGoogle/TagGoogle.apk=com.google.android.tag\n" +
               "package:/data/app/com.google.android.tts-2/base.apk=com.google.android.tts\n" +
               "package:/system/priv-app/GooglePartnerSetup/GooglePartnerSetup.apk=com.google.android.partnersetup\n" +
               "package:/system/priv-app/GooglePackageInstaller/GooglePackageInstaller.apk=com.android.packageinstaller\n" +
               "package:/data/app/com.google.android.videos-1/base.apk=com.google.android.videos\n" +
               "package:/system/priv-app/ProxyHandler/ProxyHandler.apk=com.android.proxyhandler\n" +
               "package:/system/priv-app/GoogleFeedback/GoogleFeedback.apk=com.google.android.feedback\n" +
               "package:/data/app/com.google.android.apps.photos-1/base.apk=com.google.android.apps.photos\n" +
               "package:/data/app/com.google.android.calendar-1/base.apk=com.google.android.calendar\n" +
               "package:/system/priv-app/ManagedProvisioning/ManagedProvisioning.apk=com.android.managedprovisioning\n" +
               "package:/data/app/com.example.rpaquay.myapplication.test-2/base.apk=com.example.rpaquay.myapplication.test\n" +
               "package:/system/app/NoiseField/NoiseField.apk=com.android.noisefield\n" +
               "package:/system/app/PartnerBookmarksProvider/PartnerBookmarksProvider.apk=com.android.providers.partnerbookmarks\n" +
               "package:/system/priv-app/GoogleLoginService/GoogleLoginService.apk=com.google.android.gsf.login\n" +
               "package:/system/app/LiveWallpapersPicker/LiveWallpapersPicker.apk=com.android.wallpaper.livepicker\n" +
               "package:/system/app/KoreanIME/KoreanIME.apk=com.google.android.inputmethod.korean\n" +
               "package:/system/priv-app/Settings/Settings.apk=com.android.settings\n" +
               "package:/system/app/GooglePinyinIME/GooglePinyinIME.apk=com.google.android.inputmethod.pinyin\n" +
               "package:/system/app/CalculatorGoogle/CalculatorGoogle.apk=com.android.calculator2\n" +
               "package:/data/app/com.google.android.apps.books-1/base.apk=com.google.android.apps.books\n" +
               "package:/system/app/XT9IME/XT9IME.apk=com.nuance.xt9.input\n" +
               "package:/system/app/LiveWallpapers/LiveWallpapers.apk=com.android.wallpaper\n" +
               "package:/system/priv-app/VpnDialogs/VpnDialogs.apk=com.android.vpndialogs\n" +
               "package:/system/app/GoogleEars/GoogleEars.apk=com.google.android.ears\n" +
               "package:/data/app/com.google.android.keep-2/base.apk=com.google.android.keep\n" +
               "package:/data/app/com.google.android.talk-2/base.apk=com.google.android.talk\n" +
               "package:/system/priv-app/TeleService/TeleService.apk=com.android.phone\n" +
               "package:/system/priv-app/Shell/Shell.apk=com.android.shell\n" +
               "package:/system/app/UserDictionaryProvider/UserDictionaryProvider.apk=com.android.providers.userdictionary\n" +
               "package:/system/app/iWnnIME/iWnnIME.apk=jp.co.omronsoft.iwnnime.ml\n" +
               "package:/system/priv-app/FusedLocation/FusedLocation.apk=com.android.location.fused\n" +
               "package:/system/priv-app/SystemUI/SystemUI.apk=com.android.systemui\n" +
               "package:/system/app/BluetoothMidiService/BluetoothMidiService.apk=com.android.bluetoothmidiservice\n" +
               "package:/data/app/com.google.android.play.games-2/base.apk=com.google.android.play.games\n" +
               "package:/data/app/com.google.android.apps.magazines-1/base.apk=com.google.android.apps.magazines\n" +
               "package:/data/app/com.google.android.apps.gcs-2/base.apk=com.google.android.apps.gcs\n" +
               "package:/system/app/Bluetooth/Bluetooth.apk=com.android.bluetooth\n" +
               "package:/system/priv-app/ContactsProvider/ContactsProvider.apk=com.android.providers.contacts\n" +
               "package:/system/app/CaptivePortalLogin/CaptivePortalLogin.apk=com.android.captiveportallogin\n" +
               "package:/system/app/GoogleCamera/GoogleCamera.apk=com.google.android.GoogleCamera\n" +
               "package:/data/app/com.google.earth-1/base.apk=com.google.earth\n" +
               "package:/data/app/com.hp.android.printservice-1/base.apk=com.hp.android.printservice\n" +
               "package:/data/app/com.google.android.inputmethod.latin-1/base.apk=com.google.android.inputmethod.latin\n");

    addCommand(commands,
               "run-as com.example.rpaquay.myapplication sh -c 'ls -l /data/app/com.example.rpaquay.myapplication-2/'",
               "-rw-r--r-- system   system     468458 2017-06-12 11:21 base.apk\n" +
               "drwxr-xr-x system   system            2017-06-12 11:21 lib\n" +
               "drwxrwx--x system   install           2017-06-12 11:21 oat\n" +
               "-rw-r--r-- system   system    1351085 2017-06-12 11:21 split_lib_dependencies_apk.apk\n" +
               "-rw-r--r-- system   system       3332 2017-06-12 11:21 split_lib_slice_0_apk.apk\n" +
               "-rw-r--r-- system   system       3088 2017-06-12 11:21 split_lib_slice_1_apk.apk\n" +
               "-rw-r--r-- system   system      26485 2017-06-12 11:21 split_lib_slice_2_apk.apk\n" +
               "-rw-r--r-- system   system       3262 2017-06-12 11:21 split_lib_slice_3_apk.apk\n" +
               "-rw-r--r-- system   system       3088 2017-06-12 11:21 split_lib_slice_4_apk.apk\n" +
               "-rw-r--r-- system   system       3091 2017-06-12 11:21 split_lib_slice_5_apk.apk\n" +
               "-rw-r--r-- system   system       3090 2017-06-12 11:21 split_lib_slice_6_apk.apk\n" +
               "-rw-r--r-- system   system       3254 2017-06-12 11:21 split_lib_slice_7_apk.apk\n" +
               "-rw-r--r-- system   system      44095 2017-06-12 11:21 split_lib_slice_8_apk.apk\n" +
               "-rw-r--r-- system   system       6289 2017-06-12 11:21 split_lib_slice_9_apk.apk\n");

    addCommand(commands, "mkdir /sdcard/foo-dir", "");
    addFailedCommand(commands, "mkdir /foo-dir", "mkdir: '/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir /system/foo-dir", "mkdir: '/system/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir /data", "mkdir: '/data': File exists\n");

    addCommand(commands, "rm -f /sdcard/foo.txt", "");
    addFailedCommand(commands, "rm -f /sdcard/foo-dir", "rm: sdcard/foo-dir: is a directory\n");
    addFailedCommand(commands, "rm -f /system/bin/sh", "rm: /system/bin/sh: Read-only file system\n");

    addCommand(commands, "rm -r -f /sdcard/foo-dir", "");
    addFailedCommand(commands, "rm -r -f /config", "rm: /config: Permission denied\n");

    addCommand(commands, "touch /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
    addCommand(commands, "ls /data/local/tmp/oyX2HCKL\\ acuauQGJ", "/data/local/tmp/oyX2HCKL acuauQGJ");
    addCommand(commands, "rm /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
  }

  /**
   * Add commands from a Pixel emulator, running Android 7.1, API 25
   */
  public static void addEmulatorApi25Commands(@NotNull TestShellCommands shellCommands) {
    shellCommands.setDescription("Emulator Pixel, Android 7.1, API 25");
    addCommand(shellCommands,
               "su 0 sh -c 'id'",
               "uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r)," +
               "3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc) context=u:r:su:s0\n");
    addCommand(shellCommands,
               "su 0 sh -c 'ls -l /'",
               "total 3688\n" +
               "drwxr-xr-x  29 root   root         0 2017-03-06 21:15 acct\n" +
               "drwxrwx---   6 system cache     4096 2016-12-10 21:19 cache\n" +
               "lrwxrwxrwx   1 root   root        13 1969-12-31 16:00 charger -> /sbin/healthd\n" +
               "drwxr-xr-x   3 root   root         0 2017-03-06 21:15 config\n" +
               "lrwxrwxrwx   1 root   root        17 1969-12-31 16:00 d -> /sys/kernel/debug\n" +
               "drwxrwx--x  36 system system    4096 2017-03-06 21:15 data\n" +
               "-rw-r--r--   1 root   root       928 1969-12-31 16:00 default.prop\n" +
               "drwxr-xr-x  14 root   root      3000 2017-03-06 21:15 dev\n" +
               "lrwxrwxrwx   1 root   root        11 1969-12-31 16:00 etc -> /system/etc\n" +
               "-rw-r--r--   1 root   root     76613 1969-12-31 16:00 file_contexts.bin\n" +
               "-rw-r-----   1 root   root       943 1969-12-31 16:00 fstab.goldfish\n" +
               "-rw-r-----   1 root   root       968 1969-12-31 16:00 fstab.ranchu\n" +
               "-rwxr-x---   1 root   root   1486420 1969-12-31 16:00 init\n" +
               "-rwxr-x---   1 root   root       887 1969-12-31 16:00 init.environ.rc\n" +
               "-rwxr-x---   1 root   root      2924 1969-12-31 16:00 init.goldfish.rc\n" +
               "-rwxr-x---   1 root   root      2368 1969-12-31 16:00 init.ranchu.rc\n" +
               "-rwxr-x---   1 root   root     25583 1969-12-31 16:00 init.rc\n" +
               "-rwxr-x---   1 root   root      9283 1969-12-31 16:00 init.usb.configfs.rc\n" +
               "-rwxr-x---   1 root   root      5715 1969-12-31 16:00 init.usb.rc\n" +
               "-rwxr-x---   1 root   root       411 1969-12-31 16:00 init.zygote32.rc\n" +
               "drwxr-xr-x  10 root   system     220 2017-03-06 21:15 mnt\n" +
               "drwxr-xr-x   2 root   root         0 1969-12-31 16:00 oem\n" +
               "dr-xr-xr-x 189 root   root         0 2017-03-06 21:15 proc\n" +
               "-rw-r--r--   1 root   root      4757 1969-12-31 16:00 property_contexts\n" +
               "drwx------   2 root   root         0 2016-10-04 07:46 root\n" +
               "drwxr-x---   2 root   root         0 1969-12-31 16:00 sbin\n" +
               "lrwxrwxrwx   1 root   root        21 1969-12-31 16:00 sdcard -> /storage/self/primary\n" +
               "-rw-r--r--   1 root   root       758 1969-12-31 16:00 seapp_contexts\n" +
               "-rw-r--r--   1 root   root        79 1969-12-31 16:00 selinux_version\n" +
               "-rw-r--r--   1 root   root    177921 1969-12-31 16:00 sepolicy\n" +
               "-rw-r--r--   1 root   root     11167 1969-12-31 16:00 service_contexts\n" +
               "drwxr-xr-x   5 root   root       100 2017-03-06 21:15 storage\n" +
               "dr-xr-xr-x  12 root   root         0 2017-03-06 21:15 sys\n" +
               "drwxr-xr-x  16 root   root      4096 1969-12-31 16:00 system\n" +
               "-rw-r--r--   1 root   root       323 1969-12-31 16:00 ueventd.goldfish.rc\n" +
               "-rw-r--r--   1 root   root       323 1969-12-31 16:00 ueventd.ranchu.rc\n" +
               "-rw-r--r--   1 root   root      4853 1969-12-31 16:00 ueventd.rc\n" +
               "lrwxrwxrwx   1 root   root        14 1969-12-31 16:00 vendor -> /system/vendor\n");

    shellCommands.add("su 0 sh -c 'ls -l -d /charger/'", "ls: /charger/: Not a directory\n");
    shellCommands.add("su 0 sh -c 'ls -l -d /d/'", "drwx------ 14 root root 0 2017-03-06 21:15 /d/\n");
    shellCommands.add("su 0 sh -c 'ls -l -d /etc/'", "drwxr-xr-x 7 root root 4096 2016-11-14 14:08 /etc/\n");
    shellCommands.add("su 0 sh -c 'ls -l -d /sdcard/'", "drwxrwx--x 13 root sdcard_rw 4096 2017-03-06 23:30 /sdcard/\n");
    shellCommands.add("su 0 sh -c 'ls -l -d /tombstones/'", "ls: /tombstones/: No such file or directory\n");
    shellCommands.add("su 0 sh -c 'ls -l -d /system/'", "drwxr-xr-x 16 root root 4096 1969-12-31 16:00 /system/\n");
    shellCommands.add("su 0 sh -c 'ls -l -d /vendor/'", "drwxr-xr-x 3 root shell 4096 2016-11-14 14:01 /vendor/\n");
    addCommand(shellCommands, "touch /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "");
    addCommand(shellCommands, "rm /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "");
    addFailedCommand(shellCommands, "touch /system/build.prop", "touch: '/system/build.prop': Read-only file system\n");
    addCommand(shellCommands, "su 0 sh -c 'touch /data/local/tmp/temp0'", "");
    addCommand(
      shellCommands,
      "cp /data/local/tmp/device-explorer/.__temp_cp_test_file__.tmp /data/local/tmp/device-explorer/.__temp_cp_test_file_dst__.tmp",
      "");
    addCommand(shellCommands, "rm /data/local/tmp/device-explorer/.__temp_cp_test_file__.tmp", "");
    addCommand(shellCommands, "rm /data/local/tmp/device-explorer/.__temp_cp_test_file_dst__.tmp", "");
    addFailedCommand(
      shellCommands,
      "su 0 sh -c 'cp /data/local/tmp/temp0 /system/build.prop'",
      "cp: /system/build.prop: Read-only file system\n");
    addCommand(shellCommands, "rm -f /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");
    addCommand(shellCommands, "su 0 sh -c 'rm -f /data/local/tmp/temp0'", "");
    addCommand(shellCommands, "su 0 sh -c 'ls -l /system/'",
               "total 144\n" +
               "drwxr-xr-x 47 root root  4096 2017-02-22 09:10 app\n" +
               "drwxr-xr-x  2 root shell 8192 2017-02-22 09:06 bin\n" +
               "-rw-r--r--  1 root root  2006 2017-02-22 09:07 build.prop\n" +
               "drwxr-xr-x  8 root root  4096 2017-02-22 09:11 etc\n" +
               "drwxr-xr-x  2 root root  4096 2017-02-22 09:06 fake-libs\n" +
               "drwxr-xr-x  2 root root  8192 2017-02-22 09:06 fonts\n" +
               "drwxr-xr-x  4 root root  4096 2017-02-22 09:10 framework\n" +
               "drwxr-xr-x  5 root root  8192 2017-02-22 09:09 lib\n" +
               "drwx------  2 root root  4096 1969-12-31 16:00 lost+found\n" +
               "drwxr-xr-x  3 root root  4096 2017-02-22 09:07 media\n" +
               "drwxr-xr-x 40 root root  4096 2017-02-22 09:11 priv-app\n" +
               "drwxr-xr-x  3 root root  4096 2017-02-22 09:07 tts\n" +
               "drwxr-xr-x  7 root root  4096 2017-02-22 09:07 usr\n" +
               "drwxr-xr-x  3 root shell 4096 2017-02-22 09:07 vendor\n" +
               "drwxr-xr-x  2 root shell 4096 2017-02-22 09:07 xbin\n");
    addCommand(shellCommands, "su 0 sh -c 'cp /system/build.prop /data/local/tmp/temp0'", "");

    addCommand(shellCommands, "touch /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
    addCommand(shellCommands, "ls /data/local/tmp/oyX2HCKL\\ acuauQGJ", "/data/local/tmp/oyX2HCKL acuauQGJ");
    addCommand(shellCommands, "rm /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
  }

  static void addLogcatSupportsEpochFormatModifierCommands(@NotNull TestShellCommands commands) {
    addCommand(
      commands,
      "logcat --help",
      "Usage: logcat [options] [filterspecs]\n" +
      "options include:\n" +
      "  -s              Set default filter to silent. Equivalent to filterspec '*:S'\n" +
      "  -f <file>, --file=<file>               Log to file. Default is stdout\n" +
      "  -r <kbytes>, --rotate-kbytes=<kbytes>\n" +
      "                  Rotate log every kbytes. Requires -f option\n" +
      "  -n <count>, --rotate-count=<count>\n" +
      "                  Sets max number of rotated logs to <count>, default 4\n" +
      "  --id=<id>       If the signature id for logging to file changes, then clear\n" +
      "                  the fileset and continue\n" +
      "  -v <format>, --format=<format>\n" +
      "                  Sets log print format verb and adverbs, where <format> is:\n" +
      "                    brief help long process raw tag thread threadtime time\n" +
      "                  and individually flagged modifying adverbs can be added:\n" +
      "                    color descriptive epoch monotonic printable uid\n" +
      "                    usec UTC year zone\n" +
      "                  Multiple -v parameters or comma separated list of format and\n" +
      "                  format modifiers are allowed.\n" +
      "  -D, --dividers  Print dividers between each log buffer\n" +
      "  -c, --clear     Clear (flush) the entire log and exit\n" +
      "                  if Log to File specified, clear fileset instead\n" +
      "  -d              Dump the log and then exit (don't block)\n" +
      "  -e <expr>, --regex=<expr>\n" +
      "                  Only print lines where the log message matches <expr>\n" +
      "                  where <expr> is a regular expression\n" +
      "  -m <count>, --max-count=<count>\n" +
      "                  Quit after printing <count> lines. This is meant to be\n" +
      "                  paired with --regex, but will work on its own.\n" +
      "  --print         Paired with --regex and --max-count to let content bypass\n" +
      "                  regex filter but still stop at number of matches.\n" +
      "  -t <count>      Print only the most recent <count> lines (implies -d)\n" +
      "  -t '<time>'     Print most recent lines since specified time (implies -d)\n" +
      "  -T <count>      Print only the most recent <count> lines (does not imply -d)\n" +
      "  -T '<time>'     Print most recent lines since specified time (not imply -d)\n" +
      "                  count is pure numerical, time is 'MM-DD hh:mm:ss.mmm...'\n" +
      "                  'YYYY-MM-DD hh:mm:ss.mmm...' or 'sssss.mmm...' format\n" +
      "  -g, --buffer-size                      Get the size of the ring buffer.\n" +
      "  -G <size>, --buffer-size=<size>\n" +
      "                  Set size of log ring buffer, may suffix with K or M.\n" +
      "  -L, --last      Dump logs from prior to last reboot\n" +
      "  -b <buffer>, --buffer=<buffer>         Request alternate ring buffer, 'main',\n" +
      "                  'system', 'radio', 'events', 'crash', 'default' or 'all'.\n" +
      "                  Multiple -b parameters or comma separated list of buffers are\n" +
      "                  allowed. Buffers interleaved. Default -b main,system,crash.\n" +
      "  -B, --binary    Output the log in binary.\n" +
      "  -S, --statistics                       Output statistics.\n" +
      "  -p, --prune     Print prune white and ~black list. Service is specified as\n" +
      "                  UID, UID/PID or /PID. Weighed for quicker pruning if prefix\n" +
      "                  with ~, otherwise weighed for longevity if unadorned. All\n" +
      "                  other pruning activity is oldest first. Special case ~!\n" +
      "                  represents an automatic quicker pruning for the noisiest\n" +
      "                  UID as determined by the current statistics.\n" +
      "  -P '<list> ...', --prune='<list> ...'\n" +
      "                  Set prune white and ~black list, using same format as\n" +
      "                  listed above. Must be quoted.\n" +
      "  --pid=<pid>     Only prints logs from the given pid.\n" +
      "  --wrap          Sleep for 2 hours or when buffer about to wrap whichever\n" +
      "                  comes first. Improves efficiency of polling by providing\n" +
      "                  an about-to-wrap wakeup.\n" +
      "\n" +
      "filterspecs are a series of \n" +
      "  <tag>[:priority]\n" +
      "\n" +
      "where <tag> is a log component tag (or * for all) and priority is:\n" +
      "  V    Verbose (default for <tag>)\n" +
      "  D    Debug (default for '*')\n" +
      "  I    Info\n" +
      "  W    Warn\n" +
      "  E    Error\n" +
      "  F    Fatal\n" +
      "  S    Silent (suppress all output)\n" +
      "\n" +
      "'*' by itself means '*:D' and <tag> by itself means <tag>:V.\n" +
      "If no '*' filterspec or -s on command line, all filter defaults to '*:V'.\n" +
      "eg: '*:S <tag>' prints only <tag>, '<tag>:S' suppresses all <tag> log messages.\n" +
      "\n" +
      "If not specified on the command line, filterspec is set from ANDROID_LOG_TAGS.\n" +
      "\n" +
      "If not specified with -v on command line, format is set from ANDROID_PRINTF_LOG\n" +
      "or defaults to \"threadtime\"\n" +
      "\n");
  }

  static void addLogcatDoesntSupportEpochFormatModifierCommands(@NotNull TestShellCommands commands) {
    addCommand(
      commands,
      "logcat --help",
      "Usage: logcat [options] [filterspecs]\n" +
      "options include:\n" +
      "  -s              Set default filter to silent.\n" +
      "                  Like specifying filterspec '*:S'\n" +
      "  -f <filename>   Log to file. Default is stdout\n" +
      "  -r <kbytes>     Rotate log every kbytes. Requires -f\n" +
      "  -n <count>      Sets max number of rotated logs to <count>, default 4\n" +
      "  -v <format>     Sets the log print format, where <format> is:\n" +
      "\n" +
      "                      brief color long printable process raw tag thread\n" +
      "                      threadtime time usec\n" +
      "\n" +
      "  -D              print dividers between each log buffer\n" +
      "  -c              clear (flush) the entire log and exit\n" +
      "  -d              dump the log and then exit (don't block)\n" +
      "  -t <count>      print only the most recent <count> lines (implies -d)\n" +
      "  -t '<time>'     print most recent lines since specified time (implies -d)\n" +
      "  -T <count>      print only the most recent <count> lines (does not imply -d)\n" +
      "  -T '<time>'     print most recent lines since specified time (not imply -d)\n" +
      "                  count is pure numerical, time is 'MM-DD hh:mm:ss.mmm'\n" +
      "  -g              get the size of the log's ring buffer and exit\n" +
      "  -L              dump logs from prior to last reboot\n" +
      "  -b <buffer>     Request alternate ring buffer, 'main', 'system', 'radio',\n" +
      "                  'events', 'crash' or 'all'. Multiple -b parameters are\n" +
      "                  allowed and results are interleaved. The default is\n" +
      "                  -b main -b system -b crash.\n" +
      "  -B              output the log in binary.\n" +
      "  -S              output statistics.\n" +
      "  -G <size>       set size of log ring buffer, may suffix with K or M.\n" +
      "  -p              print prune white and ~black list. Service is specified as\n" +
      "                  UID, UID/PID or /PID. Weighed for quicker pruning if prefix\n" +
      "                  with ~, otherwise weighed for longevity if unadorned. All\n" +
      "                  other pruning activity is oldest first. Special case ~!\n" +
      "                  represents an automatic quicker pruning for the noisiest\n" +
      "                  UID as determined by the current statistics.\n" +
      "  -P '<list> ...' set prune white and ~black list, using same format as\n" +
      "                  printed above. Must be quoted.\n" +
      "\n" +
      "filterspecs are a series of \n" +
      "  <tag>[:priority]\n" +
      "\n" +
      "where <tag> is a log component tag (or * for all) and priority is:\n" +
      "  V    Verbose (default for <tag>)\n" +
      "  D    Debug (default for '*')\n" +
      "  I    Info\n" +
      "  W    Warn\n" +
      "  E    Error\n" +
      "  F    Fatal\n" +
      "  S    Silent (suppress all output)\n" +
      "\n" +
      "'*' by itself means '*:D' and <tag> by itself means <tag>:V.\n" +
      "If no '*' filterspec or -s on command line, all filter defaults to '*:V'.\n" +
      "eg: '*:S <tag>' prints only <tag>, '<tag>:S' suppresses all <tag> log messages.\n" +
      "\n" +
      "If not specified on the command line, filterspec is set from ANDROID_LOG_TAGS.\n" +
      "\n" +
      "If not specified with -v on command line, format is set from ANDROID_PRINTF_LOG\n" +
      "or defaults to \"threadtime\"\n" +
      "\n");
  }

  static void addWhenLsEscapesCommands(@NotNull TestShellCommands commands) {
    addCommand(
      commands,
      "su 0 sh -c 'id'",
      "uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),3001(net_bt_admin)," +
      "3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc),3011(uhid) context=u:r:su:s0");

    addCommand(
      commands,
      "su 0 sh -c 'ls -l /sdcard/dir/'",
      "total 4\n" +
      "drwxrwx--x 2 root sdcard_rw 4096 2018-01-10 12:57 dir\\ with\\ spaces");

    addCommand(commands, "touch /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
    addCommand(commands, "ls /data/local/tmp/oyX2HCKL\\ acuauQGJ", "/data/local/tmp/oyX2HCKL\\ acuauQGJ");
    addCommand(commands, "rm /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
  }

  static void addWhenLsDoesNotEscapeCommands(@NotNull TestShellCommands commands) {
    addCommand(
      commands,
      "su 0 sh -c 'id'",
      "uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r),3001(net_bt_admin)," +
      "3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc) context=u:r:su:s0");

    addCommand(
      commands,
      "su 0 sh -c 'ls -l /sdcard/dir/'",
      "total 8\n" +
      "drwxrwx--x 2 root sdcard_rw 4096 2018-01-10 15:00 dir with spaces");

    addCommand(commands, "touch /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
    addCommand(commands, "ls /data/local/tmp/oyX2HCKL\\ acuauQGJ", "/data/local/tmp/oyX2HCKL acuauQGJ");
    addCommand(commands, "rm /data/local/tmp/oyX2HCKL\\ acuauQGJ", "");
  }

  private static void addCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    commands.add(command + COMMAND_ERROR_CHECK_SUFFIX, result);
  }

  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command) {
    addFailedCommand(commands, command, "");
  }

  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    addCommand(commands, command, result + ERROR_LINE_MARKER + "\n");
  }
}