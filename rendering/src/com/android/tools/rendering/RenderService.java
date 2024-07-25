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

import static com.android.tools.rendering.ProblemSeverity.ERROR;
import static com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic;

import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.configurations.Configuration;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.layoutlib.RenderingException;
import com.android.tools.idea.layoutlib.UnsupportedJavaRuntimeException;
import com.android.tools.rendering.api.RenderModelModule;
import com.android.tools.rendering.classloading.ClassTransform;
import com.android.tools.rendering.imagepool.ImagePool;
import com.android.tools.rendering.imagepool.ImagePoolFactory;
import com.android.tools.rendering.parsers.ILayoutPullParserFactory;
import com.android.tools.rendering.parsers.RenderXmlFile;
import com.android.tools.rendering.parsers.RenderXmlTag;
import com.android.tools.rendering.parsers.TagSnapshot;
import com.android.tools.rendering.tracking.RenderTaskAllocationTracker;
import com.android.tools.rendering.tracking.RenderTaskAllocationTrackerImpl;
import com.android.tools.rendering.tracking.StackTraceCapture;
import com.android.tools.sdk.LayoutlibFactory;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The {@link RenderService} provides rendering and layout information for Android layouts. This is a wrapper around the layout library.
 */
final public class RenderService implements Disposable {
  private static final Object ourExecutorLock = new Object();
  private static RenderExecutor ourExecutor = getOrCreateExecutor();

  @NotNull
  private static RenderExecutor getOrCreateExecutor() {
    synchronized (ourExecutorLock) {
      if (ourExecutor == null) ourExecutor = RenderExecutor.create();
      return ourExecutor;
    }
  }

  @Nullable
  private static RenderExecutor getExistingExecutor() {
    synchronized (ourExecutorLock) {
      return ourExecutor;
    }
  }

  @TestOnly
  public static void initializeRenderExecutor() {
    synchronized (ourExecutorLock) {
      ourExecutor = RenderExecutor.create();
    }
  }

  public static void shutdownRenderExecutor() {
    RenderExecutor currentExecutor = getExistingExecutor();
    if (currentExecutor != null) currentExecutor.shutdown();
  }

  /**
   * Shutdowns the render thread and cancels any pending tasks.
   * @param timeoutSeconds if >0, wait at most this number of seconds before killing any running tasks.
   */
  @TestOnly
  public static void shutdownRenderExecutor(@SuppressWarnings("SameParameterValue") long timeoutSeconds) {
    // We avoid using getExecutor here since we do not want to create a new one if it doesn't exist
    RenderExecutor currentExecutor = getExistingExecutor();
    if (currentExecutor != null) currentExecutor.shutdown(timeoutSeconds);
  }

  private static final String JDK_INSTALL_URL = "https://developer.android.com/preview/setup-sdk.html#java8";

  private final Object myCredential = new Object();

  private final ImagePool myImagePool = ImagePoolFactory.createImagePool();

  private final Consumer<RenderTaskBuilder> myConfigureBuilder;

  @NotNull
  public static RenderAsyncActionExecutor getRenderAsyncActionExecutor() {
    return getOrCreateExecutor();
  }

  /**
   * Returns the current stack trace of the render thread. Only for testing.
   */
  @TestOnly
  public static StackTraceElement[] getCurrentExecutionStackTrace() {
    return getOrCreateExecutor().currentStackTrace();
  }

  public RenderService(@NotNull Consumer<RenderTaskBuilder> configureBuilder) {
    myConfigureBuilder = configureBuilder;
  }

  @NotNull
  public RenderLogger createLogger(
    @Nullable Project project,
    boolean logFramework,
    @NotNull RenderProblem.ActionFixFactory fixFactory,
    @NotNull Supplier<HtmlLinkManager> linkManagerFactory) {
    return new RenderLogger(project, myCredential, logFramework, fixFactory, linkManagerFactory);
  }

  @NotNull
  public RenderLogger createLogger(@Nullable Project project) {
    return new RenderLogger(project);
  }

  @NotNull
  public RenderLogger getNopLogger() {
    return RenderLogger.NOP_RENDER_LOGGER;
  }

