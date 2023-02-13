/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.updater.configure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.repository.Revision;
import com.android.repository.api.RemotePackage;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.generated.addon.v2.ExtraDetailsType;
import com.android.sdklib.repository.generated.sysimg.v2.SysImgDetailsType;
import com.android.testutils.file.InMemoryFileSystems;
import com.android.utils.HtmlBuilder;
import com.android.utils.Pair;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class SdkUpdaterConfigurableTest {
  private static final long USABLE_DISK_SPACE = 1024*1024*1024L; // 1GB
  private static final String SDK_ROOT_PATH = "/sdk";

  private static final String DISK_USAGE_HTML_TEMPLATE =
    "Disk usage:\n" +
    "<DL><DD>-&NBSP;Disk space that will be freed: %1$s" +
    "<DD>-&NBSP;Estimated download size: %2$s" +
    "<DD>-&NBSP;Estimated disk space to be additionally occupied on SDK partition after installation: %3$s" +
    "<DD>-&NBSP;Currently available disk space in SDK root (" + SDK_ROOT_PATH + "): %4$s</DL>";

  private static final String DISK_USAGE_HTML_TEMPLATE_WITHOUT_SPACE_TO_FREE_UP =
    "Disk usage:\n" +
    "<DL><DD>-&NBSP;Estimated download size: %1$s" +
    "<DD>-&NBSP;Estimated disk space to be additionally occupied on SDK partition after installation: %2$s" +
    "<DD>-&NBSP;Currently available disk space in SDK root (" + SDK_ROOT_PATH + "): %3$s</DL>";

  private static final String DISK_USAGE_HTML_TEMPLATE_WITHOUT_DOWNLOADS =
    "Disk usage:\n" +
    "<DL><DD>-&NBSP;Disk space that will be freed: %1$s</DL>";

  private static final String WARNING_HTML =
    "<FONT color=\"#ff0000\"><B>WARNING: There might be insufficient disk space to perform this operation. " +
    "</B><BR/><BR/>Estimated disk usage is presented below. Consider freeing up more disk space before proceeding. </FONT><BR/><BR/>";

  private Path mySdkRoot;

  @Before
  public void setUp() {
    FileSystem fs = InMemoryFileSystems.createInMemoryFileSystem(USABLE_DISK_SPACE);
    mySdkRoot = fs.getPath(InMemoryFileSystems.getPlatformSpecificPath(SDK_ROOT_PATH));
  }

  @Test
  public void testDiskSpaceMessagesFullAndPatchAndUninstall() {
    final long fullInstallationDownloadSize = 70 * 1024 * 1024L + 42; // +42 just to avoid "nice" numbers
    final long patchesDownloadSize = 20 * 1024 * 1024L + 42;
    final long spaceToBeFreedUp = 10 * 1024 * 1024L + 42;

    Pair<HtmlBuilder, HtmlBuilder> messages = SdkUpdaterConfigurable.getDiskUsageMessages(mySdkRoot, fullInstallationDownloadSize,
                                                                                          patchesDownloadSize, spaceToBeFreedUp);
    assertEquals(String.format(DISK_USAGE_HTML_TEMPLATE, "10.0 MB", "90.0 MB", "290.0 MB", "1.0 GB"),
                 messages.getFirst().getHtml());
    assertNull(messages.getSecond());
  }

  @Test
  public void testDiskSpaceMessagesFullAndUninstall() {
    final long fullInstallationDownloadSize = 70 * 1024 * 1024L + 42;
    final long patchesDownloadSize = 0;
    final long spaceToBeFreedUp = 10 * 1024 * 1024L + 42;

    Pair<HtmlBuilder, HtmlBuilder> messages = SdkUpdaterConfigurable.getDiskUsageMessages(mySdkRoot, fullInstallationDownloadSize,
                                                                                          patchesDownloadSize, spaceToBeFreedUp);
    assertEquals(String.format(DISK_USAGE_HTML_TEMPLATE, "10.0 MB", "70.0 MB", "270.0 MB", "1.0 GB"),
                 messages.getFirst().getHtml());
    assertNull(messages.getSecond());
  }

  @Test
  public void testDiskSpaceMessagesPatchAndUninstall() {
    final long fullInstallationDownloadSize = 0;
    final long patchesDownloadSize = 20 * 1024 * 1024L + 42;
    final long spaceToBeFreedUp = 9 * 1024 * 1024L + 42;

    Pair<HtmlBuilder, HtmlBuilder> messages = SdkUpdaterConfigurable.getDiskUsageMessages(mySdkRoot, fullInstallationDownloadSize,
                                                                                          patchesDownloadSize, spaceToBeFreedUp);
    assertEquals(String.format(DISK_USAGE_HTML_TEMPLATE, "9.0 MB", "20.0 MB", "11.0 MB", "1.0 GB"),
                 messages.getFirst().getHtml());
    assertNull(messages.getSecond());
  }

  @Test
  public void testDiskSpaceMessagesPatchAndUninstallWhenInsufficientSpace() {
    final long fullInstallationDownloadSize = 0;
    final long patchesDownloadSize = 1200 * 1024 * 1024L + 42;
    final long spaceToBeFreedUp = 9 * 1024 * 1024L + 42;

    Pair<HtmlBuilder, HtmlBuilder> messages = SdkUpdaterConfigurable.getDiskUsageMessages(mySdkRoot, fullInstallationDownloadSize,
                                                                                          patchesDownloadSize, spaceToBeFreedUp);
    assertEquals(String.format(DISK_USAGE_HTML_TEMPLATE, "9.0 MB", "1.2 GB", "1.2 GB", "1.0 GB"),
                 messages.getFirst().getHtml());
    assertEquals(WARNING_HTML, messages.getSecond().getHtml());
  }

  @Test
  public void testDiskSpaceMessagesPatchWhenInsufficientSpace() {
      final long fullInstallationDownloadSize = 0;
      final long patchesDownloadSize = 1200*1024*1024L + 42;
      final long spaceToBeFreedUp = 0;

      Pair<HtmlBuilder, HtmlBuilder> messages = SdkUpdaterConfigurable.getDiskUsageMessages(mySdkRoot, fullInstallationDownloadSize,
                                                                                            patchesDownloadSize, spaceToBeFreedUp);
      assertEquals(String.format(DISK_USAGE_HTML_TEMPLATE_WITHOUT_SPACE_TO_FREE_UP, "1.2 GB", "1.2 GB", "1.0 GB"),
                   messages.getFirst().getHtml());
      assertEquals(WARNING_HTML, messages.getSecond().getHtml());
  }

  @Test
  public void testDiskSpaceMessagesUninstallOnly() {
      final long fullInstallationDownloadSize = 0;
      final long patchesDownloadSize = 0;
      final long spaceToBeFreedUp = 800*1024*1024L + 42;

      Pair<HtmlBuilder, HtmlBuilder> messages = SdkUpdaterConfigurable.getDiskUsageMessages(mySdkRoot, fullInstallationDownloadSize,
                                                                                            patchesDownloadSize, spaceToBeFreedUp);
      assertEquals(String.format(DISK_USAGE_HTML_TEMPLATE_WITHOUT_DOWNLOADS, "800.0 MB"),
                   messages.getFirst().getHtml());
      assertNull(messages.getSecond());
  }

  @Test
  public void getItemMessageForImage() {
    SysImgDetailsType detailsType = Mockito.mock(SysImgDetailsType.class);
    Mockito.when(detailsType.getAndroidVersion()).thenReturn(new AndroidVersion(30));
    Mockito.when(detailsType.getPackageDisplayNameQualifier()).thenCallRealMethod();
    Mockito.when(detailsType.getVersionTerm()).thenCallRealMethod();

    RemotePackage remotePackage = Mockito.mock(RemotePackage.class);
    Mockito.when(remotePackage.getDisplayName()).thenReturn("Test System Image");
    Mockito.when(remotePackage.getVersion()).thenReturn(new Revision(1));
    Mockito.when(remotePackage.getTypeDetails()).thenReturn(detailsType);
    Mockito.when(remotePackage.getDetailedDisplayName()).thenCallRealMethod();

    assertEquals("Test System Image API 30 (revision 1)", SdkUpdaterConfigurable.getItemMessage(remotePackage));
  }

  @Test
  public void getItemMessageForTool() {
    ExtraDetailsType detailsType = Mockito.mock(ExtraDetailsType.class);
    Mockito.when(detailsType.getPackageDisplayNameQualifier()).thenCallRealMethod();
    Mockito.when(detailsType.getVersionTerm()).thenCallRealMethod();

    RemotePackage remotePackage = Mockito.mock(RemotePackage.class);
    Mockito.when(remotePackage.getDisplayName()).thenReturn("Test SDK Tool");
    Mockito.when(remotePackage.getVersion()).thenReturn(new Revision(12));
    Mockito.when(remotePackage.getTypeDetails()).thenReturn(detailsType);
    Mockito.when(remotePackage.getDetailedDisplayName()).thenCallRealMethod();

    assertEquals("Test SDK Tool (version 12)", SdkUpdaterConfigurable.getItemMessage(remotePackage));
  }
}
