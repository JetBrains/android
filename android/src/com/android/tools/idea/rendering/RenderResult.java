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
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

import static com.android.tools.idea.rendering.RenderedImage.ShadowType;

public class RenderResult {
  @NotNull private final PsiFile myFile;
  @NotNull private final RenderLogger myLogger;
  @Nullable private final List<ViewInfo> myRootViews;
  @Nullable private final List<ViewInfo> mySystemRootViews;
  @Nullable private final RenderedImage myImage;
  @Nullable private final RenderTask myRenderTask;
  @NotNull private final Result myRenderResult;
  @Nullable private IncludeReference myIncludedWithin = IncludeReference.NONE;
  @NotNull private final Rectangle myImageBounds;

  public RenderResult(@Nullable RenderTask renderTask,
                      @Nullable RenderSession session,
                      @NotNull PsiFile file,
                      @NotNull RenderLogger logger) {
    myRenderTask = renderTask;
    myFile = file;
    myLogger = logger;
    myRenderResult = session != null ? session.getResult() : Result.Status.ERROR_UNKNOWN.createResult("Failed to initialize session");

    if (session != null && myRenderResult.isSuccess() && renderTask != null) {
      List<ViewInfo> rootViews = session.getRootViews();
      myRootViews = rootViews != null ? ImmutableList.copyOf(rootViews) : null;
      List<ViewInfo> systemRootViews = session.getSystemRootViews();
      mySystemRootViews = systemRootViews != null ? ImmutableList.copyOf(systemRootViews) : null;

      Configuration configuration = renderTask.getConfiguration();
      BufferedImage image = session.getImage();
      boolean alphaChannelImage = session.isAlphaChannelImage() || renderTask.requiresTransparency();
      ShadowType shadowType = alphaChannelImage ? ShadowType.NONE : ShadowType.RECTANGULAR;
      if (shadowType == ShadowType.NONE && renderTask.isNonRectangular()) {
        shadowType = ShadowType.ARBITRARY;
      } else {
        Device device = renderTask.getConfiguration().getDevice();
        if (device != null && device.isScreenRound()) {
          shadowType = ShadowType.ARBITRARY;
        }
      }
      // image might be null if we only inflated the layout but we didn't call render
      myImage = image != null ? new RenderedImage(configuration, image, alphaChannelImage, shadowType) : null;
    } else {
      myRootViews = null;
      mySystemRootViews = null;
      myImage = null;
    }

    myImageBounds =
      new Rectangle(0, 0, myImage != null ? myImage.getOriginalWidth() : 0, myImage != null ? myImage.getOriginalHeight() : 0);
  }

  /**
   * Creates a new blank {@link RenderResult}
   *
   * @param file the PSI file the render result corresponds to
   * @param logger the optional logger
   * @return a blank render result
   */
  @NotNull
  public static RenderResult createBlank(@NotNull PsiFile file, @Nullable RenderLogger logger) {
    Module module = ModuleUtilCore.findModuleForPsiElement(file);
    return new RenderResult(null, null, file, logger != null ? logger : new RenderLogger(null, module));
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
  public RenderedImage getImage() {
    return myImage;
  }

  @Nullable
  public BufferedImage getRenderedImage() {
    return myImage != null ? myImage.getOriginalImage() : null;
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
    Module module = myLogger.getModule();
    // This method should only be called on a valid render result
    assert module != null;
    return module;
  }

  @Nullable
  public List<ViewInfo> getRootViews() {
    return myRootViews;
  }

  @Nullable
  public IncludeReference getIncludedWithin() {
    return myIncludedWithin;
  }

  public void setIncludedWithin(@Nullable IncludeReference includedWithin) {
    myIncludedWithin = includedWithin;
  }

  @NotNull
  public Rectangle getOriginalBounds() {
    return myImageBounds;
  }
}