  /**
   * Returns a {@link RenderTaskBuilder} that can be used to build a new {@link RenderTask}
   */
  @NotNull
  public RenderTaskBuilder taskBuilder(@NotNull RenderModelModule module,
                                       @NotNull Configuration configuration,
                                       @NotNull RenderLogger logger) {
    RenderTaskBuilder builder = new RenderTaskBuilder(module, configuration, myImagePool, myCredential, logger);
    myConfigureBuilder.accept(builder);
    return builder;
  }

  @Override
  public void dispose() {
    myImagePool.dispose();
  }

  /**
   * @return true if the underlying {@link RenderExecutor} is busy, false otherwise.
   */
  public static boolean isBusy() {
    RenderExecutor currentExecutor = getExistingExecutor();
    return currentExecutor != null && currentExecutor.isBusy();
  }

  /**
   * @return true if called from the render thread.
   */
  public static boolean isRenderThread() {
    RenderExecutor currentExecutor = getExistingExecutor();
    return currentExecutor != null && currentExecutor.isRenderThread();
  }

  /**
   * Given a {@link ViewInfo} from a layoutlib rendering, checks that the view info provides
   * valid bounds. This is normally the case. However, there are known scenarios, where
   * for various reasons, the View is left in a state where some of its bounds (left, right, top
   * or bottom) are not properly resolved; they carry MeasureSpec state along, which depending
   * on whether the specification was AT_MOST or EXACTLY this will either be a very large number,
   * or a very small (negative) number. In these cases we don't want to pass on the values to
   * further UI editing processing, since it for example can lead to calling Graphics#drawLine
   * with giant coordinates which can freeze the IDE; see for example
   * https://code.google.com/p/android/issues/detail?id=178690.
   * <p/>
   * To detect this, we simply need to check to see if the MeasureSpec mode bits are set
   * in any of the four bounds fields of the {@link ViewInfo}. Note however that these
   * view bounds are sometimes manipulated (e.g. values added or subtracted if a parent view
   * bound is also invalid) so rather than simply looking for the mode mask strictly, we look
   * in the nearby range too.
   *
   * @param view the {@link ViewInfo} to check
   * @return Normally the {@link ViewInfo} itself, but a placeholder 0-bound {@link ViewInfo} if
   * the view bounds are indeed invalid
   */
  @NotNull
  public static ViewInfo getSafeBounds(@NotNull ViewInfo view) {
    int left = Math.abs(view.getLeft());
    int right = Math.abs(view.getRight());
    int top = Math.abs(view.getTop());
    int bottom = Math.abs(view.getBottom());

    if (left < MAX_MAGNITUDE && right < MAX_MAGNITUDE && top < MAX_MAGNITUDE && bottom < MAX_MAGNITUDE) {
      return view;
    }
    else {
      // Not extracted as a constant; we expect this scenario to be rare
      return new ViewInfo(null, null, 0, 0, 0, 0);
    }
  }

  /**
   * Returns the {@link RenderXmlTag} associated with a {@link ViewInfo}, if any
   *
   * @param view the view to check
   * @return the corresponding tag, if any
   */
  @Nullable
  public static RenderXmlTag getXmlTag(@NotNull ViewInfo view) {
    Object cookie = view.getCookie();
    if (cookie != null) {
      if (cookie instanceof TagSnapshot) {
        TagSnapshot snapshot = (TagSnapshot)cookie;
        return snapshot.tag;
      }
      if (cookie instanceof MergeCookie) {
        cookie = ((MergeCookie) cookie).getCookie();
        if (cookie instanceof TagSnapshot) {
          TagSnapshot snapshot = (TagSnapshot)cookie;
          return snapshot.tag;
        }
      }
      if (cookie instanceof RenderXmlTag) {
        return (RenderXmlTag)cookie;
      }
    }

    return null;
  }

  @NotNull
  public ImagePool getSharedImagePool() {
    return myImagePool;
  }

  /** This is the View.MeasureSpec mode shift */
  private static final int MEASURE_SPEC_MODE_SHIFT = 30;

  /**
   * The maximum absolute value of bounds. This tries to identify values that carry
   * remnants of View.MeasureSpec mode bits, but accounts for the fact that sometimes arithmetic
   * is carried out on these values afterwards to bring them to lower values than they started
   * at, and we want to include those as well; there's a lot of room since the bits are shifted
   * quite a long way compared to the current relevant screen pixel ranges.
   */
  private static final int MAX_MAGNITUDE = 1 << (MEASURE_SPEC_MODE_SHIFT - 5);

