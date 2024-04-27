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
package com.android.tools.rendering;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.rendering.api.EnvironmentContext;
import com.android.tools.rendering.api.IdeaModuleProvider;
import com.android.tools.rendering.api.RenderModelModule;
import com.android.tools.rendering.imagepool.ImagePool;
import com.android.tools.rendering.imagepool.ImagePoolImageDisposer;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import java.awt.Dimension;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class RenderResult {
  private static Logger LOG = Logger.getInstance(RenderResult.class);

  @NotNull private final Supplier<PsiFile> mySourceFileProvider;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final ImmutableList<ViewInfo> myRootViews;
  @NotNull private final ImmutableList<ViewInfo> mySystemRootViews;
  @NotNull private final ImagePool.Image myImage;
  @NotNull private final Result myRenderResult;
  @NotNull private final Map<Object, Map<ResourceReference, ResourceValue>> myDefaultProperties;
  @NotNull private final Map<Object, ResourceReference> myDefaultStyles;
  @NotNull private final IdeaModuleProvider myModule;
  @NotNull private final Project myProject;
  private final ReadWriteLock myDisposeLock = new ReentrantReadWriteLock();
  @Nullable private final Object myValidatorResult;
  private final boolean myHasRequestedCustomViews;
  @Nullable private final RenderContext myRenderContext;
  private boolean isDisposed;
  private final RenderResultStats myStats;

  /**
   * This is used to cache the dimensions for this result. It is calculated by getting the dimensions
   * of the root ViewInfo.
   * If there are no view infos, this can be used to cache a previous result.
   */
  @NotNull
  private final Dimension myRootViewDimensions;

  public RenderResult(@NotNull Supplier<PsiFile> sourceFileProvider,
                      @NotNull Project project,
                      @NotNull IdeaModuleProvider module,
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
                      @NotNull Dimension rootViewDimensions,
                      @NotNull RenderResultStats stats) {
    myModule = module;
    myProject = project;
    myRenderContext = renderContext;
    mySourceFileProvider = sourceFileProvider;
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
    myRootViewDimensions = rootViewDimensions;
  }

  @TestOnly
  protected RenderResult(RenderResult result) {
    this(
      result.mySourceFileProvider,
      result.myProject,
      result.myModule,
      result.myLogger,
      result.myRenderContext,
      result.myHasRequestedCustomViews,
      result.myRenderResult,
      result.myRootViews,
      result.mySystemRootViews,
      result.myImage,
      result.myDefaultProperties,
      result.myDefaultStyles,
      result.myValidatorResult,
      result.myRootViewDimensions,
      result.myStats
    );
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

  private static Supplier<PsiFile> createSourceFileProvider(
    @NotNull EnvironmentContext environment, @NotNull Supplier<PsiFile> fileProvider
  ) {
    return () -> environment.getOriginalFile(fileProvider.get());
  }

  @NotNull
  private static Dimension getRootViewDimensionFromSystemViews(@Nullable List<ViewInfo> viewInfo) {
    if (viewInfo == null || viewInfo.isEmpty()) return new Dimension(0, 0);
    return new Dimension(viewInfo.get(0).getRight(), viewInfo.get(0).getBottom());
  }

  /**
   * Creates a new {@link RenderResult} from a given RenderTask and RenderSession
   */
  @NotNull
  public static RenderResult create(@NotNull RenderContext renderContext,
                                    @NotNull RenderSession session,
                                    @NotNull Supplier<PsiFile> file,
                                    @NotNull RenderLogger logger,
                                    @NotNull ImagePool.Image image,
                                    boolean hasRequestedCustomViews) {
    List<ViewInfo> rootViews = session.getRootViews();
    List<ViewInfo> systemRootViews = session.getSystemRootViews();
    Map<Object, Map<ResourceReference, ResourceValue>> defaultProperties = session.getDefaultNamespacedProperties();
    Map<Object, ResourceReference> defaultStyles = session.getDefaultNamespacedStyles();
    RenderResult result = new RenderResult(
      createSourceFileProvider(renderContext.getModule().getEnvironment(), file),
      renderContext.getModule().getProject(),
      renderContext.getModule(),
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
      getRootViewDimensionFromSystemViews(systemRootViews), RenderResultStats.getEMPTY()
    );

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
  }

  /**
   * Creates a new {@link RenderResult} from a given RenderTask and RenderSession
   */
  @NotNull
  public RenderResult copyWithNewImageAndRootViewDimensions(
    @NotNull ImagePool.Image image,
    @NotNull Dimension rootViewDimensions
  ) {
    return new RenderResult(
      mySourceFileProvider,
      myProject,
      myModule,
      myLogger,
      myRenderContext,
      myHasRequestedCustomViews,
      myRenderResult,
      ImmutableList.of(),
      ImmutableList.of(),
      image,
      myDefaultProperties,
      myDefaultStyles,
      myValidatorResult,
      rootViewDimensions,
      myStats
    );
  }

  /**
   * Creates a new {@link RenderResult} from this with recorded render duration.
   */
  @NotNull
  public RenderResult createWithStats(@NotNull RenderResultStats stats) {
    return new RenderResult(
      mySourceFileProvider,
      myProject,
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
      myRootViewDimensions, myStats.combine(stats)
    );
  }

  @NotNull
  public static RenderResult createErrorRenderResult(
    @NotNull Result.Status status,
    @NotNull RenderModelModule renderModule,
    @NotNull Supplier<PsiFile> file,
    @Nullable Throwable throwable,
    @NotNull RenderLogger logger) {
    RenderResult result = new RenderResult(
      createSourceFileProvider(renderModule.getEnvironment(), file),
      renderModule.getProject(),
      renderModule,
      logger,
      null,
      false,
      status.createResult("Render error", throwable),
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of(),
      ImmutableMap.of(),
      null,
      new Dimension(0, 0), RenderResultStats.getEMPTY()
    );

    if (LOG.isDebugEnabled()) {
      LOG.debug(result.toString());
    }

    return result;
  }

  @NotNull
  public static RenderResult createRenderTaskErrorResult(@NotNull RenderModelModule renderModule,
                                                         @NotNull Supplier<PsiFile> file,
                                                         @Nullable Throwable throwable,
                                                         @NotNull RenderLogger logger) {
    return createErrorRenderResult(Result.Status.ERROR_RENDER_TASK, renderModule, file, throwable, logger);
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
   * Returns the source {@link PsiFile} if available. If you want to access the user source file name, use this method.
   */
  @NotNull
  public PsiFile getSourceFile() {
    return mySourceFileProvider.get();
  }

  @Nullable
  public RenderContext getRenderContext() {
    return myRenderContext;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  /**
   * Reference to the Android module object. Used exclusively by render error contributors.
   * Deprecated. RenderResult should not be the Module holder, obtain the module elsewhere.
   */
  @Deprecated
  @NotNull
  public Module getModule() {
    return myModule.getIdeaModule();
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

  @NotNull
  public Dimension getRootViewDimensions() {
    return myRootViews.isEmpty() ? myRootViewDimensions : getRootViewDimensionFromSystemViews(mySystemRootViews);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
      .add("renderResult", myRenderResult)
      .add("sourceFile", getSourceFile())
      .add("rootViews", myRootViews)
      .add("systemViews", mySystemRootViews)
      .add("stats", myStats)
      .add("rootViewDimensions", myRootViewDimensions)
      .toString();
  }

  @NotNull
  public RenderResultStats getStats() {
    return myStats;
  }
}
