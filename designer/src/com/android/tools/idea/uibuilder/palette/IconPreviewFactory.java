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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.io.CharStreams;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;

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
  private static final String PREVIEW_PLACEHOLDER_FILE = "preview.xml";
  private static final String[] PREVIEW_FILES = {"preview1.xml", "preview2.xml", "preview3.xml", "preview4.xml"};
  private static final String LINEAR_LAYOUT = "<LinearLayout\n" +
                                              "    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                                              "    android:layout_width=\"wrap_content\"\n" +
                                              "    android:layout_height=\"wrap_content\"\n" +
                                              "    android:orientation=\"vertical\">\n" +
                                              "  %1$s\n" +
                                              "</LinearLayout>\n";

  private static IconPreviewFactory ourInstance;

  @NonNull
  public static IconPreviewFactory get() {
    if (ourInstance == null) {
      ourInstance = new IconPreviewFactory();
    }
    return ourInstance;
  }

  private IconPreviewFactory() {
  }

  @Nullable
  public BufferedImage getImage(@NonNull NlPaletteItem item, @NonNull Configuration configuration, double scale) {
    BufferedImage image = readImage(item.getId(), configuration);
    if (image == null) {
      return null;
    }
    return ImageUtils.scale(image, scale, scale);
  }

  /**
   * Return a component image to display while dragging a component from the palette.
   * Return null if such an image cannot be rendered. The palette must provide a fallback in this case.
   */
  @Nullable
  public BufferedImage renderDragImage(@NonNull NlPaletteItem item, @NonNull ScreenView screenView, double scale) {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(screenView.getModel().getProject());
    String xml = item.getRepresentation();
    if (xml.isEmpty()) {
      return null;
    }
    xml = addAndroidNamespaceIfMissing(item.getRepresentation());
    XmlTag tag = null;
    try {
      tag = elementFactory.createTagFromText(xml);
    } catch (IncorrectOperationException ignore) {
    }
    if (tag == null) {
      return null;
    }
    NlModel model = screenView.getModel();
    NlComponent component = model.createComponent(screenView, tag, null, null, InsertType.CREATE_PREVIEW);
    if (component == null) {
      return null;
    }

    // Some components require a parent to render correctly.
    xml = String.format(LINEAR_LAYOUT, component.getTag().getText());
    RenderResult result = renderImage(xml, model.getConfiguration());
    if (result == null) {
      return null;
    }
    BufferedImage image = result.getRenderedImage();
    if (image == null) {
      return null;
    }
    List<ViewInfo> infos = result.getRootViews();
    if (infos == null || infos.isEmpty()) {
      return null;
    }
    infos = infos.get(0).getChildren();
    if (infos == null || infos.isEmpty()) {
      return null;
    }
    ViewInfo view = infos.get(0);
    if (image.getHeight() < view.getBottom() || image.getWidth() < view.getRight() ||
      view.getBottom() <= view.getTop() || view.getRight() <= view.getLeft()) {
      return null;
    }
    image = image.getSubimage(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    return ImageUtils.scale(image, scale, scale);
  }

  private static String addAndroidNamespaceIfMissing(@NonNull String xml) {
    // TODO: Remove this temporary hack, which adds an Android namespace if necessary
    // (this is such that the resulting tag is namespace aware, and attempts to manipulate it from
    // a component handler will correctly set namespace prefixes)

    if (!xml.contains(ANDROID_URI)) {
      int index = xml.indexOf('<');
      if (index != -1) {
        index = xml.indexOf(' ', index);
        if (index == -1) {
          index = xml.indexOf("/>");
          if (index == -1) {
            index = xml.indexOf('>');
          }
        }
        if (index != -1) {
          xml =
            xml.substring(0, index) + " xmlns:android=\"http://schemas.android.com/apk/res/android\"" + xml.substring(index);
        }
      }
    }
    return xml;
  }

  private static BufferedImage readImage(@NonNull String id, @NonNull Configuration configuration) {
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

  public void load(@NonNull final Configuration configuration, @NonNull final Runnable callback) {
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
      for (String previewFileName : PREVIEW_FILES) {
        String preview = loadPreviews(previewFileName);
        assert preview != null;
        RenderResult result = renderImage(preview, configuration);
        addResultToCache(result, configuration);
      }
      callback.run();
      return null;
      }
    });
  }

  @Nullable
  private String loadPreviews(@NonNull String previewFile) {
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

  @NonNull
  private static File getPreviewCacheDir(@NonNull Configuration configuration) {
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

  @NonNull
  private static String getTheme(@NonNull Configuration configuration) {
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

  private static int getApiVersion(@NonNull Configuration configuration) {
    IAndroidTarget target = configuration.getTarget();
    return target == null ? SdkVersionInfo.HIGHEST_KNOWN_STABLE_API : target.getVersion().getApiLevel();
  }

  private static void addResultToCache(@Nullable RenderResult result, @NonNull Configuration configuration) {
    if (result == null || result.getRenderedImage() == null || result.getRootViews() == null || result.getRootViews().isEmpty()) {
      return;
    }
    ImageAccumulator accumulator = new ImageAccumulator(result.getRenderedImage(), configuration);
    accumulator.run(result.getRootViews(), 0);
  }

  @Nullable
  private static RenderResult renderImage(@NonNull String xml, @NonNull Configuration configuration) {
    AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
    if (facet == null) {
      return null;
    }
    Project project = configuration.getModule().getProject();
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml);
    RenderService renderService = RenderService.get(facet);
    RenderLogger logger = renderService.createLogger();
    final RenderTask task = renderService.createTask(file, configuration, logger, null);
    RenderResult result = null;
    if (task != null) {
      task.setOverrideBgColor(UIUtil.TRANSPARENT_COLOR.getRGB());
      task.setDecorations(false);
      task.setRenderingMode(SessionParams.RenderingMode.FULL_EXPAND);
      task.setFolderType(ResourceFolderType.LAYOUT);
      result = task.render();
      task.dispose();
    }
    return result;
  }

  private static class ImageAccumulator {
    private final BufferedImage myImage;
    private final File myCacheDir;
    private final int myHeight;
    private final int myWidth;

    private ImageAccumulator(@NonNull BufferedImage image, @NonNull Configuration configuration) {
      myImage = image;
      myCacheDir = getPreviewCacheDir(configuration);
      myHeight = image.getRaster().getHeight();
      myWidth = image.getRaster().getWidth();
    }

    private void run(@NonNull List<ViewInfo> views, int top) {
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
              LOG.warn(String.format("Dimensions of %1$s is out of range", id));
            }
          }
        }
        if (!info.getChildren().isEmpty()) {
          run(info.getChildren(), top + info.getTop());
        }
      }
    }

    private void saveImage(@NonNull String id, @NonNull BufferedImage image) {
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
