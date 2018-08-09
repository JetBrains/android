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

import com.android.ddmlib.Log;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableShape;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
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
  private static final String TAG_PATH = "<path/>";
  private static final String ATTRIBUTE_BASE = "android:";
  private static final String ATTRIBUTE_HEIGHT = "height";
  private static final String ATTRIBUTE_WIDTH = "width";
  private static final String ATTRIBUTE_VIEWPORT_HEIGHT = "viewportHeight";
  private static final String ATTRIBUTE_VIEWPORT_WIDTH = "viewportWidth";
  private static final String ATTRIBUTE_NAME = "name";
  private static final String ATTRIBUTE_PATH_DATA = "pathData";
  private static final String ATTRIBUTE_FILL_COLOR = "fillColor";
  private static final String ATTRIBUTE_STROKE_COLOR = "strokeColor";
  private static final String ATTRIBUTE_STROKE_WIDTH = "strokeWidth";

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
      root.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_HEIGHT, Double.toString(height) + "dp");
      root.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_WIDTH, Double.toString(width) + "dp");
    });
  }

  public void setViewportDimensions(double height, double width) {
    getApplication().runReadAction(() -> {
      root.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_VIEWPORT_HEIGHT, Double.toString(height));
      root.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_VIEWPORT_WIDTH, Double.toString(width));
    });
  }

  public void addPath(@NotNull DrawableShape shape) {
    getApplication().runReadAction(() -> {
      XmlTag pathTag = XmlElementFactory.getInstance(project).createTagFromText(TAG_PATH);

      pathTag.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_NAME, shape.getName());
      pathTag.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_PATH_DATA, shape.getPathData());
      pathTag.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_FILL_COLOR, shape.getFillColor());
      pathTag.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_STROKE_COLOR, shape.getStrokeColor());
      pathTag.setAttribute(ATTRIBUTE_BASE + ATTRIBUTE_STROKE_WIDTH, shape.getStrokeWidth());

      root.addSubTag(pathTag, false);
    });
  }

  public void saveDrawableToDisk(@NotNull String path) {
    File drawableFile = new File(path);

    if (!drawableFile.exists()) {
      try {
        //noinspection ResultOfMethodCallIgnored
        drawableFile.createNewFile();
      }
      catch (IOException e) {
        e.printStackTrace();
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

  @NotNull
  public LightVirtualFile generateFile() {
    LightVirtualFile virtualFile = new LightVirtualFile();
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
    String content = getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return root.getText();
      }
    });
    virtualFile.setContent(null, content, false);
    return virtualFile;
  }
}
