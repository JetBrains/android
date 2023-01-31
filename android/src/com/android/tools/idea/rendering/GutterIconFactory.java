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

import static com.android.SdkConstants.DOT_XML;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 * Static utilities for generating scaled-down {@link Icon} instances from image resources to display in the gutter.
 */
public class GutterIconFactory {
  private static final Logger LOG = Logger.getInstance(GutterIconCache.class);
  private static final int RENDERING_SCALING_FACTOR = 10;

  /**
   * Given the path to an image resource, returns an Icon which displays the image, scaled so that its width
   * and height do not exceed {@code maxWidth} and {@code maxHeight} pixels, respectively. Returns null if unable to read
   * or render the image resource for any reason.
   * <p>
   * For an XML resource, {@code resolver} is used to resolve resource and theme references (e.g. {@code @string/foo} , {@code
   * ?android:attr/bar}). If {@code resolver} is null, then it is assumed that the image resource is either not an XML resource, or
   * that the XML file does not contain any unresolved references (otherwise, this method returns null).
   */
  @Nullable
  public static Icon createIcon(@NotNull VirtualFile file, @Nullable RenderResources resolver, int maxWidth, int maxHeight, @NotNull AndroidFacet facet) {
    String path = file.getPath();
    if (path.endsWith(DOT_XML)) {
      return createXmlIcon(file, resolver, maxWidth, maxHeight, facet);
    }

    return createBitmapIcon(file, maxWidth, maxHeight);
  }

  /**
   * Read XML data from Document when possible (in case there are unsaved changes
   * for a file open in an editor).
   */
  @NotNull
  private static String getXmlContent(@NotNull VirtualFile file) throws IOException {
    com.intellij.openapi.editor.Document document = FileDocumentManager.getInstance().getCachedDocument(file);

    if  (document == null) {
      return new String(file.contentsToByteArray());
    }

    return document.getText();
  }

  @Nullable
  private static Icon createXmlIcon(@NotNull VirtualFile file, @Nullable RenderResources resolver, int maxWidth, int maxHeight,
                                    @NotNull AndroidFacet facet) {
    try {
      String xml = getXmlContent(file);
      Image image;
      // If drawable is a vector drawable, use the renderer inside Studio.
      // Otherwise, delegate to layoutlib.
      if (xml.contains("<vector")) {
        VdPreview.TargetSize imageTargetSize =
          VdPreview.TargetSize.createFromMaxDimension((int)JBUI.pixScale(maxWidth));
        Document document = XmlUtils.parseDocumentSilently(xml, true);
        if (document == null) {
          return null;
        }
        Element root = document.getDocumentElement();
        if (root == null) {
          return null;
        }
        if (resolver != null) {
          replaceResourceReferences(root, resolver);
        }
        StringBuilder builder = new StringBuilder(100);
        image = VdPreview.getPreviewFromVectorDocument(imageTargetSize, document, builder);
        image = ImageUtil.ensureHiDPI(image, ScaleContext.create());
        if (builder.length() > 0) {
          LOG.warn("Problems rendering " + file.getPresentableUrl() + ": " + builder);
        }
      }
      else {
        Configuration configuration = ConfigurationManager.getOrCreateInstance(facet.getModule()).getConfiguration(file);
        DrawableRenderer renderer = new DrawableRenderer(facet, configuration);
        Dimension size = new Dimension(maxWidth * RENDERING_SCALING_FACTOR, maxHeight * RENDERING_SCALING_FACTOR);
        try {
          CompletableFuture<BufferedImage> imageFuture = renderer.renderDrawable(xml, size);
          // TODO(http://b/143455172): Remove the timeout by removing usages of this method on the UI thread. For now we just ensure
          //  we do not block indefinitely on the UI thread. We also do not use the timeout in unit test to avoid non deterministic tests.
          //  On production, if the request times out, it will cause the icon on the gutter not to show which is an acceptable fallback
          //  until this is correctly fixed.
          //  250ms should be enough time for inflating and rendering and is used a upper boundary.
          image = ApplicationManager.getApplication().isDispatchThread() && !ApplicationManager.getApplication().isUnitTestMode() ?
                  imageFuture.get(250, TimeUnit.MILLISECONDS) :
                  imageFuture.get();
        } catch (Throwable e) {
          // If an invalid drawable is passed, renderDrawable might throw an exception. We can not fully control the input passed to this
          // rendering call since the user might be referencing an invalid drawable so we are just less verbose about it. The user will
          // not see the preview next to the code when referencing invalid drawables.
          LOG.debug(String.format("Could not read/render icon image %1$s", file.getPresentableUrl()), e);
          image = null;
        } finally {
          Disposer.dispose(renderer);
        }
        if (image == null) {
          return null;
        }
        image = ImageUtil.ensureHiDPI(image, ScaleContext.create());
        image = ImageUtil.scaleImage(image, maxWidth, maxHeight);
      }

      return IconUtil.createImageIcon(image);
    }
    catch (Throwable e) {
      LOG.warn(String.format("Could not read/render icon image %1$s", file.getPresentableUrl()), e);
    }

    return null;
  }

