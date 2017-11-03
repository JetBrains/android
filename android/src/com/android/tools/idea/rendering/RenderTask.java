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

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.resources.LayoutDirection;
import com.android.resources.ResourceFolderType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.diagnostics.crash.CrashReport;
import com.android.tools.idea.diagnostics.crash.CrashReporter;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.RenderParamsFlags;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.model.MergedManifest.ActivityAttributes;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.AssetRepositoryImpl;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.ui.designer.EditorDesignSurface;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.reference.SoftReference;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParserException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.SdkConstants.APPCOMPAT_LIB_ARTIFACT;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

/**
 * The {@link RenderTask} provides rendering and layout information for
 * Android layouts. This is a wrapper around the layout library.
 */
public class RenderTask implements IImageFactory {
  private static final Logger LOG = Logger.getInstance(RenderTask.class);

  @NotNull
  private final RenderService myRenderService;

  @NotNull
  private final ImagePool myImagePool;

  @Nullable
  private XmlFile myPsiFile;

  @NotNull
  private final RenderLogger myLogger;

  @NotNull
  private final LayoutlibCallbackImpl myLayoutlibCallback;

  private final AndroidVersion myMinSdkVersion;

  private final AndroidVersion myTargetSdkVersion;

  @NotNull
  private final LayoutLibrary myLayoutLib;

  @NotNull
  private final HardwareConfigHelper myHardwareConfigHelper;

  @Nullable
  private IncludeReference myIncludedWithin;

  @NotNull
  private RenderingMode myRenderingMode = RenderingMode.NORMAL;

  @Nullable
  private Integer myOverrideBgColor;

  private boolean myShowDecorations = true;

  @NotNull
  private final Configuration myConfiguration;

  @NotNull
  private final AssetRepositoryImpl myAssetRepository;

  private long myTimeout;

  @Nullable
  private EditorDesignSurface mySurface;

  @NotNull
  private final Locale myLocale;

  private final Object myCredential;

  private ResourceFolderType myFolderType;

  private boolean myProvideCookiesForIncludedViews = false;
  private RenderSession myRenderSession;
  private IImageFactory myImageFactoryDelegate;
  private SoftReference<BufferedImage> myCachedImageReference;

  private boolean isSecurityManagerEnabled = true;
  private CrashReporter myCrashReporter;

  private final List<ListenableFuture<?>> myRunningFutures = new LinkedList<>();
  private AtomicBoolean isDisposed = new AtomicBoolean(false);

  /**
   * Don't create this task directly; obtain via {@link RenderService}
   */
  RenderTask(@NotNull RenderService renderService,
             @NotNull Configuration configuration,
             @NotNull RenderLogger logger,
             @NotNull LayoutLibrary layoutLib,
             @NotNull Device device,
             @NotNull Object credential,
             @NotNull CrashReporter crashReporter,
             @NotNull ImagePool imagePool,
             @Nullable ILayoutPullParserFactory parserFactory) {
    myRenderService = renderService;
    myLogger = logger;
    myCredential = credential;
    myConfiguration = configuration;
    myCrashReporter = crashReporter;
    myImagePool = imagePool;

    AndroidFacet facet = renderService.getFacet();
    Module module = facet.getModule();
    myAssetRepository = new AssetRepositoryImpl(facet);
    myHardwareConfigHelper = new HardwareConfigHelper(device);

    ScreenOrientation orientation = configuration.getFullConfig().getScreenOrientationQualifier() != null ?
                                    configuration.getFullConfig().getScreenOrientationQualifier().getValue() :
                                    ScreenOrientation.PORTRAIT;
    myHardwareConfigHelper.setOrientation(orientation);
    myLayoutLib = layoutLib;
    AppResourceRepository appResources = AppResourceRepository.getOrCreateInstance(facet);
    ActionBarHandler actionBarHandler = new ActionBarHandler(this, myCredential);
    myLayoutlibCallback =
        new LayoutlibCallbackImpl(this, myLayoutLib, appResources, module, facet, myLogger, myCredential, actionBarHandler, parserFactory);
    myLayoutlibCallback.loadAndParseRClass();
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.getInstance(facet);
    myMinSdkVersion = moduleInfo.getMinSdkVersion();
    myTargetSdkVersion = moduleInfo.getTargetSdkVersion();
    myLocale = configuration.getLocale();
  }

