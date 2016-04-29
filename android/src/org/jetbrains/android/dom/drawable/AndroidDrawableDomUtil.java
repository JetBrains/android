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
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.AnimatedStateListDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.AnimatedVectorDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.RippleDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.VectorDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class AndroidDrawableDomUtil {
  private static final String[] DRAWABLE_ROOTS_V1 =
    new String[]{"selector", "bitmap", "nine-patch", "layer-list", "level-list", "transition", "inset", "clip", "scale", "shape",
      "animation-list", "animated-rotate", "rotate", "color"};
  private static final String[] DRAWABLE_ROOTS_V21 =
    new String[]{
      RippleDomFileDescription.TAG_NAME,
      AnimatedStateListDomFileDescription.TAG_NAME,
      VectorDomFileDescription.TAG,
      AnimatedVectorDomFileDescription.TAG_NAME
    };

  private AndroidDrawableDomUtil() {
  }

  public static boolean isDrawableResourceFile(@NotNull XmlFile file) {
    return AndroidResourceDomFileDescription.doIsMyFile(file, ResourceFolderType.DRAWABLE);
  }

  public static List<String> getPossibleRoots(AndroidFacet facet) {
    AndroidVersion sdkVersion = AndroidModuleInfo.get(facet).getBuildSdkVersion();
    List<String> result = Lists.newArrayListWithExpectedSize(DRAWABLE_ROOTS_V1.length + DRAWABLE_ROOTS_V21.length);
    Collections.addAll(result, DRAWABLE_ROOTS_V1);
    if (sdkVersion == null || sdkVersion.getFeatureLevel() >= 21 ||
        ApplicationManager.getApplication().isUnitTestMode()) {
      Collections.addAll(result, DRAWABLE_ROOTS_V21);
    }

    return result;
  }
}
