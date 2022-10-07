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

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.rendering.RenderTestUtil.DEFAULT_DEVICE_ID;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.analytics.crash.CrashReport;
import com.android.tools.analytics.crash.CrashReporter;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationHolder;
import com.android.tools.idea.configurations.SystemUiModeAction;
import com.android.tools.idea.configurations.Wallpaper;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PsiTestUtil;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import javax.tools.ToolProvider;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class RenderTaskTest extends AndroidTestCase {
  // Using native rendering should have less variation between machines than Java rendering
  // so the threshold for image diff can be lower than the default.
  private static final double IMAGE_DIFF_THRESHOLD_PERCENT = 0.1;

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

  public void testCrashReport() {
    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/foo.xml", "").getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, layoutFile);
    RenderLogger logger = mock(RenderLogger.class);
    CrashReporter mockCrashReporter = mock(CrashReporter.class);

    RenderTestUtil.withRenderTask(myFacet, layoutFile, configuration, logger, task -> {
      task.setCrashReporter(mockCrashReporter);
      // Make sure we throw an exception during the inflate call
      try {
        task.render((w, h) -> {
          throw new NullPointerException();
        }).get();
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }

      verify(mockCrashReporter, times(1)).submit((CrashReport)isNotNull());
    });
  }


  public void testDrawableRender() {
    VirtualFile drawableFile = myFixture.addFileToProject("res/drawable/test.xml",
                                                          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                          "<shape xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                          "    android:shape=\"rectangle\"\n" +
                                                          "    android:tint=\"#FF0000\">\n" +
                                                          "</shape>").getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, drawableFile);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, drawableFile, configuration, logger, task -> {
      // Workaround for a bug in layoutlib that will only fully initialize the static state if a render() call is made.
      try {
        task.render().get();
        ResourceValue resourceValue = new ResourceValueImpl(RES_AUTO, ResourceType.DRAWABLE, "test", "@drawable/test");
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

        ImageDiffUtil.assertImageSimilar("drawable", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testCustomDrawableRender() throws Exception {
    File tmpDir = Files.createTempDir();
    File srcDir = new File(tmpDir, "src");
    File customDrawable = new File(srcDir, "com/google/test/CustomDrawable.java");
    FileUtil.writeToFile(customDrawable, "package com.google.test;\n" +
                                         "import android.graphics.Color;\n" +
                                         "import android.graphics.drawable.GradientDrawable;\n" +
                                         "public class CustomDrawable extends GradientDrawable {\n" +
                                         "  public CustomDrawable() {\n" +
                                         "    super(Orientation.TOP_BOTTOM, new int[] {Color.RED, Color.BLUE});\n" +
                                         "  }\n" +
                                         "}");
    ApplicationManager.getApplication().runWriteAction(
      (Computable<SourceFolder>)() -> PsiTestUtil.addSourceRoot(myModule, VfsUtil.findFileByIoFile(srcDir, true)));
    ToolProvider.getSystemJavaCompiler().run(null, null, null, customDrawable.getAbsolutePath());
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + myModule.getName());
    CompilerProjectExtension.getInstance(getProject()).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));
    FileUtil.copy(new File(srcDir, "com/google/test/CustomDrawable.class"), new File(outputDir, "com/google/test/CustomDrawable.class"));

    VirtualFile drawableFile = myFixture.addFileToProject("res/drawable/test.xml",
                                                          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                          "<drawable class=\"com.google.test.CustomDrawable\" />")
      .getVirtualFile();

    Configuration configuration = RenderTestUtil.getConfiguration(myModule, drawableFile);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, drawableFile, configuration, logger, task -> {
      ResourceValue resourceValue = new ResourceValueImpl(RES_AUTO, ResourceType.DRAWABLE, "test", "@drawable/test");
      try {
        BufferedImage result = task.renderDrawable(resourceValue).get();
        assertNotNull(result);

        Path goldenImage = Paths.get(getTestDataPath(), "drawables/custom-golden.png");
        ImageDiffUtil.assertImageSimilar(goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testCustomViewRenderOutOfDateIsReported() throws Exception {
    File tmpDir = Files.createTempDir();
    File srcDir = new File(tmpDir, "src");
    File customView = new File(srcDir, "com/google/test/CustomView.java");
    FileUtil.writeToFile(customView, "package com.google.test;\n" +
                                         "import android.content.Context;\n" +
                                         "import android.util.AttributeSet;\n" +
                                         "import android.widget.TextView;\n" +
                                         "public class CustomView extends TextView {\n" +
                                         "  public CustomView(Context context, AttributeSet attrs) {\n" +
                                         "    super(context);\n" +
                                         "    setText(\"Hello\");\n" +
                                         "  }\n" +
                                         "}");
    ApplicationManager.getApplication().runWriteAction(
      (Computable<SourceFolder>)() ->
        PsiTestUtil.addSourceRoot(myModule, Objects.requireNonNull(VfsUtil.findFileByIoFile(srcDir, true))));
    ToolProvider.getSystemJavaCompiler().run(null, null, null, customView.getAbsolutePath());
    File outputDir = new File(tmpDir, CompilerModuleExtension.PRODUCTION + "/" + myModule.getName());
    File outputFile = new File(outputDir, "com/google/test/CustomView.class");
    Objects.requireNonNull(CompilerProjectExtension.getInstance(getProject())).setCompilerOutputUrl(pathToIdeaUrl(tmpDir));
    FileUtil.copy(new File(srcDir, "com/google/test/CustomView.class"), outputFile);

    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/test.xml",
                                                          "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                                                          "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                                          "    android:layout_height=\"match_parent\"\n" +
                                                          "    android:layout_width=\"match_parent\"\n" +
                                                          "    android:orientation=\"vertical\"\n" +
                                                          "    android:background=\"#FFF\">\n" +
                                                          "  <com.google.test.CustomView" +
                                                          "    android:layout_height=\"match_parent\"\n" +
                                                          "    android:layout_width=\"match_parent\" />\n" +
                                                          "</LinearLayout>\n")
      .getVirtualFile();

    Configuration configuration = RenderTestUtil.getConfiguration(myModule, layoutFile);
    RenderLogger logger = new RenderLogger(null, null);

    RenderTestUtil.withRenderTask(myFacet, layoutFile, configuration, logger, task -> {
      try {
        RenderResult result = task.render().get();
        assertTrue(result.hasRequestedCustomViews());
        assertTrue(result.getRenderResult().isSuccess());
        assertTrue(logger.getMessages().isEmpty());

        // Drop PSI cache
        ApplicationManager.getApplication().invokeAndWait(() -> PsiManager.getInstance(getProject()).dropPsiCaches());
        ToolProvider.getSystemJavaCompiler().run(null, null, null, customView.getAbsolutePath());
        FileUtil.copy(new File(srcDir, "com/google/test/CustomView.class"), outputFile);
        VfsUtil.findFileByIoFile(outputFile, true);

        result = task.render().get();
        assertTrue(result.hasRequestedCustomViews());
        assertTrue(result.getRenderResult().isSuccess());
        assertEquals(
          "The project has been edited more recently than the last build: <A HREF=\"action:build\">Build</A> the project.",
          logger.getMessages().get(0).getHtml());
      }
      catch (Throwable e) {
        e.printStackTrace(System.err);
        fail("Unexpected exception: " + e.getMessage());
      }
    });
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

  private static void checkSimpleLayoutResult(@NotNull CompletableFuture<RenderResult> futureResult) {
    checkSimpleLayoutResult(Futures.getUnchecked(futureResult));
  }

  public void testRender() {
    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", SIMPLE_LAYOUT).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      checkSimpleLayoutResult(task.render());
      // Try a second render
      checkSimpleLayoutResult(task.render());
      // Try layout
      checkSimpleLayoutResult(task.layout());
    });

    // Now call inflate and check
    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      checkSimpleLayoutResult(task.inflate());
      checkSimpleLayoutResult(task.render());
    });

    // Now call inflate and layout
    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      checkSimpleLayoutResult(task.inflate());
      checkSimpleLayoutResult(task.layout());
    });

    // layout without inflate should return null
    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      assertNull(Futures.getUnchecked((task.layout())));
    });
  }

  public void testAsyncCallAndDispose() {
    VirtualFile layoutFile = myFixture.addFileToProject("res/layout/foo.xml", "").getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, layoutFile);
    RenderLogger logger = mock(RenderLogger.class);

    for (int i = 0; i < 5; i++) {
      RenderTestUtil.withRenderTask(myFacet, layoutFile, configuration, logger, task -> {
        Semaphore semaphore = new Semaphore(0);
        task.runAsyncRenderAction(() -> {
          try {
            semaphore.acquire();
          }
          catch (InterruptedException e) {
            fail("Failed to acquire semaphore");
          }
          return null;
        });
        task.runAsyncRenderAction(() -> {
          try {
            semaphore.acquire();
          }
          catch (InterruptedException e) {
            fail("Failed to acquire semaphore");
          }
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
        try {
          disposeFuture.get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
    }
  }

  public void testAaptGradient() {
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

    RenderTestUtil.withRenderTask(myFacet, drawableFile, configuration, logger, task -> {
      ResourceValue resourceValue = new ResourceValueImpl(RES_AUTO, ResourceType.DRAWABLE, "test", "@drawable/test");
      try {
        BufferedImage result = task.renderDrawable(resourceValue).get();
        assertNotNull(result);

        BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/drawables/gradient-golden.png"));
        ImageDiffUtil.assertImageSimilar("gradient_drawable", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testAnimatedVectorDrawable() {
    @Language("XML")
    final String vector = "<animated-vector\n" +
                           "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                           "    xmlns:aapt=\"http://schemas.android.com/aapt\">\n" +
                           "    <aapt:attr name=\"android:drawable\">\n" +
                           "        <vector\n" +
                           "            android:width=\"24dp\"\n" +
                           "            android:height=\"24dp\"\n" +
                           "            android:viewportWidth=\"24\"\n" +
                           "            android:viewportHeight=\"24\">\n" +
                           "            <path\n" +
                           "                android:name=\"outline\"\n" +
                           "                android:fillAlpha=\"0.5\"\n" +
                           "                android:fillColor=\"#f00\"\n" +
                           "                android:pathData=\"@string/path\"\n" +
                           "                android:strokeColor=\"#000\"\n" +
                           "                android:strokeWidth=\"2\" />\n" +
                           "        </vector>\n" +
                           "    </aapt:attr>\n" +
                           "</animated-vector>\n";
    @Language("XML")
    final String path = "<resources>\n" +
                        "    <string name=\"path\">M 12.075 19.67 L 16 19.67 C 16 18.477 16 17.283 16 16.09 L 14.125 14.21 C 13.5 13.583 12.875 12.957 12.25 12.33 C 12.875 11.707 13.5 11.083 14.125 10.46 L 16 8.59 L 16 6.795 C 16 6.197 16 5.598 16 5 L 8 5 C 8 5.598 8 6.197 8 6.795 L 8 8.59 C 9.25 9.837 10.5 11.083 11.75 12.33 C 11.125 12.955 10.5 13.58 9.875 14.205 L 8 16.08 C 8 17.277 8 18.473 8 19.67 L 12.075 19.67</string>\n" +
                        "</resources>\n";
    VirtualFile drawableFile = myFixture.addFileToProject("res/drawable/test.xml",
                                                          vector).getVirtualFile();
    myFixture.addFileToProject("res/values/strings.xml", path);
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, drawableFile);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, drawableFile, configuration, logger, task -> {
      ResourceValue resourceValue = new ResourceValueImpl(RES_AUTO, ResourceType.DRAWABLE, "test", "@drawable/test");
      try {
        BufferedImage result = task.renderDrawable(resourceValue).get();
        assertNotNull(result);

        BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/drawables/animated-vector-golden.png"));
        ImageDiffUtil.assertImageSimilar("animated_vector_drawable", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testCjkFontSupport() {
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
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();

        BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/layouts/cjk-golden.png"));
        ImageDiffUtil.assertImageSimilar("gradient_drawable", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testAnimatedVectorDrawableWithNestedAaptAttr() {
    @Language("XML")
    final String vector = "<animated-vector\n" +
                          "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                          "    xmlns:aapt=\"http://schemas.android.com/aapt\">\n" +
                          "    <aapt:attr name=\"android:drawable\">\n" +
                          "        <vector\n" +
                          "            android:width=\"24dp\"\n" +
                          "            android:height=\"24dp\"\n" +
                          "            android:viewportWidth=\"24\"\n" +
                          "            android:viewportHeight=\"24\">\n" +
                          "            <path\n" +
                          "                android:name=\"outline\"\n" +
                          "                android:fillColor=\"#ff0000\"\n" +
                          "                android:pathData=\"M 11.925 18 L 21.17 18 C 21.63 18 22 17.65 22 17.25 L 22 14.57 C 20.912 14.181 20.182 13.155 20.17 12 C 20.17 10.82 20.93 9.82 22 9.43 L 22 9.43 L 22 6.75 C 22 6.35 21.63 6 21.17 6 L 2.83 6 C 2.37 6 2.01 6.35 2.01 6.75 L 2.01 9.43 C 3.07 9.83 3.83 10.82 3.83 12 C 3.83 13.18 3.07 14.18 2 14.57 L 2 17.25 C 2 17.65 2.37 18 2.83 18 L 11.925 18\"\n" +
                          "                android:strokeColor=\"#FF0000\"\n" +
                          "                android:strokeWidth=\"2\" />\n" +
                          "            <group\n" +
                          "                android:name=\"star_group\"\n" +
                          "                android:pivotX=\"12\"\n" +
                          "                android:pivotY=\"13\">\n" +
                          "                <path\n" +
                          "                    android:fillColor=\"#fff\"\n" +
                          "                    android:pathData=\"M 15.1 16 L 14.16 12.46 L 17 10.13 L 13.34 9.91 L 12 6.5 L 10.65 9.9 L 6.99 10.12 L 9.83 12.45 L 8.91 16 L 12.01 14 L 15.1 16 Z\" />\n" +
                          "            </group>\n" +
                          "            <path\n" +
                          "                android:name=\"progress\"\n" +
                          "                android:pathData=\"M 12.075 19.67 L 16 19.67 C 16 18.477 16 17.283 16 16.09 L 14.125 14.21 C 13.5 13.583 12.875 12.957 12.25 12.33 C 12.875 11.707 13.5 11.083 14.125 10.46 L 16 8.59 L 16 6.795 C 16 6.197 16 5.598 16 5 L 8 5 C 8 5.598 8 6.197 8 6.795 L 8 8.59 C 9.25 9.837 10.5 11.083 11.75 12.33 C 11.125 12.955 10.5 13.58 9.875 14.205 L 8 16.08 C 8 17.277 8 18.473 8 19.67 L 12.075 19.67\"\n" +
                          "                android:strokeAlpha=\"0\"\n" +
                          "                android:strokeColor=\"#ffffff00\"\n" +
                          "                android:strokeWidth=\"2\"\n" +
                          "                android:trimPathEnd=\"0.03\"\n" +
                          "                android:trimPathOffset=\"0\"\n" +
                          "                android:trimPathStart=\"0\" />\n" +
                          "        </vector>\n" +
                          "    </aapt:attr>\n" +
                          "    <target android:name=\"progress\">\n" +
                          "        <aapt:attr name=\"android:animation\">\n" +
                          "            <set>\n" +
                          "                <objectAnimator\n" +
                          "                    android:duration=\"1333\"\n" +
                          "                    android:propertyName=\"trimPathStart\"\n" +
                          "                    android:repeatCount=\"-1\"\n" +
                          "                    android:valueFrom=\"0\"\n" +
                          "                    android:valueTo=\"0.75\"\n" +
                          "                    android:valueType=\"floatType\">\n" +
                          "                    <aapt:attr name=\"android:interpolator\">\n" +
                          "                        <pathInterpolator\n" +
                          "                            android:pathData=\"L0.5,0 C 0.7,0 0.6,1 1,1\" />\n" +
                          "                    </aapt:attr>\n" +
                          "                </objectAnimator>\n" +
                          "                <objectAnimator\n" +
                          "                    android:duration=\"1333\"\n" +
                          "                    android:propertyName=\"trimPathEnd\"\n" +
                          "                    android:repeatCount=\"-1\"\n" +
                          "                    android:valueFrom=\"0.03\"\n" +
                          "                    android:valueTo=\"0.78\"\n" +
                          "                    android:valueType=\"floatType\">\n" +
                          "                    <aapt:attr name=\"android:interpolator\">\n" +
                          "                        <pathInterpolator\n" +
                          "                            android:pathData=\"C0.2,0 0.1,1 0.5,0.96 C 0.96666666666,0.96 0.99333333333,1 1,1\" />\n" +
                          "                    </aapt:attr>\n" +
                          "                </objectAnimator>\n" +
                          "            </set>\n" +
                          "        </aapt:attr>\n" +
                          "    </target>\n" +
                          "</animated-vector>\n";
    @Language("XML")
    final String content= "<FrameLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                          "    android:layout_height=\"match_parent\"\n" +
                          "    android:layout_width=\"match_parent\"\n" +
                          "    android:background=\"#FFF\">\n" +
                          "\n" +
                          "    <ImageView\n" +
                          "        android:layout_width=\"240dp\"\n" +
                          "        android:layout_height=\"240dp\"\n" +
                          "        android:src=\"@drawable/test\"/>\n" +
                          "    \n" +
                          "</FrameLayout>";
    myFixture.addFileToProject("res/drawable/test.xml", vector);
    // We render a full layout in this test instead of just the drawable as the problem is with parsing the animator, which happens
    // only when rendering the animated vector drawable inside a layout.
    VirtualFile layout = myFixture.addFileToProject("res/layout/layout.xml", content).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, layout);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, layout, configuration, logger, task -> {
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();

        //ImageIO.write(result, "png", new File(getTestDataPath() + "/drawables/animated-vector-aapt-golden.png"));
        BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/drawables/animated-vector-aapt-golden.png"));
        ImageDiffUtil.assertImageSimilar("animated_vector_drawable", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testEmojiSupport() {
    @Language("XML") final String content = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                            "    android:layout_height=\"match_parent\"\n" +
                                            "    android:layout_width=\"match_parent\"\n" +
                                            "    android:orientation=\"vertical\"\n" +
                                            "    android:background=\"#FFF\">\n" +
                                            "\n" +
                                            "    <TextView\n" +
                                            "        android:layout_width=\"wrap_content\"\n" +
                                            "        android:layout_height=\"wrap_content\"\n" +
                                            "        android:textSize=\"50sp\"\n" +
                                            "        android:text=\"\uD83D\uDE00  \uD83D\uDC22  \uD83E\uDD51\"/>\n" +
                                            "    \n" +
                                            "\n" +
                                            "</LinearLayout>";

    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", content).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();

        BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/layouts/emoji.png"));
        ImageDiffUtil.assertImageSimilar("emojis", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testDynamicTheming() {
    @Language("XML") final String content = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                            "    android:layout_height=\"match_parent\"\n" +
                                            "    android:layout_width=\"match_parent\"\n" +
                                            "    android:orientation=\"vertical\"\n" +
                                            "    android:background=\"#FFF\">\n" +
                                            "\n" +
                                            "    <TextView\n" +
                                            "        android:layout_width=\"wrap_content\"\n" +
                                            "        android:layout_height=\"wrap_content\"\n" +
                                            "        android:background=\"@android:color/system_accent1_500\"\n" +
                                            "        android:textSize=\"50sp\"\n" +
                                            "        android:text=\"Dynamic Theming\"/>\n" +
                                            "    \n" +
                                            "\n" +
                                            "</LinearLayout>";

    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", content).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();
        ImageDiffUtil.assertImageSimilar(Path.of(getTestDataPath(), "layouts/dynamic_theming_0.png"), result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });

    configuration.setWallpaperPath(Wallpaper.WALLPAPER_1.getResourcePath());
    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();
        ImageDiffUtil.assertImageSimilar(Path.of(getTestDataPath(), "layouts/dynamic_theming_1.png"), result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });

    configuration.setWallpaperPath(Wallpaper.WALLPAPER_2.getResourcePath());
    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();
        ImageDiffUtil.assertImageSimilar(Path.of(getTestDataPath(), "layouts/dynamic_theming_2.png"), result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });

    configuration.setWallpaperPath(null);
    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();
        ImageDiffUtil.assertImageSimilar(Path.of(getTestDataPath(), "layouts/dynamic_theming_0.png"), result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  public void testMacroTagSupport() {
    @Language("XML") final String content = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                            "    android:layout_height=\"match_parent\"\n" +
                                            "    android:layout_width=\"match_parent\"\n" +
                                            "    android:orientation=\"vertical\"\n" +
                                            "    android:background=\"#FFF\">\n" +
                                            "\n" +
                                            "    <TextView\n" +
                                            "        android:layout_width=\"wrap_content\"\n" +
                                            "        android:layout_height=\"wrap_content\"\n" +
                                            "        android:textSize=\"50sp\"\n" +
                                            "        android:textColor=\"?customColor\"\n" +
                                            "        android:text=\"@macro/macroString\"/>\n" +
                                            "    \n" +
                                            "\n" +
                                            "</LinearLayout>";

    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", content).getVirtualFile();
    myFixture.addFileToProject("res/values/strings.xml",
                               // language=XML
                               "<resources>\n" +
                               "    <string name=\"fooBar\">FOO BAR LOREM IPSUM</string>\n" +
                               "    <macro name=\"macroString\">@string/fooBar</macro>\n" +
                               "    <style name=\"CustomTheme\" parent=\"@android:style/Theme\">\n" +
                               "        <item name=\"customColor\">@macro/macroColor</item>\n" +
                               "    </style>\n" +
                               "    <attr name=\"customColor\" format=\"color\"/>\n" +
                               "    <macro name=\"macroColor\">@color/purple_200</macro>\n" +
                               "    <color name=\"purple_200\">#FFBB86FC</color>" +
                               "</resources>");
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file, DEFAULT_DEVICE_ID, "@style/CustomTheme");
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      task.setDecorations(false);
      try {
        BufferedImage result = task.render().get().getRenderedImage().getCopy();

        BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/layouts/macro.png"));
        assert result != null;
        ImageDiffUtil.assertImageSimilar("macros", goldenImage, result, IMAGE_DIFF_THRESHOLD_PERCENT);
      }
      catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    });
  }

  /**
   * Returns the value of {@code android.content.res.Resources.mSystem} via reflection.
   */
  @Nullable
  private static Object getMSystemValue(@NotNull RenderTask task) {
    try {
      Class<?> resourcesClass = task.getLayoutlibCallback().findClass("android.content.res.Resources");
      Field mSystemField = resourcesClass.getDeclaredField("mSystem");
      mSystemField.setAccessible(true);
      return mSystemField.get(null);
    }
    catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      e.printStackTrace();
    }
    return null;
  }

  public void testRunRenderActionWithSessionHasAccessToResources() {
    @Language("XML") final String content = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                            "    android:layout_height=\"match_parent\"\n" +
                                            "    android:layout_width=\"match_parent\"\n" +
                                            "    android:orientation=\"vertical\"\n" +
                                            "    android:background=\"#FFF\">\n" +
                                            "</LinearLayout>";

    VirtualFile file = myFixture.addFileToProject("res/layout/layout.xml", content).getVirtualFile();
    Configuration configuration = RenderTestUtil.getConfiguration(myModule, file);
    RenderLogger logger = mock(RenderLogger.class);

    RenderTestUtil.withRenderTask(myFacet, file, configuration, logger, task -> {
      // This ensures the render session is initialized.
      task.render().join();

      // Check that runAsyncRenderAction runs without the session.
      task.runAsyncRenderAction(() -> {
        assertNull(getMSystemValue(task));
        return null;
      }).join();

      // When running under a session, mSystem will be initialized.
      task.runAsyncRenderActionWithSession(() -> {
        assertNotNull(getMSystemValue(task));
      }).join();
    });
  }
}
