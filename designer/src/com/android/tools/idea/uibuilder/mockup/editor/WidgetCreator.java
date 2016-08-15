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
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.MockupFileHelper;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
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

  private final ScreenView myScreenView;
  private final NlModel myModel;
  private Mockup myMockup;

  public WidgetCreator(Mockup mockup, ScreenView screenView) {
    myMockup = mockup;
    myScreenView = screenView;
    myModel = myScreenView.getModel();
  }

  /**
   * Create a new widget of of the size and location of selection
   *
   * @param selection the selection in {@link MockupViewPanel}
   * @param fqcn
   */
  public void createWidget(Rectangle selection, final String fqcn) {
    final NlComponent parent = myMockup.getComponent();
    final Rectangle parentCropping = myMockup.getRealCropping();
    final NlModel model = parent.getModel();
    final String stringPath = getMockupImagePath();

    new CreateNewWidgetAction(model, fqcn, parent, selection, parentCropping, stringPath)
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
  private void createIncludeTag(Rectangle bounds, String resourceName) {
    final String stringPath = getMockupImagePath();
    new WriteIncludeTagAction(resourceName, bounds, stringPath)
      .execute();
  }

  /**
   * Create a new layout that will be included as a child of the mockup component
   *
   * @param bounds bounds of the include tag. Also use to define the size of the new layout
   */
  public void createNewIncludedLayout(final Rectangle bounds) {
    final AndroidFacet facet = myMockup.getComponent().getModel().getFacet();
    final ResourceFolderType folderType = AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.get(ResourceType.LAYOUT);
    final XmlFile newFile = CreateResourceFileAction.createFileResource(
      facet, folderType, null, null, null, true, null, null, null, false);

    if (newFile == null) {
      return;
    }
    final XmlTag rootTag = newFile.getRootTag();
    if (rootTag == null) {
      return;
    }
    final NlModel nlModel = NlModel.create(myScreenView.getSurface(), newFile.getProject(), facet, newFile);
    final ModelListener listener = new ModelListener() {

      @Override
      public void modelChanged(@NotNull NlModel model) {
      }

      @Override
      public void modelRendered(@NotNull NlModel model) {
        model.removeListener(this);
        if (model.getComponents().isEmpty()) {
          return;
        }
        final NlComponent component = model.getComponents().get(0);
        new AddAttributeToNewLayout(newFile, component, bounds)
          .execute();
      }
    };
    nlModel.addListener(listener);
    nlModel.render();

    final String resourceName = getResourceName(newFile);
    createIncludeTag(bounds, resourceName);
  }

  /**
   * Call {@link AndroidCommonUtils#getResourceName(String, String)} with {@link ResourceType#LAYOUT}.
   *
   * @param newFile file to get the resource name from
   * @return the resource name
   */
  @NotNull
  private static String getResourceName(XmlFile newFile) {
    return AndroidCommonUtils.getResourceName(
      ResourceType.LAYOUT.getName(),
      newFile.getName());
  }

  /**
   * Add the attribute {@value SdkConstants#ATTR_SHOW_IN} to component
   *
   * @param component the component where the attributes will be added
   */
  private void addShowInAttribute(NlComponent component) {
    final String showInName = getResourceName(myScreenView.getModel().getFile());
    component.setAttribute(TOOLS_URI, ATTR_SHOW_IN, LAYOUT_RESOURCE_PREFIX + showInName);
  }

  private String getMockupImagePath() {
    final Path xmlFilePath = MockupFileHelper.getXMLFilePath(
      myScreenView.getModel().getProject(),
      myMockup.getFilePath());
    return xmlFilePath != null ? xmlFilePath.toString() : "";
  }

  /**
   * Add the attributes relative to the mockup
   *
   * @param component the component where the attributes will be added
   */
  private static void addMockupAttributes(NlComponent component, Rectangle parentCropping, Rectangle bounds, String mockupImagePath) {
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
  private void addLayoutEditorPositionAttribute(NlComponent component, Rectangle bounds) {
    component.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X,
                           String.format("%ddp", Coordinates.pxToDp(myScreenView, bounds.x)));
    component.setAttribute(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y,
                           String.format("%ddp", Coordinates.pxToDp(myScreenView, bounds.y)));
  }

  /**
   * Adds the width and height attributes
   *
   * @param component
   * @param bounds
   */
  private void addSizeAttributes(NlComponent component, Rectangle bounds) {
    component.setAttribute(ANDROID_URI, ATTR_LAYOUT_WIDTH,
                           String.format("%ddp", Coordinates.pxToDp(myScreenView, bounds.width)));
    component.setAttribute(ANDROID_URI, ATTR_LAYOUT_HEIGHT,
                           String.format("%ddp", Coordinates.pxToDp(myScreenView, bounds.height)));
  }

  /**
   * Add the include="@layout/resourceName" attribute
   *
   * @param component
   * @param resourceName
   */
  private static void addIncludeAttribute(NlComponent component, String resourceName) {
    component.setAttribute(null, ATTR_LAYOUT, LAYOUT_RESOURCE_PREFIX + resourceName);
  }

  /**
   * Create a new include tag and add it to the NlModel
   */
  private class WriteIncludeTagAction extends WriteCommandAction {

    public static final String ACTION_TITLE = "Include new layout";
    private final NlComponent myParent;
    private final String myResourceName;
    private final Rectangle mySelection;
    private final Rectangle myParentCropping;
    private final String myStringPath;

    public WriteIncludeTagAction(String resourceName,
                                 Rectangle selection,
                                 String stringPath) {
      super(myModel.getProject(), ACTION_TITLE, MOCKUP_NEW_LAYOUT_CREATION_ID, myModel.getFile());
      myParent = myMockup.getComponent();
      myResourceName = resourceName;
      mySelection = selection;
      myParentCropping = myMockup.getRealCropping();
      myStringPath = stringPath;
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      final NlComponent component = myModel.createComponent(
        myScreenView, VIEW_INCLUDE, myParent, null, InsertType.CREATE_PREVIEW);
      addIncludeAttribute(component, myResourceName);
      addLayoutEditorPositionAttribute(component, mySelection);
      addSizeAttributes(component, mySelection);
      addMockupAttributes(component, myParentCropping, mySelection, myStringPath);
    }
  }

  /**
   * Add the mockup and size attribute to the newly created layout
   */
  private class AddAttributeToNewLayout extends WriteCommandAction {

    private final NlComponent myComponent;
    private final Rectangle mySelection;

    public AddAttributeToNewLayout(XmlFile newFile, NlComponent component, Rectangle selection) {
      super(newFile.getProject(), "Create new layout", MOCKUP_NEW_LAYOUT_CREATION_ID, newFile);
      myComponent = component;
      mySelection = selection;
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      addSizeAttributes(myComponent, mySelection);
      addMockupAttributes(myComponent, myMockup.getCropping(), mySelection, getMockupImagePath());
      addShowInAttribute(myComponent);
    }
  }

  /**
   * Create a new widget, using the provided fqcn, inside the mockup's component
   */
  private class CreateNewWidgetAction extends WriteCommandAction {

    private final NlModel myModel;
    private final String myFqcn;
    private final NlComponent myParent;
    private final Rectangle mySelection;
    private final Rectangle myParentCropping;
    private final String myStringPath;

    public CreateNewWidgetAction(NlModel model,
                                 String fqcn,
                                 NlComponent parent,
                                 Rectangle selection,
                                 Rectangle parentCropping,
                                 String stringPath) {
      super(model.getProject(), model.getFile());
      myModel = model;
      myFqcn = fqcn;
      myParent = parent;
      mySelection = selection;
      myParentCropping = parentCropping;
      myStringPath = stringPath;
    }

    @Override
    protected void run(@NotNull Result result) throws Throwable {
      final NlComponent component = myModel.createComponent(
        myScreenView, myFqcn, myParent, null, InsertType.CREATE);
      addLayoutEditorPositionAttribute(component, mySelection);
      addSizeAttributes(component, mySelection);
      addMockupAttributes(component, myParentCropping, mySelection, myStringPath);
    }
  }
}
