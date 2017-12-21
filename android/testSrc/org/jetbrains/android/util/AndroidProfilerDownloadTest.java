package org.jetbrains.android.util;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.android.download.AndroidProfilerDownloader;

import java.io.File;

public class AndroidProfilerDownloadTest extends LightPlatformTestCase {

  public void testDownloadProfiler() {
    File dir = AndroidProfilerDownloader.getHostDir("");
    if (dir.exists())
      assertTrue(FileUtil.delete(dir));
    assertFalse(dir.exists());

    assertTrue(AndroidProfilerDownloader.makeSureProfilerIsInPlace());

    assertTrue(AndroidProfilerDownloader.getHostDir("").exists());
    assertTrue(AndroidProfilerDownloader.getHostDir("plugins/android/resources/perfd").exists());
  }
}
