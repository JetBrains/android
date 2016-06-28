/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.mockup;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.MockupLayer;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.pixelprobe.*;
import com.android.tools.pixelprobe.Image;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.WeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>
 * Parse and store the value from the mockup attributes in the xml.
 * </p>
 *
 * <p>
 * The available attributes are :
 * </p>
 *
 * <ul>
 * <li><i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i> [filename in PROJECT_DIR or Path}</li>
 * <li><i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP_POSITION}</i> (See below)</li>
 * <li><i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP_OPACITY} </i> [0..1]</li>
 * </ul>
 *
 *
 * <p>
 * The mockup is displayed as a layer on top of the blueprint ScreenView
 * using the <i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>attribute.
 * It is bounds the the component given in parameter of the {@link #create(NlComponent)} method.
 * </p>
 *
 * <p id="position">
 * Its position relative to the component and cropping can be set through the <i>{@code tools:mockup_position}</i>
 * attribute with a string having the following form :</p>
 *
 * <pre>  x, y, [w, h, [cx, cy, cw, ch]]</pre>
 * <ul>
 * <li> x : x offset of the mockup on the ScreenView (in dip) (default to 0) </li>
 * <li> y : y offset of the mockup on the ScreenView (in dip) (default to 0) </li>
 * <li> w : width of the mockup on the ScreenView (in dip). Will be scaled if needed. (default to component width) </li>
 * <li> h : height of the mockup on the ScreenView (in dip). will be scaled if needed (default to component height)</li>
 * <li> cx : x offset of cropping area on the mockup (in px) (default to 0)</li>
 * <li> cy : y offset of cropping area on the mockup (in px) (default to 0)</li>
 * <li> cw : width of cropping area on the mockup  (in px) (default to image width)</li>
 * <li> cy : height of cropping area on the mockup  (in px) (default to image height)</li>
 * </ul>
 *
 * @see MockupLayer
 */
public class Mockup implements ModelListener {

  private final static Pattern REGEX_BOUNDS = Pattern.compile("([-]?[0-9]+\\s+[-]?[0-9]+\\s*){1,2}");
  private final static Pattern REGEX_BOUNDS_CROP = Pattern.compile(REGEX_BOUNDS + "(\\s+[0-9]+\\s+[0-9]+\\s*){1,2}");

  private final static Pattern REGEX_OPACITY = Pattern.compile("[01]|[01]?\\.\\d+");
  public static final float DEFAULT_OPACITY = 0.5f;
  public static final float DEFAULT_OPACITY_IF_ERROR = 1f;

  // Position string indexes for
  // x,y,weight,height of the positioning rectangle
  private final static int X = 0;
  private final static int Y = 1;
  private final static int W = 2;
  private final static int H = 3;

  // Position string indexes for
  // x,y,weight,height of the cropping rectangle
  private final static int C_X = 4;
  private final static int C_Y = 5;
  private final static int C_W = 6;
  private final static int C_H = 7;

  private static final WeakHashMap<NlComponent, Mockup> MOCKUP_CACHE = new WeakHashMap<>();

  private final List<MockupModelListener> myListeners = new ArrayList<>();
  private final Rectangle myBounds;
  private final Rectangle myCropping;
  private final Rectangle mySwingBounds;
  private final Dimension myScreenSize;
  private NlModel myNlModel;
  @Nullable String myFilePath;
  @Nullable Image myImage;
  private float myAlpha = DEFAULT_OPACITY;
  private NlComponent myComponent;
  private boolean myIsFullScreen;

  /**
   * Create a new MockupModel using the mockup file name attribute found in component.
   * If no attribute are found, returns null.
   *
   * If a mockup has been previously created for this component, a cached instance of the
   * mockup object is returned.
   *
   * @param component              The component where the mockup will be drawn and containing at least
   *                               the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute
   * @param createWithoutAttribute if true, forces the creation of a {@link Mockup} object even if component does not contain the
   *                               {@value SdkConstants#ATTR_MOCKUP} attribute. This is useful if the user tries to open
   *                               the mockup editor after selecting a component that does not have a mockup attribute.
   * @return The newly created or cached MockupModel, or null if the it couldn't be created
   */
  @Nullable
  public static Mockup create(@NotNull NlComponent component, boolean createWithoutAttribute) {
    // Check if the the component contains a mockup attribute,
    // force the create in if createWithout Attribute is true. This is useful
    // if the user tries to open the mockup editor after selecting a component
    // that does not have a mockup attribute.
    if (hasMockupAttribute(component) || createWithoutAttribute) {

      // Check a mockup has already been created for the component
      if (MOCKUP_CACHE.containsKey(component)) {
        return MOCKUP_CACHE.get(component);
      }
      else {
        final Mockup mockup = new Mockup(component);
        MOCKUP_CACHE.put(component, mockup);
        return mockup;
      }
    }
    else {
      return null;
    }
  }

  /**
   * Create a new MockupModel using the mockup file name attribute found in component.
   * If no attribute are found, returns null.
   *
   * If a mockup has been previously created for this component, a cached instance of the
   * mockup object is returned.
   *
   * @param component The component where the mockup will be drawn and containing at least
   *                  the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute
   * @return The newly created MockupModel or null if the it couldn't be created
   */
  public static Mockup create(@NotNull NlComponent component) {
    return create(component, false);
  }

  /**
   * Look into all components in the provided model if they contain the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute
   * and create the corresponding MockupModel.
   *
   * @param model The {@link NlModel} to parse.
   * @return A list containing all the newly created MockupModel. Can be empty.
   */
  @NotNull
  public static List<Mockup> createAll(NlModel model) {
    final List<Mockup> mockup = new ArrayList<>();
    final List<NlComponent> components = model.getComponents();
    if (!components.isEmpty()) {
      final NlComponent root = components.get(0).getRoot();
      createAll(mockup, root);
    }
    return mockup;
  }

  /**
   * Create a new Mockup model and add it to list if component contains
   * the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute then recursively
   * parse its children
   *
   * @param component the current component to parse
   * @param list      the current list of {@link Mockup} where the newly created {@link Mockup} will be added.
   */
  private static void createAll(@NotNull List<Mockup> list, @NotNull NlComponent component) {
    final Mockup mockup = create(component, false);
    if (mockup != null) {
      list.add(mockup);
    }
    for (int i = 0; i < component.getChildCount(); i++) {
      final NlComponent child = component.getChild(i);
      if (child != null) {
        createAll(list, child);
      }
    }
  }

  public static boolean hasMockupAttribute(NlComponent component) {
    return component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP) != null;
  }

  private Mockup(NlComponent component) {
    myBounds = new Rectangle();
    myCropping = new Rectangle(0, 0, -1, -1);
    myScreenSize = new Dimension();
    mySwingBounds = new Rectangle();
    myComponent = component;
    myNlModel = component.getModel();
    myNlModel.addListener(this);
    setDefaultBounds();
    parseComponent(component);
  }

  /**
   * Parse the Mockup following attribute and set the corresponding variable.
   * <ul>
   * <li><i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i></li>
   * <li><i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP_POSITION}</i></li>
   * </ul>
   *
   * @param component
   */
  private void parseComponent(NlComponent component) {
    myComponent = component;
    final String fileName = myComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP);
    final String position = myComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_POSITION);
    final String opacity = myComponent.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP_OPACITY);
    if (fileName != null) {
      setFilePath(fileName);
    }
    if (position != null) {
      myIsFullScreen = false;
      parsePositionString(position);
    }
    else if (component.isRoot()) {
      myIsFullScreen = true;
    }
    if (opacity != null) {
      setAlpha(opacity);
    }
    notifyListeners();
  }

  public void setAlpha(String opacity) {
    if (REGEX_OPACITY.matcher(opacity).matches()) {
      setAlpha(Float.parseFloat(opacity));
    }
    else {
      setAlpha(DEFAULT_OPACITY_IF_ERROR);
    }
  }

  /**
   * Takes the file name (typically gotten from the XML file)
   * and construct the full filePath if filePath is neither an absolute or relative path.
   * Then set the value in myFilePath.
   *
   * @param filePath The filePath read from the xml.
   */
  private void setFilePath(@NotNull String filePath) {
    final Path path = MockupFileHelper.getFullFilePath(myNlModel.getProject(), filePath);
    if (path == null) {
      return;
    }

    if (myFilePath == null || !Paths.get(myFilePath).equals(path)) {
      myFilePath = path.toString();
      myImage = MockupFileHelper.openImageFile(myFilePath);

      //Update the cropping with the new image size
      setCropping(myCropping.x, myCropping.y, myCropping.width, myCropping.height);
      notifyListeners();
    }
  }

  /**
   * Parse the position string from the xml if the string syntax is correct then set the position and cropping of the mockup
   *
   * @param position
   */
  private void parsePositionString(String position) {
    if (isPositionStringCorrect(position)) {
      position = position.trim();
      final String[] split = position.split("\\s+");

      // Parse position
      if (split.length >= 4) {
        // Position and Size attributes
        setBounds(Integer.parseInt(split[X]),
                  Integer.parseInt(split[Y]),
                  Integer.parseInt(split[W]),
                  Integer.parseInt(split[H]));
      }
      else if (split.length == 2) {
        // Position only attribute
        setBounds(Integer.parseInt(split[X]), Integer.parseInt(split[Y]), -1, -1);

      }

      // Parse cropping
      if (split.length >= 8) {
        // Cropping attributes
        setCropping(Integer.parseInt(split[C_X]),
                    Integer.parseInt(split[C_Y]),
                    Integer.parseInt(split[C_W]),
                    Integer.parseInt(split[C_H]));
      }
      else if (split.length >= 6) {
        setCropping(Integer.parseInt(split[C_X]),
                    Integer.parseInt(split[C_Y]),
                    -1,
                    -1);
      }
    }
    else {
      // Default bounds and cropping (whole image covering all the component)
      setDefaultBounds();
      setCropping(0, 0, -1, -1);
    }
  }

  /**
   * Set bounds with 0,0,-1,-1.
   */
  void setDefaultBounds() {
    setBounds(0, 0, -1, -1);
  }

  public void setDefaultCrop() {
    setCropping(0,0,-1,-1);
  }

  /**
   * Set the bounds (position and size) in Dip of the mockup
   * @param x x coordinate in the Android Screen in Dip
   * @param y y coordinate in the Android Screen in Dip
   * @param width width in the Android Screen in Dip or -1 to fill the component
   * @param height height in the Android Screen in Dip or -1 to fill the component
   */
  public void setBounds(int x, int y, int width, int height) {
    if (myBounds.x != x
        || myBounds.y != y
        || myBounds.width != width
        || myBounds.height != height) {
      myBounds.setBounds(x, y, width, height);
      myIsFullScreen = false;
      notifyListeners();
    }
  }

  /**
   * Set the bounds (position and size) in Dip of the mockup
   * @param x x coordinate in the image in PX
   * @param y y coordinate in the image in PX
   * @param width width of area to crop in the image in PX or -1 to use the whole image
   * @param height height of area to crop in PX in Dip or -1 to use the whole image
   */
  public void setCropping(int x, int y, int width, int height) {
    if (myImage != null) {
      width = width > 0 ? width : myImage.getWidth() - x;
      height = height > 0 ? height : myImage.getHeight() - y;
    }
    if (myCropping.x != x
        || myCropping.y != y
        || myCropping.width != width
        || myCropping.height != height) {
      myCropping.setBounds(x, y, width, height);
      notifyListeners();
    }
    myIsFullScreen = false;
  }

  /**
   * Get the size and position of the mockup in Dip. If the width or height <= 0,
   * that means that they will default to the seize of the container
   * @return The bounding Rectangle
   */
  public Rectangle getBounds() {
    return myBounds;
  }

  /**
   * Compute the bounds where we will draw the mockup image in the {@link ScreenView}.
   *
   * @param screenView The screenView where the mockup will be drawn
   * @return The rectangle where the mockup will be drawn in the screen view
   */
  public Rectangle getSwingBounds(ScreenView screenView) {

    // If mockup is in full screen, the mockup will cover the ScreenView
    if (myIsFullScreen) {
      final Dimension screenViewSize = screenView.getSize(myScreenSize);
      mySwingBounds.x = screenView.getX();
      mySwingBounds.y = screenView.getY();
      mySwingBounds.width = screenViewSize.width;
      mySwingBounds.height = screenViewSize.height;
      return mySwingBounds;
    }
    else {
      final int androidX = Coordinates.dpToPx(screenView, myBounds.x);
      final int androidY = Coordinates.dpToPx(screenView, myBounds.y);
      final int androidWidth = myBounds.width <= 0 ? myComponent.w : Coordinates.dpToPx(screenView, myBounds.width);
      final int androidHeight = myBounds.height <= 0 ? myComponent.h : Coordinates.dpToPx(screenView, myBounds.height);

      mySwingBounds.x = Coordinates.getSwingX(screenView, myComponent.x + androidX);
      mySwingBounds.y = Coordinates.getSwingY(screenView, myComponent.y + androidY);
      // if one of the dimension was not set in the xml.
      // it had been set to -1 in the model, meaning we should
      // use the ScreenView dimension and/or the Image dimension
      mySwingBounds.width = Coordinates.getSwingDimension(screenView, androidWidth - androidX);
      mySwingBounds.height = Coordinates.getSwingDimension(screenView, androidHeight - androidY);

    }
    return mySwingBounds;
  }

  public boolean isFullScreen() {
    return myIsFullScreen;
  }

  /**
   * Get the bounds of the area in the image that will be paint.
   * @return the bounds of the area in the image that will be paint.
   */
  public Rectangle getCropping() {
    return myCropping;
  }

  @Nullable
  public String getFilePath() {
    return myFilePath;
  }

  /**
   * @return The virtual file corresponding to the file given in the xml or null if the file does not exist
   */
  @Nullable
  public VirtualFile getVirtualFile() {
    VirtualFile toSelect;
    toSelect = getFilePath() == null ? null : VfsUtil.findFileByIoFile(new File(FileUtil.toSystemIndependentName(getFilePath())), false);
    return toSelect;
  }

  @Nullable
  public BufferedImage getImage() {
    return myImage == null ? null : myImage.getMergedImage();
  }

  @Nullable
  public List<Guide> getGuidelines() {
    if (myImage == null && myFilePath != null && !myFilePath.isEmpty()) {
      myImage = MockupFileHelper.openImageFile(myFilePath);
    }
    return myImage == null ? null : myImage.getGuides();
  }

  public static boolean isPositionStringCorrect(@Nullable String s) {
    if (s == null) {
      return false;
    }
    return s.isEmpty()
           || REGEX_BOUNDS.matcher(s).matches()
           || REGEX_BOUNDS_CROP.matcher(s).matches();
  }

  public void setAlpha(float alpha) {
    if (alpha != myAlpha) {
      myAlpha = Math.min(1, Math.max(0, alpha));
      notifyListeners();
    }
  }

  public float getAlpha() {
    return myAlpha;
  }

  @Override
  public void modelChanged(@NotNull NlModel model) {
    parseComponent(myComponent);
  }

  @Override
  public void modelRendered(@NotNull NlModel model) {
  }

  public void addMockupModelListener(MockupModelListener listener) {
    if (listener != null) {
      myListeners.remove(listener);
      myListeners.add(listener);
    }
  }

  private void notifyListeners() {
    for (int i = 0; i < myListeners.size(); i++) {
      myListeners.get(i).mockupChanged(this);
    }
  }

  public NlComponent getComponent() {
    return myComponent;
  }

  public void clearCrop() {
    setDefaultBounds();
    myIsFullScreen = true;
  }

  public interface MockupModelListener {
    void mockupChanged(Mockup mockup);
  }
}
