/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.ide.common.rendering.api.Result;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static java.io.File.separator;
import static java.io.File.separatorChar;

/**
 * Base for unit tests which perform rendering; this test can generate configurations and perform
 * rendering, then check that the rendered result matches a known thumbnail (by a certain maximum
 * percentage difference). The test will generate the required thumbnail if it does not exist,
 * so to create a new render test just call {@link #checkRenderedImage(java.awt.image.BufferedImage, String)}
 * and run the test once; then verify that the thumbnail looks fine, and if so, check it in; the test
 * will now check that subsequent renders are similar.
 * <p>
 * The reason the test checks for similarity is that whenever rendering includes fonts, there are some
 * platform differences in text rendering etc which does not give us a pixel for pixel match.
 */
public abstract class RenderTestBase extends AndroidTestCase {
  protected static final String DEFAULT_DEVICE_ID = "Nexus 4";
  private static final String DEFAULT_THEME_STYLE = "@android:style/Theme.Holo";
  private static final float MAX_PERCENT_DIFFERENT = 5.0f;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    RenderService.shutdownRenderExecutor(5);
    RenderService.initializeRenderExecutor();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      RenderLogger.resetFidelityErrorsFilters();
      waitForRenderTaskDisposeToFinish();
    } finally {
      super.tearDown();
    }
  }

  protected RenderTask createRenderTask(VirtualFile file) throws Exception {
    Configuration configuration = getConfiguration(file, DEFAULT_DEVICE_ID, DEFAULT_THEME_STYLE);
    return createRenderTask(file, configuration);
  }

  protected Configuration getConfiguration(VirtualFile file, String deviceId) {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    assertNotNull(facet);
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    assertNotNull(configurationManager);
    Configuration configuration = configurationManager.getConfiguration(file);
    configuration.setDevice(findDeviceById(configurationManager, deviceId), false);

    return configuration;
  }

  protected Configuration getConfiguration(VirtualFile file, String deviceId, String themeStyle) {
    Configuration configuration = getConfiguration(file, deviceId);
    configuration.setTheme(themeStyle);
    return configuration;
  }

  protected RenderTask createRenderTask(VirtualFile file, Configuration configuration, RenderLogger logger) throws IOException {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);
    assertNotNull(facet);
    RenderService renderService = RenderService.getInstance(facet);
    final RenderTask task = renderService.createTask(psiFile, configuration, logger, null);
    assertNotNull(task);
    task.disableSecurityManager();
    return task;
  }

  protected RenderTask createRenderTask(VirtualFile file, Configuration configuration) throws IOException {
    AndroidFacet facet = AndroidFacet.getInstance(myModule);
    return createRenderTask(file, configuration, RenderService.getInstance(facet).createLogger());
  }

    protected void checkRendering(RenderTask task, String thumbnailPath) throws IOException {
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
    double scale = Math.min(1, Math.min(200 / ((double)image.getWidth()), 200 / ((double)image.getHeight())));
    image = ImageUtils.scale(image, scale, scale);
    image = ShadowPainter.createRectangularDropShadow(image);
    checkRenderedImage(image, "render" + separator + "thumbnails" + separator + thumbnailPath.replace('/', separatorChar));
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
  protected static Device findDeviceById(ConfigurationManager manager, String id) {
    for (Device device : manager.getDevices()) {
      if (device.getId().equals(id)) {
        return device;
      }
    }
    fail("Can't find device " + id);
    throw new IllegalStateException();
  }

  protected void checkRenderedImage(BufferedImage image, String relativePath) throws IOException {
    relativePath = relativePath.replace('/', separatorChar);

    final String testDataPath = getTestDataPath();
    assert testDataPath != null : "test data path not specified";

    File fromFile = new File(testDataPath + "/" + relativePath);
    System.out.println("fromFile=" + fromFile);

    if (fromFile.exists()) {
      BufferedImage goldenImage = ImageIO.read(fromFile);
      ImageDiffUtil.assertImageSimilar(relativePath, goldenImage, image, MAX_PERCENT_DIFFERENT);
    } else {
      File dir = fromFile.getParentFile();
      assertNotNull(dir);
      if (!dir.exists()) {
        boolean ok = dir.mkdirs();
        assertTrue(dir.getPath(), ok);
      }
      ImageIO.write(image, "PNG", fromFile);
      fail("File did not exist, created " + fromFile);
    }
  }

  /**
   * Waits for any RenderTask dispose threads to finish
   */
  public static void waitForRenderTaskDisposeToFinish() {
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
}
