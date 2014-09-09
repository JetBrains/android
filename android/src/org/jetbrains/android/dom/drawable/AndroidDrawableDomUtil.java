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

import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidDrawableDomUtil {
  public static final Map<String, String> SPECIAL_STYLEABLE_NAMES = new HashMap<String, String>();
  private static final String[] DRAWABLE_ROOTS_V1 =
    new String[]{"selector", "bitmap", "nine-patch", "layer-list", "level-list", "transition", "inset", "clip", "scale", "shape",
      "animation-list", "animated-rotate", "rotate", "color"};
  private static final String[] DRAWABLE_ROOTS_V21 =
    new String[]{
      RippleDomFileDescription.TAG,
      AnimatedStateListDomFileDescription.TAG,
      VectorDomFileDescription.TAG,
      AnimatedVectorDomFileDescription.TAG
    };

  static {
    SPECIAL_STYLEABLE_NAMES.put("selector", "StateListDrawable");
    SPECIAL_STYLEABLE_NAMES.put("bitmap", "BitmapDrawable");
    SPECIAL_STYLEABLE_NAMES.put("nine-patch", "NinePatchDrawable");
    SPECIAL_STYLEABLE_NAMES.put("layer-list", "LayerDrawable");
    SPECIAL_STYLEABLE_NAMES.put("inset", "InsetDrawable");
    SPECIAL_STYLEABLE_NAMES.put("clip", "ClipDrawable");
    SPECIAL_STYLEABLE_NAMES.put("scale", "ScaleDrawable");
    SPECIAL_STYLEABLE_NAMES.put("animation-list", "AnimationDrawable");
    SPECIAL_STYLEABLE_NAMES.put("rotate", "RotateDrawable");
    SPECIAL_STYLEABLE_NAMES.put("animated-rotate", "AnimatedRotateDrawable");
    SPECIAL_STYLEABLE_NAMES.put("shape", "GradientDrawable");
    SPECIAL_STYLEABLE_NAMES.put("corners", "DrawableCorners");
    SPECIAL_STYLEABLE_NAMES.put("gradient", "GradientDrawableGradient");
    SPECIAL_STYLEABLE_NAMES.put("padding", "GradientDrawablePadding");
    SPECIAL_STYLEABLE_NAMES.put("size", "GradientDrawableSize");
    SPECIAL_STYLEABLE_NAMES.put("solid", "GradientDrawableSolid");
    SPECIAL_STYLEABLE_NAMES.put("stroke", "GradientDrawableStroke");
    SPECIAL_STYLEABLE_NAMES.put("transition", "LayerDrawable"); // Transition extends LayerDrawable, doesn't define its own styleable
    SPECIAL_STYLEABLE_NAMES.put(ColorDrawableDomFileDescription.TAG, "ColorDrawable");
    SPECIAL_STYLEABLE_NAMES.put(RippleDomFileDescription.TAG, "RippleDrawable");
    SPECIAL_STYLEABLE_NAMES.put(AnimatedStateListDomFileDescription.TAG, "AnimatedStateListDrawable");
    SPECIAL_STYLEABLE_NAMES.put(VectorDomFileDescription.TAG, "VectorDrawable");
    SPECIAL_STYLEABLE_NAMES.put(AnimatedVectorDomFileDescription.TAG, "AnimatedVectorDrawable");
  }

  private AndroidDrawableDomUtil() {
  }

  public static boolean isDrawableResourceFile(@NotNull XmlFile file) {
    return AndroidResourceDomFileDescription.doIsMyFile(file, new String[]{ResourceType.DRAWABLE.getName()});
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
