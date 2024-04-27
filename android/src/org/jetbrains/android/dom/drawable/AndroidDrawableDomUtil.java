/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.dom.drawable;

import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AndroidDrawableDomUtil {
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
    AndroidVersion sdkVersion = StudioAndroidModuleInfo.getInstance(facet).getBuildSdkVersion();
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