  private static Logger getLogger() {
    return Logger.getInstance(RenderService.class);
  }

  public static class RenderTaskBuilder {
    private final RenderContext myContext;
    private final Object myCredential;
    @NotNull private ImagePool myImagePool;
    @Nullable private RenderXmlFile myXmlFile;
    @NotNull private final RenderLogger myLogger;
    @Nullable private ILayoutPullParserFactory myParserFactory;
    private boolean isSecurityManagerEnabled = false; // is not supported for JDK 21
    private float myQuality = 1f;
    private boolean showDecorations = true;
    private boolean showWithToolsVisibilityAndPosition = true;
    private int myMaxRenderWidth = -1;
    private int myMaxRenderHeight = -1;
    private boolean enableLayoutScanner = false;
    private SessionParams.RenderingMode myRenderingMode = null;
    private boolean useTransparentBackground = false;
    private Function<Object, List<ViewInfo>> myCustomContentHierarchyParser = null;

    /**
     * If two RenderTasks share the same ModuleClassLoader they share the same compose framework. This way they share the state. If we would
     * like to control the state of the framework we want to create a dedicated ClassLoader so that the RenderTask has its own compose
     * framework. Having a dedicated ClassLoader also allows for clearing resources right after the RenderTask no longer used.
     */
    private boolean privateClassLoader = false;

    /**
     * Force classes preloading in RenderTask right after creation.
     */
    private Collection<String> classesToPreload = Collections.emptyList();

    /**
     * Additional bytecode transform to apply to project classes when loaded.
     */
    private ClassTransform myAdditionalProjectTransform = ClassTransform.getIdentity();

    /**
     * Additional bytecode transform to apply to non project classes when loaded.
     */
    private ClassTransform myAdditionalNonProjectTransform = ClassTransform.getIdentity();

    /**
     * Handler called when a new class loader has been instantiated. This allows resetting some state that
     * might be specific to the classes currently loaded.
     */
    @NotNull
    private Runnable myOnNewModuleClassLoader = () -> {};

    /**
     * If true, the {@link RenderTask#render()} will report when the user classes loaded by this class loader are out of date.
     */
    private boolean reportOutOfDateUserClasses = true;
    /**
     * Enum value to specify the context or tool in which a render is happening and its priority
     */
    @NotNull private RenderingTopic myTopic = RenderingTopic.NOT_SPECIFIED;

    /**
     * When true, layoutlib will try to create views using the viewInflaterClass
     * defined in the project's theme, or, if not defined, it will try to use the
     * android.support.v7.app.AppCompatViewInflater.
     *
     * Material themes, for example, usually contain a viewInflaterClass attribute.
     */
    private boolean useCustomInflater = true;
    private RenderTask.TestEventListener myTestEventListener = RenderTask.NOP_TEST_EVENT_LISTENER;

    private RenderTaskBuilder(@NotNull RenderModelModule module,
                              @NotNull Configuration configuration,
                              @NotNull ImagePool defaultImagePool,
                              @NotNull Object credential,
                              @NotNull RenderLogger logger) {
      myContext = new RenderContext(module, configuration);
      myImagePool = defaultImagePool;
      myCredential = credential;
      myLogger = logger;
    }


    /**
     * Forces preloading classes in RenderTask after creation.
     */
    @NotNull
    public RenderTaskBuilder preloadClasses(Collection<String> classesToPreload) {
      this.classesToPreload = classesToPreload;
      return this;
    }

    /**
     * Forces the task to create its own ModuleClassLoader instead of using a shared one from the ModuleClassLoaderManager
     */
    @NotNull
    public RenderTaskBuilder usePrivateClassLoader() {
      privateClassLoader = true;
      return this;
    }

    @NotNull
    public RenderTaskBuilder withPsiFile(@NotNull RenderXmlFile xmlFile) {
      this.myXmlFile = xmlFile;
      return this;
    }

    @NotNull
    public RenderTaskBuilder withParserFactory(@NotNull ILayoutPullParserFactory parserFactory) {
      this.myParserFactory = parserFactory;
      return this;
    }

