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
package com.android.tools.idea.npw.assetstudio;

import com.intellij.openapi.diagnostic.Logger;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Methods for accessing built-in library of images.
 */
public class BuiltInImages {
  /** Do not instantiate - all methods are static. */
  private BuiltInImages() {}

  /**
   * Returns one of the built-in stencil images, or null if the image was not found.
   *
   * @param relativePath stencil path such as "launcher-stencil/square/web/back.png"
   * @return the image, or null
   * @throws IOException if an unexpected I/O error occurs
   */
  @Nullable
  public static BufferedImage getStencilImage(@NotNull String relativePath) throws IOException {
    try (InputStream is = BuiltInImages.class.getResourceAsStream(relativePath)) {
      return is == null ? null : ImageIO.read(is);
    }
  }

  /**
   * Returns the full size clip art image for a given image name.
   *
   * @param name the name of the image to be loaded (which can be looked up via {@link #getResourcesNames(String, String)})
   * @return the clip art image
   * @throws IOException if the image cannot be loaded
   */
  @Nullable
  public static BufferedImage getClipartImage(@NotNull String name) throws IOException {
    try (InputStream is = BuiltInImages.class.getResourceAsStream("/images/clipart/big/" + name)) {
      return ImageIO.read(is);
    }
  }

  private static final String JAR_URL_PREFIX = "file:";
  private static final String JAR_RESOURCE_PATH_SEPARATOR = ".jar!";

  /**
   * Returns the path of the jar file that contains the resource at the {@link URL}.
   * The assumption is that the URL is of "file:<path to jar file>.jar!<path to the resource within jar>" format.
   */
  public static String getJarFilePath(URL jarFileResourceUrl) {
    String imagesFolderPath = jarFileResourceUrl.getPath();
    int pathSeparator = imagesFolderPath.indexOf(JAR_RESOURCE_PATH_SEPARATOR) + JAR_RESOURCE_PATH_SEPARATOR.length() - 1;
    return imagesFolderPath.substring(JAR_URL_PREFIX.length(), pathSeparator);
  }

  /**
   * Returns the names of available clip art images which can be obtained by passing the name
   * to {@link #getClipartImage(String)}.
   *
   * @return an iterator for the available image names
   */
  @NotNull
  public static List<String> getResourcesNames(@NotNull String pathPrefix, @NotNull String filenameExtension) {
    List<String> names = new ArrayList<>(80);
    try {
      Enumeration<URL> en = BuiltInImages.class.getClassLoader().getResources(pathPrefix);
      if (en.hasMoreElements()) {
        URL url = en.nextElement();
        if ("jar".equals(url.getProtocol())) {
          String jarPath = getJarFilePath(url);
          try (ZipFile jarFile = new ZipFile(jarPath)) {
            jarFile.stream().forEach(zipEntry -> {
              String name = zipEntry.getName();
              if (name.startsWith(pathPrefix) && name.endsWith(filenameExtension)) {
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash >= 0) {
                  name = name.substring(lastSlash + 1);
                }
                names.add(name);
              }
            });
          }
        } else if ("file".equals(url.getProtocol())) { //$NON-NLS-1$
          File directory = new File(url.getPath());
          String[] list = directory.list();
          if (list != null) {
            names.addAll(Arrays.asList(list));
          }
        }
      }
    }
    catch (Exception e) {
      getLog().error(e);
    }

    return names;
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(BuiltInImages.class);
  }
}
