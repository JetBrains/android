package com.android.tools.idea.resourceExplorer.sketchImporter.logic;

import com.android.tools.idea.resourceExplorer.sketchImporter.structure.DrawableModel;
import com.android.tools.layoutlib.annotations.NotNull;

import java.awt.*;
import java.util.List;

public class VectorDrawable {

  @NotNull private final List<DrawableModel> drawableModels;
  @NotNull private final Rectangle.Double artboardDimension;
  @NotNull private final Rectangle.Double viewportDimension;

  public VectorDrawable(@NotNull List<DrawableModel> shapes, @NotNull Rectangle.Double viewport){
    drawableModels = shapes;
    viewportDimension = viewport;
    artboardDimension = viewport;
  }

  public double getArtboardHeight(){
    return artboardDimension.getHeight();
  }

  public double getArtboardWidth(){
    return artboardDimension.getWidth();
  }

  public double getViewportHeight(){
    return viewportDimension.getHeight();
  }

  public double getViewportWidth(){
    return viewportDimension.getWidth();
  }

  @NotNull public List<DrawableModel> getDrawableModels(){
    return drawableModels;
  }
}