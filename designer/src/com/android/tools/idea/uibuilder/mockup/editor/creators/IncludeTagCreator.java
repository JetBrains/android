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

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.command.NlWriteCommandAction;
import com.android.tools.idea.common.model.AttributesTransaction;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.SdkConstants.*;

/**
 * Create a new layout and an include tag referencing this layout
 * in the {@link NlModel} provided in the constructor
 */
public class IncludeTagCreator extends SimpleViewCreator {

  private String myNewLayoutResource;

  /**
   * Create a new layout and an include tag referencing this layout
   * in the {@link NlModel} provided in the constructor
   *
   * @param mockup     the mockup to extract the information from
   * @param model      the model to insert the new component into
   * @param screenView The currentScreen view displayed in the {@link NlDesignSurface}.
   *                   Used to convert the size of component from the mockup to the Android coordinates.
   * @param selection  The selection made in the {@link com.android.tools.idea.uibuilder.mockup.editor.MockupEditor}
   */
  public IncludeTagCreator(@NotNull Mockup mockup,
                           @NotNull NlModel model,
                           @NotNull ScreenView screenView, @NotNull Rectangle selection) {
    super(mockup, model, screenView, selection);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void addAttributes(@NotNull AttributesTransaction transaction) {
    myNewLayoutResource = createNewIncludedLayout();
    super.addAttributes(transaction);
    if (myNewLayoutResource != null) {
      addIncludeAttribute(transaction, myNewLayoutResource);
    }
  }

  /**
   * {@inheritDoc}
   */
  @NotNull
  @Override
  public String getAndroidViewTag() {
    return VIEW_INCLUDE;
  }

  @Nullable
  @Override
  public NlComponent addToModel() {
    NlComponent component = getMockup().getComponent();
    if (NlComponentHelperKt.isOrHasSuperclass(component, CLASS_RECYCLER_VIEW)) {
      addListItemAttribute(component);
      return component;
    }
    else {
      return super.addToModel();
    }
  }

  private void addListItemAttribute(NlComponent component) {
    String newLayoutResource = createNewIncludedLayout();
    NlWriteCommandAction.run(component, "Add listitem attribute", () ->
      component.setAttribute(TOOLS_URI, ATTR_LISTITEM, LAYOUT_RESOURCE_PREFIX + newLayoutResource));
  }

  /**
   * Add the include="@layout/resourceName" attribute
   *
   * @param transaction
   * @param resourceName
   */
  private static void addIncludeAttribute(@NotNull AttributesTransaction transaction, @NotNull String resourceName) {
    transaction.setAttribute(null, ATTR_LAYOUT, LAYOUT_RESOURCE_PREFIX + resourceName);
  }

  /**
   * Create a new layout that will be included as a child of the mockup component
   */
  private String createNewIncludedLayout() {
    AndroidFacet facet = getMockup().getComponent().getModel().getFacet();
    ResourceFolderType folderType = AndroidResourceUtil.XML_FILE_RESOURCE_TYPES.get(ResourceType.LAYOUT);
    XmlFile newFile = CreateResourceFileAction.createFileResource(
      facet, folderType, null, null, null, true, null, null, null, false);

    if (newFile == null) {
      return null;
    }
    XmlTag rootTag = newFile.getRootTag();
    if (rootTag == null) {
      return null;
    }
    NlModel nlModel = NlModel.create(getScreenView().getSurface(), newFile.getProject(), facet, newFile);
    ModelListener listener = new ModelListener() {

      @Override
      public void modelRendered(@NotNull NlModel model) {
        model.removeListener(this);
        if (model.getComponents().isEmpty()) {
          return;
        }
        NlComponent component = model.getComponents().get(0);
        final AttributesTransaction transaction = component.startAttributeTransaction();
        addShowInAttribute(transaction);
        addSizeAttributes(transaction, getAndroidBounds());
        addMockupAttributes(transaction, getSelectionBounds());
        NlWriteCommandAction.run(component, "", transaction::commit);
      }

      @Override
      public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
        // Do nothing
      }
    };
    nlModel.addListener(listener);
    return getResourceName(newFile);
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
   * Add the attribute {@value SdkConstants#ATTR_SHOW_IN} to transaction
   *
   * @param transaction the transaction where the attributes will be added
   */
  private void addShowInAttribute(@NotNull AttributesTransaction transaction) {
    final String showInName = getResourceName(getModel().getFile());
    transaction.setAttribute(TOOLS_URI, ATTR_SHOW_IN, LAYOUT_RESOURCE_PREFIX + showInName);
  }
}
