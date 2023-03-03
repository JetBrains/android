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

import com.android.ide.common.rendering.api.*;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.imagepool.ImagePoolImageDisposer;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class RenderResult {
  private static Logger LOG = Logger.getInstance(RenderResult.class);

  @NotNull private final PsiFile myRenderedFile;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final ImmutableList<ViewInfo> myRootViews;
  @NotNull private final ImmutableList<ViewInfo> mySystemRootViews;
  @NotNull private final ImagePool.Image myImage;
  @NotNull private final Result myRenderResult;
  @NotNull private final Map<Object, Map<ResourceReference, ResourceValue>> myDefaultProperties;
  @NotNull private final Map<Object, ResourceReference> myDefaultStyles;
  @NotNull private final Module myModule;
  private final ReadWriteLock myDisposeLock = new ReentrantReadWriteLock();
  @Nullable private final Object myValidatorResult;
  private final boolean myHasRequestedCustomViews;
  @Nullable private final RenderContext myRenderContext;
  private boolean isDisposed;
  private final RenderResultStats myStats;

  protected RenderResult(@NotNull PsiFile renderedFile,
                         @NotNull Module module,
                         @NotNull RenderLogger logger,
                         @Nullable RenderContext renderContext,
                         boolean hasRequestedCustomViews,
                         @NotNull Result renderResult,
                         @NotNull ImmutableList<ViewInfo> rootViews,
                         @NotNull ImmutableList<ViewInfo> systemRootViews,
                         @NotNull ImagePool.Image image,
                         @NotNull Map<Object, Map<ResourceReference, ResourceValue>> defaultProperties,
                         @NotNull Map<Object, ResourceReference> defaultStyles,
                         @Nullable Object validatorResult,
                         @NotNull RenderResultStats stats) {
    myModule = module;
    myRenderContext = renderContext;
    myRenderedFile = renderedFile;
    myLogger = logger;
    myRenderResult = renderResult;
    myRootViews = rootViews;
    mySystemRootViews = systemRootViews;
    myImage = image;
    myDefaultProperties = defaultProperties;
    myDefaultStyles = defaultStyles;
    myValidatorResult = validatorResult;
    myStats = stats;
    myHasRequestedCustomViews = hasRequestedCustomViews;
  }

  /**
   * The {@link #dispose()} may be called in other thread, thus the returned image from {@link #getRenderedImage()} may has been disposed
   * before using. This function gives a chance to process rendered image and guarantee the image is not disposed during the processing.
   * If the image is disposed before executing task, then this function does nothing and return false.
   *
   * @param processTask The task to process on rendered image
   * @return            True if the process is executed, false if the rendered image has disposed before executing processTask.
   */
  public boolean processImageIfNotDisposed(@NotNull Consumer<ImagePool.Image> processTask) {
    myDisposeLock.readLock().lock();
    try {
      if (isDisposed) {
        return false;
      }
      processTask.accept(myImage);
      return true;
    }
    finally {
      myDisposeLock.readLock().unlock();
    }
  }

  public void dispose() {
    myDisposeLock.writeLock().lock();
    try {
      isDisposed = true;
      ImagePoolImageDisposer.disposeImage(myImage);
    } finally {
      myDisposeLock.writeLock().unlock();
    }
  }

  /**
   * Creates a new {@link RenderResult} from a given RenderTask and RenderSession
   */
  @NotNull
  public static RenderResult create(@NotNull RenderContext renderContext,
                                    @NotNull RenderSession session,
                                    @NotNull PsiFile file,
                                    @NotNull RenderLogger logger,
                                    @NotNull ImagePool.Image image,
                                    boolean hasRequestedCustomViews) {
    List<ViewInfo> rootViews = session.getRootViews();
    List<ViewInfo> systemRootViews = session.getSystemRootViews();
    Map<Object, Map<ResourceReference, ResourceValue>> defaultProperties = session.getDefaultNamespacedProperties();
    Map<Object, ResourceReference> defaultStyles = session.getDefaultNamespacedStyles();
    RenderResult result = new RenderResult(
      file,
      renderContext.getModule().getIdeaModule(),
      logger,
      renderContext,
      hasRequestedCustomViews,
      session.getResult(),
      rootViews != null ? ImmutableList.copyOf(rootViews) : ImmutableList.of(),
      systemRootViews != null ? ImmutableList.copyOf(systemRootViews) : ImmutableList.of(),
      image, // image might be ImagePool.NULL_POOL_IMAGE if there is no rendered image (as in layout())
      defaultProperties != null ? ImmutableMap.copyOf(defaultProperties) : ImmutableMap.of(),
      defaultStyles != null ? ImmutableMap.copyOf(defaultStyles) : ImmutableMap.of(),
      session.getValidationData(),
      RenderResultStats.getEMPTY());

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
  }

  /**
   * Creates a new {@link RenderResult} from this with recorded render duration.
   */
  @NotNull
  RenderResult createWithStats(@NotNull RenderResultStats stats) {
    return new RenderResult(
      myRenderedFile,
      myModule,
      myLogger,
      myRenderContext,
      myHasRequestedCustomViews,
      myRenderResult,
      myRootViews,
      mySystemRootViews,
      myImage,
      myDefaultProperties,
      myDefaultStyles,
      myValidatorResult,
      myStats.combine(stats));
  }

  /**
   * Creates a new blank {@link RenderResult}
   *
   * @param file the PSI file the render result corresponds to
   * @return a blank render result
   */
  @NotNull
  public static RenderResult createBlank(@NotNull PsiFile file) {
    return createErrorResult(file, Result.Status.ERROR_UNKNOWN.createResult(""), null);
  }

  /**
   * Creates a blank {@link RenderResult} to report render task creation errors
   *
   * @param file the PSI file the render result corresponds to
   * @param logger the logger containing the errors to surface to the user
   */
  @NotNull
  public static RenderResult createRenderTaskErrorResult(@NotNull PsiFile file, @NotNull RenderLogger logger) {
    return createErrorResult(file, Result.Status.ERROR_RENDER_TASK.createResult(), logger);
  }

  @NotNull
  public static RenderResult createRenderTaskErrorResult(@NotNull PsiFile file, @Nullable Throwable throwable) {
    return createErrorResult(file, Result.Status.ERROR_RENDER_TASK.createResult("Render error", throwable), null);
  }

  @NotNull
  private static RenderResult createErrorResult(@NotNull PsiFile file, @NotNull Result errorResult, @Nullable RenderLogger logger) {
    Module module = ReadAction.compute(() -> ModuleUtilCore.findModuleForPsiElement(file));
    assert module != null;
    RenderResult result = new RenderResult(
      file,
      module,
      logger != null ? logger : new RenderLogger(module, null, false),
      null,
      false,
      errorResult,
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of(),
      ImmutableMap.of(),
      null,
      RenderResultStats.getEMPTY());

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
  }

  @NotNull
  public Result getRenderResult() {
    return myRenderResult;
  }

  @NotNull
  public RenderLogger getLogger() {
    return myLogger;
  }

  @NotNull
  public ImagePool.Image getRenderedImage() {
    myDisposeLock.readLock().lock();
    try {
      return !isDisposed ? myImage : ImagePool.NULL_POOLED_IMAGE;
    } finally {
      myDisposeLock.readLock().unlock();
    }
  }

  /**
   * Returns the source {@link PsiFile} if available. This might be different from the {@link #getRenderedFile()} in cases where the
   * render is generated via a synthetic layout (like menus, drawables or Compose.).
   * If you want to access the user source file name, use this method.
   */
  @SuppressWarnings("UnstableApiUsage")
  @NotNull
  public PsiFile getSourceFile() {
    VirtualFile renderedVirtualFile = myRenderedFile.getVirtualFile();
    if (!renderedVirtualFile.isInLocalFileSystem() && renderedVirtualFile instanceof BackedVirtualFile) {
      VirtualFile sourceVirtualFile = ((BackedVirtualFile)renderedVirtualFile).getOriginFile();
      PsiFile sourcePsiFile = AndroidPsiUtils.getPsiFileSafely(myRenderedFile.getProject(), sourceVirtualFile);
      if (sourcePsiFile != null) return sourcePsiFile;
    }

    return myRenderedFile;
  }

  /**
   * Returns the {@link PsiFile} being rendered. This might be different from the actual user source file when the render comes from a
   * layout generated synthetically by the Layout Editor. This happens in cases like menus, drawables or Compose.
   */
  @NotNull
  public PsiFile getRenderedFile() { return myRenderedFile; }

  @Nullable
  public RenderContext getRenderContext() {
    return myRenderContext;
  }

  @NotNull
  public Module getModule() {
    return myModule;
  }

  @NotNull
  public ImmutableList<ViewInfo> getRootViews() {
    return myRootViews;
  }

  @NotNull
  public ImmutableList<ViewInfo> getSystemRootViews() {
    return mySystemRootViews;
  }

  @Nullable
  public Object getValidatorResult() {
    return myValidatorResult;
  }

  /**
   * Returns the default properties map. This map contains a list of the widgets default values for every attribute as returned by layoutlib.
   * The map is index by view cookie.
   */
  @NotNull
  public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties() {
    return myDefaultProperties;
  }

  /**
   * Returns the default style map. This map contains the default style of the widgets as returned by layoutlib.
   * The map is index by view cookie.
   */
  @NotNull
  public Map<Object, ResourceReference> getDefaultStyles() {
    return myDefaultStyles;
  }

  /**
   * Returns whether the render has requested any custom views.
   */
  public boolean hasRequestedCustomViews() {
    return myHasRequestedCustomViews;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("renderResult", myRenderResult)
      .add("psiFile", myRenderedFile)
      .add("rootViews", myRootViews)
      .add("systemViews", mySystemRootViews)
      .toString();
  }

  @NotNull
  public RenderResultStats getStats() {
    return myStats;
  }
}
