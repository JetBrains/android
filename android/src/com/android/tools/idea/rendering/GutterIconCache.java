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

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.vectordrawable.VdPreview;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.Gray;
import com.intellij.util.RetinaImage;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
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
import java.util.Map;

import static com.android.SdkConstants.*;

public class GutterIconCache {
  private static final Logger LOG = Logger.getInstance(GutterIconCache.class);
  private static final int MAX_WIDTH = JBUI.scale(16);
  private static final int MAX_HEIGHT = JBUI.scale(16);
  private static final Icon NONE = AndroidIcons.Android; // placeholder

  private static final GutterIconCache ourInstance = new GutterIconCache();
  // TODO: Timestamps?
  private Map<String,Icon> myThumbnailCache = Maps.newHashMap();
  private boolean myRetina;
  private static boolean ourRetinaEnabled = true;

  public GutterIconCache() {
  }

  @NotNull
  public static GutterIconCache getInstance() {
    return ourInstance;
  }

  @Nullable
  public Icon getIcon(@NotNull String path, @Nullable ResourceResolver resolver) {
    boolean isRetina = UIUtil.isRetina();
    if (myRetina != isRetina) {
      myRetina = isRetina;
      myThumbnailCache.clear();
    }
    Icon myIcon = myThumbnailCache.get(path);
    if (myIcon == null) {
      myIcon = createIcon(path, resolver);

      if (myIcon == null) {
        myIcon = NONE;
      }

      myThumbnailCache.put(path, myIcon);
    }

    return myIcon != NONE ? myIcon : null;
  }

  // TODO: Make method which passes in the image here!
  @Nullable
  private static Icon createIcon(@NotNull String path, @Nullable ResourceResolver resolver) {
    if (path.endsWith(DOT_XML)) {
      return createXmlIcon(path, resolver);
    } else {
      return createBitmapIcon(path);
    }
  }

  @Nullable
  private static Icon createXmlIcon(@NotNull String path, @Nullable ResourceResolver resolver) {
    try {
      boolean isRetina = ourRetinaEnabled && UIUtil.isRetina();
      VdPreview.TargetSize imageTargetSize = VdPreview.TargetSize.createSizeFromWidth(isRetina ? 2 * MAX_WIDTH : MAX_WIDTH);

      String xml = Files.toString(new File(path), Charsets.UTF_8);
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
          if (isRetina) {
            // The Retina image uses a scale of 2, and the RetinaImage class creates an
            // image of size w/scale, h/scale. If the width or height is less than the scale,
            // this rounds to width or height 0, which will cause exceptions to be thrown.
            // Don't attempt to create a Retina image for images like that. See issue 65676.
            final int scale = 2;
            if (image.getWidth() >= scale && image.getHeight() >= scale) {
              try {
                @SuppressWarnings("ConstantConditions") Image hdpiImage = RetinaImage.createFrom(image, scale, null);
                return new RetinaImageIcon(hdpiImage);
              }
              catch (Throwable t) {
                // Can't always create Retina images (see issue 65609); fall through to non-Retina code path
                ourRetinaEnabled = false;
              }
            }
          }
          return new ImageIcon(image);
        }
      }
    } catch (Throwable e) {
      LOG.warn(String.format("Could not read/render icon image %1$s", path), e);
    }

    return null;
  }

  private static void replaceResourceReferences(@NonNull Node node, @NonNull ResourceResolver resolver) {
    if (node.getNodeType() == Node.ELEMENT_NODE) {
      Element element = (Element)node;
      NamedNodeMap attributes = element.getAttributes();

      attributes:
      for (int i = 0, n = attributes.getLength(); i < n; i++) {
        Node attribute = attributes.item(i);
        String value = attribute.getNodeValue();
        if (!(value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF))) {
          continue;
        }
        for (int j = 0; j < 10; j++) {
          ResourceUrl resolvedUrl = ResourceUrl.parse(value);
          if (resolvedUrl == null) {
            continue attributes;
          }
          ResourceValue resourceValue;
          if (resolvedUrl.theme) {
            resourceValue = resolver.findItemInTheme(resolvedUrl.name, resolvedUrl.framework);
          }
          else {
            resourceValue = resolver.findResValue(resolvedUrl.toString(), resolvedUrl.framework);
          }
          if (resourceValue == null) {
            continue attributes;
          }
          value = resourceValue.getValue();
          if (value == null) {
            continue attributes;
          }
          if (!(value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF))) {
            // Found leaf value
            attribute.setNodeValue(value);
            break;
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
  private static Icon createBitmapIcon(@NotNull String path) {
    try {
      BufferedImage image = ImageIO.read(new File(path));
      if (image != null) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        if (ourRetinaEnabled && UIUtil.isRetina()) {
          BufferedImage scaled = image;
          if (imageWidth > 2 * MAX_WIDTH || imageHeight > 2 * MAX_HEIGHT) {
            double scale = 2 * Math.min(MAX_WIDTH / (double)imageWidth, MAX_HEIGHT / (double)imageHeight);
            scaled = ImageUtils.scale(image, scale, scale);
          }

          // The Retina image uses a scale of 2, and the RetinaImage class creates an
          // image of size w/scale, h/scale. If the width or height is less than the scale,
          // this rounds to width or height 0, which will cause exceptions to be thrown.
          // Don't attempt to create a Retina image for images like that. See issue 65676.
          final int scale = 2;
          if (scaled.getWidth() >= scale && scaled.getHeight() >= scale) {
            try {
              @SuppressWarnings("ConstantConditions")
              Image hdpiImage = RetinaImage.createFrom(scaled, scale, null);
              return new RetinaImageIcon(hdpiImage);
            } catch (Throwable t) {
              // Can't always create Retina images (see issue 65609); fall through to non-Retina code path
              ourRetinaEnabled = false;
            }
          }
        }

        if (imageWidth > MAX_WIDTH || imageHeight > MAX_HEIGHT) {
          double scale = Math.min(MAX_WIDTH / (double)imageWidth, MAX_HEIGHT / (double)imageHeight);

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
    }
    catch (Throwable e) {
      // Not just IOExceptions here; for example, we've seen
      // IllegalArgumentEx @ ...... < PNGImageReader:1479 < ... ImageIO.read
      LOG.warn(String.format("Could not read icon image %1$s", path), e);
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
