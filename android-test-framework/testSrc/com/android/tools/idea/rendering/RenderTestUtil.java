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
package com.android.tools.idea.rendering;

import static com.android.tools.idea.rendering.StudioRenderServiceKt.taskBuilder;
import static java.io.File.separatorChar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.ide.common.rendering.api.Result;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.ImageUtils;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.RenderAsyncActionExecutor.RenderingPriority;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ui.UIUtil;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for tests which perform rendering; these methods can generate configurations and perform
 * rendering, then check that the rendered result matches a known thumbnail (by a certain maximum
 * percentage difference). The test will generate the required thumbnail if it does not exist,
 * so to create a new render test just call {@link RenderTestUtil#checkRenderedImage(BufferedImage, String)}
 * and run the test once; then verify that the thumbnail looks fine, and if so, check it in; the test
 * will now check that subsequent renders are similar.
 * <p>
 * The reason the test checks for similarity is that whenever rendering includes fonts, there are some
 * platform differences in text rendering etc which does not give us a pixel for pixel match.
 */
public class RenderTestUtil {
  public static final String DEFAULT_DEVICE_ID = "Nexus 4";
  public static final String HOLO_THEME = "@android:style/Theme.Holo";
  private static final float MAX_PERCENT_DIFFERENT = 1.0f;

  /**
   * Method to be called before every render test case. If you are using JUnit 4, use the {@link RenderTest} instead.
   */
  public static void beforeRenderTestCase() {
    RenderService.shutdownRenderExecutor(5);
    RenderService.initializeRenderExecutor();
  }

  /**
   * Method to be called before every after test case. If you are using JUnit 4, use the {@link RenderTest} instead.
   */
  public static void afterRenderTestCase() {
    RenderLogger.resetFidelityErrorsFilters();
    RenderTestUtil.waitForRenderTaskDisposeToFinish();
  }

  @Nullable
  public static RenderResult renderOnSeparateThread(@NotNull final RenderTask task) {
    // Ensure that we don't render on the read lock (since we want to test that all parts of the
    // rendering system which needs a read lock asks for one!)
    final AtomicReference<RenderResult> holder = new AtomicReference<>();
    Thread thread = new Thread("render test") {
      @Override
      public void run() {
        holder.set(Futures.getUnchecked(task.render()));
      }
    };
    thread.start();
    try {
      thread.join();
    }
    catch (InterruptedException e) {
      fail("Interrupted");
    }

    return holder.get();
  }

  @NotNull
  public static Device findDeviceById(ConfigurationManager manager, String id) {
    for (Device device : manager.getDevices()) {
      if (device.getId().equals(id)) {
        return device;
      }
    }
    fail("Can't find device " + id);
    throw new IllegalStateException();
  }

  /**
   * Waits for any RenderTask dispose threads to finish
   */
  public static void waitForRenderTaskDisposeToFinish() {
    // Make sure there is no RenderTask disposing event in the event queue.
    UIUtil.dispatchAllInvocationEvents();
    Thread.getAllStackTraces().keySet().stream()
      .filter(t -> t.getName().startsWith("RenderTask dispose"))
      .forEach(t -> {
        try {
          t.join(10 * 1000); // 10s
        }
        catch (InterruptedException e) {
          e.printStackTrace();
        }
      });
  }

  @NotNull
  public static Configuration getConfiguration(@NotNull Module module, @NotNull VirtualFile file, @NotNull String deviceId) {
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(module);
    Configuration configuration = configurationManager.getConfiguration(file);
    configuration.setDevice(findDeviceById(configurationManager, deviceId), false);

    return configuration;
  }

  @NotNull
  public static Configuration getConfiguration(@NotNull Module module, @NotNull VirtualFile file) {
    return getConfiguration(module, file, DEFAULT_DEVICE_ID);
  }

  @NotNull
  public static Configuration getConfiguration(@NotNull Module module,
                                           @NotNull VirtualFile file,
                                           @NotNull String deviceId,
                                           @NotNull String themeStyle) {
    Configuration configuration = getConfiguration(module, file, deviceId);
    configuration.setTheme(themeStyle);
    return configuration;
  }

  @NotNull
  protected static RenderTask createRenderTask(@NotNull AndroidFacet facet,
                                             @NotNull VirtualFile file,
                                             @NotNull Configuration configuration,
                                             @NotNull RenderLogger logger,
                                             @NotNull RenderingPriority priority) {
    Module module = facet.getModule();
    PsiFile psiFile = ReadAction.compute(() -> PsiManager.getInstance(module.getProject()).findFile(file));
    assertNotNull(psiFile);
    RenderService renderService = StudioRenderService.getInstance(module.getProject());
    final CompletableFuture<RenderTask> taskFuture = taskBuilder(renderService, facet, configuration, logger)
      .withPsiFile(psiFile)
      .disableSecurityManager()
      .withPriority(priority)
      .build();
    RenderTask task = Futures.getUnchecked(taskFuture);
    assertNotNull(task);
    return task;
  }

  protected static void withRenderTask(@NotNull AndroidFacet facet,
                                       @NotNull VirtualFile file,
                                       @NotNull Configuration configuration,
                                       @NotNull RenderLogger logger,
                                       @NotNull Consumer<RenderTask> f,
                                       boolean layoutScannerEnabled) {
    final RenderTask task = createRenderTask(facet, file, configuration, logger, RenderingPriority.HIGH);
    task.setEnableLayoutScanner(layoutScannerEnabled);
    try {
      f.accept(task);
    } finally {
      try {
        if (!task.isDisposed()) {
          task.dispose().get(5, TimeUnit.SECONDS);
        }
      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  protected static void withRenderTask(@NotNull AndroidFacet facet,
                                        @NotNull VirtualFile file,
                                        @NotNull Configuration configuration,
                                        @NotNull RenderLogger logger,
                                        @NotNull Consumer<RenderTask> f) {
    withRenderTask(facet, file, configuration, logger, f, false);
  }

  /**
   * @deprecated use {@link withRenderTask} instead
   */
  @Deprecated
  @NotNull
  public static RenderTask createRenderTask(@NotNull AndroidFacet facet,
                                    @NotNull VirtualFile file,
                                    @NotNull Configuration configuration) {
    RenderService renderService = StudioRenderService.getInstance(facet.getModule().getProject());
    return createRenderTask(facet, file, configuration, renderService.createLogger(facet.getModule()), RenderingPriority.HIGH);
  }

  public static void withRenderTask(@NotNull AndroidFacet facet,
                                     @NotNull VirtualFile file,
                                     @NotNull Configuration configuration,
                                     @NotNull Consumer<RenderTask> f) {
    RenderService renderService = StudioRenderService.getInstance(facet.getModule().getProject());
    withRenderTask(facet, file, configuration, renderService.createLogger(facet.getModule()), f);
  }

  public static void withRenderTask(@NotNull AndroidFacet facet,
                                    @NotNull VirtualFile file,
                                    @NotNull Configuration configuration,
                                    boolean enableLayoutScanner,
                                    @NotNull Consumer<RenderTask> f) {
    RenderService renderService = StudioRenderService.getInstance(facet.getModule().getProject());
    withRenderTask(facet, file, configuration, renderService.createLogger(facet.getModule()), f, enableLayoutScanner);
  }

  public static void withRenderTask(@NotNull AndroidFacet facet,
                                     @NotNull VirtualFile file,
                                     @NotNull String theme,
                                     @NotNull Consumer<RenderTask> f) {
    Configuration configuration = getConfiguration(facet.getModule(), file, DEFAULT_DEVICE_ID, theme);
    withRenderTask(facet, file, configuration, f);
  }

  public static void checkRendering(@NotNull AndroidFacet androidFacet,
                                    @NotNull VirtualFile layout,
                                    @NotNull String goldenImagePath) {
    withRenderTask(androidFacet, layout, getConfiguration(androidFacet.getModule(), layout),
                   task -> checkRendering(task, goldenImagePath));
  }

  public static void checkRendering(@NotNull RenderTask task, @NotNull String thumbnailPath) {
    BufferedImage image = getImage(task);
    checkRenderedImage(image, thumbnailPath.replace('/', separatorChar));
  }

  public static void scaleAndCheckRendering(@NotNull RenderTask task, @NotNull String thumbnailPath) {
    BufferedImage image = getImage(task);
    double scale = Math.min(1, Math.min(200 / ((double)image.getWidth()), 200 / ((double)image.getHeight())));
    image = ImageUtils.scale(image, scale, scale);

    checkRenderedImage(image, thumbnailPath.replace('/', separatorChar));
  }

  @NotNull
  private static BufferedImage getImage(@NotNull RenderTask task) {
    // Next try a render
    RenderResult result = Futures.getUnchecked(task.render());
    RenderResult render = renderOnSeparateThread(task);
    assertNotNull(render);

    assertNotNull(result);
    Result renderResult = result.getRenderResult();
    assertEquals(String.format("Render failed with message: %s\n%s", renderResult.getErrorMessage(), renderResult.getException()),
                 Result.Status.SUCCESS, result.getRenderResult().getStatus());
    BufferedImage image = result.getRenderedImage().getCopy();
    assertNotNull(image);
    return image;
  }

  private static void checkRenderedImage(@NotNull BufferedImage image, @NotNull String fullPath) {
    fullPath = fullPath.replace('/', separatorChar);

    File fromFile = new File(fullPath);
    System.out.println("fromFile=" + fromFile);

    try {
      if (fromFile.exists()) {
        BufferedImage goldenImage = ImageIO.read(fromFile);
        ImageDiffUtil.assertImageSimilar(fullPath, goldenImage, image, MAX_PERCENT_DIFFERENT);
      }
      else {
        File dir = fromFile.getParentFile();
        assertNotNull(dir);
        if (!dir.exists()) {
          boolean ok = dir.mkdirs();
          assertTrue(dir.getPath(), ok);
        }
        ImageIO.write(image, "PNG", fromFile);
        fail("File did not exist, created " + fromFile);
      }
    } catch (IOException ex) {
      throw new UncheckedIOException(ex);
    }
  }
}
