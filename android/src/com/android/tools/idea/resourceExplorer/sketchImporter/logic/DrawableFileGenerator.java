/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import com.android.SdkConstants;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGradient;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGradientStop;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class DrawableFileGenerator {
  public static final Logger LOG = Logger.getInstance(SketchGradient.class);

  private static final String TAG_VECTOR_HEAD = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"";
  private static final String TAG_PATH = "<path";
  private static final String TAG_AAPT_ATTR = "<aapt:attr name = \"android:fillColor\"";
  private static final String TAG_GRADIENT = "<gradient";
  private static final String TAG_ITEM = "<item/>";

  private static final String ATTRIBUTE_AAPT = "xmlns:aapt";
  private static final String VALUE_AAPT = SdkConstants.AAPT_URI;
  private static final String ATTRIBUTE_HEIGHT = "android:height";
  private static final String ATTRIBUTE_WIDTH = "android:width";
  private static final String ATTRIBUTE_VIEWPORT_HEIGHT = "android:viewportHeight";
  private static final String ATTRIBUTE_VIEWPORT_WIDTH = "android:viewportWidth";
  private static final String ATTRIBUTE_PATH_DATA = "android:pathData";
  private static final String ATTRIBUTE_FILL_COLOR = "android:fillColor";
  private static final String ATTRIBUTE_GRADIENT_ENDX = "android:endX";
  private static final String ATTRIBUTE_GRADIENT_ENDY = "android:endY";
  private static final String ATTRIBUTE_GRADIENT_STARTX = "android:startX";
  private static final String ATTRIBUTE_GRADIENT_STARTY = "android:startY";
  private static final String ATTRIBUTE_GRADIENT_CENTERX = "android:centerX";
  private static final String ATTRIBUTE_GRADIENT_CENTERY = "android:centerY";
  private static final String ATTRIBUTE_GRADIENT_RADIUS = "android:gradientRadius";
  private static final String ATTRIBUTE_GRADIENT_TYPE = "android:type";
  private static final String ATTRIBUTE_GRADIENT_STOP_COLOR = "android:color";
  private static final String ATTRIBUTE_GRADIENT_STOP_OFFSET = "android:offset";
  private static final String ATTRIBUTE_STROKE_COLOR = "android:strokeColor";
  private static final String ATTRIBUTE_STROKE_WIDTH = "android:strokeWidth";

  private static final int INVALID_COLOR_VALUE = 0;

  @NotNull private Project myProject;

  public DrawableFileGenerator(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  private XmlTag createVectorDrawable() {
    return getApplication()
      .runReadAction((Computable<XmlTag>)() -> XmlElementFactory.getInstance(myProject).createTagFromText(TAG_VECTOR_HEAD));
  }

  private static void updateDimensionsFromVectorDrawable(@NotNull VectorDrawable vectorDrawable, @NotNull XmlTag root) {
    getApplication().runReadAction(() -> {
      root.setAttribute(ATTRIBUTE_HEIGHT, Double.toString(vectorDrawable.getArtboardHeight()) + "dp");
      root.setAttribute(ATTRIBUTE_WIDTH, Double.toString(vectorDrawable.getArtboardWidth()) + "dp");
      root.setAttribute(ATTRIBUTE_VIEWPORT_HEIGHT, Double.toString(vectorDrawable.getViewportHeight()));
      root.setAttribute(ATTRIBUTE_VIEWPORT_WIDTH, Double.toString(vectorDrawable.getViewportWidth()));
    });
  }

  private void addPath(@NotNull DrawableModel shape, @NotNull XmlTag root) {
    getApplication().runReadAction(() -> {
      XmlTag pathTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_PATH);
      pathTag.setAttribute(ATTRIBUTE_PATH_DATA, shape.getPathData());
      if (shape.getStrokeColor() != INVALID_COLOR_VALUE) {
        pathTag.setAttribute(ATTRIBUTE_STROKE_COLOR, colorToHex(shape.getStrokeColor()));
        pathTag.setAttribute(ATTRIBUTE_STROKE_WIDTH, shape.getStrokeWidth());
      }
      if (shape.getGradient() != null) {
        root.setAttribute(ATTRIBUTE_AAPT, VALUE_AAPT);
        pathTag.addSubTag(generateGradientSubTag(shape.getGradient()), false);
      }
      else if (shape.getFillColor() != INVALID_COLOR_VALUE) {
        pathTag.setAttribute(ATTRIBUTE_FILL_COLOR, colorToHex(shape.getFillColor()));
      }
      root.addSubTag(pathTag, false);
    });
  }

  @NotNull
  private XmlTag generateGradientSubTag(@NotNull SketchGradient gradient) {
    XmlTag aaptAttrTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_AAPT_ATTR);
    XmlTag gradientTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_GRADIENT);
    String gradientType = gradient.getDrawableGradientType();
    if (gradientType != null) {
      switch (gradient.getDrawableGradientType()) {
        case SketchGradient.GRADIENT_LINEAR:
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_ENDX, gradient.getGradientEndX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_ENDY, gradient.getGradientEndY());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_STARTX, gradient.getGradientStartX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_STARTY, gradient.getGradientStartY());
          break;
        case SketchGradient.GRADIENT_RADIAL:
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERX, gradient.getGradientStartX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERY, gradient.getGradientStartY());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_RADIUS, gradient.getGradientRadius());
          break;
        case SketchGradient.GRADIENT_SWEEP:
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERX, gradient.getGradientStartX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERY, gradient.getSweepCenterY());
          break;
      }
      gradientTag.setAttribute(ATTRIBUTE_GRADIENT_TYPE, gradient.getDrawableGradientType());
    }

    for (SketchGradientStop item : gradient.getStops()) {
      XmlTag itemTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_ITEM);
      itemTag.setAttribute(ATTRIBUTE_GRADIENT_STOP_COLOR, colorToHex(item.getColor().getRGB()));
      itemTag.setAttribute(ATTRIBUTE_GRADIENT_STOP_OFFSET, Double.toString(item.getPosition()));
      gradientTag.addSubTag(itemTag, false);
    }

    aaptAttrTag.addSubTag(gradientTag, false);
    return aaptAttrTag;
  }

  /**
   * Generate a Vector Drawable (.xml) file from the {@link VectorDrawable}.
   */
  @NotNull
  public LightVirtualFile generateFile(@Nullable VectorDrawable vectorDrawable) {
    String name = vectorDrawable == null ? "null.xml" : vectorDrawable.getName() + ".xml";
    LightVirtualFile virtualFile = new LightVirtualFile(name);
    XmlTag root = createVectorDrawable();
    if (vectorDrawable != null) {
      updateDimensionsFromVectorDrawable(vectorDrawable, root);
      List<DrawableModel> drawableModels = vectorDrawable.getDrawableModels();
      for (DrawableModel drawableModel : drawableModels) {
        addPath(drawableModel, root);
      }
    }
    String content = getApplication().runReadAction((Computable<String>)() -> root.getText());
    virtualFile.setContent(null, content, false);
    return virtualFile;
  }

  @NotNull
  private static String colorToHex(int rgb) {
    return "#" + String.format("%08x", rgb);
  }
}