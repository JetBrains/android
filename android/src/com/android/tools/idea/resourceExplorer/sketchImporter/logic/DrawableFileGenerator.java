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

import static com.intellij.openapi.application.ApplicationManager.getApplication;

import com.android.SdkConstants;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGradient;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGradientStop;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGraphicContextSettings;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DrawableFileGenerator {
  public static final Logger LOG = Logger.getInstance(SketchGradient.class);

  private static final String ATTRIBUTE_AAPT = SdkConstants.XMLNS_PREFIX + SdkConstants.AAPT_PREFIX;
  private static final String ATTRIBUTE_HEIGHT = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_HEIGHT;
  private static final String ATTRIBUTE_WIDTH = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_WIDTH;
  private static final String ATTRIBUTE_VIEWPORT_HEIGHT = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_VIEWPORT_HEIGHT;
  private static final String ATTRIBUTE_VIEWPORT_WIDTH = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_VIEWPORT_WIDTH;
  private static final String ATTRIBUTE_PATH_DATA = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_PATH_DATA;
  private static final String ATTRIBUTE_FILL_COLOR = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_FILL_COLOR;
  private static final String ATTRIBUTE_GRADIENT_ENDX = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_END_X;
  private static final String ATTRIBUTE_GRADIENT_ENDY = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_END_Y;
  private static final String ATTRIBUTE_GRADIENT_STARTX = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_START_X;
  private static final String ATTRIBUTE_GRADIENT_STARTY = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_START_Y;
  private static final String ATTRIBUTE_GRADIENT_CENTERX = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_CENTER_X;
  private static final String ATTRIBUTE_GRADIENT_CENTERY = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_CENTER_Y;
  private static final String ATTRIBUTE_GRADIENT_RADIUS = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_GRADIENT_RADIUS;
  private static final String ATTRIBUTE_GRADIENT_TYPE = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_TYPE;
  private static final String ATTRIBUTE_GRADIENT_STOP_COLOR = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_STOP_COLOR;
  private static final String ATTRIBUTE_GRADIENT_STOP_OFFSET = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_STOP_OFFSET;
  private static final String ATTRIBUTE_STROKE_COLOR = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_STROKE_COLOR;
  private static final String ATTRIBUTE_STROKE_WIDTH = SdkConstants.ANDROID_NS_NAME_PREFIX + SdkConstants.ATTR_STROKE_WIDTH;

  private static final String TAG_VECTOR_HEAD =
    '<' + SdkConstants.TAG_VECTOR + ' ' + SdkConstants.XMLNS_ANDROID + "=\"" + SdkConstants.ANDROID_URI + '\"';
  private static final String TAG_PATH = '<' + SdkConstants.TAG_PATH;
  private static final String TAG_AAPT_ATTR =
    '<' + SdkConstants.AAPT_PREFIX + ':' + SdkConstants.TAG_ATTR + ' ' + SdkConstants.ATTR_NAME + " = \"" + ATTRIBUTE_FILL_COLOR + "\"";
  private static final String TAG_GRADIENT = '<' + SdkConstants.TAG_GRADIENT;
  private static final String TAG_ITEM = '<' + SdkConstants.TAG_ITEM + "/>";

  private static final int INVALID_COLOR_VALUE = 0;

  @NotNull private Project myProject;

  public DrawableFileGenerator(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  private static XmlTag createVectorDrawable(@NotNull Project project) {
    return getApplication()
      .runReadAction((Computable<XmlTag>)() -> XmlElementFactory.getInstance(project).createTagFromText(TAG_VECTOR_HEAD));
  }

  private static void updateDimensionsFromVectorDrawable(@NotNull VectorDrawable vectorDrawable, @NotNull XmlTag root) {
    getApplication().runReadAction(() -> {
      root.setAttribute(ATTRIBUTE_HEIGHT, Double.toString(vectorDrawable.getArtboardHeight()) + SdkConstants.UNIT_DP);
      root.setAttribute(ATTRIBUTE_WIDTH, Double.toString(vectorDrawable.getArtboardWidth()) + SdkConstants.UNIT_DP);
      root.setAttribute(ATTRIBUTE_VIEWPORT_HEIGHT, Double.toString(vectorDrawable.getViewportHeight()));
      root.setAttribute(ATTRIBUTE_VIEWPORT_WIDTH, Double.toString(vectorDrawable.getViewportWidth()));
    });
  }

  private void addArtboardPathForTesting(@NotNull VectorDrawable vectorDrawable, @NotNull XmlTag root) {
    getApplication().runReadAction(() -> {
      XmlTag pathTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_PATH);
      PathStringBuilder pathStringBuilder = new PathStringBuilder();
      pathStringBuilder.startPath(0, 0);
      pathStringBuilder.createLine(vectorDrawable.getArtboardWidth(), 0);
      pathStringBuilder.createLine(vectorDrawable.getArtboardWidth(), vectorDrawable.getArtboardHeight());
      pathStringBuilder.createLine(0, vectorDrawable.getArtboardHeight());
      pathStringBuilder.endPath();
      pathTag.setAttribute(ATTRIBUTE_PATH_DATA, pathStringBuilder.build());
      pathTag.setAttribute(ATTRIBUTE_FILL_COLOR, "#FFFFFFFF");
      root.addSubTag(pathTag, false);
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
        root.setAttribute(ATTRIBUTE_AAPT, SdkConstants.AAPT_URI);
        pathTag.addSubTag(generateGradientSubTag(shape.getGradient(), shape.getGraphicContextSettings()), false);
      }
      else if (shape.getFillColor() != INVALID_COLOR_VALUE) {
        pathTag.setAttribute(ATTRIBUTE_FILL_COLOR, colorToHex(shape.getFillColor()));
      }
      root.addSubTag(pathTag, false);
    });
  }

  @NotNull
  private XmlTag generateGradientSubTag(@NotNull SketchGradient gradient, @Nullable SketchGraphicContextSettings contextSettings) {
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

    gradient.applyGraphicContextSettings(contextSettings);
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
    XmlTag root = createVectorDrawable(myProject);
    if (vectorDrawable != null) {
      updateDimensionsFromVectorDrawable(vectorDrawable, root);
      //addArtboardPathForTesting();
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