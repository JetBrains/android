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

import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupCoordinate;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.mockup.editor.creators.viewgroupattributes.ViewGroupAttributesManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Collections;

import static com.android.SdkConstants.*;

/**
 * The {@link WidgetCreator} is the base class to create a new widget or resource with
 * data extracted from the Mockup and add it the model.
 *
 * <p>
 * The subclasses should implement {@link #getAndroidViewTag()} and return the desired tag for the
 * newly created {@link NlComponent}. If the subclass creates only a resource, it can return an empty string.
 * </p>
 *
 * <p>
 * If the component needs do any processing before adding the component, such as running the color extractor,
 * It can implements {@link #getOptionsComponent(DoneCallback)} and {@link #hasOptionsComponent()}. Also, if the creator
 * only creates a resource, all the processing can be done using the {@link #getOptionsComponent(DoneCallback)} method. In this case,
 * The {@link #addToModel()} method should be overridden to avoid the creation of a new component.
 * </p>
 *
 * <p>
 * The pre-processing is done inside {@link #getOptionsComponent(DoneCallback)}, and once done the callback should
 * be called with {@link DoneCallback#done(int)} and {@link DoneCallback#FINISH}. To cancel the component addition,
 * the callback can also be called with {@link DoneCallback#CANCEL}.
 * </p>
 *
 * <p>
 * {@link #getOptionsComponent(DoneCallback)} does not need to return a component if not action is needed from the
 * user to do the pre-processing.
 * </p>
 *
 * <p>
 * The {@link WidgetCreatorFactory} is useful to create a WidgetCreator using the only View name.
 * </p>
 *
 * <p>
 * This class also provide helper methods to add attributes common to all Views
 * </p>
 *
 * @see WidgetCreatorFactory
 * @see com.android.tools.idea.uibuilder.mockup.editor.tools.ExtractWidgetTool
 */
public abstract class WidgetCreator {

  private static final String COLORS_XML = "colors.xml";
  private final NlModel myModel;
  private final SceneView myScreenView;
  @NotNull private Mockup myMockup;
  @Nullable private NlComponent myComponent;
  @Nullable private ViewGroupAttributesManager myViewGroupAttributesManager;

  /**
   * Create a new  View
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link NlDesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates
   */
  protected WidgetCreator(@NotNull Mockup mockup, @NotNull NlModel model, @NotNull SceneView screenView) {
    myMockup = mockup;
    myModel = model;
    myScreenView = screenView;
  }

  /**
   * Create a color resource with the provided name and color.
   *
   * IF the color name is empty, the method won't create the resource and won't throw any exception
   *
   * @param colorName The name for the color value
   * @param color     The value for the resource
   * @param model
   */
  protected static void createColorResource(@NotNull String colorName, @NotNull Color color, @NotNull NlModel model) {
    if (colorName.isEmpty()) {
      Logger.getInstance(FloatingActionButtonCreator.class).error("The color name can't be empty. Aborting color resource creation");
      return;
    }
    VirtualFile primaryResourceDir = ResourceFolderManager.getInstance(model.getFacet()).getPrimaryFolder();
    FolderConfiguration configForFolder = FolderConfiguration.getConfigForFolder(ResourceFolderType.VALUES.getName());
    if (primaryResourceDir != null && configForFolder != null) {
      AndroidResourceUtil.createValueResource(
        model.getProject(),
        primaryResourceDir,
        colorName,
        ResourceType.COLOR,
        COLORS_XML,
        Collections.singletonList(
          configForFolder.getFolderName(ResourceFolderType.VALUES)),
        String.format("#%06X", color.getRGB()) // write the color value in hex format (#RRGGBB)
      );
    }
  }

  /**
   * Add a LayoutManager if the container of the new widget needs special attribute
   *
   * @param viewGroupAttributesManager
   */
  public void setViewGroupAttributesManager(@Nullable ViewGroupAttributesManager viewGroupAttributesManager) {
    myViewGroupAttributesManager = viewGroupAttributesManager;
  }

