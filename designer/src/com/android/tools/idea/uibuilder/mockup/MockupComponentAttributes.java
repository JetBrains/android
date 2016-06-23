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
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.MockupLayer;
import com.android.tools.pixelprobe.Guide;
import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.util.Lists;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
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
public class MockupComponentAttributes implements ModelListener {

  private final static Pattern REGEX_POSITION_XY = Pattern.compile("([-]?[0-9]+\\s+[-]?[0-9]+)\\s*");
  private final static Pattern REGEX_POSITION_XY_SIZE = Pattern.compile(REGEX_POSITION_XY + "\\s+([0-9]+\\s+)[0-9]+\\s*");
  private final static Pattern REGEX_POSITION_CROP_XY = Pattern.compile(REGEX_POSITION_XY_SIZE + "\\s+([0-9]+\\s+)[0-9]+\\s*");
  private final static Pattern REGEX_POSITION_CROP_XY_SIZE = Pattern.compile(REGEX_POSITION_CROP_XY + "\\s+([0-9]+\\s+)[0-9]+\\s*");

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

  private final List<MockupModelListener> myListeners = new ArrayList<>();
  private final Rectangle myPosition;
  private final Rectangle myCropping;
  private NlModel myNlModel;
  @Nullable String myFilePath;
  @Nullable Image myImage;
  private float myAlpha = DEFAULT_OPACITY; // TODO read from xml
  private NlComponent myComponent;

  /**
   * Create a new MockupModel using the mockup file name attribute found in component.
   * If no attribute are found, returns null.
   *
   * @param component The component where the mockup will be drawn and containing at least
   *                  the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute
   * @return The newly created MockupModel or null if the it couldn't be created
   */
  @Nullable
  public static MockupComponentAttributes create(@NotNull NlComponent component) {
    final String file = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_MOCKUP);
    if (file == null) {
      return null;
    }
    return new MockupComponentAttributes(component);
  }

  /**
   * Look into all components in the provided model if they contain the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute
   * and create the corresponding MockupModel.
   *
   * @param model The {@link NlModel} to parse.
   * @return A list containing all the newly created MockupModel. Can be empty.
   */
  @NotNull
  public static List<MockupComponentAttributes> createAll(NlModel model) {
    final List<MockupComponentAttributes> mockupAttributes = new ArrayList<>();
    final List<NlComponent> components = model.getComponents();
    if (!components.isEmpty()) {
      final NlComponent root = components.get(0).getRoot();
      createAll(mockupAttributes, root);
    }
    return mockupAttributes;
  }

  /**
   * Create a new Mockup model and add it to list if component contains
   * the "<i>{@value SdkConstants#TOOLS_PREFIX}:{@value SdkConstants#ATTR_MOCKUP}</i>" attribute then recursively
   * parse its children
   *
   * @param component the current component to parse
   * @param list      the current list of {@link MockupComponentAttributes} where the newly created {@link MockupComponentAttributes} will be added.
   */
  private static void createAll(@NotNull List<MockupComponentAttributes> list, @NotNull NlComponent component) {
    final MockupComponentAttributes mockupComponentAttributes = create(component);
    if (mockupComponentAttributes != null) {
      list.add(mockupComponentAttributes);
    }
    for (int i = 0; i < component.getChildCount(); i++) {
      final NlComponent child = component.getChild(i);
      if (child != null) {
        createAll(list, child);
      }
    }
  }

  private MockupComponentAttributes(NlComponent component) {
    myPosition = new Rectangle(0, 0, -1, -1);
    myCropping = new Rectangle(0, 0, -1, -1);
    myComponent = component;
    myNlModel = component.getModel();
    myNlModel.addListener(this);
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
      setPositionString(position);
    }
    if (opacity != null) {
      setAlpha(opacity);
    }
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
      myImage = null;
      notifyListener();
    }
  }

  /**
   * Parse the position string from the xml if the string syntax is correct then set the position and cropping of the mockup
   *
   * @param position
   */
  private void setPositionString(String position) {
    if (isPositionStringCorrect(position)) {
      position = position.trim();
      final String[] split = position.split("\\s+");

      // Parse position
      if (split.length >= 4) {
        // Position and Size attributes
        setPosition(Integer.parseInt(split[X]),
                    Integer.parseInt(split[Y]),
                    Integer.parseInt(split[W]),
                    Integer.parseInt(split[H]));
      }
      else if (split.length == 2) {
        // Position only attribute
        setPosition(Integer.parseInt(split[X]), Integer.parseInt(split[Y]), -1, -1);

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
      setPosition(0, 0, -1, -1);
      setCropping(0, 0, -1, -1);
    }
  }

  public void setPosition(int x, int y, int width, int height) {
    if (myPosition.x != x
        || myPosition.y != y
        || myPosition.width != width
        || myPosition.height != height) {
      myPosition.setBounds(x, y, width, height);
      notifyListener();
    }
  }

  public void setCropping(int x, int y, int width, int height) {
    if (myCropping.x != x
        || myCropping.y != y
        || myCropping.width != width
        || myCropping.height != height) {
      myCropping.setBounds(x, y, width, height);
      notifyListener();
    }
  }

  public Rectangle getPosition() {
    return myPosition;
  }

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
    if (myImage == null && myFilePath != null && !myFilePath.isEmpty()) {
      myImage = MockupFileHelper.openImageFile(myFilePath);
    }
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
           || REGEX_POSITION_XY.matcher(s).matches()
           || REGEX_POSITION_XY_SIZE.matcher(s).matches()
           || REGEX_POSITION_CROP_XY.matcher(s).matches()
           || REGEX_POSITION_CROP_XY_SIZE.matcher(s).matches();
  }

  public void setAlpha(float alpha) {
    if (alpha != myAlpha) {
      myAlpha = alpha;
      notifyListener();
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

  private void notifyListener() {
    for (int i = 0; i < myListeners.size(); i++) {
      myListeners.get(i).mockupChanged(this);
    }
  }

  public NlComponent getComponent() {
    return myComponent;
  }

  public interface MockupModelListener {
    void mockupChanged(MockupComponentAttributes mockupComponentAttributes);
  }
}
