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
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.diagnostics.crash.CrashReport;
import com.android.tools.idea.diagnostics.crash.CrashReporter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

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
    task.render((w, h) -> { throw new NullPointerException(); }).get();

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
    task.render().get();
    ResourceValue resourceValue = new ResourceValue(ResourceUrl.create(null, ResourceType.DRAWABLE, "test"),
                                                    "@drawable/test",
                                                    null);
    BufferedImage result = task.renderDrawable(resourceValue).get();

    assertNotNull(result);
    BufferedImage goldenImage = new BufferedImage(result.getWidth(), result.getHeight(), result.getType());
    Graphics2D g = goldenImage.createGraphics();
    try {
      g.setColor(Color.RED);
      g.fillRect(0, 0, result.getWidth(), result.getHeight());
    } finally {
      g.dispose();
    }

    task.dispose();
  }

  public void testRender() throws Exception {
    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml",
                                                          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                          "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                          "    android:layout_height=\"match_parent\"\n" +
                                                          "    android:layout_width=\"match_parent\"\n" +
                                                          "    android:orientation=\"vertical\">\n" +
                                                          "\n" +
                                                          "    <LinearLayout\n" +
                                                          "        android:layout_width=\"50dp\"\n" +
                                                          "        android:layout_height=\"50dp\"\n" +
                                                          "        android:background=\"#F00\"/>\n" +
                                                          "    <LinearLayout\n" +
                                                          "        android:layout_width=\"50dp\"\n" +
                                                          "        android:layout_height=\"50dp\"\n" +
                                                          "        android:background=\"#0F0\"/>\n" +
                                                          "    <LinearLayout\n" +
                                                          "        android:layout_width=\"50dp\"\n" +
                                                          "        android:layout_height=\"50dp\"\n" +
                                                          "        android:background=\"#00F\"/>\n" +
                                                          "    \n" +
                                                          "\n" +
                                                          "</LinearLayout>").getVirtualFile();
    Configuration configuration = getConfiguration(file, DEFAULT_DEVICE_ID);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = createRenderTask(file, configuration, logger);
    ListenableFuture<RenderResult> resultFuture = task.render();
    RenderResult result = Futures.getUnchecked(resultFuture);

    assertNotNull(result);
    assertEquals(Result.Status.SUCCESS, result.getRenderResult().getStatus());
    List<ViewInfo> views = result.getRootViews().get(0).getChildren();
    assertEquals(3, views.size());
    for (int i = 0; i < 3; i++) {
      assertEquals("android.widget.LinearLayout", views.get(0).getClassName());
    }

    task.dispose();
  }

  // http://b.android.com/278500
  public void ignored_testAsyncCallAndDispose() throws IOException, ExecutionException, InterruptedException, BrokenBarrierException {
    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/foo.xml", "").getVirtualFile();
    Configuration configuration = getConfiguration(layoutFile, DEFAULT_DEVICE_ID);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = createRenderTask(layoutFile, configuration, logger);
    Semaphore semaphore = new Semaphore(0);
    task.runAsyncRenderAction(() -> {
      semaphore.acquire();

      return null;
    });
    task.runAsyncRenderAction(() -> {
      semaphore.acquire();

      return null;
    });

    boolean timedOut = false;

    Future<?> disposeFuture = task.dispose();
    semaphore.release();

    // The render tasks won't finish until all tasks are done
    try {
      disposeFuture.get(500, TimeUnit.MILLISECONDS);
    }
    catch (InterruptedException | ExecutionException ignored) {
    }
    catch (TimeoutException e) {
      timedOut = true;
    }
    assertTrue(timedOut);

    semaphore.release();
    disposeFuture.get();
  }

}
