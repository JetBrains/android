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
  @NotNull public static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
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