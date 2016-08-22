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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.nio.file.Path;

import static com.android.SdkConstants.*;

/**
 * Handles the creation of the widgets and layouts from a mockup
 */
public class WidgetCreator {

  public static final String MOCKUP_NEW_LAYOUT_CREATION_ID = "mockupNewLayoutCreation";
  @NotNull private final DesignSurface mySurface;
  private Mockup myMockup;

  public WidgetCreator(@NotNull MockupEditor editor, @NotNull DesignSurface surface) {
    Mockup mockup = editor.getMockup();
    if (mockup != null) {
      setMockup(mockup);
    }
    editor.addListener(this::setMockup);
    mySurface = surface;
  }

  /**
   * Create a new widget of of the size and location of selection
   *
   * @param selection the selection in {@link MockupViewPanel}
   * @param fqcn
   */
  public void createWidget(@NotNull Rectangle selection, @NotNull final String fqcn) {
    final NlComponent parent = myMockup.getComponent();
    final Rectangle parentCropping = myMockup.getRealCropping();
    final NlModel model = parent.getModel();
    final String stringPath = getMockupImagePath(myMockup);

    new CreateNewComponentAction(mySurface, model, fqcn, parent, selection, parentCropping, stringPath)
      .execute();
  }

  public void setMockup(@NotNull Mockup mockup) {
    myMockup = mockup;
  }

  /**
   * Create a include tag inside the component to which the mockup is associated
   *
   * @param bounds       bounds where the include tag will be located
   * @param resourceName name of the layout that will appear in the attribute include="@layout/resourceName"
   */
  private void createIncludeTag(@NotNull Rectangle bounds, @NotNull String resourceName) {
    final String stringPath = getMockupImagePath(myMockup);
    new WriteIncludeTagAction(mySurface, myMockup, myMockup.getComponent().getModel(),
                              resourceName, bounds, stringPath)
      .execute();
  }

  /**
   * Create a new layout that will be included as a child of the mockup component
   *
   * @param bounds bounds of the include tag. Also use to define the size of the new layout
   */
  public void createNewIncludedLayout(@NotNull Rectangle bounds) {
    AndroidFacet facet = myMockup.getComponent().getModel().getFacet();
    ResourceFolderType folderType = AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.get(ResourceType.LAYOUT);
    XmlFile newFile = CreateResourceFileAction.createFileResource(
      facet, folderType, null, null, null, true, null, null, null, false);

    if (newFile == null) {
      return;
    }
    XmlTag rootTag = newFile.getRootTag();
    if (rootTag == null) {
      return;
    }
    NlModel nlModel = NlModel.create(mySurface, newFile.getProject(), facet, newFile);
    ModelListener listener = new ModelListener() {

      @Override
      public void modelChanged(@NotNull NlModel model) {
      }

      @Override
      public void modelRendered(@NotNull NlModel model) {
        model.removeListener(this);
        if (model.getComponents().isEmpty()) {
          return;
        }
        NlComponent component = model.getComponents().get(0);
        new AddAttributeToNewLayout(myMockup, newFile, component, bounds)
          .execute();
      }
    };
    nlModel.addListener(listener);
    nlModel.render();

    String resourceName = getResourceName(newFile);
    createIncludeTag(bounds, resourceName);
  }

  /**
   * Call {@link AndroidCommonUtils#getResourceName(String, String)} with {@link ResourceType#LAYOUT}.
   *
   * @param newFile file to get the resource name from
   * @return the resource name
   */
  @NotNull
  private static String getResourceName(@NotNull XmlFile newFile) {
    return AndroidCommonUtils.getResourceName(
      ResourceType.LAYOUT.getName(),
      newFile.getName());
  }

  /**
   * Add the attribute {@value SdkConstants#ATTR_SHOW_IN} to component
   *
   * @param component the component where the attributes will be added
   */
  private static void addShowInAttribute(@NotNull NlComponent component) {
    final String showInName = getResourceName(component.getModel().getFile());
    component.setAttribute(TOOLS_URI, ATTR_SHOW_IN, LAYOUT_RESOURCE_PREFIX + showInName);
  }

  @NotNull
  private static String getMockupImagePath(@NotNull Mockup mockup) {
    final Path xmlFilePath = MockupFileHelper.getXMLFilePath(
      mockup.getComponent().getModel().getProject(),
      mockup.getFilePath());
    return xmlFilePath != null ? xmlFilePath.toString() : "";
  }

  /**
   * Add the attributes relative to the mockup
   *
   * @param component the component where the attributes will be added
   */
  private static void addMockupAttributes(@NotNull NlComponent component,
                                          @NotNull Rectangle parentCropping,
                                          @NotNull Rectangle bounds,
                                          @NotNull String mockupImagePath) {
    component.setAttribute(TOOLS_URI, ATTR_MOCKUP, mockupImagePath);
    // Add the selected part of the mockup as the new mockup of this component
    final Mockup newMockup = Mockup.create(component);
    newMockup.setCropping(parentCropping.x + bounds.x,
                          parentCropping.y + bounds.y,
                          bounds.width,
                          bounds.height);
    component.setAttribute(TOOLS_URI, ATTR_MOCKUP_POSITION,
                           MockupFileHelper.getPositionString(newMockup));
  }

