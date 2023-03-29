// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.download;

import org.jetbrains.annotations.NotNull;
import java.io.File;

public class AndroidProfilerDownloader extends AndroidComponentDownloader {

  private static class Holder {
    private static final AndroidProfilerDownloader INSTANCE = new AndroidProfilerDownloader();
  }

  private AndroidProfilerDownloader() {
    // singleton. Use "getInstance"
  }

  @NotNull
  @Override
  protected String getArtifactName() {
    return "android-plugin-resources";
  }


  /**
   * Checks if installer directory is pre-downloaded in plugins/android/resources directory.
   * <p>
   * This handles a case where Android Plugin resources zip is already pre-downloaded.
   */
  @Override
  protected File getPreInstalledPluginDir() {
    return getPreInstalledPluginDir("plugins/android/resources/installer");
  }

  public static AndroidProfilerDownloader getInstance() {
    return Holder.INSTANCE;
  }
}
