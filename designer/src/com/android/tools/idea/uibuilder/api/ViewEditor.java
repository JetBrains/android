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
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.resources.ResourceType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.*;
import java.util.function.Predicate;

import static com.android.SdkConstants.VALUE_N_DP;
import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.res.ResourceHelper.parseFloatAttribute;
import static com.android.tools.idea.res.ResourceHelper.resolveStringValue;

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
    ResourceHelper.TypedValue out = new ResourceHelper.TypedValue();
    if (parseFloatAttribute(resValue, out, true)) {
      return ResourceHelper.TypedValue.complexToDimensionPixelSize(out.data, configuration);
    }
    return null;
  }

  public abstract int getDpi();

  /**
   * Converts a device independent pixel to a screen pixel for the current screen density
   *
   * @param dp the device independent pixel dimension
   * @return the corresponding pixel dimension
   */
  public int dpToPx(int dp) {
    int dpi = getDpi();
    return dp * dpi / DEFAULT_DENSITY;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density
   *
   * @param px the pixel dimension (in Android screen pixels)
   * @return the corresponding dp dimension
   */
  public int pxToDp(@AndroidCoordinate int px) {
    int dpi = getDpi();
    return px * DEFAULT_DENSITY / dpi;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density,
   * and returns it as a dimension string.
   *
   * @param px the pixel dimension
   * @return the corresponding dp dimension string
   */
  @NotNull
  public String pxToDpWithUnits(int px) {
    return String.format(Locale.US, VALUE_N_DP, pxToDp(px));
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

  public abstract boolean moduleContainsResource(@NotNull ResourceType type, @NotNull String name);

  public abstract void copyVectorAssetToMainModuleSourceSet(@NotNull String asset);

  public abstract void copyLayoutToMainModuleSourceSet(@NotNull String layout, @Language("XML") @NotNull String xml);

  /**
   * Measures the children of the given parent and returns them as a map to view info instances.
   *
   * @param parent the parent component whose children we want to measure
   * @param filter an optional filter we'll apply to the attributes of each of the children
   * @return a map from child to bounds information, if possible
   */
  @Nullable
  public abstract Map<NlComponent, Dimension> measureChildren(@NotNull NlComponent parent, @Nullable RenderTask.AttributeFilter filter);

  @Nullable
  public final String displayResourceInput(@NotNull EnumSet<ResourceType> types) {
    return displayResourceInput("", types);
  }

  @Nullable
  public abstract String displayResourceInput(@NotNull String title, @NotNull EnumSet<ResourceType> types);

  @Nullable
  public abstract String displayClassInput(@NotNull Set<String> superTypes,
                                           @Nullable Predicate<PsiClass> filter,
                                           @Nullable String currentValue);

  /**
   * Opens the resource using the resource resolver in the configuration.
   *
   * @param reference   the resource reference
   * @param currentFile the currently open file. It's pushed onto the file navigation stack under the resource to open.
   * @return true if the resource was opened
   * @see RenderResources#findResValue(String, boolean)
   */
  public abstract boolean openResource(@NotNull Configuration configuration, @NotNull String reference, @NotNull VirtualFile currentFile);
}