  /**
   * Get the mockup used in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   *
   * @return the mockup used in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  @NotNull
  protected final Mockup getMockup() {
    return myMockup;
  }

  /**
   * Get the current model associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   *
   * @return the current model associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor
   */
  protected final NlModel getModel() {
    return myModel;
  }

  /**
   * Get the screen view associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   *
   * @return the screen view associated with the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  @NotNull
  public SceneView getScreenView() {
    return myScreenView;
  }

  /**
   * This is where any modification to the new component should be added.
   * The transaction will be committed before the addition of the component to the model
   *
   * @param transaction open transaction to edit attributes of the new component.
   */
  abstract protected void addAttributes(@NotNull AttributesTransaction transaction);

  /**
   * Add the new component to the model provided in the constructor
   *
   * @return
   */
  @Nullable
  public NlComponent addToModel() {
    ensureNewComponentCreated();
    assert myComponent != null;
    AttributesTransaction transaction = myComponent.startAttributeTransaction();
    addAttributes(transaction);
    if (myViewGroupAttributesManager != null) {
      myViewGroupAttributesManager.addLayoutAttributes(transaction);
    }
    transaction.commit();
    myModel.addComponents(Collections.singletonList(myComponent), myMockup.getComponent(), null, InsertType.CREATE_PREVIEW,
                          ViewEditorImpl.getOrCreate(myScreenView));
    return myComponent;
  }

  /**
   * @return true is the implementing class provides a Option compornent to
   * retrieve information from the user before creating the widget
   * @see #getOptionsComponent(DoneCallback)
   */
  public boolean hasOptionsComponent() {
    return false;
  }

  /**
   * Return a JComponent meant to retrieve information from the user. It is up
   * to the implementing method to save the information to add them later, if needed, in the attribute transaction
   * inside {@link #addAttributes(AttributesTransaction)}.
   *
   * @param doneCallback The callback to notify the calling method that the user interaction
   *                     with the component is done.
   * @return The component the calling method has to display
   * @see #hasOptionsComponent()
   */
  @Nullable
  public JComponent getOptionsComponent(@NotNull DoneCallback doneCallback) {
    return null;
  }

  /**
   * Adds the width and height attributes
   *
   * @param transaction
   * @param bounds
   */
  protected void addSizeAttributes(@NotNull AttributesTransaction transaction, @AndroidCoordinate @NotNull Rectangle bounds) {
    transaction.setAttribute(null, ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH,
                             String.format("%ddp", Coordinates.pxToDp(myModel, bounds.width)));
    transaction.setAttribute(null, ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT,
                             String.format("%ddp", Coordinates.pxToDp(myModel, bounds.height)));
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
                             String.format("%ddp", Coordinates.pxToDp(myModel, bounds.x)));
    transaction.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                             String.format("%ddp", Coordinates.pxToDp(myModel, bounds.y)));
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
    final Rectangle cropping = myMockup.getComputedCropping();
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
      myComponent = myModel.createComponent(childTag);
    }
  }

  /**
   * Return the String representation of the Mockup's image path
   *
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
   * Override to specify the tag of the new {@link NlComponent}
   *
   * @return the Tag of the Android View as it will appears in the XML file
   */
  @NotNull
  public abstract String getAndroidViewTag();

  /**
   * Callback to notify the end if the pre-processing in {@link #getOptionsComponent(DoneCallback)}
   */
  public interface DoneCallback {
    int FINISH = 1;
    int CANCEL = 0;

    /**
     * Notify that the calling class is done with the pre-processing on the component attributes
     *
     * @param doneType {@link #FINISH} to notify that the widget can be added to the {@link NlModel}.
     *                 {@link #CANCEL} to cancel the addition to the NlModel
     */
    void done(@MagicConstant(intValues = {FINISH, CANCEL}) int doneType);
  }

  /**
   * Holder class for the color and the associated name
   */
  public static class ColorResourceHolder {

    @Nullable public Color value;
    @Nullable public String name;

    public ColorResourceHolder(@Nullable Color value, @Nullable String name) {
      this.value = value;
      this.name = name;
    }
  }
}
