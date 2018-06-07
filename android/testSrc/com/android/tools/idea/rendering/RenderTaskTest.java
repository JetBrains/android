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
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.diagnostics.crash.CrashReport;
import com.android.tools.idea.diagnostics.crash.CrashReporter;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.*;

public class RenderTaskTest extends AndroidTestCase {
  @Language("XML")
  private static final String SIMPLE_LAYOUT = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
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
                                              "</LinearLayout>";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    RenderTestUtil.beforeRenderTestCase();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderTestUtil.afterRenderTestCase();
    } finally {
      super.tearDown();
    }
  }

  public void testCrashReport() throws Exception {
    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/foo.xml", "").getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, layoutFile);
    RenderLogger logger = mock(RenderLogger.class);
    CrashReporter mockCrashReporter = mock(CrashReporter.class);

    RenderTask task = RenderTestUtil.createRenderTask(myModule, layoutFile, configuration, logger);
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
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, drawableFile);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = RenderTestUtil.createRenderTask(myModule, drawableFile, configuration, logger);
    // Workaround for a bug in layoutlib that will only fully initialize the static state if a render() call is made.
    task.render().get();
    ResourceValue resourceValue = new ResourceValue(ResourceUrl.create(null, ResourceType.DRAWABLE, "test"),
                                                    "@drawable/test",
                                                    null);
    BufferedImage result = task.renderDrawable(resourceValue).get();

    assertNotNull(result);
    //noinspection UndesirableClassUsage
    BufferedImage goldenImage = new BufferedImage(result.getWidth(), result.getHeight(), result.getType());
    Graphics2D g = goldenImage.createGraphics();
    try {
      g.setColor(Color.RED);
      g.fillRect(0, 0, result.getWidth(), result.getHeight());
    }
    finally {
      g.dispose();
    }

    ImageDiffUtil.assertImageSimilar("drawable", goldenImage, result, 0.1);

    task.dispose().get(5, TimeUnit.SECONDS);
  }

  /**
   * Asserts that the given result matches the {@link #SIMPLE_LAYOUT} structure
   */
  private static void checkSimpleLayoutResult(@NotNull RenderResult result) {
    assertEquals(Result.Status.SUCCESS, result.getRenderResult().getStatus());

    List<ViewInfo> views = result.getRootViews().get(0).getChildren();
    assertEquals(3, views.size());
    String previousCoordinates = "";
    for (int i = 0; i < 3; i++) {
      ViewInfo view = views.get(i);
      assertEquals("android.widget.LinearLayout", view.getClassName());
      // Check the coordinates are different for each box
      String currentCoordinates = String.format("%dx%d - %dx%d", view.getTop(), view.getLeft(), view.getBottom(), view.getRight());
      assertNotEquals(previousCoordinates, currentCoordinates);
      previousCoordinates = currentCoordinates;
    }
  }

  private static void checkSimpleLayoutResult(@NotNull ListenableFuture<RenderResult> futureResult) {
    checkSimpleLayoutResult(Futures.getUnchecked(futureResult));
  }

  public void testRender() throws Exception {
    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = RenderTestUtil.createRenderTask(myModule, file, configuration, logger);
    checkSimpleLayoutResult(task.render());
    // Try a second render
    checkSimpleLayoutResult(task.render());
    // Try layout
    checkSimpleLayoutResult(task.layout());
    task.dispose().get(5, TimeUnit.SECONDS);

    // Now call inflate and check
    task = RenderTestUtil.createRenderTask(myModule, file, configuration, logger);
    checkSimpleLayoutResult(task.inflate());
    checkSimpleLayoutResult(task.render());
    task.dispose().get(5, TimeUnit.SECONDS);

    // Now call inflate and layout
    task = RenderTestUtil.createRenderTask(myModule, file, configuration, logger);
    checkSimpleLayoutResult(task.inflate());
    checkSimpleLayoutResult(task.layout());
    task.dispose().get(5, TimeUnit.SECONDS);

    // layout without inflate should return null
    task = RenderTestUtil.createRenderTask(myModule, file, configuration, logger);
    assertNull(Futures.getUnchecked((task.layout())));
    task.dispose().get(5, TimeUnit.SECONDS);
  }

  public void testAsyncCallAndDispose()
    throws IOException, ExecutionException, InterruptedException, BrokenBarrierException, TimeoutException {
    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/foo.xml", "").getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, layoutFile);
    RenderLogger logger = mock(RenderLogger.class);

    for (int i = 0; i < 5; i++) {
      RenderTask task = RenderTestUtil.createRenderTask(myModule, layoutFile, configuration, logger);
      Semaphore semaphore = new Semaphore(0);
      task.runAsyncRenderAction(() -> {
        semaphore.acquire();
        return null;
      });
      task.runAsyncRenderAction(() -> {
        semaphore.acquire();
        return null;
      });

      Future<?> disposeFuture = task.dispose();
      semaphore.release();

      Throwable exception = null;
      // The render tasks won't finish until all tasks are done
      try {
        disposeFuture.get(500, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException | ExecutionException e) {
        exception = e;
      }
      catch (TimeoutException ignored) {
      }

      if (exception != null) {
        exception.printStackTrace();
        fail("Unexpected exception");
      }

      semaphore.release();
      disposeFuture.get(500, TimeUnit.MILLISECONDS);
    }
  }

  public void testAaptGradient() throws Exception {
    @Language("XML")
    final String content = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "        xmlns:aapt=\"http://schemas.android.com/aapt\"\n" +
                           "        android:width=\"24dp\"\n" +
                           "        android:height=\"24dp\"\n" +
                           "        android:viewportWidth=\"24.0\"\n" +
                           "        android:viewportHeight=\"24.0\">\n" +
                           "  <path android:pathData=\"l24,0,0,24,-24,0z\">\n" +
                           "    <aapt:attr name=\"android:fillColor\">\n" +
                           "      <gradient\n" +
                           "          xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "          android:endX=\"30\"\n" +
                           "          android:endY=\"30\"\n" +
                           "          android:startX=\"0\"\n" +
                           "          android:startY=\"0\"\n" +
                           "          android:type=\"linear\">\n" +
                           "        <item\n" +
                           "            android:color=\"#FF0\"\n" +
                           "            android:offset=\"0.0\" />\n" +
                           "        <item\n" +
                           "            android:color=\"#F0F\"\n" +
                           "            android:offset=\"0.5\" />\n" +
                           "        <item\n" +
                           "            android:color=\"#0FF\"\n" +
                           "            android:offset=\"1.0\" />\n" +
                           "      </gradient>\n" +
                           "    </aapt:attr>\n" +
                           "  </path>\n" +
                           "</vector>\n";
    VirtualFile drawableFile = myFixture.addFileToProject("res/drawable/test.xml",
                                                          content).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, drawableFile);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = RenderTestUtil.createRenderTask(myModule, drawableFile, configuration, logger);
    ResourceValue resourceValue = new ResourceValue(ResourceUrl.create(null, ResourceType.DRAWABLE, "test"),
                                                    "@drawable/test",
                                                    null);
    BufferedImage result = task.renderDrawable(resourceValue).get();
    assertNotNull(result);

    BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/drawables/gradient-golden.png"));
    ImageDiffUtil.assertImageSimilar("gradient_drawable", goldenImage, result, 0.1);

    task.dispose().get(5, TimeUnit.SECONDS);
  }

  public void testCjkFontSupport() throws InterruptedException, ExecutionException, TimeoutException, IOException {
    @Language("XML")
    final String content= "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                          "    android:layout_height=\"match_parent\"\n" +
                          "    android:layout_width=\"match_parent\"\n" +
                          "    android:orientation=\"vertical\"\n" +
                          "    android:background=\"#FFF\">\n" +
                          "\n" +
                          // CJK Unified Ideographs
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"门蠁\"/>\n" +
                          // Chinese only
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"λ点  e书本s ，。？！\"/>\n" +
                          // Korean only
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"곶㭐㸴\"/>\n" +
                          // Japanese only
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"蘰躵鯏\"/>\n" +
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"さしすせそ\"/>\n" +
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"ラリルレロ\"/>\n" +
                          // Combined only
                          "    <TextView\n" +
                          "        android:layout_width=\"wrap_content\"\n" +
                          "        android:layout_height=\"wrap_content\"\n" +
                          "        android:textSize=\"50sp\"\n" +
                          "        android:text=\"蘰门Hello鯏\"/>\n" +
                          "    \n" +
                          "\n" +
                          "</LinearLayout>";

    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", content).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    configuration.setTheme("android:Theme.NoTitleBar.Fullscree");
    RenderLogger logger = mock(RenderLogger.class);

    RenderTask task = RenderTestUtil.createRenderTask(myModule, file, configuration, logger);
    BufferedImage result = task.render().get().getRenderedImage().getCopy();

    BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/layouts/cjk-golden.png"));
    // Fonts on OpenJDK look slightly different than on the IntelliJ version. Increate the diff tolerance to
    // 0.5 to account for that. We mostly care about characters not being displayed at all.
    ImageDiffUtil.assertImageSimilar("gradient_drawable", goldenImage, result, 0.5);
    task.dispose().get(5, TimeUnit.SECONDS);
  }
}