  public void setPsiFile(final @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof XmlFile)) {
      throw new IllegalArgumentException("Can only render XML files: " + psiFile.getClass().getName());
    }
    myPsiFile = (XmlFile)psiFile;

    ApplicationManager.getApplication().runReadAction(() -> {
      myFolderType = ResourceHelper.getFolderType(myPsiFile);
    });
  }

  @Nullable
  public AndroidPlatform getPlatform() {
    return myRenderService.getPlatform();
  }

  /**
   * Returns the {@link ResourceResolver} for this editor
   *
   * @return the resolver used to resolve resources for the current configuration of
   *         this editor, or null
   */
  @Nullable
  public ResourceResolver getResourceResolver() {
    return myConfiguration.getResourceResolver();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  @Nullable
  public ResourceFolderType getFolderType() {
    return myFolderType;
  }

  public void setFolderType(@NotNull ResourceFolderType folderType) {
    myFolderType = folderType;
  }

  @NotNull
  public Module getModule() {
    return myRenderService.getFacet().getModule();
  }

  @NotNull
  public IRenderLogger getLogger() {
    return myLogger;
  }

  @NotNull
  public HardwareConfigHelper getHardwareConfigHelper() {
    return myHardwareConfigHelper;
  }

  public boolean getShowDecorations() {
    return myShowDecorations;
  }

  public boolean isDisposed() {
    return isDisposed.get();
  }

  /**
   * Disposes the RenderTask and releases the allocated resources. The execution of the dispose operation will run asynchronously.
   * The returned {@link Future} can be used to wait for the dispose operation to complete.
   */
  public Future<?> dispose() {
    if (isDisposed.getAndSet(true)) {
      assert false : "RenderTask was already disposed";
      return Futures.immediateFailedFuture(new IllegalStateException("RenderTask was already disposed"));
    }

    FutureTask<Void> disposeTask = new FutureTask<>(() -> {
      try {
        ImmutableList<ListenableFuture<?>> currentRunningFutures;
        synchronized (myRunningFutures) {
          currentRunningFutures = ImmutableList.copyOf(myRunningFutures);
          myRunningFutures.clear();
        }
        // Wait for all current running operations to complete
        Futures.successfulAsList(currentRunningFutures).get(5, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException e) {
        // We do not care about these exceptions since we are disposing the task anyway
        LOG.debug(e);
      }
      myLayoutlibCallback.setLogger(IRenderLogger.NULL_LOGGER);
      myLayoutlibCallback.setResourceResolver(null);
      if (myRenderSession != null) {
        try {
          RenderService.runAsyncRenderAction(myRenderSession::dispose);
          myRenderSession = null;
        }
        catch (Exception ignored) {
        }
      }
      myImageFactoryDelegate = null;

      return null;
    });

    new Thread(disposeTask, "RenderTask dispose thread").start();
    return disposeTask;
  }

  /**
   * Overrides the width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(SessionParams.RenderingMode)} is {@link SessionParams.RenderingMode#FULL_EXPAND}.
   * <p/>
   * A value of -1 will make the rendering use the normal width and height coming from the
   * {@link Configuration#getDevice()} object.
   *
   * @param overrideRenderWidth  the width in pixels of the layout to be rendered
   * @param overrideRenderHeight the height in pixels of the layout to be rendered
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setOverrideRenderSize(int overrideRenderWidth, int overrideRenderHeight) {
    myHardwareConfigHelper.setOverrideRenderSize(overrideRenderWidth, overrideRenderHeight);
    return this;
  }

  /**
   * Sets the max width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(SessionParams.RenderingMode)} is {@link SessionParams.RenderingMode#FULL_EXPAND}.
   * <p/>
   * A value of -1 will make the rendering use the normal width and height coming from the
   * {@link Configuration#getDevice()} object.
   *
   * @param maxRenderWidth  the max width in pixels of the layout to be rendered
   * @param maxRenderHeight the max height in pixels of the layout to be rendered
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setMaxRenderSize(int maxRenderWidth, int maxRenderHeight) {
    myHardwareConfigHelper.setMaxRenderSize(maxRenderWidth, maxRenderHeight);
    return this;
  }

  /**
   * Sets the {@link SessionParams.RenderingMode} to be used during rendering. If none is specified, the default is
   * {@link SessionParams.RenderingMode#NORMAL}.
   *
   * @param renderingMode the rendering mode to be used
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setRenderingMode(@NotNull RenderingMode renderingMode) {
    myRenderingMode = renderingMode;
    return this;
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setTimeout(long timeout) {
    myTimeout = timeout;
    return this;
  }

  /**
   * Sets the overriding background color to be used, if any. The color should be a
   * bitmask of AARRGGBB. The default is null.
   *
   * @param overrideBgColor the overriding background color to be used in the rendering,
   *                        in the form of a AARRGGBB bitmask, or null to use no custom background.
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setOverrideBgColor(@Nullable Integer overrideBgColor) {
    myOverrideBgColor = overrideBgColor;
    return this;
  }

  /**
   * Sets whether the rendering should include decorations such as a system bar, an
   * application bar etc depending on the SDK target and theme. The default is true.
   *
   * @param showDecorations true if the rendering should include system bars etc.
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setDecorations(boolean showDecorations) {
    myShowDecorations = showDecorations;
    return this;
  }

  /**
   * Gets the context for the usage of this {@link RenderTask}, which can
   * control for example how {@code <fragment/>} tags are processed when missing
   * preview data
   */
  @Nullable
  public EditorDesignSurface getDesignSurface() {
    return mySurface;
  }

  /**
   * Sets the context for the usage of this {@link RenderTask}, which can
   * control for example how {@code <fragment/>} tags are processed when missing
   * preview data
   *
   * @param surface the design surface
   * @return this, for constructor chaining
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setDesignSurface(@Nullable EditorDesignSurface surface) {
    mySurface = surface;
    return this;
  }

  /** Returns whether this parser will provide view cookies for included views. */
  public boolean getProvideCookiesForIncludedViews() {
    return myProvideCookiesForIncludedViews;
  }

  /**
   * Renders the model and returns the result as a {@link RenderSession}.
   *
   * @param factory Factory for images which would be used to render layouts to.
   * @return the {@link RenderResult resulting from rendering the current model
   */
  @Nullable
  private RenderResult createRenderSession(@NotNull IImageFactory factory) {
    if (myPsiFile == null) {
      throw new IllegalStateException("createRenderSession shouldn't be called on RenderTask without PsiFile");
    }

    ResourceResolver resolver = ResourceResolver.copy(getResourceResolver());
    if (resolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    ILayoutPullParser modelParser = LayoutPullParsers.create(this);
    if (modelParser == null) {
      return null;
    }

    myLayoutlibCallback.reset();

    if (modelParser instanceof LayoutPsiPullParser) {
      // For regular layouts, if we use appcompat, we have to emulat the app:srcCompat attribute behaviour
      AndroidModuleModel androidModel = AndroidModuleModel.get(myRenderService.getFacet());
      boolean useSrcCompat = androidModel != null && GradleUtil.dependsOn(androidModel, APPCOMPAT_LIB_ARTIFACT);
      ((LayoutPsiPullParser)modelParser).setUseSrcCompat(useSrcCompat);
      myLayoutlibCallback.setAaptDeclaredResources(((LayoutPsiPullParser)modelParser).getAaptDeclaredAttrs());
    }


    ILayoutPullParser includingParser = getIncludingLayoutParser(resolver, modelParser);
    if (includingParser != null) {
      modelParser = includingParser;
    }


    IAndroidTarget target = myConfiguration.getTarget();
    int simulatedPlatform = target instanceof CompatibilityRenderTarget ? target.getVersion().getApiLevel() : 0;

    Module module = myRenderService.getFacet().getModule();
    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
    final SessionParams params =
      new SessionParams(modelParser, myRenderingMode, module /* projectKey */, hardwareConfig, resolver,
                        myLayoutlibCallback,
                        myMinSdkVersion.getApiLevel(), myTargetSdkVersion.getApiLevel(), myLogger, simulatedPlatform);
    params.setAssetRepository(myAssetRepository);

    params.setFlag(RenderParamsFlags.FLAG_KEY_ROOT_TAG, AndroidPsiUtils.getRootTagName(myPsiFile));
    params.setFlag(RenderParamsFlags.FLAG_KEY_RECYCLER_VIEW_SUPPORT, true);
    params.setFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, true);
    params.setFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true);

    // Request margin and baseline information.
    // TODO: Be smarter about setting this; start without it, and on the first request
    // for an extended view info, re-render in the same session, and then set a flag
    // which will cause this to create extended view info each time from then on in the
    // same session
    params.setExtendedViewInfoMode(true);

    MergedManifest manifestInfo = MergedManifest.get(module);

    LayoutDirectionQualifier qualifier = myConfiguration.getFullConfig().getLayoutDirectionQualifier();
    if (qualifier != null && qualifier.getValue() == LayoutDirection.RTL && !getLayoutLib().isRtl(myLocale.toLocaleId())) {
      // We don't have a flag to force RTL regardless of locale, so just pick a RTL locale (note that
      // this is decoupled from resource lookup)
      params.setLocale("ur");
    } else {
      params.setLocale(myLocale.toLocaleId());
    }
    try {
      params.setRtlSupport(manifestInfo.isRtlSupported());
    } catch (Exception e) {
      // ignore.
    }

    // Don't show navigation buttons on older platforms
    Device device = myConfiguration.getDevice();
    if (!myShowDecorations || HardwareConfigHelper.isWear(device)) {
      params.setForceNoDecor();
    }
    else {
      try {
        params.setAppLabel(manifestInfo.getApplicationLabel());
        params.setAppIcon(manifestInfo.getApplicationIcon());
        String activity = myConfiguration.getActivity();
        if (activity != null) {
          params.setActivityName(activity);
          ActivityAttributes attributes = manifestInfo.getActivityAttributes(activity);
          if (attributes != null) {
            if (attributes.getLabel() != null) {
              params.setAppLabel(attributes.getLabel());
            }
            if (attributes.getIcon() != null) {
              params.setAppIcon(attributes.getIcon());
            }
          }
        }
      }
      catch (Exception e) {
        // ignore.
      }
    }

    if (myOverrideBgColor != null) {
      params.setOverrideBgColor(myOverrideBgColor.intValue());
    } else if (requiresTransparency()) {
      params.setOverrideBgColor(0);
    }

    params.setImageFactory(factory);

    if (myTimeout > 0) {
      params.setTimeout(myTimeout);
    }

    try {
      myLayoutlibCallback.setLogger(myLogger);
      myLayoutlibCallback.setResourceResolver(resolver);

      RenderSecurityManager securityManager =
        isSecurityManagerEnabled ? RenderSecurityManagerFactory.create(module, getPlatform()) : null;
      if (securityManager != null) {
        securityManager.setActive(true, myCredential);
      }

      try {
        RenderSession session = myLayoutLib.createSession(params);

        if (session.getResult().isSuccess()) {
          long now = System.nanoTime();
          session.setSystemBootTimeNanos(now);
          session.setSystemTimeNanos(now);
          // Advance the frame time to display the material progress bars
          session.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(500));
        }
        RenderResult result =
          RenderResult.create(this, session, myPsiFile, myLogger, myImagePool.copyOf(session.getImage()));
        myRenderSession = session;
        addDiagnostics(result.getRenderResult());
        return result;
      }
      finally {
        if (securityManager != null) {
          securityManager.dispose(myCredential);
        }
      }
    }
    catch (RuntimeException t) {
      // Exceptions from the bridge
      myLogger.error(null, t.getLocalizedMessage(), t, null, null);
      throw t;
    }
  }

  @Nullable
  private ILayoutPullParser getIncludingLayoutParser(ResourceResolver resolver, ILayoutPullParser modelParser) {
    if (myPsiFile == null) {
      throw new IllegalStateException("getIncludingLayoutParser shouldn't be called on RenderTask without PsiFile");
    }

    // Code to support editing included layout
    if (myIncludedWithin == null) {
      String layout = IncludeReference.getIncludingLayout(myPsiFile);
      Module module = myRenderService.getFacet().getModule();
      myIncludedWithin = layout != null ? IncludeReference.get(module, myPsiFile, resolver) : IncludeReference.NONE;
    }
    if (myIncludedWithin != IncludeReference.NONE) {
      assert Comparing.equal(myIncludedWithin.getToFile(), myPsiFile.getVirtualFile());
      // TODO: Validate that we're really including the same layout here!
      //ResourceValue contextLayout = resolver.findResValue(myIncludedWithin.getFromResourceUrl(), false  /* forceFrameworkOnly*/);
      //if (contextLayout != null) {
      //  File layoutFile = new File(contextLayout.getValue());
      //  if (layoutFile.isFile()) {
      //
      VirtualFile layoutVirtualFile = myIncludedWithin.getFromFile();

      try {
        // Get the name of the layout actually being edited, without the extension
        // as it's what IXmlPullParser.getParser(String) will receive.
        String queryLayoutName = ResourceHelper.getResourceName(myPsiFile);
        myLayoutlibCallback.setLayoutParser(queryLayoutName, modelParser);

        // Attempt to read from PSI
        ILayoutPullParser topParser;
        topParser = null;
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myRenderService.getProject(), layoutVirtualFile);
        if (psiFile instanceof XmlFile) {
          LayoutPsiPullParser parser = LayoutPsiPullParser.create((XmlFile)psiFile, myLogger);
          // For included layouts, we don't normally see view cookies; we want the leaf to point back to the include tag
          parser.setProvideViewCookies(myProvideCookiesForIncludedViews);
          topParser = parser;
        }

        if (topParser == null) {
          topParser = LayoutFilePullParser.create(myLayoutlibCallback, myIncludedWithin.getFromPath());
        }

        return topParser;
      }
      catch (IOException e) {
        myLogger.error(null, String.format("Could not read layout file %1$s", myIncludedWithin.getFromPath()), e, null, e);
      }
      catch (XmlPullParserException e) {
        myLogger.error(null, String.format("XML parsing error: %1$s", e.getMessage()), e, null,
                       e.getDetail() != null ? e.getDetail() : e);
      }
    }

    return null;
  }

  /**
   * Executes the passed {@link Callable} as an async render action and keeps track of it. If {@link #dispose()} is called, the call will
   * wait until all the async actions have finished running.
   * See {@link RenderService#runAsyncRenderAction(Callable)}.
   */
  @VisibleForTesting
  @NotNull
  <V> ListenableFuture<V> runAsyncRenderAction(@NotNull Callable<V> callable) {
    if (isDisposed.get()) {
      return Futures.immediateFailedFuture(new IllegalStateException("RenderTask was already disposed"));
    }

    synchronized (myRunningFutures) {
      ListenableFuture<V> newFuture = RenderService.runAsyncRenderAction(callable);
      Futures.addCallback(newFuture, new FutureCallback<V>() {
        @Override
        public void onSuccess(@Nullable V result) {
          synchronized (myRunningFutures) {
            myRunningFutures.remove(newFuture);
          }
        }

        @Override
        public void onFailure(@Nullable Throwable ignored) {
          synchronized (myRunningFutures) {
            myRunningFutures.remove(newFuture);
          }
        }
      });
      myRunningFutures.add(newFuture);

      return newFuture;
    }
  }

  /**
   * Inflates the layout but does not render it.
   * @return A {@link RenderResult} with the result of inflating the inflate call. The result might not contain a result bitmap.
   */
  @Nullable
  public RenderResult inflate() {
    // During development only:
    //assert !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock during inflate!";

    if (myPsiFile == null) {
      throw new IllegalStateException("inflate shouldn't be called on RenderTask without PsiFile");
    }

    try {
      return RenderService.runRenderAction(() -> createRenderSession((width, height) -> {
        if (myImageFactoryDelegate != null) {
          return myImageFactoryDelegate.getImage(width, height);
        }

        //noinspection UndesirableClassUsage
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      }));
    }
    catch (final Exception e) {
      String message = e.getMessage();
      if (message == null) {
        message = e.toString();
      }
      myLogger.addMessage(RenderProblem.createPlain(ERROR, message, myLogger.getProject(), myLogger.getLinkManager(), e));
      return RenderResult.createSessionInitializationError(this, myPsiFile, myLogger, e);
    }
  }

  /**
   * Only do a measure pass using the current render session
   */
  @NotNull
  public ListenableFuture<RenderResult> layout() {
    if (myRenderSession == null) {
      return Futures.immediateFuture(null);
    }

    assert myPsiFile != null;
    try {
      // runAsyncRenderAction might not run immediately so we need to capture the current myRenderSession and myPsiFile values
      final RenderSession renderSession = myRenderSession;
      final PsiFile psiFile = myPsiFile;
      return runAsyncRenderAction(() -> {
        myRenderSession.measure();
        return RenderResult.create(this, renderSession, psiFile, myLogger, ImagePool.NULL_POOLED_IMAGE);
      });
    }
    catch (final Exception e) {
      // nothing
    }
    return Futures.immediateFuture(null);
  }

  /**
   * Method used to report unhandled layoutlib exceptions to the crash reporter
   */
  private void reportException(@NotNull Throwable e) {
    // This in an unhandled layoutlib exception, pass it to the crash reporter
    myCrashReporter.submit(
      CrashReport.Builder.createForException(e)
        .build());
  }

  /**
   * Renders the layout to the current {@link IImageFactory} set in {@link #myImageFactoryDelegate}
   */
  @NotNull
  private ListenableFuture<RenderResult> renderInner() {
    // During development only:
    //assert !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock during render!";

    if (myRenderSession == null) {
      RenderResult renderResult = inflate();
      Result result = renderResult != null ? renderResult.getRenderResult() : null;
      if (result == null || !result.isSuccess()) {
        if (result != null) {
          if (result.getException() != null) {
            reportException(result.getException());
          }
          myLogger.error(null, result.getErrorMessage(), result.getException(), null, null);
        }
        return Futures.immediateFuture(renderResult);
      }
    }
    assert myPsiFile != null;

    try {
      return runAsyncRenderAction(() -> {
        myRenderSession.render();
        RenderResult result =
          RenderResult.create(this, myRenderSession, myPsiFile, myLogger, myImagePool.copyOf(myRenderSession.getImage()));
        Result renderResult = result.getRenderResult();
        if (renderResult.getException() != null) {
          reportException(renderResult.getException());
          myLogger.error(null, renderResult.getErrorMessage(), renderResult.getException(), null, null);
        }
        return result;
      });
    }
    catch (final Exception e) {
      reportException(e);
      String message = e.getMessage();
      if (message == null) {
        message = e.toString();
      }
      myLogger.addMessage(RenderProblem.createPlain(ERROR, message, myLogger.getProject(), myLogger.getLinkManager(), e));
      return Futures.immediateFuture(RenderResult.createSessionInitializationError(this, myPsiFile, myLogger, e));
    }
  }

  /**
   * Method that renders the layout to a bitmap using the given {@link IImageFactory}. This render call will render the image to a
   * bitmap that can be accessed via the returned {@link RenderResult}.
   * <p/>
   * If {@link #inflate()} hasn't been called before, this method will implicitly call it.
   */
  @NotNull
  public ListenableFuture<RenderResult> render(@NotNull final IImageFactory factory) {
    myImageFactoryDelegate = factory;

    return renderInner();
  }

  /**
   * Run rendering with default IImageFactory implementation provided by RenderTask. This render call will render the image to a bitmap
   * that can be accessed via the returned {@link RenderResult}
   * <p/>
   * If {@link #inflate()} hasn't been called before, this method will implicitly call it.
   */
  @NotNull
  public ListenableFuture<RenderResult> render() {
    return render(this);
  }

  /**
   * Sets the time for which the next frame will be selected. The time is the elapsed time from
   * the current system nanos time.
   */
  public void setElapsedFrameTimeNanos(long nanos) {
    if (myRenderSession != null) {
      myRenderSession.setElapsedFrameTimeNanos(nanos);
    }
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private void addDiagnostics(@NotNull Result result) {
    if (!myLogger.hasProblems() && !result.isSuccess()) {
      if (result.getException() != null || result.getErrorMessage() != null) {
        myLogger.error(null, result.getErrorMessage(), result.getException(), null, null);
      } else if (result.getStatus() == Result.Status.ERROR_TIMEOUT) {
        myLogger.error(null, "Rendering timed out.", null, null, null);
      } else {
        myLogger.error(null, "Unknown render problem: " + result.getStatus(), null, null, null);
      }
    } else if (myIncludedWithin != null && myIncludedWithin != IncludeReference.NONE) {
      ILayoutPullParser layoutEmbeddedParser = myLayoutlibCallback.getLayoutEmbeddedParser();
      if (layoutEmbeddedParser != null) {  // Should have been nulled out if used
        myLogger.error(null, String.format("The surrounding layout (%1$s) did not actually include this layout. " +
                                           "Remove tools:" + SdkConstants.ATTR_SHOW_IN + "=... from the root tag.",
                                           myIncludedWithin.getFromResourceUrl()), null, null, null);
      }
    }
  }

  /**
   * Asynchronously renders the given resource value (which should refer to a drawable)
   * and returns it as an image.
   *
   * @param drawableResourceValue the drawable resource value to be rendered, or null
   * @return a {@link ListenableFuture} with the BufferedImage of the passed drawable.
   */
  @NotNull
  public ListenableFuture<BufferedImage> renderDrawable(ResourceValue drawableResourceValue) {
    if (drawableResourceValue == null) {
      return Futures.immediateFuture(null);
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    Module module = myRenderService.getFacet().getModule();
    final DrawableParams params =
      new DrawableParams(drawableResourceValue, module, hardwareConfig, getResourceResolver(), myLayoutlibCallback,
                         myMinSdkVersion.getApiLevel(), myTargetSdkVersion.getApiLevel(), myLogger);
    params.setForceNoDecor();
    params.setAssetRepository(myAssetRepository);

    ListenableFuture<Result> futureResult = runAsyncRenderAction(() -> myLayoutLib.renderDrawable(params));
    return Futures.transform(futureResult, (Function<Result, BufferedImage>)result -> {
      if (result != null && result.isSuccess()) {
        Object data = result.getData();
        if (data instanceof BufferedImage) {
          return (BufferedImage)data;
        }
      }

      return null;
    });
  }

  /**
   * Renders the given resource value (which should refer to a drawable) and returns it
   * as an image
   *
   * @param drawableResourceValue the drawable resource value to be rendered, or null
   * @return the image, or null if something went wrong
   */
  @NotNull
  @SuppressWarnings("unchecked")
  public List<BufferedImage> renderDrawableAllStates(ResourceValue drawableResourceValue) {
    if (drawableResourceValue == null) {
      return Collections.emptyList();
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    Module module = myRenderService.getFacet().getModule();
    final DrawableParams params =
      new DrawableParams(drawableResourceValue, module, hardwareConfig, getResourceResolver(), myLayoutlibCallback,
                         myMinSdkVersion.getApiLevel(), myTargetSdkVersion.getApiLevel(), myLogger);
    params.setForceNoDecor();
    params.setAssetRepository(myAssetRepository);
    final boolean supportsMultipleStates = myLayoutLib.supports(Features.RENDER_ALL_DRAWABLE_STATES);
    if (supportsMultipleStates) {
      params.setFlag(RenderParamsFlags.FLAG_KEY_RENDER_ALL_DRAWABLE_STATES, Boolean.TRUE);
    }

    try {
      Result result = RenderService.runRenderAction(() -> myLayoutLib.renderDrawable(params));

      if (result != null && result.isSuccess()) {
        Object data = result.getData();
        if (supportsMultipleStates && data instanceof List) {
          return (List<BufferedImage>)data;
        } else if (!supportsMultipleStates && data instanceof BufferedImage) {
          return Collections.singletonList((BufferedImage) data);
        }
      }
    }
    catch (final Exception e) {
      // ignore
    }

    return Collections.emptyList();
  }

  @NotNull
  private LayoutLibrary getLayoutLib() {
    return myLayoutLib;
  }

  @NotNull
  public LayoutlibCallbackImpl getLayoutlibCallback() {
    return myLayoutlibCallback;
  }

  @Nullable
  public XmlFile getPsiFile() {
    return myPsiFile;
  }

  public boolean supportsCapability(@MagicConstant(flagsFromClass = Features.class) int capability) {
    return myLayoutLib.supports(capability);
  }

  /** Returns true if this service can render a non-rectangular shape */
  public boolean isNonRectangular() {
    // Drawable images can have non-rectangular shapes; we need to ensure that we blank out the
    // background with full alpha
    return getFolderType() == ResourceFolderType.DRAWABLE || getFolderType() == ResourceFolderType.MIPMAP;
  }

  /** Returns true if this service requires rendering into a transparent/alpha channel image */
  public boolean requiresTransparency() {
    // Drawable images can have non-rectangular shapes; we need to ensure that we blank out the
    // background with full alpha
    return isNonRectangular();
  }

  // ---- Implements IImageFactory ----

  @SuppressWarnings("UndesirableClassUsage") // Don't need Retina for layoutlib rendering; will scale down anyway
  @Override
  public BufferedImage getImage(int width, int height) {
    BufferedImage cached = myCachedImageReference != null ? myCachedImageReference.get() : null;

    // This can cause flicker; see steps listed in http://b.android.com/208984
    if (cached == null || cached.getWidth() != width || cached.getHeight() != height) {
      cached = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      myCachedImageReference = new SoftReference<>(cached);
    }

    return cached;
  }

  /**
   * Notifies the render service that it is being used in design mode for this layout.
   * For example, that means that when rendering a ScrollView, it should measure the necessary
   * vertical space, and size the layout according to the needs rather than the available
   * device size.
   * <p>
   * We don't want to do this when for example offering thumbnail previews of the various
   * layouts.
   *
   * @param file the layout file, if any
   */
  public void useDesignMode(@Nullable final PsiFile file) {
    if (file == null) {
      return;
    }
    ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      if (file instanceof XmlFile) {
        XmlTag root = ((XmlFile)file).getRootTag();
        if (root != null) {
          root = LayoutPsiPullParser.getRootTag(root);
          if (root != null) {
            return root.getName();
          }
        }
      }

      return null;
    });
  }

  /**
   * Measure the children of the given parent tag, applying the given filter to the
   * pull parser's attribute values.
   *
   * @param parent the parent tag to measure children for
   * @param filter the filter to apply to the attribute values
   * @return a map from the children of the parent to new bounds of the children
   */
  @Nullable
  public Map<XmlTag, ViewInfo> measureChildren(XmlTag parent, final AttributeFilter filter) {
    ILayoutPullParser modelParser = LayoutPsiPullParser.create(filter, parent, myLogger);
    Map<XmlTag, ViewInfo> map = Maps.newHashMap();
    RenderSession session = null;
    try {
      session = RenderService.runRenderAction(() -> measure(modelParser));
    }
    catch (Exception ignored) {
    }
    if (session != null) {
      try {
        Result result = session.getResult();

        if (result != null && result.isSuccess()) {
          assert session.getRootViews().size() == 1;
          ViewInfo root = session.getRootViews().get(0);
          List<ViewInfo> children = root.getChildren();
          for (ViewInfo info : children) {
            XmlTag tag = RenderService.getXmlTag(info);
            if (tag != null) {
              map.put(tag, info);
            }
          }
        }

        return map;
      } finally {
        RenderService.runAsyncRenderAction(session::dispose);
      }
    }

    return null;
  }

  /**
   * Measure the given child in context, applying the given filter to the
   * pull parser's attribute values.
   *
   * @param tag the child to measure
   * @param filter the filter to apply to the attribute values
   * @return a view info, if found
   */
  @Nullable
  public ViewInfo measureChild(XmlTag tag, final AttributeFilter filter) {
    XmlTag parent = tag.getParentTag();
    if (parent != null) {
      Map<XmlTag, ViewInfo> map = measureChildren(parent, filter);
      if (map != null) {
        for (Map.Entry<XmlTag, ViewInfo> entry : map.entrySet()) {
          if (entry.getKey() == tag) {
            return entry.getValue();
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private RenderSession measure(ILayoutPullParser parser) {
    ResourceResolver resolver = getResourceResolver();
    if (resolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    myLayoutlibCallback.reset();

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
    Module module = myRenderService.getFacet().getModule();
    final SessionParams params = new SessionParams(
      parser,
      RenderingMode.NORMAL,
      module /* projectKey */,
      hardwareConfig,
      resolver,
      myLayoutlibCallback,
      myMinSdkVersion.getApiLevel(),
      myTargetSdkVersion.getApiLevel(),
      myLogger);
    //noinspection deprecation We want to measure while creating the session. RenderSession.measure would require a second call
    params.setLayoutOnly();
    params.setForceNoDecor();
    params.setExtendedViewInfoMode(true);
    params.setLocale(myLocale.toLocaleId());
    params.setAssetRepository(myAssetRepository);
    params.setFlag(RenderParamsFlags.FLAG_KEY_RECYCLER_VIEW_SUPPORT, true);
    MergedManifest manifestInfo = MergedManifest.get(module);
    try {
      params.setRtlSupport(manifestInfo.isRtlSupported());
    } catch (Exception e) {
      // ignore.
    }

    try {
      myLayoutlibCallback.setLogger(myLogger);
      myLayoutlibCallback.setResourceResolver(resolver);

      return myLayoutLib.createSession(params);
    }
    catch (RuntimeException t) {
      // Exceptions from the bridge
      myLogger.error(null, t.getLocalizedMessage(), t, null, null);
      throw t;
    }
  }

  @VisibleForTesting
  void setCrashReporter(@NotNull CrashReporter crashReporter) {
    myCrashReporter = crashReporter;
  }

  /**
   * Bazel has its own security manager. We allow rendering tests to disable the security manager by calling this method.
   */
  @VisibleForTesting
  public void disableSecurityManager() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      throw new IllegalStateException("This method can only be called in unit test mode");
    }
    LOG.debug("Security manager was disabled");
    isSecurityManagerEnabled = false;
  }

  /**
   * The {@link AttributeFilter} allows a client of {@link #measureChildren} to modify the actual
   * XML values of the nodes being rendered, for example to force width and height values to
   * wrap_content when measuring preferred size.
   */
  public interface AttributeFilter {
    /**
     * Returns the attribute value for the given node and attribute name. This filter
     * allows a client to adjust the attribute values that a node presents to the
     * layout library.
     * <p/>
     * Returns "" to unset an attribute. Returns null to return the unfiltered value.
     *
     * @param node      the node for which the attribute value should be returned
     * @param namespace the attribute namespace
     * @param localName the attribute local name
     * @return an override value, or null to return the unfiltered value
     */
    @Nullable
    String getAttribute(@NotNull XmlTag node, @Nullable String namespace, @NotNull String localName);
  }
}
