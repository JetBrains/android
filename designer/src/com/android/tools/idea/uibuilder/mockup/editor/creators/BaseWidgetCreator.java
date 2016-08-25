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
package com.android.tools.idea.uibuilder.mockup.editor.creators;

import com.android.resources.Density;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupCoordinate;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.mockup.editor.creators.viewgroupattributes.ViewGroupAttributesManager;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.nio.file.Path;
import java.util.Collections;

import static com.android.SdkConstants.*;

/**
 * Create a new widget with information extracted from the Mockup and add it the model.
 *
 * The subclasses should implement {@link #getAndroidViewTag()} and return the desired tag for the
 * newly created {@link NlComponent}.
 *
 * If a subclass needs to do other task just before the {@link NlComponent} is added to the {@link NlModel},
 * they can implement {@link #beforeAdd(NlComponent)}.
 *
 * This class also provide helper method to add attribute commons to all Views
 */
abstract class BaseWidgetCreator {

  private final NlModel myModel;
  private final ScreenView myScreenView;
  @NotNull private Mockup myMockup;
  @Nullable private NlComponent myComponent;
  @Nullable private ViewGroupAttributesManager myViewGroupAttributesManager;


  /**
   * Create a new  View
   * @param mockup the mockup to extract the information from
   * @param model the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link com.android.tools.idea.uibuilder.surface.DesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates
   */
  public BaseWidgetCreator(@NotNull Mockup mockup, @NotNull NlModel model, @NotNull ScreenView screenView) {
    myMockup = mockup;
    myModel = model;
    myScreenView = screenView;
  }

  /**
   * Add a LayoutManager if the container of the new widget needs special attribute
   * @param viewGroupAttributesManager
   */
  public void setViewGroupAttributesManager(@Nullable ViewGroupAttributesManager viewGroupAttributesManager) {
    myViewGroupAttributesManager = viewGroupAttributesManager;
  }

  /**
   * Override to specify the tag of the new {@link NlComponent}
   * @return the Tag of the Android View as it will appears in the XML file
   */
  @NotNull
  public abstract String getAndroidViewTag();

  /**
   * Get the mockup used in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   * @return the mockup used in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  @NotNull
  protected final Mockup getMockup() {
    return myMockup;
  }

  /**
   * Get the current model associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   * @return the current model associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor
   */
  protected final NlModel getModel() {
    return myModel;
  }

  /**
   * Get the screen view associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   * @return the screen view associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  public ScreenView getScreenView() {
    return myScreenView;
  }

  /**
   * This is where any modification to the new component should be added.
   * The transaction will be committed in between the call to {@link #beforeAdd(NlComponent)} and
   * the addition of the component to the model
   * @param transaction open transaction to edit attributes of the new component.
   */
  abstract protected void addAttributes(@NotNull AttributesTransaction transaction);

  /**
   * This is where any action that needs to <b>read</b> the newComponent happens.
   * Any modification to the attributes of the components should be done in {@link #addAttributes(AttributesTransaction)}
   * @param newComponent The newly created component that will be added to the model
   */
  protected void beforeAdd(@NotNull NlComponent newComponent) {
  }

  /**
   * Add the new component to the model provided in the constructor
   * @return
   */
  public final NlComponent addToModel() {
    ensureNewComponentCreated();
    assert myComponent != null;
    AttributesTransaction transaction = myComponent.startAttributeTransaction();
    addAttributes(transaction);
    if (myViewGroupAttributesManager != null) {
      myViewGroupAttributesManager.addLayoutAttributes(transaction);
    }
    beforeAdd(myComponent);
    transaction.commit();
    myModel.addComponents(Collections.singletonList(myComponent), myMockup.getComponent(), null, InsertType.CREATE_PREVIEW);
    return myComponent;
  }

  /**
   * Adds the width and height attributes
   *
   * @param transaction
   * @param bounds
   */
  protected void addSizeAttributes(@NotNull AttributesTransaction transaction, @AndroidCoordinate @NotNull Rectangle bounds) {
    transaction.setAttribute(null, ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH,
                             String.format("%ddp", pxToDp(myModel, bounds.width)));
    transaction.setAttribute(null, ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT,
                             String.format("%ddp", pxToDp(myModel, bounds.height)));
  }

  /**
   * Add the position attributes for ConstraintLayout
   *
   * @param transaction
   * @param bounds
   */
  protected void addLayoutEditorPositionAttribute(@NotNull AttributesTransaction transaction,
                                                  @AndroidDpCoordinate @NotNull Rectangle bounds) {
    transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                             String.format("%ddp", pxToDp(myModel, bounds.x)));
    transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                             String.format("%ddp", pxToDp(myModel, bounds.y)));
  }

  /**
   * Add the attributes relative to the mockup
   *
   * @param transaction the transaction where the attributes will be added
   */
  protected void addMockupAttributes(@NotNull AttributesTransaction transaction,
                                     @MockupCoordinate @NotNull Rectangle bounds) {
    // Add the selected part of the mockup as the new mockup of this transaction
    ensureNewComponentCreated();
    assert myComponent != null;
    final Mockup newMockup = Mockup.create(myComponent, true);
    if (newMockup == null) {
      return;
    }
    final Rectangle cropping = myMockup.getRealCropping();
    newMockup.setCropping(cropping.x + bounds.x,
                          cropping.y + bounds.y,
                          bounds.width,
                          bounds.height);
    transaction.setAttribute(TOOLS_URI, ATTR_MOCKUP_CROP,
                             MockupFileHelper.getPositionString(newMockup));
    transaction.setAttribute(TOOLS_URI, ATTR_MOCKUP, getMockupImagePath(myMockup));
  }

  /**
   * Check if the new component is created, or create it if needed
   */
  private void ensureNewComponentCreated() {
    if (myComponent == null) {
      final XmlTag parentTag = myMockup.getComponent().getTag();
      final XmlTag childTag = parentTag.createChildTag(getAndroidViewTag(), null, null, false);
      myComponent = new NlComponent(myModel, childTag);
    }
  }

  /**
   * Return the String representation of the Mockup's image path
   * @param mockup The mockup to get the image path from
   * @return empty string if the mockup has no associated image path or the image path of the mockup
   */
  @NotNull
  private static String getMockupImagePath(@NotNull Mockup mockup) {
    final Path xmlFilePath = MockupFileHelper.getXMLFilePath(
      mockup.getComponent().getModel().getProject(),
      mockup.getFilePath());
    return xmlFilePath != null ? xmlFilePath.toString() : "";
  }

  /**
   * Helper method to convert pixel to Android dp using the current configuration
   * of the provided model
   * @param model model to find the device configuration
   * @param px Android Coordinates in pixel
   * @return Android Coordinates in dip
   */
  @AndroidDpCoordinate
  protected static int pxToDp(@NotNull NlModel model, @AndroidCoordinate int px) {
    final float dpiValue = model.getConfiguration().getDensity().getDpiValue();
    return Math.round(px * (Density.DEFAULT_DENSITY / dpiValue));
  }
}
