// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.dom.drawable;

import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AndroidDrawableDomUtil {
  private static final String[] DRAWABLE_ROOTS_V1 =
    new String[]{"selector", "bitmap", "nine-patch", "layer-list", "level-list", "transition", "inset", "clip", "scale", "shape",
      "animation-list", "animated-rotate", "rotate", "color"};
  private static final String[] DRAWABLE_ROOTS_V16 =
    new String[]{
      CustomDrawableDomFileDescription.TAG_NAME
    };
  private static final String[] DRAWABLE_ROOTS_V21 =
    new String[]{
      RippleDomFileDescription.TAG_NAME,
      AnimatedStateListDomFileDescription.TAG_NAME,
      VectorDomFileDescription.TAG,
      AnimatedVectorDomFileDescription.TAG_NAME,
    };

  private AndroidDrawableDomUtil() {
  }

  public static boolean isDrawableResourceFile(@NotNull XmlFile file) {
    return AndroidResourceDomFileDescription.isFileInResourceFolderType(file, ResourceFolderType.DRAWABLE);
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet, @NotNull ResourceFolderType folderType) {
    AndroidVersion sdkVersion = AndroidModuleInfo.getInstance(facet).getBuildSdkVersion();
    List<String> result = new ArrayList<>(DRAWABLE_ROOTS_V1.length + DRAWABLE_ROOTS_V16.length
                                          + DRAWABLE_ROOTS_V21.length + AdaptiveIconDomFileDescription.TAGS.size());

    // In MIPMAP folders, we only support adaptive-icon
    if (folderType != ResourceFolderType.MIPMAP) {
      Collections.addAll(result, DRAWABLE_ROOTS_V1);
      if (sdkVersion == null || sdkVersion.getFeatureLevel() >= 16 ||
          ApplicationManager.getApplication().isUnitTestMode()) {
        Collections.addAll(result, DRAWABLE_ROOTS_V16);
      }
      if (sdkVersion == null || sdkVersion.getFeatureLevel() >= 21 ||
          ApplicationManager.getApplication().isUnitTestMode()) {
        Collections.addAll(result, DRAWABLE_ROOTS_V21);
      }
    }

    if (sdkVersion == null || sdkVersion.getFeatureLevel() >= 26 || ApplicationManager.getApplication().isUnitTestMode()) {
      result.addAll(AdaptiveIconDomFileDescription.TAGS);
    }

    return result;
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    return getPossibleRoots(facet, ResourceFolderType.DRAWABLE);
  }
}
