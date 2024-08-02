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

import static com.android.tools.configurations.AdditionalDevices.DEVICE_CLASS_DESKTOP_ID;
import static com.android.tools.configurations.AdditionalDevices.DEVICE_CLASS_TABLET_ID;
import static com.android.tools.rendering.ProblemSeverity.ERROR;
import static com.android.tools.rendering.ProblemSeverity.WARNING;
import static com.android.tools.rendering.RenderAsyncActionExecutor.DEFAULT_RENDER_THREAD_TIMEOUT_MS;
import static com.intellij.openapi.application.ActionsKt.runReadAction;

import com.android.SdkConstants;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.api.DrawableParams;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.rendering.api.IImageFactory;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.RenderResources;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.repository.GoogleMavenArtifactId;
import com.android.ide.common.resources.Locale;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier;
import com.android.ide.common.util.PathString;
import com.android.resources.LayoutDirection;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.analytics.crash.CrashReporter;
import com.android.tools.configurations.Configuration;
import com.android.tools.dom.ActivityAttributesSnapshot;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.RenderParamsFlags;
import com.android.tools.rendering.api.IncludeReference;
import com.android.tools.rendering.api.RenderModelManifest;
import com.android.tools.rendering.api.RenderModelModule;
import com.android.tools.rendering.classloading.ClassLoaderPreloaderKt;
import com.android.tools.rendering.classloading.ClassTransform;
import com.android.tools.rendering.classloading.ModuleClassLoader;
import com.android.tools.rendering.classloading.ModuleClassLoaderManager;
import com.android.tools.rendering.compose.RenderTaskPatcher;
import com.android.tools.rendering.imagepool.ImagePool;
import com.android.tools.rendering.parsers.ILayoutPullParserFactory;
import com.android.tools.rendering.parsers.LayoutFilePullParser;
import com.android.tools.rendering.parsers.LayoutPullParsers;
import com.android.tools.rendering.parsers.LayoutRenderPullParser;
import com.android.tools.rendering.parsers.RenderXmlFile;
import com.android.tools.rendering.parsers.RenderXmlTag;
import com.android.tools.rendering.security.RenderSecurityManager;
import com.android.tools.rendering.tracking.RenderTaskAllocationTracker;
import com.android.tools.rendering.tracking.StackTraceCapture;
import com.android.tools.sdk.CompatibilityRenderTarget;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.serviceContainer.AlreadyDisposedException;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The {@link RenderTask} provides rendering and layout information for
 * Android layouts. This is a wrapper around the layout library.
 */
public class RenderTask {
  /**
   * Listener that receives call before and after relevant {@link RenderTask} events.
   * This is <b>only for testing</b> and can only be set via a {@link TestOnly} method. This can be used
   * for test logging or to inject errors during a specific phase of the execution.
   */
  // TODO(b/313930015): Find a better name/location for this class.
  public interface TestEventListener {
    default void onBeforeInflate() {}
    default void onAfterInflate() {}
    default void onBeforeRender() {}
    default void onAfterRender() {}
  }

  public static final TestEventListener NOP_TEST_EVENT_LISTENER = new TestEventListener() {
  };

  private static final Logger LOG = Logger.getInstance(RenderTask.class);

  /**
   * When an element in Layoutlib does not take any space, it will ask for a 0px X 0px image. This will throw an exception so we limit the
   * min size of the returned bitmap to 1x1.
   */
  private static final int MIN_BITMAP_SIZE_PX = 1;

  /**
   * {@link IImageFactory} that returns a new image exactly of the requested size. It does not do caching or resizing.
   */
  private static final IImageFactory SIMPLE_IMAGE_FACTORY = new IImageFactory() {
    @NotNull
    @Override
    public BufferedImage getImage(int width, int height) {
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage image =
        new BufferedImage(Math.max(MIN_BITMAP_SIZE_PX, width), Math.max(MIN_BITMAP_SIZE_PX, height), BufferedImage.TYPE_INT_ARGB_PRE);
      image.setAccelerationPriority(1f);

      return image;
    }
  };

  /**
   * When quality < 1.0, the max allowed size for the rendering is DOWNSCALED_IMAGE_MAX_BYTES * downscalingFactor
   */
  private static final int DEFAULT_DOWNSCALED_IMAGE_MAX_BYTES = 2_500_000; // 2.5MB

  /**
   * Executor to run the dispose tasks. The thread will run them sequentially.
   */
  private static final ExecutorService ourDisposeService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("RenderTask Dispose Thread", 1);

  @NotNull RenderTaskAllocationTracker myTracker;
  @NotNull private final ImagePool myImagePool;
  @NotNull private final RenderContext myContext;

  @NotNull private final ModuleClassLoaderManager<?> myClassLoaderManager;
  @NotNull private final RenderLogger myLogger;
  @NotNull private final LayoutlibCallbackImpl myLayoutlibCallback;
  @NotNull private final LayoutLibrary myLayoutLib;
  @NotNull private final HardwareConfigHelper myHardwareConfigHelper;
  /**
   *  Indicates the quality value expected to be used the next time a render is executed
   *  using this render task. It can be updated while setting up the render task using
   *  {@link RenderTask#setQuality(float)}
   */
  private float myTargetQuality = 1f;
  /**
   *  Indicates the quality value used in the last {@link RenderTask#render(IImageFactory)},
   *  or in the current one when a render is being executed.
   */
  private float myCurrentQuality = 1f;
  private final long myDownScaledImageMaxBytes;
  @Nullable private IncludeReference myIncludedWithin;
  @NotNull private RenderingMode myRenderingMode = RenderingMode.NORMAL;
  private boolean mySetTransparentBackground = false;
  private boolean myShowDecorations = true;
  private boolean myEnableLayoutScanner = false;
  private boolean myShowWithToolsVisibilityAndPosition = true;
  private Function<Object, List<ViewInfo>> myCustomContentHierarchyParser = null;
  private long myTimeout;
  @NotNull private final Locale myLocale;
  @NotNull private final Object myCredential;
  @Nullable private RenderSession myRenderSession;
  @NotNull private IImageFactory myCachingImageFactory = SIMPLE_IMAGE_FACTORY;
  @Nullable private IImageFactory myImageFactoryDelegate;
  private final boolean isSecurityManagerEnabled;
  @NotNull private CrashReporter myCrashReporter;
  private final List<CompletableFuture<?>> myRunningFutures = new LinkedList<>();
  @NotNull private final AtomicBoolean isDisposed = new AtomicBoolean(false);
  @Nullable private RenderXmlFile myXmlFile;
  @NotNull private String myDefaultForegroundColor = "#333333";
  @NotNull private final ModuleClassLoaderManager.Reference<?> myModuleClassLoaderReference;
  @NotNull private final TestEventListener myTestEventListener;

