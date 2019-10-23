/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.builders;

import com.android.SdkConstants;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ColorAssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.DrawableAssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.GradientModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.GradientStopModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ShapeModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.DrawableAssetModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.GradientModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.GradientStopModel;
import com.android.tools.idea.ui.resourcemanager.sketchImporter.converter.models.ShapeModel;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.xml.XmlTagValueImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ThrowableRunnable;
import java.awt.Color;
import java.util.List;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResourceFileGenerator {
  public static final Logger LOG = Logger.getInstance(ResourceFileGenerator.class);

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
    '<' + SdkConstants.TAG_VECTOR + ' ' + SdkConstants.XMLNS_ANDROID + "=\"" + SdkConstants.ANDROID_URI + "\"/>";
  private static final String TAG_PATH = '<' + SdkConstants.TAG_PATH + "/>";
  private static final String TAG_AAPT_ATTR =
    '<' + SdkConstants.AAPT_PREFIX + ':' + SdkConstants.TAG_ATTR + ' ' + SdkConstants.ATTR_NAME + " = \"" + ATTRIBUTE_FILL_COLOR + "\"/>";
  private static final String TAG_GRADIENT = '<' + SdkConstants.TAG_GRADIENT + "/>";
  private static final String TAG_ITEM = '<' + SdkConstants.TAG_ITEM + "/>";
  private static final String TAG_GROUP = '<' + SdkConstants.TAG_GROUP + "/>";
  private static final String TAG_CLIP_PATH = '<' + SdkConstants.TAG_CLIP_PATH + "/>";
  private static final String TAG_RESOURCES = '<' + SdkConstants.TAG_RESOURCES + "/>";
  private static final String TAG_COLOR = '<' + SdkConstants.TAG_COLOR + "/>";

  public static final int INVALID_COLOR_VALUE = 0;
  public static final int INVALID_BORDER_WIDTH_VALUE = -1;

  @NotNull private Project myProject;

  public ResourceFileGenerator(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  private XmlTag createXmlTag(@NotNull String tag) {
    return XmlElementFactory.getInstance(myProject).createTagFromText(tag);
  }

  private static void updateDimensionsFromVectorDrawable(@NotNull DrawableAssetModel vectorDrawable, @NotNull XmlTag root) {
    root.setAttribute(ATTRIBUTE_HEIGHT, vectorDrawable.getArtboardHeight() + SdkConstants.UNIT_DP);
    root.setAttribute(ATTRIBUTE_WIDTH, vectorDrawable.getArtboardWidth() + SdkConstants.UNIT_DP);
    root.setAttribute(ATTRIBUTE_VIEWPORT_HEIGHT, Double.toString(vectorDrawable.getViewportHeight()));
    root.setAttribute(ATTRIBUTE_VIEWPORT_WIDTH, Double.toString(vectorDrawable.getViewportWidth()));
  }

  private void addArtboardPathForTesting(@NotNull DrawableAssetModel drawableAsset, @NotNull XmlTag root) {
    XmlTag pathTag = createXmlTag(TAG_PATH);
    PathStringBuilder pathStringBuilder = new PathStringBuilder();
    pathStringBuilder.startPath(0, 0);
    pathStringBuilder.createLine(drawableAsset.getArtboardWidth(), 0);
    pathStringBuilder.createLine(drawableAsset.getArtboardWidth(), drawableAsset.getArtboardHeight());
    pathStringBuilder.createLine(0, drawableAsset.getArtboardHeight());
    pathStringBuilder.endPath();
    pathTag.setAttribute(ATTRIBUTE_PATH_DATA, pathStringBuilder.build());
    pathTag.setAttribute(ATTRIBUTE_FILL_COLOR, "#FFFFFFFF");
    root.addSubTag(pathTag, false);
  }

  private void addPath(@NotNull ShapeModel shape, @NotNull XmlTag parentTag) {
    XmlTag pathTag = createXmlTag(TAG_PATH);
    pathTag.setAttribute(ATTRIBUTE_PATH_DATA, shape.getPathString());
    if (shape.getBorderColor() != INVALID_COLOR_VALUE) {
      pathTag.setAttribute(ATTRIBUTE_STROKE_COLOR, colorToHex(shape.getBorderColor()));
      pathTag.setAttribute(ATTRIBUTE_STROKE_WIDTH, Integer.toString(shape.getBorderWidth()));
    }
    if (shape.getGradient() != null) {
      parentTag.setAttribute(ATTRIBUTE_AAPT, SdkConstants.AAPT_URI);
      pathTag.addSubTag(generateGradientSubTag(shape.getGradient()), false);
    }
    else if (shape.getFill() != null) {
      pathTag.setAttribute(ATTRIBUTE_FILL_COLOR, colorToHex(shape.getFillColor()));
    }
    parentTag.addSubTag(pathTag, false);
  }

  private void addClipPath(@NotNull String pathData, @NotNull XmlTag parentTag) {
    XmlTag pathTag = createXmlTag(TAG_CLIP_PATH);
    pathTag.setAttribute(ATTRIBUTE_PATH_DATA, pathData);
    parentTag.addSubTag(pathTag, false);
  }

  private void addColor(@NotNull Pair<Color, String> colorToName, @NotNull XmlTag parentTag) {
    XmlTag colorTag = createXmlTag(TAG_COLOR);
    colorTag.setAttribute(SdkConstants.ATTR_NAME, colorToName.getSecond());

    XmlTagValueImpl colorTagValue = new XmlTagValueImpl(XmlTagChild.EMPTY_ARRAY, colorTag);
    colorTagValue.setText(colorToHex(colorToName.getFirst().getRGB()));

    parentTag.addSubTag(colorTag, false);
  }

  @NotNull
  private XmlTag generateGradientSubTag(@NotNull GradientModel gradient) {
    XmlTag aaptAttrTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_AAPT_ATTR);
    XmlTag gradientTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_GRADIENT);
    String gradientType = gradient.getDrawableGradientType();
    if (gradientType != null) {
      switch (gradient.getDrawableGradientType()) {
        case GradientModel.GRADIENT_LINEAR:
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_ENDX, gradient.getGradientEndX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_ENDY, gradient.getGradientEndY());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_STARTX, gradient.getGradientStartX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_STARTY, gradient.getGradientStartY());
          break;
        case GradientModel.GRADIENT_RADIAL:
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERX, gradient.getGradientStartX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERY, gradient.getGradientStartY());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_RADIUS, gradient.getGradientRadius());
          break;
        case GradientModel.GRADIENT_SWEEP:
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERX, gradient.getGradientStartX());
          gradientTag.setAttribute(ATTRIBUTE_GRADIENT_CENTERY, gradient.getSweepCenterY());
          break;
      }
      gradientTag.setAttribute(ATTRIBUTE_GRADIENT_TYPE, gradient.getDrawableGradientType());
    }

    for (GradientStopModel item : gradient.getGradientStopModels()) {
      XmlTag itemTag = XmlElementFactory.getInstance(myProject).createTagFromText(TAG_ITEM);
      itemTag.setAttribute(ATTRIBUTE_GRADIENT_STOP_COLOR, colorToHex(item.getColor().getRGB()));
      itemTag.setAttribute(ATTRIBUTE_GRADIENT_STOP_OFFSET, Double.toString(item.getPosition()));
      gradientTag.addSubTag(itemTag, false);
    }

    aaptAttrTag.addSubTag(gradientTag, false);
    return aaptAttrTag;
  }

  @NotNull
  private XmlTag generateClippedGroup(@NotNull ShapeModel shape) {
    XmlTag group = createXmlTag(TAG_GROUP);
    addClipPath(shape.getPathString(), group);
    return group;
  }

  @Nullable
  private static XmlTag closeClippedGroup(@NotNull XmlTag groupTag, @NotNull XmlTag root) {
    root.addSubTag(groupTag, false);
    return null;
  }

  /**
   * Generate a Vector Drawable (.xml) file from the {@link DrawableAssetModel}.
   */
  @NotNull
  public LightVirtualFile generateDrawableFile(@Nullable DrawableAssetModel drawableAsset) {
    String name = drawableAsset == null ? "null.xml" : drawableAsset.getName() + ".xml";
    LightVirtualFile virtualFile = new LightVirtualFile(name);

    try {
      WriteAction.runAndWait((ThrowableRunnable<Throwable>)() -> {
        XmlTag root = createXmlTag(TAG_VECTOR_HEAD);
        if (drawableAsset != null) {
          updateDimensionsFromVectorDrawable(drawableAsset, root);
          List<ShapeModel> shapeModels = drawableAsset.getShapeModels();
          XmlTag groupTag = null;

          // Adding the path tags to the file
          for (ShapeModel shapeModel : shapeModels) {
            // First we need to check if the DrawableModel is used as a clip path or not
            // to determine it corresponding tag (<clip-path> or <path>)
            if (shapeModel.hasClippingMask()) {
              // If the model is the first layer of the file and is a clip path, then
              // we can add the <clip-path> tag directly to the root tag, because
              // this will be the mask for the entire vector drawable, meaning it
              // will be applied to all the shapes below. It does not need a separate group.
              if (shapeModel == shapeModels.get(0)) {
                addClipPath(shapeModel.getPathString(), root);
              }
              // Else, it means that the clip path belongs only to a component of the
              // vector drawable, thus we generate a group and we append the <clip-path>
              // tag to it, so that the mask does not affect the rest of the elements.
              else {
                groupTag = generateClippedGroup(shapeModel);
              }
            }
            // If the group tag is inexistent, it means there is no current clipping group
            // to add the current shape to, so we just add it to the root.
            if (groupTag == null) {
              addPath(shapeModel, root);
            }
            // Else, we might be editing a current clipped group
            else {
              // If the shapeModel is the first to not have the mask applied after a
              // chain of masked shapes, the shape should not be part of the current
              // clipping group anymore. The group should be closed and the path should
              // be added to the root.
              if (shapeModel.shouldBreakMaskChain()) {
                groupTag = closeClippedGroup(groupTag, root);
                addPath(shapeModel, root);
              }
              // If the model does not break the mask chain, it should be added to the
              // group like the previous shapes.
              else {
                addPath(shapeModel, groupTag);
              }
              // If the model is the last shape in the list of a SketchPage's layers
              // we should clearly close the group we have been adding paths to.
              if (shapeModel.isLastShape() && groupTag != null) {
                groupTag = closeClippedGroup(groupTag, root);
              }
            }
          }
        }
        virtualFile.setContent(null, root.getText(), false);
      });
    }
    catch (Throwable throwable) {
      LOG.error(throwable);
    }

    return virtualFile;
  }

  @NotNull
  public LightVirtualFile generateColorsFile(@NotNull List<Pair<Color, String>> colorToNameList) {
    LightVirtualFile virtualFile = new LightVirtualFile("sketch_colors.xml");

    try {
      WriteAction.runAndWait((ThrowableRunnable<Throwable>)() -> {
        XmlTag resourcesTag = createXmlTag(TAG_RESOURCES);
        if (!colorToNameList.isEmpty()) {
          for (Pair<Color, String> colorToName : colorToNameList) {
            addColor(colorToName, resourcesTag);
          }
        }

        virtualFile.setContent(null, resourcesTag.getText(), false);
      });
    }
    catch (Throwable throwable) {
      LOG.error(throwable);
    }

    return virtualFile;
  }

  @NotNull
  private static String colorToHex(int rgb) {
    return "#" + String.format("%08X", rgb);
  }
}