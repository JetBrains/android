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
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.util.List;

import static com.android.tools.idea.rendering.RenderedImage.ShadowType;

public class RenderResult {
  @NotNull private final PsiFile myFile;
  @NotNull private final RenderLogger myLogger;
  @Nullable private final List<ViewInfo> myRootViews;
  @Nullable private final RenderedImage myImage;
  @Nullable private RenderedViewHierarchy myHierarchy;
  @Nullable private final RenderService myRenderService;
  @Nullable private final RenderSession mySession; // TEMPORARY

  public RenderResult(@Nullable RenderService renderService,
                      @Nullable RenderSession session,
                      @NotNull PsiFile file,
                      @NotNull RenderLogger logger) {
    myRenderService = renderService;
    mySession = session;
    myFile = file;
    myLogger = logger;
    if (session != null && session.getResult().isSuccess() && renderService != null) {
      myRootViews = session.getRootViews();
      Configuration configuration = renderService.getConfiguration();
      BufferedImage image = session.getImage();
      boolean alphaChannelImage = session.isAlphaChannelImage() || renderService.requiresTransparency();
      ShadowType shadowType = alphaChannelImage ? ShadowType.NONE : ShadowType.RECTANGULAR;
      if (shadowType == ShadowType.NONE && renderService.isNonRectangular()) {
        shadowType = ShadowType.ARBITRARY;
      }
      myImage = new RenderedImage(configuration, image, alphaChannelImage, shadowType);
    } else {
      myRootViews = null;
      myImage = null;
    }
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

  @Nullable
  public RenderSession getSession() {
    return mySession;
  }

  @NotNull
  public RenderLogger getLogger() {
    return myLogger;
  }

  @Nullable
  public RenderedViewHierarchy getHierarchy() {
    if (myHierarchy == null && myRootViews != null) {
      myHierarchy = RenderedViewHierarchy.create(myFile, myRootViews);
    }

    return myHierarchy;
  }

  @Nullable
  public RenderedImage getImage() {
    return myImage;
  }

  @Nullable
  public List<ViewInfo> getRootViews() {
    return myRootViews;
  }

  @NotNull
  public PsiFile getFile() {
    return myFile;
  }

  @Nullable
  public RenderService getRenderService() {
    return myRenderService;
  }

  @NotNull
  public Module getModule() {
    return myLogger.getModule();
  }
}
