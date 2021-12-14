// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.download;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

public class AndroidProfilerDownloader extends AndroidComponentDownloader {
  private static final Logger LOG = Logger.getInstance(AndroidProfilerDownloader.class);

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

  public static AndroidProfilerDownloader getInstance() {
    return Holder.INSTANCE;
  }

  @Override
  protected @NotNull String getVersion() {
    LOG.assertTrue(super.getVersion().startsWith("27.3.0."), "Obsolete version override.");
    return "27.3.0.1";
  }
}