    public RenderTaskBuilder withLayoutScanner(Boolean enableLayoutScanner) {
      this.enableLayoutScanner = enableLayoutScanner;
      return this;
    }

    /**
     * Disables the image pooling for this render task
     */
    @SuppressWarnings("unused")
    @NotNull
    public RenderTaskBuilder disableImagePool() {
      this.myImagePool = ImagePoolFactory.getNonPooledPool();
      return this;
    }

    /**
     * Sets the quality level as a float between 0 and 1.
     * By default, this is set to 1, which is the highest quality.
     */
    @NotNull
    public RenderTaskBuilder withQuality(float quality) {
      this.myQuality = quality;
      return this;
    }

    /**
     * @see RenderTask#setMaxRenderSize(int, int)
     */
    @NotNull
    public RenderTaskBuilder withMaxRenderSize(int maxRenderWidth, int maxRenderHeight) {
      myMaxRenderWidth = maxRenderWidth;
      myMaxRenderHeight = maxRenderHeight;
      return this;
    }

    /**
     * Disables the security manager for the {@link RenderTask}.
     * Bazel has its own security manager. We allow rendering tests to disable the security manager by calling this method. If this method
     * is not called from a test method, it will throw an {@link IllegalStateException}
     */
    @TestOnly
    @VisibleForTesting
    @NotNull
    public RenderTaskBuilder disableSecurityManager() {
      this.isSecurityManagerEnabled = false;
      return this;
    }

    /**
     * Disables the decorations (status and navigation bars) for the rendered image.
     */
    @NotNull
    public RenderTaskBuilder disableDecorations() {
      this.showDecorations = false;
      return this;
    }

    @NotNull
    public RenderTaskBuilder disableToolsVisibilityAndPosition() {
      this.showWithToolsVisibilityAndPosition = false;
      return this;
    }

    /**
     * @see RenderTask#setRenderingMode(SessionParams.RenderingMode)
     */
    @NotNull
    public RenderTaskBuilder withRenderingMode(@NotNull SessionParams.RenderingMode renderingMode) {
      myRenderingMode = renderingMode;
      return this;
    }

    /**
     * @see RenderTask#setTransparentBackground()
     */
    @NotNull
    public RenderTaskBuilder useTransparentBackground() {
      useTransparentBackground = true;
      return this;
    }

    /**
     * Sets an additional Java bytecode transformation to be applied to the loaded project classes.
     */
    @NotNull
    public RenderTaskBuilder setProjectClassesTransform(@NotNull ClassTransform transform) {
      myAdditionalProjectTransform = transform;
      return this;
    }

    /**
     * Sets an additional Java bytecode transformation to be applied to the loaded non project classes.
     */
    @NotNull
    public RenderTaskBuilder setNonProjectClassesTransform(@NotNull ClassTransform transform) {
      myAdditionalNonProjectTransform = transform;
      return this;
    }

    /**
     * Sets a callback to be notified when a new class loader has been instantiated.
     */
    @NotNull
    public RenderTaskBuilder setOnNewClassLoader(@NotNull Runnable runnable) {
      myOnNewModuleClassLoader = runnable;
      return this;
    }

    /**
     * Stops the render calls from reporting out of date user classes as a warning in the issues.
     */
    @NotNull
    public RenderTaskBuilder doNotReportOutOfDateUserClasses() {
      reportOutOfDateUserClasses = false;
      return this;
    }

    /**
     * Sets a {@link RenderingTopic} for the RenderTask.
     * By default, the topic used is {@link RenderingTopic#NOT_SPECIFIED}
     */
    @NotNull
    public RenderTaskBuilder withTopic(@NotNull RenderingTopic topic) {
      myTopic = topic;
      return this;
    }

    /**
     * Sets whether layoutlib should try to use a custom inflater when rendering.
     * By default, this value is true.
     */
    @NotNull
    public RenderTaskBuilder setUseCustomInflater(boolean useCustomInflater) {
      this.useCustomInflater = useCustomInflater;
      return this;
    }

    /**
     * Sets a custom parser for creating the {@link ViewInfo} hierarchy from the layout root view.
     */
    @NotNull
    public RenderTaskBuilder setCustomContentHierarchyParser(@NotNull Function<Object, List<ViewInfo>> parser) {
      myCustomContentHierarchyParser = parser;
      return this;
    }

