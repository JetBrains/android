/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.api;

import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.FloatResources;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.ui.resourcemanager.ResourcePickerDialog;
import com.android.tools.idea.uibuilder.editor.LayoutNavigationManager;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.util.ArrayUtil;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;

import static com.android.tools.idea.res.FloatResources.parseFloatAttribute;
import static com.android.tools.idea.res.IdeResourcesUtil.resolveStringValue;

/**
 * The UI builder / layout editor as exposed to {@link ViewHandler} instances.
 * This allows the view handlers to query the surrounding editor for more information.
 */
public abstract class ViewEditor {

  /**
   * Tries to resolve the given resource value to a dimension in pixels. The returned value is
   * function of the configuration's device's density.
   *
   * @param resources     the resource resolver to use to follow references
   * @param value         the dimension to resolve
   * @param configuration the device configuration
   * @return a dimension in pixels, or null
   */
  @Nullable
  @AndroidCoordinate
  public static Integer resolveDimensionPixelSize(@NotNull RenderResources resources, @NotNull String value,
                                                  @NotNull Configuration configuration) {
    String resValue = resolveStringValue(resources, value);
    FloatResources.TypedValue out = new FloatResources.TypedValue();
    if (parseFloatAttribute(resValue, out, true)) {
      return FloatResources.TypedValue.complexToDimensionPixelSize(out.data, configuration);
    }
    return null;
  }

  /**
   * Converts a device independent pixel to a screen pixel for the current screen density
   *
   * @param dp the device independent pixel dimension
   * @return the corresponding pixel dimension
   */
  @AndroidCoordinate
  public int dpToPx(@AndroidDpCoordinate int dp) {
    return Coordinates.dpToPx(getScene().getSceneManager(), dp);
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density
   *
   * @param px the pixel dimension (in Android screen pixels)
   * @return the corresponding dp dimension
   */
  @AndroidDpCoordinate
  public int pxToDp(@AndroidCoordinate int px) {
    return Coordinates.pxToDp(getScene().getSceneManager(), px);
  }

  /**
   * Returns the version used to compile the module containing this editor with
   */
  @Nullable
  public abstract AndroidVersion getCompileSdkVersion();

  /**
   * Returns the minSdkVersion for the module containing this editor
   */
  @NotNull
  public abstract AndroidVersion getMinSdkVersion();

  /**
   * Returns the targetSdkVersion for the module containing this editor
   */
  @NotNull
  public abstract AndroidVersion getTargetSdkVersion();

  /**
   * Returns the configuration for the editor
   */
  @NotNull
  public abstract Configuration getConfiguration();

  /**
   * Returns the model for the editor
   */
  @NotNull
  public abstract NlModel getModel();

  /**
   * Returns the SceneManager used to generate a Scene from our model.
   */
  @NotNull
  public abstract LayoutlibSceneManager getSceneBuilder();

  @NotNull
  public abstract Collection<ViewInfo> getRootViews();

  /**
   * Measures the children of the given parent and returns them as a map to view info instances.
   *
   * @param parent the parent component whose children we want to measure
   * @param filter an optional filter we'll apply to the attributes of each of the children
   * @return a map from child to bounds information, if possible
   */
  @Nullable
  public abstract CompletableFuture<Map<NlComponent, Dimension>> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter);

  @Nullable
  public static String displayResourceInput(@NotNull NlModel model, @NotNull EnumSet<ResourceType> types) {
    return displayResourceInput(model, "", types);
  }

  @Nullable
  public static String displayResourceInput(@NotNull NlModel model, @NotNull EnumSet<ResourceType> types, boolean includeSampleData) {
    return displayResourceInput(model, "", types, includeSampleData);
  }

  @Nullable
  public static String displayResourceInput(@NotNull NlModel model, @NotNull String title, @NotNull EnumSet<ResourceType> types) {
    return displayResourceInput(model, title, types, false);
  }

  @Nullable
  public static String displayResourceInput(@NotNull NlModel model, @NotNull String title, @NotNull EnumSet<ResourceType> types, boolean includeSampleData) {
    ResourcePickerDialog dialog = ResourceChooserHelperKt.createResourcePickerDialog(
      title.isEmpty() ? "Pick a Resource" : title,
      null,
      model.getFacet(),
      types,
      null,
      false,
      includeSampleData,
      true, model.getVirtualFile()
    );

    dialog.show();

    if (dialog.isOK()) {
      String resource = dialog.getResourceName();

      if (resource != null && !resource.isEmpty()) {
        return resource;
      }
    }

    return null;
  }

  /**
   * Open a dialog to pick a class among classes derived from a specified set of super classes.
   *
   * @param title        the title representing the class being picked ex: "Fragments", "Views"
   * @param superTypes   the possible super classes that the user is picking a class from
   * @param filter       a filter for the qualified name of the class, or null to specify user defined classes only
   * @param currentValue the current value which may be initially selected in the class selector
   * @return class name if user has selected one, or null if either the user cancelled, no classes were found, or we are in dumb mode.
   */
  @Nullable
  public static String displayClassInput(@NotNull NlModel model,
                                         @NotNull String title,
                                         @NotNull Set<String> superTypes,
                                         @Nullable Predicate<String> filter,
                                         @Nullable String currentValue) {
    Module module = model.getModule();
    String[] superTypesArray = ArrayUtil.toStringArray(superTypes);

    Predicate<PsiClass> psiFilter = ChooseClassDialog.getIsPublicAndUnrestrictedFilter();
    if (filter == null) {
      filter = ChooseClassDialog.getIsUserDefinedFilter();
    }
    psiFilter = psiFilter.and(ChooseClassDialog.qualifiedNameFilter(filter));
    return ChooseClassDialog.openDialog(module, title, currentValue, psiFilter, superTypesArray);
  }

  @NotNull
  public abstract Scene getScene();

  /**
   * If the children have dependencies that are not met by the project, this method will add them after asking the developer.
   * This method should NOT be called from within a write transaction.
   *
   * @return true if the children can be inserted into the parent
   */
  public abstract boolean canInsertChildren(@NotNull NlComponent parent, @NotNull List<NlComponent> children, int index);

  /**
   * Inserts the children into the parent. This method will also add missing dependencies after prompting the developer.
   * If no user interaction is wanted you can call canInsertChildren first and then addDependencies if neccessary.
   * This method can optionally be called from within a write transaction.
   *
   * @param index the index at which to insert the children or -1 to insert them at the end. If existing children are being moved to a new
   *              position, the index is based on the state before the move.
   */
  public abstract void insertChildren(@NotNull NlComponent parent,
                                      @NotNull List<NlComponent> children,
                                      int index,
                                      @NotNull InsertType insertType);

  public static void openResourceFile(@NotNull NlModel model, @NotNull String resourceId) {
    Configuration config = model.getConfiguration();
    ResourceResolver resourceResolver = config.getResourceResolver();
    ResourceValue resValue = resourceResolver.findResValue(resourceId, false);

    VirtualFile file = IdeResourcesUtil.resolveLayout(resourceResolver, resValue);
    if (file == null) {
      return;
    }
    LayoutNavigationManager.getInstance(config.getConfigModule().getProject()).pushFile(model.getVirtualFile(), file);
  }

  /**
   * Returns true if the current module depends on AppCompat.
   */
  public abstract boolean moduleDependsOnAppCompat();
}