  /**
   * Returns true if {@code attributeValue} is a theme or resource reference, false otherwise.
   */
  @VisibleForTesting
  static boolean isReference(String attributeValue) {
    return ResourceUrl.parse(attributeValue) != null;
  }

  /**
   * Recursively traverses a document tree starting at {@code node} and uses {@code resolver} to
   * to resolve and replace attribute values which are resource or theme references. If a reference can not
   * be resolved, the value of that attribute remains unchanged.
   */
  @VisibleForTesting
  static void replaceResourceReferences(@NotNull Node node, @NotNull RenderResources resolver) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      Element element = (Element)node;
      NamedNodeMap attributes = element.getAttributes();

      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        Node attribute = attributes.item(i);
        String value = attribute.getNodeValue();

        if (isReference(value)) {
          String resolvedValue = IdeResourcesUtil.resolveStringValue(resolver, value);

          // Leave the attribute value alone if we were unable to resolve it
          if (!isReference(resolvedValue)) {
            attribute.setNodeValue(resolvedValue);
          }
        }
      }
    }

    node = node.getFirstChild();
    while (node != null) {
      replaceResourceReferences(node, resolver);
      node = node.getNextSibling();
    }
  }

  @Nullable
  private static Icon createBitmapIcon(@NotNull VirtualFile file, int maxWidth, int maxHeight) {
    try (InputStream stream = file.getInputStream()) {
      return createBitmapIcon(ImageIO.read(stream), maxWidth, maxHeight);
    }
    catch (Exception e) {
      // Not just IOExceptions here; for example, we've seen
      // IllegalArgumentException @ ...... < PNGImageReader:1479 < ... ImageIO.read
      LOG.warn(String.format("Could not read icon image %1$s", file.getPresentableUrl()), e);
      return null;
    }
  }

  @Nullable
  private static Icon createBitmapIcon(BufferedImage bufferedImage, int maxWidth, int maxHeight) {
    if (bufferedImage != null) {
      Image image = ImageUtil.ensureHiDPI(bufferedImage, ScaleContext.create());
      int imageWidth = image.getWidth(null);
      int imageHeight = image.getHeight(null);

      if (imageWidth > maxWidth || imageHeight > maxHeight) {
        double scale = Math.min(maxWidth / (double)imageWidth, maxHeight / (double)imageHeight);

        if (bufferedImage.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
          // Indexed images look terrible if they are scaled directly; instead, paint into an ARGB blank image
          BufferedImage bg = ImageUtil.createImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
          Graphics g = bg.getGraphics();
          g.setColor(Gray.TRANSPARENT);
          g.fillRect(0, 0, bg.getWidth(), bg.getHeight());
          UIUtil.drawImage(g, image, 0, 0, null);
          g.dispose();
          image = bg;
        }

        image = ImageUtil.scaleImage(image, scale);
      } else {
        // If the image is smaller than the max size, simply use it as is instead of scaling down and then up.
        image = bufferedImage;
      }

      return IconUtil.createImageIcon(image);
    }
    return null;
  }
}
