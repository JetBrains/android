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

import com.android.annotations.NonNull;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.resources.ResourceUrl;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.DOT_XML;

/**
 * Static utilities for generating scaled-down {@link Icon} instances from image resources to display in the gutter.
 */
public class GutterIconFactory {
  private static final Logger LOG = Logger.getInstance(GutterIconCache.class);

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
  public static Icon createIcon(@NotNull String path, @Nullable ResourceResolver resolver, int maxWidth, int maxHeight) {
    if (path.endsWith(DOT_XML)) {
      return createXmlIcon(path, resolver, maxWidth);
    }
    else {
      return createBitmapIcon(path, maxWidth, maxHeight);
    }
  }

  /**
   * Read XML data from Document when possible (in case there are unsaved changes
   * for a file open in an editor).
   */
  private static String getXmlContent(@NotNull String path) throws IOException {
    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);

    if (file == null) {
      return Files.toString(new File(path), Charsets.UTF_8);
    }

    com.intellij.openapi.editor.Document document =
      FileDocumentManager.getInstance().getCachedDocument(file);

    if  (document == null) {
      return new String(file.contentsToByteArray());
    }

    return document.getText();
  }


  @Nullable
  private static Icon createXmlIcon(@NotNull String path, @Nullable ResourceResolver resolver, int maxWidth) {
    try {
      VdPreview.TargetSize imageTargetSize =
        VdPreview.TargetSize.createSizeFromWidth(isRetinaEnabled() ? ImageUtils.RETINA_SCALE * maxWidth : maxWidth);

      String xml = getXmlContent(path);
      // See if this drawable is a vector; we can't render other drawables yet.
      // TODO: Consider resolving selectors to render for example the default image!
      if (xml.contains("<vector")) {
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
        BufferedImage image = VdPreview.getPreviewFromVectorDocument(imageTargetSize, document, builder);
        if (builder.length() > 0) {
          LOG.warn("Problems rendering " + path + ": " + builder);
        }
        if (image != null) {
          if (isRetinaEnabled()) {
            RetinaImageIcon retinaIcon = getRetinaIcon(image);
            if (retinaIcon != null) {
              return retinaIcon;
            }
          }
          return new ImageIcon(image);
        }
      }
    }
    catch (Throwable e) {
      LOG.warn(String.format("Could not read/render icon image %1$s", path), e);
    }

    return null;
  }

  private static boolean isRetinaEnabled() {
    return UIUtil.isRetina();
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
  static void replaceResourceReferences(@NonNull Node node, @NonNull ResourceResolver resolver) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      Element element = (Element)node;
      NamedNodeMap attributes = element.getAttributes();

      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        Node attribute = attributes.item(i);
        String value = attribute.getNodeValue();

        if (isReference(value)) {
          String resolvedValue = ResourceHelper.resolveStringValue(resolver, value);

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
  private static Icon createBitmapIcon(@NotNull String path, int maxWidth, int maxHeight) {
    try {
      Icon icon = createBitmapIcon(ImageIO.read(new File(path)), maxWidth, maxHeight);
      if (icon != null) return icon;
    }
    catch (Throwable e) {
      // Not just IOExceptions here; for example, we've seen
      // IllegalArgumentEx @ ...... < PNGImageReader:1479 < ... ImageIO.read
      LOG.warn(String.format("Could not read icon image %1$s", path), e);
    }

    return null;
  }

  @Nullable
  private static Icon createBitmapIcon(BufferedImage image, int maxWidth, int maxHeight) {
    if (image != null) {
      int imageWidth = image.getWidth();
      int imageHeight = image.getHeight();
      if (isRetinaEnabled() && (imageWidth > ImageUtils.RETINA_SCALE * maxWidth || imageHeight > ImageUtils.RETINA_SCALE * maxHeight)) {
        double scale = ImageUtils.RETINA_SCALE * Math.min(maxWidth / (double)imageWidth, maxHeight / (double)imageHeight);
        BufferedImage scaled = ImageUtils.scale(image, scale, scale);
        RetinaImageIcon retinaIcon = getRetinaIcon(scaled);
        if (retinaIcon != null) {
          return retinaIcon;
        }
      }

      if (imageWidth > maxWidth || imageHeight > maxHeight) {
        double scale = Math.min(maxWidth / (double)imageWidth, maxHeight / (double)imageHeight);

        if (image.getType() == BufferedImage.TYPE_BYTE_INDEXED) {
          // Indexed images look terrible if they are scaled directly; instead, paint into an ARGB blank image
          BufferedImage bg = UIUtil.createImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
          Graphics g = bg.getGraphics();
          //noinspection UseJBColor
          g.setColor(Gray.TRANSPARENT);
          g.fillRect(0, 0, bg.getWidth(), bg.getHeight());
          //noinspection ConstantConditions
          UIUtil.drawImage(g, image, 0, 0, null);
          g.dispose();
          image = bg;
        }

        image = ImageUtils.scale(image, scale, scale);
      }

      return new ImageIcon(image);
    }
    return null;
  }

  /**
   * Returns a {@link RetinaImageIcon} for the given {@link BufferedImage}, if possible. Returns null otherwise.
   */
  @Nullable
  private static RetinaImageIcon getRetinaIcon(@NonNull BufferedImage image) {
    if (isRetinaEnabled()) {
      Image hdpiImage = ImageUtils.convertToRetina(image);
      if (hdpiImage != null) {
        return new RetinaImageIcon(hdpiImage);
      }
    }
    return null;
  }

  private static class RetinaImageIcon extends ImageIcon {
    private RetinaImageIcon(Image image) {
      super(image, "");
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
      UIUtil.drawImage(g, getImage(), x, y, null);
    }
  }
}
