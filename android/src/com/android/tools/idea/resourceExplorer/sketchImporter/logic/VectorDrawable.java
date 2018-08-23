package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableModel;
import com.android.tools.idea.resourceExplorer.sketchImporter.structure.SketchArtboard;
import com.android.tools.layoutlib.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class VectorDrawable {

  @NotNull private final List<DrawableModel> myDrawableModels;
  @NotNull private final Rectangle.Double myArtboardDimension;
  @NotNull private final Rectangle.Double myViewportDimension;
  @NotNull private String myName;

  public VectorDrawable(@NotNull SketchArtboard artboard) {
    myDrawableModels = artboard.createAllDrawableShapes();
    myViewportDimension = myArtboardDimension = artboard.getFrame();
    myName = artboard.getName();
  }

  public double getArtboardHeight() {
    return myArtboardDimension.getHeight();
  }

  public double getArtboardWidth() {
    return myArtboardDimension.getWidth();
  }

  public double getViewportHeight() {
    return myViewportDimension.getHeight();
  }

  public double getViewportWidth() {
    return myViewportDimension.getWidth();
  }

  @NotNull
  public List<DrawableModel> getDrawableModels() {
    return myDrawableModels;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  public void setName(@NotNull String name) {
    myName = name;
  }
}