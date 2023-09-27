/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.adtui.webp;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;

/**
 * Test that Webp is supported, and print result into info log
 * <p>
 * WebP support could be functional, but failed to register during Studio start. Run simplified version of WebpSupportTest
 * in E2E test
 * <p>
 * See also: https://youtrack.jetbrains.com/issue/IDEA-316037
 * See also: b/274700617
 */
public class WebpSupportTestAction extends AnAction {

  private static final Logger LOG = Logger.getInstance(WebpSupportTestAction.class);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (webpSupported()) {
      LOG.info("ImageIO supports WebP");
    } else {
      LOG.error("ImageIO don't support WebP");
    }
  }

  private static boolean webpSupported() {
    try {
      return
        ImageIO.getImageWritersByFormatName("webp").hasNext() &&
        ImageIO.getImageWritersByFormatName("WEBP").hasNext() &&

        ImageIO.getImageReadersByFormatName("webp").hasNext() &&
        ImageIO.getImageReadersByFormatName("WEBP").hasNext() &&

        ImageIO.getImageReadersByMIMEType("image/webp").hasNext() &&
        ImageIO.getImageWritersByMIMEType("image/webp").hasNext();
    }
    catch (Exception ignore) {
      return false;
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