  /**
   * Add the position attributes for ConstraintLayout
   *
   * @param component
   * @param bounds
   */
  private static void addLayoutEditorPositionAttribute(@NotNull NlComponent component, @NotNull Rectangle bounds) {
    final NlModel model = component.getModel();
    component.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                           String.format("%ddp", pxToDp(model, bounds.x)));
    component.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                           String.format("%ddp", pxToDp(model, bounds.y)));
  }

  /**
   * Adds the width and height attributes
   *
   * @param component
   * @param bounds
   */
  private static void addSizeAttributes(@NotNull NlComponent component, @NotNull Rectangle bounds) {
    final NlModel model = component.getModel();
    component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH,
                           String.format("%ddp", pxToDp(model, bounds.width)));
    component.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT,
                           String.format("%ddp", pxToDp(model, bounds.height)));
  }

  /**
   * Add the include="@layout/resourceName" attribute
   *
   * @param component
   * @param resourceName
   */
  private static void addIncludeAttribute(@NotNull NlComponent component, @NotNull String resourceName) {
    component.setAttribute(null, ATTR_LAYOUT, LAYOUT_RESOURCE_PREFIX + resourceName);
  }

  @AndroidDpCoordinate
  private static int pxToDp(@NotNull NlModel model, @AndroidCoordinate int px) {
    final float dpiValue = model.getConfiguration().getDensity().getDpiValue();
    return Math.round(px * (Density.DEFAULT_DENSITY / dpiValue));
  }

  /**
   * Create a new include tag and add it to the NlModel
   */
  private static class WriteIncludeTagAction extends WriteCommandAction {

    public static final String ACTION_TITLE = "Include new layout";
    private final NlComponent myParent;
    @NotNull private final String myResourceName;
    @NotNull private final Rectangle mySelection;
    private final Rectangle myParentCropping;
    @NotNull private final String myStringPath;
    @NotNull private final DesignSurface mySurface;
    @NotNull private final NlModel myModel;

    public WriteIncludeTagAction(@NotNull DesignSurface surface,
                                 @NotNull Mockup mockup,
                                 @NotNull NlModel model,
                                 @NotNull String resourceName,
                                 @NotNull Rectangle selection,
                                 @NotNull String stringPath) {
      super(model.getProject(), ACTION_TITLE, MOCKUP_NEW_LAYOUT_CREATION_ID, model.getFile());
      mySurface = surface;
      myModel = model;
      myParent = mockup.getComponent();
      myResourceName = resourceName;
      mySelection = selection;
      myParentCropping = mockup.getRealCropping();
      myStringPath = stringPath;
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      final ScreenView currentScreenView = mySurface.getCurrentScreenView();
      if (currentScreenView != null) {
        final NlComponent component = myModel.createComponent(
          currentScreenView, VIEW_INCLUDE, myParent, null, InsertType.CREATE_PREVIEW);
        addIncludeAttribute(component, myResourceName);
        addLayoutEditorPositionAttribute(component, mySelection);
        addSizeAttributes(component, mySelection);
        addMockupAttributes(component, myParentCropping, mySelection, myStringPath);
      }
    }
  }

  /**
   * Add the mockup and size attribute to the newly created layout
   */
  private static class AddAttributeToNewLayout extends WriteCommandAction {

    @NotNull private final NlComponent myComponent;
    @NotNull private final Rectangle mySelection;
    @NotNull private final Mockup myMockup;

    public AddAttributeToNewLayout(@NotNull Mockup mockup,
                                   @NotNull XmlFile newFile,
                                   @NotNull NlComponent component,
                                   @NotNull Rectangle selection) {
      super(newFile.getProject(), "Create new layout", MOCKUP_NEW_LAYOUT_CREATION_ID, newFile);
      myMockup = mockup;
      myComponent = component;
      mySelection = selection;
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      addSizeAttributes(myComponent, mySelection);
      addMockupAttributes(myComponent, myMockup.getCropping(), mySelection, getMockupImagePath(myMockup));
      addShowInAttribute(myComponent);
    }
  }

  /**
   * Create a new widget, using the provided fqcn, inside the mockup's component
   */
  private static class CreateNewComponentAction extends WriteCommandAction {

    @NotNull private final NlModel myModel;
    @NotNull private final String myFqcn;
    @NotNull private final NlComponent myParent;
    @NotNull private final Rectangle myComponentBounds;
    @NotNull private final Rectangle myParentCropping;
    @NotNull private final String myMockupPath;
    @NotNull private final DesignSurface mySurface;

    public CreateNewComponentAction(@NotNull DesignSurface surface,
                                    @NotNull NlModel model,
                                    @NotNull String fqcn,
                                    @NotNull NlComponent parent,
                                    @NotNull Rectangle componentBounds,
                                    @NotNull Rectangle parentCropping,
                                    @NotNull String mockupPath) {
      super(model.getProject(), model.getFile());
      mySurface = surface;
      myModel = model;
      myFqcn = fqcn;
      myParent = parent;
      myComponentBounds = componentBounds;
      myParentCropping = parentCropping;
      myMockupPath = mockupPath;
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      final ScreenView currentScreenView = mySurface.getCurrentScreenView();
      if (currentScreenView != null) {
        final NlComponent component = myModel.createComponent(
          currentScreenView, myFqcn, myParent, null, InsertType.CREATE);
        addLayoutEditorPositionAttribute(component, myComponentBounds);
        addSizeAttributes(component, myComponentBounds);
        addMockupAttributes(component, myParentCropping, myComponentBounds, myMockupPath);
      }
    }
  }
}
