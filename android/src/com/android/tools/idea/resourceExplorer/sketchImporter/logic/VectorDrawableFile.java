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
import com.android.ddmlib.Log;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableShape;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGradient;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchGradientStop;
import com.android.tools.layoutlib.annotations.NotNull;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightVirtualFile;

import java.awt.geom.Rectangle2D;
import java.io.*;
import java.util.List;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

public class VectorDrawableFile {

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
  private static final String ATTRIBUTE_NAME = "android:name";
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

  @NotNull private SketchArtboard artboard;
  @NotNull private Project project;
  @NotNull private XmlTag root;

  public VectorDrawableFile(@NotNull Project projectParam) {
    project = projectParam;
  }

  public VectorDrawableFile(@NotNull Project projectParam, @NotNull SketchArtboard artboardParam) {
    project = projectParam;
    artboard = artboardParam;
  }

  public void createVectorDrawable() {
    root = getApplication()
      .runReadAction((Computable<XmlTag>)() -> XmlElementFactory.getInstance(project).createTagFromText(TAG_VECTOR_HEAD));
  }

  public void setVectorDimensions(double height, double width) {
    getApplication().runReadAction(() -> {
      root.setAttribute(ATTRIBUTE_HEIGHT, Double.toString(height) + "dp");
      root.setAttribute(ATTRIBUTE_WIDTH, Double.toString(width) + "dp");
    });
  }

  public void setViewportDimensions(double height, double width) {
    getApplication().runReadAction(() -> {
      root.setAttribute(ATTRIBUTE_VIEWPORT_HEIGHT, Double.toString(height));
      root.setAttribute(ATTRIBUTE_VIEWPORT_WIDTH, Double.toString(width));
    });
  }

  public void addPath(@NotNull DrawableShape shape) {
    getApplication().runReadAction(() -> {
      XmlTag pathTag = XmlElementFactory.getInstance(project).createTagFromText(TAG_PATH);
      pathTag.setAttribute(ATTRIBUTE_NAME, shape.getName());
      pathTag.setAttribute(ATTRIBUTE_PATH_DATA, shape.getPathData());
      if (shape.getStrokeColor() != null) {
        pathTag.setAttribute(ATTRIBUTE_STROKE_COLOR, shape.getStrokeColor());
        pathTag.setAttribute(ATTRIBUTE_STROKE_WIDTH, shape.getStrokeWidth());
      }
      if (shape.getGradient() != null) {
        root.setAttribute(ATTRIBUTE_AAPT, VALUE_AAPT);
        pathTag.addSubTag(generateGradientSubTag(shape.getGradient()), false);
      }
      else {
        pathTag.setAttribute(ATTRIBUTE_FILL_COLOR, shape.getFillColor());
      }
      root.addSubTag(pathTag, false);
    });
  }

  @NotNull
  private XmlTag generateGradientSubTag(@NotNull SketchGradient gradient) {
    XmlTag aaptAttrTag = XmlElementFactory.getInstance(project).createTagFromText(TAG_AAPT_ATTR);
    XmlTag gradientTag = XmlElementFactory.getInstance(project).createTagFromText(TAG_GRADIENT);
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

    for (SketchGradientStop item : gradient.getStops()) {
      XmlTag itemTag = XmlElementFactory.getInstance(project).createTagFromText(TAG_ITEM);
      itemTag.setAttribute(ATTRIBUTE_GRADIENT_STOP_COLOR, "#" + Integer.toHexString(item.getColor().getRGB()));
      itemTag.setAttribute(ATTRIBUTE_GRADIENT_STOP_OFFSET, Double.toString(item.getPosition()));
      gradientTag.addSubTag(itemTag, false);
    }

    aaptAttrTag.addSubTag(gradientTag, false);
    return aaptAttrTag;
  }

  public void saveDrawableToDisk(@NotNull String path) {
    File drawableFile = new File(path);

    if (!drawableFile.exists()) {
      try {
        //noinspection ResultOfMethodCallIgnored
        drawableFile.createNewFile();
      }
      catch (IOException e) {
        Log.e(VectorDrawableFile.class.getName(), "Could not save file to disk");
      }
    }

    try (DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(drawableFile))) {
      String text = getApplication().runReadAction((Computable<String>)() -> root.getText());
      dataOutputStream.writeBytes(text);
      dataOutputStream.writeBytes(System.getProperty("line.separator"));
    }
    catch (FileNotFoundException e) {
      Log.e(VectorDrawableFile.class.getName(), "Could not save file to disk");
    }
    catch (IOException e) {
      Log.e(VectorDrawableFile.class.getName(), e);
    }
  }

  /**
   * @return virtual Vector Drawable file whose name corresponds to the {@code filename}
   */
  @NotNull
  public LightVirtualFile generateFile(String filename) {
    LightVirtualFile virtualFile = new LightVirtualFile(filename + ".xml");
    createVectorDrawable();
    if (artboard != null) {
      Rectangle2D.Double frame = artboard.getFrame();
      setVectorDimensions(frame.getHeight(), frame.getWidth());
      setViewportDimensions(frame.getHeight(), frame.getWidth());
      List<DrawableShape> shapes = artboard.getShapes();
      for (DrawableShape shape : shapes) {
        addPath(shape);
      }
    }
    String content = getApplication().runReadAction((Computable<String>)() -> root.getText());
    virtualFile.setContent(null, content, false);
    return virtualFile;
  }
}
