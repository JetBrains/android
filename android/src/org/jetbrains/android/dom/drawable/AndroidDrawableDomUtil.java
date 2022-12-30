// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.dom.drawable;

import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.AdaptiveIconDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.AnimatedStateListDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.AnimatedVectorDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.CustomDrawableDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.RippleDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.VectorDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

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
                                          + DRAWABLE_ROOTS_V21.length + 1); // Add 1 for adaptive icon tag

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
      result.add(AdaptiveIconDomFileDescription.TAG_NAME);
    }

    return result;
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    return getPossibleRoots(facet, ResourceFolderType.DRAWABLE);
  }
}