    @TestOnly
    @NotNull
    public RenderTaskBuilder setTestEventListener(@NotNull RenderTask.TestEventListener testEventListener) {
      myTestEventListener = testEventListener;
      return this;
    }

    /**
     * Builds a new {@link RenderTask}. The returned future always completes successfully but the value might be null if the RenderTask
     * can not be created.
     */
    @NotNull
    public CompletableFuture<RenderTask> build() {
      RenderTaskAllocationTracker tracker = new RenderTaskAllocationTrackerImpl(myContext.getModule().getEnvironment().isInTest());
      StackTraceCapture stackTraceCaptureElement = tracker.captureAllocationStackTrace();

      return CompletableFuture.supplyAsync(() -> {
        RenderModelModule module = myContext.getModule();
        if (module.isDisposed()) {
          getLogger().warn("Module was already disposed");
          return null;
        }
        IAndroidTarget target = myContext.getConfiguration().getTarget();

        if (module.getAndroidPlatform() == null) {
          myContext.getModule().getEnvironment().reportMissingSdkDependency(myLogger);
          return null;
        }

        if (target == null) {
          myLogger.addMessage(RenderProblem.createPlain(ERROR, "No render target was chosen"));
          return null;
        }

        LayoutLibrary layoutLib;
        try {
          layoutLib = LayoutlibFactory.getLayoutLibrary(target, module.getAndroidPlatform(), module.getEnvironment().getLayoutlibContext());
        }
        catch (UnsupportedJavaRuntimeException e) {
          RenderProblem.Html javaVersionProblem = RenderProblem.create(ERROR);
          javaVersionProblem.getHtmlBuilder()
            .add(e.getPresentableMessage())
            .newline()
            .addLink("Install a supported JDK", JDK_INSTALL_URL);
          myLogger.addMessage(javaVersionProblem);
          return null;
        }
        catch (RenderingException e) {
          String message = e.getPresentableMessage();
          message = message != null ? message : RenderingBundle.message("android.layout.preview.default.error.message");
          myLogger.addMessage(
            RenderProblem.createHtml(
              ERROR, message, module.getProject(), myLogger.getLinkManager(), e, module.getEnvironment().getActionFixFactory()));
          return null;
        }

        try {
          RenderTask task =
            new RenderTask(myContext, myContext.getModule().getEnvironment().getModuleClassLoaderManager(), myLogger, layoutLib,
                           myCredential, myContext.getModule().getEnvironment().getCrashReporter(), myImagePool,
                           myParserFactory, isSecurityManagerEnabled, myQuality, stackTraceCaptureElement, tracker,
                           privateClassLoader, myAdditionalProjectTransform, myAdditionalNonProjectTransform, myOnNewModuleClassLoader,
                           classesToPreload, reportOutOfDateUserClasses, myTopic, useCustomInflater, myTestEventListener);

          if (myXmlFile != null) {
            task.setXmlFile(myXmlFile);
          }

          task
            .setDecorations(showDecorations)
            .setShowWithToolsVisibilityAndPosition(showWithToolsVisibilityAndPosition)
            .setEnableLayoutScanner(enableLayoutScanner);

          if (myMaxRenderWidth != -1 && myMaxRenderHeight != -1) {
            task.setMaxRenderSize(myMaxRenderWidth, myMaxRenderHeight);
          }

          if (useTransparentBackground) {
            task.setTransparentBackground();
          }

          if (myRenderingMode != null) {
            task.setRenderingMode(myRenderingMode);
          }

          if (myCustomContentHierarchyParser != null) {
            task.setCustomContentHierarchyParser(myCustomContentHierarchyParser);
          }

          return task;
        } catch (NoDeviceException e) {
          myLogger.addMessage(RenderProblem.createPlain(ERROR, "No device selected"));
          return null;
        } catch (IllegalStateException | IncorrectOperationException | AssertionError e) {
          // Ignore the exception if it was generated when the facet is being disposed (project is being closed)
          if (!module.isDisposed()) {
            throw e;
          }
        }

        return null;
      }, AppExecutorUtil.getAppExecutorService());
    }
  }
}
