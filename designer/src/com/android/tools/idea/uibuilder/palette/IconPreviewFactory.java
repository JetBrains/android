/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.google.common.io.CharStreams;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * IconPreviewFactory generates a preview of certain palette components.
 * The images are rendered from preview.xml and are used as an alternate representation on
 * the palette i.e. a button is rendered as the SDK button would look like on the target device.
 */
public class IconPreviewFactory {
  private static final Logger LOG = Logger.getInstance(IconPreviewFactory.class);
  private static final String DEFAULT_THEME = "AppTheme";
  private static final String[] PREVIEW_FILES = {"preview1.xml", "preview2.xml", "preview3.xml"};

  private static IconPreviewFactory ourInstance;

  @NotNull
  public static IconPreviewFactory get() {
    if (ourInstance == null) {
      ourInstance = new IconPreviewFactory();
    }
    return ourInstance;
  }

  private IconPreviewFactory() {
  }

  @Nullable
  public Image getImage(@NotNull NlPaletteItem item, @NotNull Configuration configuration, double scale) {
    BufferedImage image = readImage(item.getId(), configuration);
    if (image == null) {
      return null;
    }
    return ImageUtils.scale(image, scale, scale);
  }

  private static BufferedImage readImage(@NotNull String id, @NotNull Configuration configuration) {
    File file = new File(getPreviewCacheDir(configuration), id + DOT_PNG);
    if (!file.exists()) {
      return null;
    }
    try {
      return ImageIO.read(file);
    }
    catch (IOException ignore) {
    }
    catch (Throwable ignore) {
      // corrupt cached image, e.g. I've seen
      //  java.lang.IndexOutOfBoundsException
      //  at java.io.RandomAccessFile.readBytes(Native Method)
      //  at java.io.RandomAccessFile.read(RandomAccessFile.java:338)
      //  at javax.imageio.stream.FileImageInputStream.read(FileImageInputStream.java:101)
      //  at com.sun.imageio.plugins.common.SubImageInputStream.read(SubImageInputStream.java:46)
    }
    return null;
  }

  public void load(@NotNull final Configuration configuration, @NotNull final Runnable callback) {
    File cacheDir = getPreviewCacheDir(configuration);
    String[] files = cacheDir.list();
    if (files != null && files.length > 0) {
      // The previews have already been generated.
      callback.run();
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Computable<Void>() {
      @Override
      public Void compute() {
        AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
        if (facet == null) {
          return null;
        }
        Project project = configuration.getModule().getProject();
        for (String previewFileName : PREVIEW_FILES) {
          String preview = loadPreviews(previewFileName);
          if (preview != null) {
            PsiFile file = PsiFileFactory.getInstance(project).createFileFromText("preview.xml", XmlFileType.INSTANCE, preview);
            RenderService renderService = RenderService.get(facet);
            RenderLogger logger = renderService.createLogger();
            Color bg = UIUtil.getTreeBackground();
            //noinspection UseJBColor
            Color color = new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 0);
            final RenderTask task = renderService.createTask(file, configuration, logger, null);
            if (task != null) {
              task.setOverrideBgColor(color.getRGB());
              task.setDecorations(false);
              task.setRenderingMode(SessionParams.RenderingMode.FULL_EXPAND);
              task.setFolderType(ResourceFolderType.LAYOUT);
              RenderResult result = task.render();
              addResult(result, configuration);
              task.dispose();
            }
          }
        }
        callback.run();
        return null;
      }
    });
  }

  @Nullable
  private String loadPreviews(@NotNull String previewFile) {
    try {
      InputStream stream = getClass().getResourceAsStream(previewFile);
      try {
        return CharStreams.toString(new InputStreamReader(stream));
      }
      finally {
        stream.close();
      }
    }
    catch (IOException ex) {
      LOG.error(ex);
      return null;
    }
  }

  @NotNull
  private static File getPreviewCacheDir(@NotNull Configuration configuration) {
    final String path = PathUtil.getCanonicalPath(PathManager.getSystemPath());
    int density = configuration.getDensity().getDpiValue();
    String theme = getTheme(configuration);
    int apiVersion = getApiVersion(configuration);
    //noinspection StringBufferReplaceableByString
    String cacheFolder = new StringBuilder()
      .append(path).append(File.separator)
      .append("android-palette").append(File.separator)
      .append("v1").append(File.separator)
      .append(theme).append(File.separator)
      .append(density).append("-").append(apiVersion)
      .toString();
    return new File(cacheFolder);
  }

  @NotNull
  private static String getTheme(@NotNull Configuration configuration) {
    String theme = configuration.getTheme();
    if (theme == null) {
      theme = DEFAULT_THEME;
    } else if (theme.startsWith(STYLE_RESOURCE_PREFIX)) {
      theme = theme.substring(STYLE_RESOURCE_PREFIX.length());
    } else if (theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX)) {
      theme = theme.substring(ANDROID_STYLE_RESOURCE_PREFIX.length());
    }
    return theme;
  }

  private static int getApiVersion(@NotNull Configuration configuration) {
    IAndroidTarget target = configuration.getTarget();
    return target == null ? SdkVersionInfo.HIGHEST_KNOWN_STABLE_API : target.getVersion().getApiLevel();
  }

  private static void addResult(@Nullable RenderResult result, @NotNull Configuration configuration) {
    if (result == null || result.getRenderedImage() == null || result.getRootViews() == null || result.getRootViews().isEmpty()) {
      return;
    }
    ImageAccumulator accumulator = new ImageAccumulator(result.getRenderedImage(), configuration);
    accumulator.run(result.getRootViews(), 0);
  }

  private static class ImageAccumulator {
    private final BufferedImage myImage;
    private final File myCacheDir;
    private final int myHeight;
    private final int myWidth;

    private ImageAccumulator(@NotNull BufferedImage image, @NotNull Configuration configuration) {
      myImage = image;
      myCacheDir = getPreviewCacheDir(configuration);
      myHeight = image.getRaster().getHeight();
      myWidth = image.getRaster().getWidth();
    }

    private void run(@NotNull List<ViewInfo> views, int top) {
      for (ViewInfo info : views) {
        if (info.getCookie() instanceof XmlTag) {
          XmlTag tag = (XmlTag)info.getCookie();
          String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
          if (id != null && !id.startsWith(PREFIX_RESOURCE_REF)) {
            if (info.getBottom() + top <= myHeight && info.getRight() <= myWidth && info.getBottom() > info.getTop()) {
              Rectangle bounds =
                new Rectangle(info.getLeft(), info.getTop() + top, info.getRight() - info.getLeft(), info.getBottom() - info.getTop());
              BufferedImage image = myImage.getSubimage(bounds.x, bounds.y, bounds.width, bounds.height);
              saveImage(id, image);
            }
            else {
              LOG.warn(String.format("Dimensions of %$1s is out of range", id));
            }
          }
        }
        if (!info.getChildren().isEmpty()) {
          run(info.getChildren(), top + info.getTop());
        }
      }
    }

    private void saveImage(@NotNull String id, @NotNull BufferedImage image) {
      //noinspection ResultOfMethodCallIgnored
      myCacheDir.mkdirs();
      File file = new File(myCacheDir, id + DOT_PNG);
      try {
        ImageIO.write(image, "PNG", file);
      }
      catch (IOException e) {
        // pass
        if (file.exists()) {
          //noinspection ResultOfMethodCallIgnored
          file.delete();
        }
      }
    }
  }
}