  /**
   * If true, the {@link RenderTask#render()} will report when the user classes loaded by this class loader are out of date.
   */
  private final boolean reportOutOfDateUserClasses;

  /**
   * Enum value to specify the context or tool in which the render is happening and its priority
   */
  @NotNull private final RenderAsyncActionExecutor.RenderingTopic myTopic;

  /**
   * Don't create this task directly; obtain via {@link RenderService}
   * @param quality            Factor from 0 to 1 used to downscale the rendered image. A lower value means smaller images used
   *                           during rendering at the expense of quality. 1 means that downscaling is disabled.
   * @param privateClassLoader if true, this task should have its own ModuleClassLoader, if false it can use a shared one for the module
   * @param topic              enum value to specify the context or tool in which the render is happening
   */
  RenderTask(@NotNull RenderContext renderContext,
             @NotNull ModuleClassLoaderManager classLoaderManager,
             @NotNull RenderLogger logger,
             @NotNull LayoutLibrary layoutLib,
             @NotNull Object credential,
             @NotNull CrashReporter crashReporter,
             @NotNull ImagePool imagePool,
             @Nullable ILayoutPullParserFactory parserFactory,
             boolean isSecurityManagerEnabled,
             float quality,
             @NotNull StackTraceCapture stackTraceCaptureElement,
             @NotNull RenderTaskAllocationTracker tracker,
             boolean privateClassLoader,
             @NotNull ClassTransform additionalProjectTransform,
             @NotNull ClassTransform additionalNonProjectTransform,
             @NotNull Runnable onNewModuleClassLoader,
             @NotNull Collection<String> classesToPreload,
             boolean reportOutOfDateUserClasses,
             @NotNull RenderAsyncActionExecutor.RenderingTopic topic,
             boolean useCustomInflater,
             @NotNull TestEventListener testEventListener) throws NoDeviceException {
    myTracker = tracker;
    myImagePool = imagePool;
    myContext = renderContext;
    myClassLoaderManager = classLoaderManager;
    this.isSecurityManagerEnabled = isSecurityManagerEnabled;
    this.reportOutOfDateUserClasses = reportOutOfDateUserClasses;

    if (!isSecurityManagerEnabled) {
      LOG.debug("Security manager was disabled");
    }

    myTopic = topic;
    myLogger = logger;
    myCredential = credential;
    myCrashReporter = crashReporter;
    myTestEventListener = testEventListener;
    Device device = renderContext.getConfiguration().getDevice();
    if (device == null) {
      throw new NoDeviceException();
    }
    myHardwareConfigHelper = new HardwareConfigHelper(device);

    ScreenOrientation orientation = renderContext.getConfiguration().getFullConfig().getScreenOrientationQualifier() != null ?
                                    renderContext.getConfiguration().getFullConfig().getScreenOrientationQualifier().getValue() :
                                    ScreenOrientation.PORTRAIT;
    myHardwareConfigHelper.setOrientation(orientation);
    myLayoutLib = layoutLib;
    ActionBarHandler actionBarHandler = new ActionBarHandler(this, myCredential);
    ModuleRenderContext moduleRenderContext = renderContext.getModule().createModuleRenderContext(new WeakReference<>(this));
    if (privateClassLoader) {
      myModuleClassLoaderReference = classLoaderManager.getPrivate(
        myLayoutLib.getClassLoader(),
        moduleRenderContext,
        additionalProjectTransform, additionalNonProjectTransform);
      onNewModuleClassLoader.run();
    } else {
      myModuleClassLoaderReference = classLoaderManager.getShared(myLayoutLib.getClassLoader(),
                                                         moduleRenderContext,
                                                         additionalProjectTransform,
                                                         additionalNonProjectTransform,
                                                         onNewModuleClassLoader);
    }
    ModuleClassLoader moduleClassLoader = myModuleClassLoaderReference.getClassLoader();
    ClassLoaderPreloaderKt.preload(moduleClassLoader, moduleClassLoader::isDisposed, classesToPreload);
    try {
      myLayoutlibCallback =
        new LayoutlibCallbackImpl(
          this,
          myLayoutLib,
          renderContext.getModule(),
          myLogger,
          myCredential,
          actionBarHandler,
          parserFactory,
          moduleClassLoader,
          useCustomInflater);
      if (renderContext.getModule().getResourceIdManager().getFinalIdsUsed()) {
        myLayoutlibCallback.loadAndParseRClass();
      }
      myLocale = renderContext.getConfiguration().getLocale();
      // Some devices need more memory to avoid the blur when rendering. These are special cases.
      // The image looks acceptable after dividing both width and height to half. So we divide memory usage by 4 for these devices.
      if (DEVICE_CLASS_DESKTOP_ID.equals(device.getId())) {
        // Desktop device is 1920dp * 1080dp with XXHDPI density, it needs roughly 6K * 3K * 32 (ARGB) / 8 = 72 MB.
        // We divide it by 4, which is 18 MB.
        myDownScaledImageMaxBytes = 18_000_000L;
      }
      else if (DEVICE_CLASS_TABLET_ID.equals(device.getId())) {
        // Desktop device is 1280dp * 800dp with XXHDPI density, it needs roughly (1280 * 3) * (800 * 3) * 32 (ARGB) / 8 = 36 MB.
        // We divide it by 4, which is 9 MB.
        myDownScaledImageMaxBytes = 9_000_000L;
      }
      else {
        myDownScaledImageMaxBytes = DEFAULT_DOWNSCALED_IMAGE_MAX_BYTES;
      }
      setQuality(quality);

      stackTraceCaptureElement.bind(this);
    } catch (Exception ex) {
      clearClassLoader();
      throw ex;
    }
  }

  public void setQuality(float quality) {
    quality = Math.max(0f, Math.min(quality, 1f));
    if (quality == myTargetQuality) return;
    myTargetQuality = quality;
    if (quality >= 1.f) {
      myCachingImageFactory = SIMPLE_IMAGE_FACTORY;
      return;
    }
    long maxSize = myDownScaledImageMaxBytes;
    // Each dimension should be scaled using the sqrt(quality), so that
    // the whole image is scaled with quality as a consequence.
    double dimensionScale = Math.sqrt(quality);
    myCachingImageFactory = new CachingImageFactory(((width, height) -> {
      double downscaleWidth = width * dimensionScale;
      double downscaleHeight = height * dimensionScale;
      double size = width * height;
      // Use maxSize as absolute upper bound
      if (size > maxSize) {
        double newDimensionScale = Math.sqrt(maxSize / size);
        downscaleWidth *= newDimensionScale;
        downscaleHeight *= newDimensionScale;
      }
      // Make sure both dimensions are positive integers
      int w = Math.max(1, (int)downscaleWidth);
      int h = Math.max(1, (int)downscaleHeight);
      return SIMPLE_IMAGE_FACTORY.getImage(w, h);
    }));
  }

