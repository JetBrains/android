// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.download;

import org.jetbrains.annotations.NotNull;

public final class AndroidProfilerDownloader extends AndroidComponentDownloader {
  private static class Holder {
    private static final AndroidProfilerDownloader INSTANCE = new AndroidProfilerDownloader();
  }

  private AndroidProfilerDownloader(){
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
}
