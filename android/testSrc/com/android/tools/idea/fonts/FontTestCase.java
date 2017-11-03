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
package com.android.tools.idea.fonts;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public abstract class FontTestCase extends AndroidTestCase {
  protected File myFontPath;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    DownloadableFontCacheServiceImpl service = new FontCache();
    registerApplicationComponent(DownloadableFontCacheService.class, service);
    myFontPath = service.getFontPath();
  }

  @NotNull
  public static File makeFile(@NotNull File base, String... children) {
    File file = base;
    for (String child : children) {
      file = new File(file, child);
    }
    return file;
  }

  private static class FontCache extends DownloadableFontCacheServiceImpl {
    private File mySdkFontPath;

    @NotNull
    @Override
    protected File locateSdkHome() {
      if (mySdkFontPath == null) {
        try {
          mySdkFontPath = FileUtil.createTempDirectory("font", "sdk");
        }
        catch (IOException ex) {
          throw new RuntimeException(ex);
        }
      }
      return mySdkFontPath;
    }
  }
}