  public void setXmlFile(@NotNull RenderXmlFile file) {
    myXmlFile = file;
    ReadAction.run(() -> getContext().setFolderType(file.getFolderType()));
  }

  @Nullable
  public RenderXmlFile getXmlFile() {
    return myXmlFile;
  }

  /**
   * Sets the default foreground color in the format #XXXXXX that can be used by different previews if they do not have dedicated color by
   * default, used e.g. to set color that is distinguishable in the current IDE color scheme.
   */
  public void setDefaultForegroundColor(@NotNull String defaultForegroundColor) {
    myDefaultForegroundColor = defaultForegroundColor;
  }

  /** Returns the default foreground color in the format #XXXXXX. */
  @NotNull
  public String getDefaultForegroundColor() {
    return myDefaultForegroundColor;
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

  public boolean getShowWithToolsVisibilityAndPosition() {
    return myShowWithToolsVisibilityAndPosition;
  }

  public boolean isDisposed() {
    return isDisposed.get();
  }

  // Workaround for http://b/143378087
  private void clearClassLoader() {
    try {
      myClassLoaderManager.release(myModuleClassLoaderReference);
    }
    catch (AlreadyDisposedException e) {
      // The project has already been disposed.
    }
    catch (Throwable t) {
      LOG.warn(t); // Failure detected here will most probably cause a memory leak
    }
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

    myTracker.captureDisposeStackTrace().bind(this);

    CompletableFuture<?>[] currentRunningFutures;
    synchronized (myRunningFutures) {
      currentRunningFutures = myRunningFutures.toArray(new CompletableFuture<?>[0]);
      myRunningFutures.clear();
    }
    myLayoutlibCallback.setLogger(IRenderLogger.NULL_LOGGER);
    RenderSession renderSessionToDispose = myRenderSession;
    myRenderSession = null;

    if (renderSessionToDispose != null) {
      renderSessionToDispose.releaseRender();
    }

    return ourDisposeService.submit(() -> {
      try {
        // Wait for all current running operations to complete
        CompletableFuture.allOf(currentRunningFutures).get(5, TimeUnit.SECONDS);
      }
      catch (InterruptedException | ExecutionException e) {
        // We do not care about these exceptions since we are disposing the task anyway
        LOG.debug(e);
      }
      if (renderSessionToDispose != null) {
        try {
          disposeRenderSession(renderSessionToDispose)
            .whenComplete((result, ex) -> clearClassLoader())
            .orTimeout(2, TimeUnit.SECONDS)
            .join(); // This is running on the dispose thread so wait for the full dispose to happen.
        }
        catch (Exception ignored) {
        }
      }
      else {
        clearClassLoader();
      }
      myImageFactoryDelegate = null;
      Disposer.dispose(myContext.getModule());

      return null;
    });
  }

  @TestOnly
  @Nullable
  public ClassLoader getClassLoader() {
    return myModuleClassLoaderReference.getClassLoader();
  }

  /**
   * Overrides the width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(RenderingMode)} is {@link RenderingMode#FULL_EXPAND}.
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
   * the {@link #setRenderingMode(RenderingMode)} is {@link RenderingMode#FULL_EXPAND}.
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
   * Sets the {@link RenderingMode} to be used during rendering. If none is specified, the default is
   * {@link RenderingMode#NORMAL}.
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
   * Sets the transparent background to be used.
   *
   * @return this (such that chains of setters can be stringed together)
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setTransparentBackground() {
    mySetTransparentBackground = true;
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
   * Sets the value of the  {@link com.android.layoutlib.bridge.android.RenderParamsFlags#FLAG_KEY_ENABLE_LAYOUT_VALIDATOR}
   * which enables layout validation during the render process. The validation includes accessibility checks (whether the layout properly
   * support accessibilty cases), and various other layout sanity checks.
   */
  public RenderTask setEnableLayoutScanner(boolean enableLayoutScanner) {
    myEnableLayoutScanner = enableLayoutScanner;
    return this;
  }

  /**
   * Sets whether the rendering should use 'tools' namespaced 'visibility' and 'layout_editor_absoluteX/Y' attributes.
   * <p>
   * Default is {@code true}.
   */
  @NotNull
  public RenderTask setShowWithToolsVisibilityAndPosition(boolean showWithToolsVisibilityAndPosition) {
    myShowWithToolsVisibilityAndPosition = showWithToolsVisibilityAndPosition;
    return this;
  }

  /**
   * Sets a custom parser for creating the {@link ViewInfo} hierarchy from the layout root view.
   */
  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public RenderTask setCustomContentHierarchyParser(@NotNull Function<Object, List<ViewInfo>> parser) {
    myCustomContentHierarchyParser = parser;
    return this;
  }

  /**
   * Returns the root tag for the given {@link RenderXmlFile}, if any, acquiring the read
   * lock to do so if necessary
   *
   * @param file the file to look up the root tag for
   * @return the corresponding root tag, if any
   */
  @Nullable
  private static String getRootTagName(@NotNull RenderXmlFile file) {
    ResourceFolderType folderType = file.getFolderType();
    if (folderType == ResourceFolderType.XML || folderType == ResourceFolderType.MENU || folderType == ResourceFolderType.DRAWABLE) {
      RenderXmlTag rootTag = file.getRootTag();
      return rootTag == null ? null : rootTag.getName();
    }
    return null;
  }

  /**
   * Returns a valid pooled image or {@link ImagePool#NULL_POOLED_IMAGE} if the input if null or not valid.
   */
  @NotNull
  private ImagePool.Image toPooledImage(@Nullable BufferedImage result) {
    // Check if the image exists and it's a valid image. Layoutlib can sometimes return a 1x1 image when
    // an error has happened. Even if the image is valid, a 1x1 image is not useful so we approximate it to
    // the null image.
    if (result != null && result.getWidth() > 1 && result.getHeight() > 1) {
      return myImagePool.copyOf(result);
    }
    return ImagePool.NULL_POOLED_IMAGE;
  }

  /**
   * Renders the model and returns the result as a {@link RenderSession}.
   *
   * @param factory Factory for images which would be used to render layouts to.
   * @return the {@link RenderResult resulting from rendering the current model
   */
  @Nullable
  private RenderResult createRenderSession(@NotNull IImageFactory factory) {
    RenderContext context = getContext();
    RenderModelModule module = context.getModule();
    if (module.isDisposed()) {
      return null;
    }

    RenderXmlFile xmlFile = getXmlFile();
    if (xmlFile == null) {
      throw new IllegalStateException("createRenderSession shouldn't be called on RenderTask without PsiFile");
    }
    if (isDisposed.get()) {
      return null;
    }

    Configuration configuration = context.getConfiguration();
    ResourceResolver resolver = ResourceResolver.copy(configuration.getResourceResolver());
    if (resolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    ILayoutPullParser modelParser = LayoutPullParsers.create(this);
    if (modelParser == null) {
      return null;
    }

    myLayoutlibCallback.reset();

    if (modelParser instanceof LayoutRenderPullParser) {
      // For regular layouts, if we use appcompat, we have to emulat the app:srcCompat attribute behaviour.
      boolean useSrcCompat = context.getModule().getDependencies().dependsOn(GoogleMavenArtifactId.APP_COMPAT_V7) ||
                             context.getModule().getDependencies().dependsOn(GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7);
      ((LayoutRenderPullParser)modelParser).setUseSrcCompat(useSrcCompat);
      myLayoutlibCallback.setAaptDeclaredResources(((LayoutRenderPullParser)modelParser).getAaptDeclaredAttrs());
    }

    ILayoutPullParser includingParser = getIncludingLayoutParser(resolver, modelParser);
    if (includingParser != null) {
      modelParser = includingParser;
    }

    IAndroidTarget target = configuration.getTarget();
    int simulatedPlatform = target instanceof CompatibilityRenderTarget ? target.getVersion().getApiLevel() : 0;

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
    SessionParams params =
      new SessionParams(modelParser, myRenderingMode, context.getModule().getModuleKey(), hardwareConfig, resolver,
                        myLayoutlibCallback, context.getMinSdkVersion().getApiLevel(), context.getTargetSdkVersion().getApiLevel(),
                        myLogger, simulatedPlatform);
    params.setAssetRepository(context.getModule().getAssetRepository());

    params.setFlag(RenderParamsFlags.FLAG_KEY_ROOT_TAG, getRootTagName(xmlFile));
    params.setFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, true);
    params.setFlag(RenderParamsFlags.FLAG_DO_NOT_RENDER_ON_CREATE, true);
    params.setFlag(RenderParamsFlags.FLAG_KEY_RESULT_IMAGE_AUTO_SCALE, true);
    params.setFlag(RenderParamsFlags.FLAG_KEY_ENABLE_LAYOUT_SCANNER, myEnableLayoutScanner);
    params.setFlag(RenderParamsFlags.FLAG_ENABLE_LAYOUT_SCANNER_IMAGE_CHECK, myEnableLayoutScanner);
    params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH, configuration.getAdaptiveShape().getPathDescription());
    params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, configuration.getUseThemedIcon());
    params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH, configuration.getWallpaperPath());
    params.setFlag(RenderParamsFlags.FLAG_KEY_USE_GESTURE_NAV, configuration.isGestureNav());
    params.setFlag(RenderParamsFlags.FLAG_KEY_EDGE_TO_EDGE, configuration.isEdgeToEdge());
    params.setFlag(RenderParamsFlags.FLAG_KEY_SHOW_CUTOUT, true);

    params.setCustomContentHierarchyParser(myCustomContentHierarchyParser);
    params.setImageTransformation(configuration.getImageTransformation());

    // Request margin and baseline information.
    // TODO: Be smarter about setting this; start without it, and on the first request
    // for an extended view info, re-render in the same session, and then set a flag
    // which will cause this to create extended view info each time from then on in the
    // same session.
    params.setExtendedViewInfoMode(true);

    LayoutDirectionQualifier qualifier = configuration.getFullConfig().getLayoutDirectionQualifier();
    if (qualifier != null && qualifier.getValue() == LayoutDirection.RTL && !getLayoutLib().isRtl(myLocale.toLocaleId())) {
      // We don't have a flag to force RTL regardless of locale, so just pick a RTL locale (note that
      // this is decoupled from resource lookup)
      params.setLocale("ur");
    }
    else {
      params.setLocale(myLocale.toLocaleId());
    }
    try {
      @Nullable RenderModelManifest manifestInfo = context.getModule().getManifest();
      params.setRtlSupport(manifestInfo != null && manifestInfo.isRtlSupported());
    }
    catch (Exception e) {
      // ignore.
    }

    // Don't show navigation buttons on older platforms.
    Device device = configuration.getDevice();
    if (!myShowDecorations || Device.isWear(device)) {
      params.setForceNoDecor();
    }
    else {
      try {
        @Nullable RenderModelManifest manifestInfo = context.getModule().getManifest();
        ResourceValue appLabel = manifestInfo != null
                                 ? manifestInfo.getApplicationLabel()
                                 : new ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.STRING, "appName", "");
        if (manifestInfo != null) {
          params.setAppIcon(manifestInfo.getApplicationIcon());
        }
        String activity = configuration.getActivity();
        if (activity != null) {
          params.setActivityName(activity);
          ActivityAttributesSnapshot attributes = manifestInfo != null ? manifestInfo.getActivityAttributes(activity) : null;
          if (attributes != null) {
            if (attributes.getLabel() != null) {
              appLabel = attributes.getLabel();
            }
            if (attributes.getIcon() != null) {
              params.setAppIcon(attributes.getIcon());
            }
          }
        }
        ResourceValue resource = params.getResources().resolveResValue(appLabel);
        if (resource != null) {
          params.setAppLabel(resource.getValue());
        }
      }
      catch (Exception ignored) {
      }
    }

    if (mySetTransparentBackground || requiresTransparency()) {
      params.setTransparentBackground();
    }

    params.setImageFactory(factory);

    if (myTimeout > 0) {
      params.setTimeout(myTimeout);
    }

    params.setFontScale(configuration.getFontScale());
    params.setUiMode(configuration.getUiModeFlagValue());

    try {
      myLayoutlibCallback.setLogger(myLogger);

      RenderSecurityManager securityManager =
        isSecurityManagerEnabled ?
        myContext.getModule().getEnvironment().createRenderSecurityManager(
          module.getProject().getBasePath(),
          context.getModule().getAndroidPlatform()
        ) : null;
      if (securityManager != null) {
        securityManager.setActive(true, myCredential);
      }

      try {
        RenderSession session = myLayoutLib.createSession(params);

        if (session.getResult().isSuccess()) {
          session.setSystemBootTimeNanos(0);
          session.setSystemTimeNanos(0);
          // Advance the frame time to display the material progress bars
          session.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(500));
        }
        BufferedImage resultImage = session.getImage();

        RenderResult result = RenderResult.create(context, session, xmlFile, myLogger, toPooledImage(resultImage), myLayoutlibCallback.isUsed());
        RenderSession oldRenderSession = myRenderSession;
        myRenderSession = session;
        RenderTaskPatcher.enableComposeHotReloadMode(myModuleClassLoaderReference.getClassLoader());
        if (oldRenderSession != null) {
          disposeRenderSession(oldRenderSession);
        }
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
  private ILayoutPullParser getIncludingLayoutParser(RenderResources resolver, ILayoutPullParser modelParser) {
    RenderXmlFile xmlFile = getXmlFile();
    if (xmlFile == null) {
      throw new IllegalStateException("getIncludingLayoutParser shouldn't be called on RenderTask without PsiFile");
    }

    if (!myShowWithToolsVisibilityAndPosition) {
      // Don't support 'showIn' when 'tools' attributes are ignored for rendering.
      return null;
    }

    // Code to support editing included layout.
    if (myIncludedWithin == null) {
      myIncludedWithin = myContext.getModule().getEnvironment().createIncludeReference(xmlFile, resolver);
    }

    ILayoutPullParser topParser = null;
    if (myIncludedWithin != IncludeReference.NONE) {
      // Get the name of the layout actually being edited, without the extension
      // as it's what IXmlPullParser.getParser(String) will receive.
      String queryLayoutName = SdkUtils.fileNameToResourceName(xmlFile.getName());
      myLayoutlibCallback.setLayoutParser(queryLayoutName, modelParser);

      // Attempt to read from PSI.
      RenderXmlFile fromXmlFile = myIncludedWithin.getFromXmlFile(myContext.getModule().getProject());
      if (fromXmlFile != null) {
        LayoutRenderPullParser parser = LayoutRenderPullParser.create(fromXmlFile, myLogger,
                                                                      myContext.getModule().getResourceRepositoryManager());
        topParser = parser;
      }
      else {
        // TODO(namespaces, b/74003372): figure out where to get the namespace from.
        topParser = LayoutFilePullParser.create(new PathString(myIncludedWithin.getFromPath()), ResourceNamespace.TODO(), myContext.getModule().getResourceIdManager());
        if (topParser == null) {
          myLogger.error(null, String.format("Could not read layout file %1$s", myIncludedWithin.getFromPath()), null, null, null);
        }
      }
    }

    return topParser;
  }

  private static <T> CompletableFuture<T> immediateFailedFuture(Throwable exception) {
    CompletableFuture<T> future = new CompletableFuture<>();
    future.completeExceptionally(exception);
    return future;
  }

  /**
   * Executes the passed {@link Callable} as an async render action and keeps track of it. If {@link #dispose()} is called, the call will
   * wait until all the async actions have finished running.
   *
   * @param callable the {@link Callable} to be executed in the Render thread.
   * @param timeout  maximum time to wait for the action to execute. If <= 0, the default timeout
   *                 (see {@link RenderAsyncActionExecutor#DEFAULT_RENDER_THREAD_TIMEOUT_MS}) will be used.
   * @param unit     the {@link TimeUnit} for the timeout.
   *                 See {@link RenderService#getRenderAsyncActionExecutor()}.
   */
  @VisibleForTesting
  @NotNull
  private <V> CompletableFuture<V> runAsyncRenderAction(@NotNull Callable<V> callable, long timeout, @NotNull TimeUnit unit) {
    if (isDisposed.get()) {
      return immediateFailedFuture(new IllegalStateException("RenderTask was already disposed"));
    }

    synchronized (myRunningFutures) {
      CompletableFuture<V> newFuture = timeout < 1 ?
                                       RenderService.getRenderAsyncActionExecutor().runAsyncAction(myTopic, callable) :
                                       RenderService.getRenderAsyncActionExecutor().runAsyncActionWithTimeout(timeout, unit, myTopic,
                                                                                                              callable);
      myRunningFutures.add(newFuture);
      newFuture
        .whenCompleteAsync((result, ex) -> {
          synchronized (myRunningFutures) {
            myRunningFutures.remove(newFuture);
          }
        });

      return newFuture;
    }
  }

  /**
   * Executes the passed {@link Callable} as an async render action and keeps track of it. If {@link #dispose()} is called, the call will
   * wait until all the async actions have finished running. This will wait the default timeout
   * (see {@link DEFAULT_RENDER_THREAD_TIMEOUT_MS}) for the invoked action to complete.
   * See {@link RenderService#getRenderAsyncActionExecutor()}.
   */
  @VisibleForTesting
  public @NotNull <V> CompletableFuture<V> runAsyncRenderAction(@NotNull Callable<V> callable) {
    return runAsyncRenderAction(callable, 0, TimeUnit.SECONDS);
  }

  /**
   * Inflates the layout but does not render it.
   *
   * @return A {@link RenderResult} with the result of inflating the inflate call. The result might not contain a result bitmap.
   */
  @NotNull
  public CompletableFuture<RenderResult> inflate() {
    // During development only:
    //assert !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock during inflate!";

    RenderXmlFile xmlFile = getXmlFile();
    if (xmlFile == null) {
      return immediateFailedFuture(new IllegalStateException("inflate shouldn't be called on RenderTask without PsiFile"));
    }
    if (xmlFile.getProject().isDisposed()) {
      return CompletableFuture.completedFuture(null);
    }

    long startInflateTimeMs = System.currentTimeMillis();
    // Inflation can be way slower than a regular render since it will load classes and initiate most of the state.
    // That's why, for inflating, we allow a more generous timeout than for rendering.
    return runAsyncRenderAction(() -> createRenderSession((width, height) -> {
      myTestEventListener.onBeforeInflate();
      if (myImageFactoryDelegate != null) {
        return myImageFactoryDelegate.getImage(width, height);
      }

      return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
    }), DEFAULT_RENDER_THREAD_TIMEOUT_MS * 10, TimeUnit.MILLISECONDS)
      .whenComplete((result, ex) -> myTestEventListener.onAfterInflate())
      .handle((result, ex) -> {
        if (ex != null) {
          while (ex instanceof CompletionException) {
            ex = ex.getCause();
          }
          String message = ex.getMessage();
          if (message == null) {
            message = ex.toString();
          }
          RenderModelModule module = myContext.getModule();
          RenderProblem.ActionFixFactory fixFactory = module.getEnvironment().getActionFixFactory();
          myLogger.addMessage(RenderProblem.createHtml(ERROR, message, module.getProject(), myLogger.getLinkManager(), ex, fixFactory));
        }

        if (result != null) {
          return result.createWithStats(
            new RenderResultStats(
              System.currentTimeMillis() - startInflateTimeMs,
              -1,
              myModuleClassLoaderReference.getClassLoader().getStats()));
        }
        else {
          if (runReadAction(xmlFile::isValid)) {
            return RenderResult.createErrorRenderResult(Result.Status.ERROR_RENDER_TASK, myContext.getModule(), xmlFile, ex, myLogger);
          }
          else {
            LOG.warn("Invalid file " + xmlFile);
            return null;
          }
        }
      });
  }

  /**
   * Only do a measure pass using the current render session.
   */
  @NotNull
  public CompletableFuture<RenderResult> layout() {
    if (myRenderSession == null) {
      return CompletableFuture.completedFuture(null);
    }

    assert getXmlFile() != null;
    try {
      // runAsyncRenderAction might not run immediately so we need to capture the current myRenderSession and myPsiFile values
      RenderSession renderSession = myRenderSession;
      RenderXmlFile xmlFile = getXmlFile();
      return runAsyncRenderAction(() -> {
        myRenderSession.measure();
        return RenderResult.create(myContext, renderSession, xmlFile, myLogger, ImagePool.NULL_POOLED_IMAGE, myLayoutlibCallback.isUsed());
      });
    }
    catch (Exception e) {
      // nothing
    }
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Triggers execution of the Handler and frame callbacks in layoutlib.
   *
   * @return a {@link ExecuteCallbacksResult} future that is completed when callbacks are executed that is true if there are more callbacks
   * to execute.
   */
  @NotNull
  public CompletableFuture<ExecuteCallbacksResult> executeCallbacks(long timeNanos) {
    if (myRenderSession == null) {
      return CompletableFuture.completedFuture(ExecuteCallbacksResult.EMPTY);
    }

    // Execute the callbacks with a 500ms timeout for all of them to run. Callbacks should not take a long time to execute, if they do,
    // we can safely ignore this render request and wait for the next.
    // With the current implementation, the callbacks will eventually run anyway, the timeout will allow us to detect the timeout sooner.
    return runAsyncRenderAction(() -> {
      myRenderSession.setSystemTimeNanos(timeNanos);
      long start = System.currentTimeMillis();
      boolean hasMoreCallbacks = myRenderSession.executeCallbacks(timeNanos);
      return ExecuteCallbacksResult.create(hasMoreCallbacks, System.currentTimeMillis() - start);
    }, 500, TimeUnit.MILLISECONDS);
  }

  /**
   * Sets layoutlib system time (needed for the correct touch event handling) and informs layoutlib that there was a (mouse) touch event
   * detected of a particular type at a particular point.
   *
   * @param touchEventType type of a touch event.
   * @param x              horizontal android coordinate of the detected touch event.
   * @param y              vertical android coordinate of the detected touch event.
   * @return a {@link InteractionEventResult} future that is completed when layoutlib handled the touch event.
   */
  @NotNull
  public CompletableFuture<InteractionEventResult> triggerTouchEvent(@NotNull RenderSession.TouchEventType touchEventType, int x, int y, long timeNanos) {
    if (myRenderSession == null) {
      return CompletableFuture.completedFuture(null);
    }

    return runAsyncRenderAction(() -> {
      myRenderSession.setSystemTimeNanos(timeNanos);
      long start = System.currentTimeMillis();
      myRenderSession.triggerTouchEvent(touchEventType, x, y);
      return InteractionEventResult.create(System.currentTimeMillis() - start);
    });
  }

  /**
   * Sets layoutlib system time (needed for the correct event handling) and pass the Java KeyEvent to layoutlib.
   *
   * @return a {@link InteractionEventResult} future that is completed when layoutlib handled the key event.
   */
  @NotNull
  public CompletableFuture<InteractionEventResult> triggerKeyEvent(@NotNull KeyEvent event, long timeNanos) {
    if (myRenderSession == null) {
      return CompletableFuture.completedFuture(null);
    }

    return runAsyncRenderAction(() -> {
      myRenderSession.setSystemTimeNanos(timeNanos);
      long start = System.currentTimeMillis();
      myRenderSession.triggerKeyEvent(event);
      return InteractionEventResult.create(System.currentTimeMillis() - start);
    });
  }

  /**
   * Method used to report unhandled layoutlib exceptions to the crash reporter
   */
  private void reportException(@NotNull Throwable e) {
    if (e instanceof CancellationException) {
      // Cancellation exceptions are due to either cancelled Visual Linting tasks or tasks evicted from a full render queue.
      // They are not crashes and so should not be reported to the crash reporter.
      return;
    }
    // This in an unhandled layoutlib exception, pass it to the crash reporter
    myCrashReporter.submit(myContext.getModule().getEnvironment().createCrashReport(e));
  }

  /**
   * Renders the layout to the current {@link IImageFactory} set in {@link #myImageFactoryDelegate}
   *
   * @param forceMeasure indicates whether it is necessary to re-measure before rendering. This is
   *                     needed for example when re-rendering after changing the quality up.
   */
  @NotNull
  private CompletableFuture<RenderResult> renderInner(boolean forceMeasure) {
    // During development only:
    //assert !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock during render!";

    RenderXmlFile xmlFile = getXmlFile();
    assert xmlFile != null;

    CompletableFuture<RenderResult> inflateCompletableResult;
    if (myRenderSession == null) {
      inflateCompletableResult = inflate()
        .whenComplete((renderResult, exception) -> {
          Result result = renderResult != null ? renderResult.getRenderResult() : null;
          if (result == null || !result.isSuccess()) {
            Throwable e = result != null ? result.getException() : exception;
            if (e != null) {
              reportException(e);
            }
            if (result != null) {
              myLogger.error(null, result.getErrorMessage(), e, null, null);
            }
          }
        });
    }
    else {
      inflateCompletableResult = CompletableFuture.completedFuture(null);
    }

    return inflateCompletableResult.thenCompose(inflateResult -> {
      try {
        long startRenderTimeMs = System.currentTimeMillis();
        return runAsyncRenderAction(() -> {
          myTestEventListener.onBeforeRender();
          myRenderSession.render(forceMeasure);
          BufferedImage resultImage = myRenderSession.getImage();
          RenderResult result =
            RenderResult.create(myContext, myRenderSession, xmlFile, myLogger, toPooledImage(resultImage), myLayoutlibCallback.isUsed());
          Result renderResult = result.getRenderResult();
          if (renderResult.getException() != null) {
            reportException(renderResult.getException());
            myLogger.error(null, renderResult.getErrorMessage(), renderResult.getException(), null, null);
          }
          if (reportOutOfDateUserClasses && ! myModuleClassLoaderReference.getClassLoader().isUserCodeUpToDate()) {
            RenderProblem.Html problem = RenderProblem.create(WARNING);
            HtmlBuilder builder = problem.getHtmlBuilder();
            builder.addLink("The project has been edited more recently than the last build: ", "Build", " the project.",
                            myLogger.getLinkManager().createBuildProjectUrl());
            myLogger.addMessage(problem);
          }
          return result;
        })
          .whenComplete((result, ex) -> myTestEventListener.onAfterRender())
          .whenComplete((result, ex) -> {
            ModuleClassLoader moduleClassLoader = myModuleClassLoaderReference.getClassLoader();
            // After render clean-up. Dispose the GapWorker cache.
            RenderSessionCleaner.clearGapWorkerCache(moduleClassLoader);
            RenderSessionCleaner.clearFontRequestWorker(moduleClassLoader);
            RenderSessionCleaner.clearCompositions(moduleClassLoader);
          })
          .handle((result, ex) -> {
            if (ex != null) {
              while (ex instanceof CompletionException) {
                ex = ex.getCause();
              }
            }
            if (result == null) {
              result = RenderResult.createErrorRenderResult(Result.Status.ERROR_RENDER, myContext.getModule(), xmlFile, ex, myLogger);
            }
            ModuleClassLoader moduleClassLoader = myModuleClassLoaderReference.getClassLoader();
            return result.createWithStats(new RenderResultStats(
              inflateResult != null ? inflateResult.getStats().getInflateDurationMs() : result.getStats().getInflateDurationMs(),
              System.currentTimeMillis() - startRenderTimeMs,
              moduleClassLoader.getStats().getClassesFound(),
              moduleClassLoader.getStats().getAccumulatedFindTimeMs(),
              moduleClassLoader.getStats().getAccumulatedRewriteTimeMs()));
          });
      }
      catch (Exception e) {
        reportException(e);
        String message = e.getMessage();
        if (message == null) {
          message = e.toString();
        }
        RenderModelModule module = myContext.getModule();
        RenderProblem.ActionFixFactory fixFactory = module.getEnvironment().getActionFixFactory();
        myLogger.addMessage(RenderProblem.createHtml(ERROR, message, module.getProject(), myLogger.getLinkManager(), e, fixFactory));
        return CompletableFuture.completedFuture(
          RenderResult.createErrorRenderResult(Result.Status.ERROR_RENDER_TASK, module, xmlFile, e, myLogger));
      }
    });
  }

  /**
   * Method that renders the layout to a bitmap using the given {@link IImageFactory}. This render call will render the image to a
   * bitmap that can be accessed via the returned {@link RenderResult}.
   * <p/>
   * If {@link #inflate()} hasn't been called before, this method will implicitly call it.
   */
  @NotNull
  public CompletableFuture<RenderResult> render(@NotNull IImageFactory factory) {
    myImageFactoryDelegate = factory;
    // If a re-render is happening after changing the quality up, then we need to re-measure
    // to avoid the rendered image to show things out of place.
    boolean forceMeasure = myTargetQuality > myCurrentQuality;
    myCurrentQuality = myTargetQuality;
    return renderInner(forceMeasure);
  }

  /**
   * Run rendering with default IImageFactory implementation provided by RenderTask. This render call will render the image to a bitmap
   * that can be accessed via the returned {@link RenderResult}
   * <p/>
   * If {@link #inflate()} hasn't been called before, this method will implicitly call it.
   */
  @NotNull
  public CompletableFuture<RenderResult> render() {
    return render(myCachingImageFactory);
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
      }
      else if (result.getStatus() == Result.Status.ERROR_TIMEOUT) {
        myLogger.error(null, "Rendering timed out.", null, null, null);
      }
      else {
        myLogger.error(null, "Unknown render problem: " + result.getStatus(), null, null, null);
      }
    }
    else if (myIncludedWithin != null && myIncludedWithin != IncludeReference.NONE) {
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
   * @param drawableResourceValue the drawable resource value to be rendered
   * @return a {@link CompletableFuture} with the BufferedImage of the passed drawable.
   */
  @NotNull
  public CompletableFuture<BufferedImage> renderDrawable(@NotNull ResourceValue drawableResourceValue) {
    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    RenderContext context = getContext();
    Configuration configuration = context.getConfiguration();
    DrawableParams params =
      new DrawableParams(drawableResourceValue, context.getModule().getModuleKey(), hardwareConfig, configuration.getResourceResolver(),
                         myLayoutlibCallback, context.getMinSdkVersion().getApiLevel(), context.getTargetSdkVersion().getApiLevel(),
                         myLogger);
    params.setForceNoDecor();
    params.setAssetRepository(context.getModule().getAssetRepository());
    params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH, configuration.getAdaptiveShape().getPathDescription());
    params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, configuration.getUseThemedIcon());
    params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH, configuration.getWallpaperPath());

    return runAsyncRenderAction(() -> myLayoutLib.renderDrawable(params))
      .thenCompose(result -> {
        if (result != null && result.isSuccess()) {
          Object data = result.getData();
          if (!(data instanceof BufferedImage)) {
            data = null;
          }
          return CompletableFuture.completedFuture((BufferedImage)data);
        }
        else {
          if (result.getStatus() == Result.Status.ERROR_NOT_A_DRAWABLE) {
            LOG.debug("renderDrawable called with a non-drawable resource" + drawableResourceValue);
            return CompletableFuture.completedFuture(null);
          }

          Throwable exception = result == null ? new RuntimeException("Rendering failed - null result") : result.getException();
          if (exception == null) {
            String message = result.getErrorMessage();
            exception = new RuntimeException(message == null ? "Rendering failed" : "Rendering failed - " + message);
          }
          reportException(exception);
          return immediateFailedFuture(exception);
        }
      });
  }

  @NotNull
  private LayoutLibrary getLayoutLib() {
    return myLayoutLib;
  }

  @NotNull
  public LayoutlibCallbackImpl getLayoutlibCallback() {
    return myLayoutlibCallback;
  }

  /**
   * Returns true if this service can render a non-rectangular shape
   */
  private boolean isNonRectangular() {
    ResourceFolderType folderType = getContext().getFolderType();
    // Drawable images can have non-rectangular shapes; we need to ensure that we blank out the
    // background with full alpha
    return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP;
  }

  /**
   * Returns true if this service requires rendering into a transparent/alpha channel image
   */
  private boolean requiresTransparency() {
    // Drawable images can have non-rectangular shapes; we need to ensure that we blank out the
    // background with full alpha
    return isNonRectangular();
  }

  /**
   * Measure the children of the given parent tag, applying the given filter to the pull parser's
   * attribute values.
   *
   * @param parent the parent tag to measure children for
   * @param filter the filter to apply to the attribute values
   * @return a map from the children of the parent to new bounds of the children
   */
  @NotNull
  public CompletableFuture<Map<RenderXmlTag, ViewInfo>> measureChildren(@NotNull RenderXmlTag parent, @Nullable AttributeFilter filter) {
    ILayoutPullParser modelParser = LayoutRenderPullParser.create(filter,
                                                                  parent,
                                                                  myLogger,
                                                                  myContext.getModule().getResourceRepositoryManager());
    Map<RenderXmlTag, ViewInfo> map = new HashMap<>();
    return RenderService.getRenderAsyncActionExecutor().runAsyncAction(myTopic, () -> measure(modelParser))
      .thenComposeAsync(session -> {
        if (session != null) {
          try {
            Result result = session.getResult();

            if (result != null && result.isSuccess()) {
              assert session.getRootViews().size() == 1;
              ViewInfo root = session.getRootViews().get(0);
              List<ViewInfo> children = root.getChildren();
              for (ViewInfo info : children) {
                RenderXmlTag tag = RenderService.getXmlTag(info);
                if (tag != null) {
                  map.put(tag, info);
                }
              }
            }

            return CompletableFuture.completedFuture(map);
          }
          finally {
            disposeRenderSession(session);
          }
        }

        return CompletableFuture.completedFuture(Collections.emptyMap());
      }, AppExecutorUtil.getAppExecutorService());
  }

  /**
   * Measure the given child in context, applying the given filter to the
   * pull parser's attribute values.
   *
   * @param tag    the child to measure
   * @param filter the filter to apply to the attribute values
   * @return a {@link CompletableFuture} that will return the {@link ViewInfo} if found.
   */
  @NotNull
  public CompletableFuture<ViewInfo> measureChild(@NotNull RenderXmlTag tag, @Nullable AttributeFilter filter) {
    RenderXmlTag parent = tag.getParentTag();
    if (parent == null) {
      return CompletableFuture.completedFuture(null);
    }

    return measureChildren(parent, filter).thenApply(map -> map.get(tag));
  }

  @Nullable
  private RenderSession measure(ILayoutPullParser parser) {
    RenderContext context = getContext();
    Configuration configuration = context.getConfiguration();
    ResourceResolver resolver = configuration.getResourceResolver();

    myLayoutlibCallback.reset();

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
    SessionParams params = new SessionParams(parser,
                                             RenderingMode.NORMAL,
                                             context.getModule().getModuleKey(),
                                             hardwareConfig,
                                             resolver,
                                             myLayoutlibCallback,
                                             context.getMinSdkVersion().getApiLevel(),
                                             context.getTargetSdkVersion().getApiLevel(),
                                             myLogger);
    params.setForceNoDecor();
    params.setExtendedViewInfoMode(true);
    params.setLocale(myLocale.toLocaleId());
    params.setAssetRepository(context.getModule().getAssetRepository());
    params.setFlag(RenderParamsFlags.FLAG_KEY_ADAPTIVE_ICON_MASK_PATH, configuration.getAdaptiveShape().getPathDescription());
    params.setFlag(RenderParamsFlags.FLAG_KEY_USE_THEMED_ICON, configuration.getUseThemedIcon());
    params.setFlag(RenderParamsFlags.FLAG_KEY_WALLPAPER_PATH, configuration.getWallpaperPath());
    @Nullable RenderModelManifest manifestInfo = context.getModule().getManifest();
    params.setRtlSupport(manifestInfo != null && manifestInfo.isRtlSupported());

    try {
      myLayoutlibCallback.setLogger(myLogger);

      return myLayoutLib.createSession(params);
    }
    catch (RuntimeException t) {
      // Exceptions from the bridge.
      myLogger.error(null, t.getLocalizedMessage(), t, null, null);
      throw t;
    }
  }

  /**
   * Similar to {@link #runAsyncRenderAction(Callable)} but executes it under a {@link RenderSession}. This allows the
   * given block to access resources since they are setup before executing it.
   *
   *
   * @param block the {@link Callable} to be executed in the Render thread.
   * @param timeout  maximum time to wait for the action to execute. If <= 0, the default timeout
   *                (see {@link DEFAULT_RENDER_THREAD_TIMEOUT_MS}) will be used.
   * @param unit    the {@link TimeUnit} for the timeout.
   *
   * @return A {@link CompletableFuture} that completes when the block finalizes.
   */
  @NotNull
  public CompletableFuture<Void> runAsyncRenderActionWithSession(@NotNull Runnable block, long timeout, @NotNull TimeUnit unit) {
    if (isDisposed.get()) {
      return immediateFailedFuture(new IllegalStateException("RenderTask was already disposed"));
    }
    RenderSession renderSession = myRenderSession;
    if (renderSession == null) {
      return immediateFailedFuture(new IllegalStateException("No RenderSession available"));
    }
    return runAsyncRenderAction(() -> {
      renderSession.execute(block);
      return null;
    }, timeout, unit);
  }

  @VisibleForTesting
  public void setCrashReporter(@NotNull CrashReporter crashReporter) {
    myCrashReporter = crashReporter;
  }

  /**
   * Returns the context used in this render task. The context includes things like platform information, file or module.
   */
  @NotNull
  public RenderContext getContext() {
    return myContext;
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
    String getAttribute(@NotNull RenderXmlTag node, @Nullable String namespace, @NotNull String localName);
  }

  /**
   * Properly disposes {@link RenderSession} as a single {@link RenderService} call. It returns a {@link CompletableFuture} that
   * will complete once the disposal has completed.
   *
   * @param renderSession a session to be disposed of
   */
  @NotNull
  private CompletableFuture<Void> disposeRenderSession(@NotNull RenderSession renderSession) {
    return RenderSessionCleaner.dispose(renderSession, myModuleClassLoaderReference.getClassLoader());
  }
}
