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

package com.android.tools.idea.ddms.screenshot;

import com.android.resources.ScreenOrientation;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DeviceArtGenerator {
  @NonNls private static final String FN_BASE = "device-art-resources";
  @NonNls private static final String FN_SPECS = "device-art-specs.json";

  /** Returns the absolute path to {@link #FN_BASE} folder, or null if it couldn't be located. */
  @Nullable
  private static File getBundledSpecsFolder() {
    // In the IDE distribution, this should be in plugins/android/lib/FN_BASE
    String androidLibs = PathManager.getJarPathForClass(DeviceArtGenerator.class);
    if (androidLibs != null) {
      File base = new File(androidLibs, FN_BASE);
      if (base.exists() && base.isDirectory()) {
        return base;
      }
    }

    // In development environments, search a few other folders
    String basePath = PathManager.getHomePath();
    if (basePath != null) {
      String[] paths = new String[] {
        "plugins" + File.separatorChar + "android" + File.separatorChar,
        ".." + File.separator + "adt" + File.separator + "idea" + File.separator + "android" + File.separatorChar,
      };

      for (String p : paths) {
        File base = new File(basePath, p);
        if (base.isDirectory()) {
          File specs = new File(base, FN_BASE);
          if (specs.isDirectory()) {
            return specs;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static File getSpecsFile(@NotNull File specsFolder) {
    File specs = new File(specsFolder, FN_SPECS);
    return specs.isFile() ? specs : null;
  }

  private static List<File> getSpecFiles(@Nullable File[] additionalRoots) {
    Set<File> roots = new HashSet<File>();

    File base = getBundledSpecsFolder();
    if (base != null) {
      roots.add(base);
    }

    if (additionalRoots != null) {
      for (File f : additionalRoots) {
        roots.add(f);
      }
    }

    List<File> specs = new ArrayList<File>(roots.size());
    for (File root : roots) {
      File spec = getSpecsFile(root);
      if (spec != null) {
        specs.add(spec);
      }
    }

    return specs;
  }

  /** Returns the list of device art specifications from both the installation and from the input folders. */
  public static List<DeviceArtSpec> getSpecs(@Nullable File[] specFolders) {
    List<File> specFiles = getSpecFiles(specFolders);
    List<DeviceArtSpec> result = Lists.newArrayList();

    Gson gson = new Gson();
    for (File specFile : specFiles) {
      Reader reader;
      try {
        reader = Files.newReader(specFile, Charsets.UTF_8);
      }
      catch (FileNotFoundException e) {
        // We only search files that exist, so this shouldn't happen
        continue;
      }

      DeviceArtSpec[] specs = gson.fromJson(reader, DeviceArtSpec[].class);
      for (DeviceArtSpec spec : specs) {
        spec.rootFolder = new File(specFile.getParentFile(), spec.getId());
        result.add(spec);
      }
    }

    return result;
  }

  public static BufferedImage frame(BufferedImage image, DeviceArtSpec spec, boolean addShadow, boolean addReflection) {
    double EPSILON = 1e-5;

    double imgAspectRatio = (double) image.getHeight() / image.getWidth();
    double specAspectRatio = spec.getAspectRatio();

    ScreenOrientation orientation;
    if (Math.abs(imgAspectRatio - specAspectRatio) < EPSILON) {
      orientation = ScreenOrientation.PORTRAIT;
    } else if (Math.abs(imgAspectRatio - 1/specAspectRatio) < EPSILON) {
      orientation = ScreenOrientation.LANDSCAPE;
    } else {
      // TODO: should we crop the image to required dimensions instead?
      return image;
    }

    File shadow = spec.getDropShadow(orientation);
    File background = spec.getFrame(orientation);
    File reflection = spec.getReflectionOverlay(orientation);

    Graphics2D g2d = null;
    try {
      BufferedImage bg = ImageIO.read(background);
      g2d = bg.createGraphics();

      if (addShadow) {
        BufferedImage shadowImage = ImageIO.read(shadow);
        g2d.drawImage(shadowImage, 0, 0, null, null);
      }

      Point offsets = spec.getScreenOffset(orientation);
      g2d.drawImage(image, offsets.x, offsets.y, null, null);

      if (addReflection) {
        BufferedImage reflectionImage = ImageIO.read(reflection);
        g2d.drawImage(reflectionImage, 0, 0, null, null);
      }
      return bg;
    }
    catch (IOException e) {
      return image;
    }
    finally {
      if (g2d != null) {
        g2d.dispose();
      }
    }
  }
}
