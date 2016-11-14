/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.util.PropertiesMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class RenderResult {
  private static final RenderLogger EMPTY_LOGGER = new RenderLogger(null, null);

  @NotNull private final PsiFile myFile;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final List<ViewInfo> myRootViews;
  @NotNull private final List<ViewInfo> mySystemRootViews;
  @Nullable private final BufferedImage myImage;
  @Nullable private final RenderTask myRenderTask;
  @NotNull private final Result myRenderResult;
  @NotNull private final Map<Object, PropertiesMap> myDefaultProperties;
  @NotNull private final Module myModule;

  protected RenderResult(@NotNull PsiFile file,
                         @NotNull Module module,
                         @NotNull RenderLogger logger,
                         @Nullable RenderTask renderTask,
                         @NotNull Result renderResult,
                         @NotNull List<ViewInfo> rootViews,
                         @NotNull List<ViewInfo> systemRootViews,
                         @Nullable BufferedImage image,
                         @NotNull Map<Object, PropertiesMap> defaultProperties) {
    myRenderTask = renderTask;
    myModule = module;
    myFile = file;
    myLogger = logger;
    myRenderResult = renderResult;
    myRootViews = rootViews;
    mySystemRootViews = systemRootViews;
    myImage = image;
    myDefaultProperties = defaultProperties;
  }

  /**
   * Creates a new {@link RenderResult} from a given RenderTask and RenderSession
   */
  @NotNull
  public static RenderResult create(@NotNull RenderTask renderTask,
                                    @NotNull RenderSession session,
                                    @NotNull PsiFile file,
                                    @NotNull RenderLogger logger) {
    List<ViewInfo> rootViews = session.getRootViews();
    List<ViewInfo> systemRootViews = session.getSystemRootViews();
    Map<Object, PropertiesMap> defaultProperties = session.getDefaultProperties();
    return new RenderResult(
      file,
      renderTask.getModule(),
      logger,
      renderTask,
      session.getResult(),
      rootViews != null ? rootViews : Collections.emptyList(),
      systemRootViews != null ? systemRootViews : Collections.emptyList(),
      session.getImage(), // image might be null if we only inflated the layout but we didn't call render
      defaultProperties != null ? defaultProperties : Collections.emptyMap());
  }

  /**
   * Creates a new session initialization error {@link RenderResult} from a given RenderTask
   */
  @NotNull
  public static RenderResult createSessionInitializationError(@NotNull RenderTask renderTask,
                                                              @NotNull PsiFile file,
                                                              @NotNull RenderLogger logger) {
    return new RenderResult(
      file,
      renderTask.getModule(),
      logger,
      renderTask,
      Result.Status.ERROR_UNKNOWN.createResult("Failed to initialize session"),
      Collections.emptyList(),
      Collections.emptyList(),
      null,
      Collections.emptyMap());
  }

  /**
   * Creates a new blank {@link RenderResult}
   *
   * @param file the PSI file the render result corresponds to
   * @return a blank render result
   */
  @NotNull
  public static RenderResult createBlank(@NotNull PsiFile file) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    assert module != null;
    return new RenderResult(
      file,
      module,
      EMPTY_LOGGER,
      null,
      Result.Status.ERROR_UNKNOWN.createResult(),
      Collections.emptyList(),
      Collections.emptyList(),
      null,
      Collections.emptyMap());
  }

  @NotNull
  public Result getRenderResult() {
    return myRenderResult;
  }

  @NotNull
  public RenderLogger getLogger() {
    return myLogger;
  }

  @Nullable
  public BufferedImage getRenderedImage() {
    return myImage;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @Nullable
  public RenderTask getRenderTask() {
    return myRenderTask;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public List<ViewInfo> getRootViews() {
    return myRootViews;
  }

  @NotNull
  public List<ViewInfo> getSystemRootViews() {
    return mySystemRootViews;
  }

  /**
   * Returns the default properties map. This map contains a list of the widgets default values for every attribute as returned by layoutlib.
   * The map is index by view cookie.
   */
  @NotNull
  public Map<Object, PropertiesMap> getDefaultProperties() {
    return myDefaultProperties;
  }
}
