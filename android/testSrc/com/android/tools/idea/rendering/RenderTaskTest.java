/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.diagnostics.crash.CrashReport;
import com.android.tools.idea.diagnostics.crash.CrashReporter;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.mockito.Mockito.*;

public class RenderTaskTest extends RenderTestBase {
  public void testCrashReport() throws Exception {
    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/foo.xml", "").getVirtualFile();
    Configuration configuration = getConfiguration(layoutFile, DEFAULT_DEVICE_ID);
    RenderLogger logger = mock(RenderLogger.class);
    CrashReporter mockCrashReporter = mock(CrashReporter.class);

    RenderTask task = createRenderTask(layoutFile, configuration, logger);
    task.setCrashReporter(mockCrashReporter);
    // Make sure we throw an exception during the inflate call
    task.render((w, h) -> { throw new NullPointerException(); });

    verify(mockCrashReporter, times(1)).submit(isNotNull(CrashReport.class));
  }


  public void testDrawableRender() throws Exception {
    VirtualFile drawableFile = myFixture.addFileToProject("res/drawable/test.xml",
                               "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                        "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                        "    android:shape=\"rectangle\"\n" +
                                                        "    android:tint=\"#FF0000\">\n" +
                                                        "</shape>").getVirtualFile();
    Configuration configuration = getConfiguration(drawableFile, DEFAULT_DEVICE_ID);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = createRenderTask(drawableFile, configuration, logger);
    // Workaround for a bug in layoutlib that will only fully initialize the static state if a render() call is made.
    task.render();
    BufferedImage result = task.renderDrawable(new ResourceValue(ResourceType.DRAWABLE, "test", "@drawable/test", false)).get();

    assertNotNull(result);
    BufferedImage goldenImage = new BufferedImage(result.getWidth(), result.getHeight(), result.getType());
    Graphics2D g = goldenImage.createGraphics();
    try {
      g.setColor(Color.RED);
      g.fillRect(0, 0, result.getWidth(), result.getHeight());
    } finally {
      g.dispose();
    }
  }

}
