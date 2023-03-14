/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.common.surface.SceneView.SQUARE_SHAPE_POLICY;
import static com.android.tools.idea.rendering.StudioRenderServiceKt.taskBuilder;
import static com.android.tools.idea.rendering.ProblemSeverity.ERROR;
import static com.intellij.util.ui.update.Update.HIGH_PRIORITY;
import static com.intellij.util.ui.update.Update.LOW_PRIORITY;

import com.android.annotations.concurrency.GuardedBy;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.tools.idea.common.analytics.CommonUsageTracker;
import com.android.tools.idea.common.diagnostics.NlDiagnosticsManager;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.DefaultSceneManagerHierarchyProvider;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LayoutScannerConfiguration;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.editors.powersave.PreviewPowerSaveManager;
import com.android.tools.idea.rendering.AndroidFacetRenderModelModule;
import com.android.tools.idea.rendering.ExecuteCallbacksResult;
import com.android.tools.idea.rendering.InteractionEventResult;
import com.android.tools.idea.rendering.RenderConfiguration;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.rendering.RenderModelModule;
import com.android.tools.idea.rendering.RenderProblem;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderResults;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.ShowFixFactory;
import com.android.tools.idea.rendering.StudioRenderConfiguration;
import com.android.tools.idea.rendering.StudioRenderService;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragDndTarget;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SlowOperations;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Update;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends SceneManager {
  private static final SceneDecoratorFactory DECORATOR_FACTORY = new NlSceneDecoratorFactory();

  @VisibleForTesting
  public static final String[] INTERACTIVE_CLASSES_TO_PRELOAD = {
    // As of Compose alpha12
    // First touch event
    "android.view.MotionEvent",
    "androidx.compose.ui.input.pointer.PointerId",
    "androidx.compose.ui.input.pointer.MotionEventAdapterKt",
    "android.view.MotionEvent$PointerCoords",
    "androidx.compose.ui.input.pointer.PointerType",
    "androidx.compose.ui.input.pointer.PointerInputEventData",
    "androidx.compose.ui.input.pointer.PointerInputEvent",
    "androidx.compose.ui.input.pointer.PointerInputChangeEventProducer$PointerInputData",
    "androidx.compose.ui.input.pointer.PointerInputChange",
    "androidx.compose.ui.input.pointer.ConsumedData",
    "androidx.compose.ui.input.pointer.InternalPointerEvent",
    "androidx.compose.ui.input.pointer.PointerEventKt",
    "androidx.compose.ui.input.pointer.Node",
    "androidx.compose.ui.input.pointer.HitPathTracker$CustomEventDispatcherImpl",
    "androidx.compose.ui.input.pointer.CustomEventDispatcher",
    "androidx.compose.ui.input.pointer.NodeParent$removeDetachedPointerInputFilters$1",
    "androidx.compose.ui.input.pointer.NodeParent$removeDetachedPointerInputFilters$2",
    "androidx.compose.ui.input.pointer.NodeParent$removeDetachedPointerInputFilters$3",
    "androidx.compose.ui.input.pointer.PointerEventPass",
    "androidx.compose.ui.input.pointer.PointerEvent",
    "androidx.compose.ui.gesture.GestureUtilsKt",
    "androidx.compose.ui.input.pointer.PointerInputEventProcessorKt",
    "androidx.compose.ui.input.pointer.ProcessResult",
    // First callback execution after touch event
    "androidx.compose.runtime.snapshots.SnapshotStateObserver$applyObserver$1$2",
    "androidx.compose.ui.platform.AndroidComposeViewKt$sam$java_lang_Runnable$0",
    "androidx.compose.runtime.Invalidation",
    "androidx.compose.runtime.InvalidationResult",
    "androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$4",
    "androidx.compose.runtime.PausableMonotonicFrameClock$withFrameNanos$1",
    "androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1",
    "androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$1",
    "kotlinx.coroutines.InvokeOnCancel",
    "androidx.compose.runtime.ComposerImpl$updateValue$2",
    "androidx.compose.runtime.ComposerImpl$realizeOperationLocation$2",
    "androidx.compose.runtime.ComposerImpl$realizeDowns$1",
    "androidx.compose.runtime.ComposerImpl$realizeUps$1",
    "androidx.compose.ui.platform.JvmActualsKt",
    "kotlinx.coroutines.JobCancellationException",
    "kotlinx.coroutines.CopyableThrowable",
    "kotlinx.coroutines.DebugStringsKt",
    // Animation
    "androidx.compose.material.ElevationDefaults",
    "androidx.compose.animation.core.AnimationKt",
    "androidx.compose.animation.core.TargetBasedAnimation",
    "androidx.compose.animation.core.Animation",
    "androidx.compose.animation.core.VectorizedTweenSpec",
    "androidx.compose.animation.core.VectorizedDurationBasedAnimationSpec",
    "androidx.compose.animation.core.VectorizedFiniteAnimationSpec",
    "androidx.compose.animation.core.VectorizedAnimationSpec",
    "androidx.compose.animation.core.VectorizedFloatAnimationSpec",
    "androidx.compose.animation.core.FloatTweenSpec",
    "androidx.compose.animation.core.FloatAnimationSpec",
    "androidx.compose.animation.core.VectorizedFloatAnimationSpec$1",
    "androidx.compose.animation.core.Animations",
    "androidx.compose.animation.core.VectorizedDurationBasedAnimationSpec$DefaultImpls",
    "androidx.compose.animation.core.VectorizedFiniteAnimationSpec$DefaultImpls",
    "androidx.compose.animation.core.VectorizedAnimationSpec$DefaultImpls",
    "androidx.compose.animation.core.Animatable$runAnimation$2",
    "kotlin.jvm.internal.Ref$BooleanRef",
    "androidx.compose.animation.core.Animatable$runAnimation$2$1",
    "androidx.compose.animation.core.SuspendAnimationKt",
    "androidx.compose.animation.core.SuspendAnimationKt$animate$4",
    "androidx.compose.animation.core.Animation$DefaultImpls",
    "androidx.compose.animation.core.SuspendAnimationKt$animate$startTimeNanosSpecified$1",
    "androidx.compose.runtime.MonotonicFrameClockKt",
    "androidx.compose.runtime.BroadcastFrameClock$FrameAwaiter",
    "androidx.compose.runtime.BroadcastFrameClock$withFrameNanos$2$1",
    "androidx.compose.material.ripple.RippleAnimation",
    "androidx.compose.material.ripple.RippleIndicationInstance$addRipple$ripple$1",
    "androidx.compose.animation.core.AnimationVector2D",
    "androidx.compose.material.ripple.RippleAnimation$1",
    "kotlinx.collections.immutable.internal.ListImplementation",
    "androidx.compose.ui.graphics.ClipOp",
    "androidx.compose.ui.graphics.AndroidCanvas$WhenMappings",
    "androidx.compose.ui.graphics.PointMode",
    "android.graphics.Region$Op",
    "androidx.compose.ui.input.pointer.NodeParent$removePointerId$2",
    "androidx.compose.animation.core.AnimationScope",
    "androidx.compose.animation.core.SuspendAnimationKt$animate$6",
    "androidx.compose.animation.core.SuspendAnimationKt$animate$7",
    "androidx.compose.material.ripple.RippleAnimation$fadeIn$2",
    "androidx.compose.material.ripple.RippleAnimation$fadeIn$2$1",
    "androidx.compose.material.ripple.RippleAnimation$fadeIn$2$2",
    "androidx.compose.material.ripple.RippleAnimation$fadeIn$2$3",
    "kotlinx.coroutines.JobSupport$ChildCompletion",
    "androidx.compose.animation.core.AnimationSpecKt",
    "kotlinx.coroutines.internal.StackTraceRecoveryKt",
    "kotlin.coroutines.jvm.internal.BaseContinuationImpl",
    "kotlinx.coroutines.internal.StackTraceRecoveryKt",
    "kotlinx.coroutines.internal.ExceptionsConstuctorKt",
    "kotlin.jvm.JvmClassMappingKt",
    "kotlin.jvm.internal.ClassReference",
    "kotlin.jvm.internal.ClassReference$Companion",
    "kotlin.jvm.functions.Function12",
    "kotlin.jvm.functions.Function22",
    "java.util.concurrent.locks.ReentrantReadWriteLock",
    "java.util.ArrayDeque",
    "kotlin.coroutines.jvm.internal.DebugMetadataKt",
    "kotlin.coroutines.jvm.internal.DebugMetadata",
    "java.lang.annotation.Annotation",
    "kotlin.annotation.Target",
    "java.lang.annotation.Retention",
    "java.lang.annotation.RetentionPolicy",
    "java.lang.annotation.Target",
    "kotlin.Metadata",
    "java.lang.reflect.Proxy",
    "java.lang.reflect.UndeclaredThrowableException",
    "java.lang.NoSuchMethodError",
    "java.lang.NoClassDefFoundError",
    "java.lang.reflect.InvocationHandler",
    "kotlin.annotation.Retention",
    "kotlin.coroutines.jvm.internal.ModuleNameRetriever",
    "kotlin.coroutines.jvm.internal.ModuleNameRetriever$Cache",
    "java.lang.ClassLoader",
    "java.lang.Module",
    "java.lang.module.ModuleDescriptor",
    "kotlinx.coroutines.TimeoutCancellationException",
    "java.util.IdentityHashMap",
    "androidx.compose.animation.core.AnimationEndReason",
    "androidx.compose.animation.core.AnimationResult",
  };

  @Nullable private SceneView mySecondarySceneView;

  private int myDpi = 0;
  private final SelectionChangeListener mySelectionChangeListener = new SelectionChangeListener();
  private final ModelChangeListener myModelChangeListener = new ModelChangeListener();
  private final ConfigurationListener myConfigurationChangeListener = new ConfigurationChangeListener();
  private final boolean myAreListenersRegistered;
  private final DesignSurfaceProgressIndicator myProgressIndicator;
  private final RenderingQueue myRenderingQueue;
  @GuardedBy("myRenderingTaskLock")
  private RenderTask myRenderTask;
  @GuardedBy("myRenderingTaskLock")
  private SessionClock mySessionClock;
  private final Supplier<SessionClock> mySessionClockFactory;
  // Protects all accesses to the myRenderTask reference. RenderTask calls to render and layout do not need to be protected
  // since RenderTask is able to handle those safely.
  private final Object myRenderingTaskLock = new Object();
  private ResourceNotificationManager.ResourceVersion myRenderedVersion;
  // Protects all read/write accesses to the myRenderResult reference
  private final ReentrantReadWriteLock myRenderResultLock = new ReentrantReadWriteLock();
  @GuardedBy("myRenderResultLock")
  @Nullable
  private RenderResult myRenderResult;
  // Variables to track previous values of the configuration bar for tracking purposes
  private final AtomicInteger myConfigurationUpdatedFlags = new AtomicInteger(0);
  private long myElapsedFrameTimeMs = -1;
  private final Object myFuturesLock = new Object();
  @GuardedBy("myFuturesLock")
  private final LinkedList<CompletableFuture<Void>> myRenderFutures = new LinkedList<>();
  @GuardedBy("myFuturesLock")
  private final LinkedList<CompletableFuture<Void>> myPendingFutures = new LinkedList<>();
  private final Semaphore myUpdateHierarchyLock = new Semaphore(1);
  @NotNull private final ViewEditor myViewEditor;
  private final ListenerCollection<RenderListener> myRenderListeners = ListenerCollection.createWithDirectExecutor();
  /**
   * {@code Executor} to run the {@code Runnable} that disposes {@code RenderTask}s. This allows
   * {@code SyncLayoutlibSceneManager} to use a different strategy to dispose the tasks that does not involve using
   * pooled threads.
   */
  @NotNull private final Executor myRenderTaskDisposerExecutor;
  /**
   * True if we are currently in the middle of a render. This attribute is used to prevent listeners from triggering unnecessary renders.
   * If we try to schedule a new render while this is true, we simply re-use the last render in progress.
   */
  @GuardedBy("myFuturesLock")
  private Boolean myIsCurrentlyRendering = false;

  /**
   * If true, the renders using this LayoutlibSceneManager will use transparent backgrounds
   */
  private boolean useTransparentRendering = false;

  /**
   * If true, the renders will use {@link SessionParams.RenderingMode#SHRINK}
   */
  private boolean useShrinkRendering = false;

  /**
   * If true, the scene should use a private ClassLoader.
   */
  private boolean myUsePrivateClassLoader = false;

  /**
   * If true, listen the resource change.
   */
  private boolean myListenResourceChange = true;

  /**
   * If true, the render will paint the system decorations (status and navigation bards)
   */
  private boolean useShowDecorations;

  /**
   * If true, automatically update (if needed) and re-render when being activated. Which happens after {@link #activate(Object)} is called.
   * Note that if the it is activated already, then it will not re-render.
   */
  private boolean myUpdateAndRenderWhenActivated = true;

  /**
   * If true, the scene is interactive.
   */
  private boolean myIsInteractive;

  /**
   * If false, the use of the {@link ImagePool} will be disabled for the scene manager.
   */
  private boolean useImagePool = true;

  private boolean myLogRenderErrors = true;

  /**
   * Value in the range [0f..1f] to set the quality of the rendering, 0 meaning the lowest quality.
   */
  private float quality = 1f;

  /**
   * If true, the rendering will report when the user classes used by this {@link SceneManager} are out of date and have been modified
   * after the last build. The reporting will be done via the rendering log.
   * Compose has its own mechanism to track out of date files so it will disable this reporting.
   */
  private boolean reportOutOfDateUserClasses = false;

  /**
   * When true, this will force the current {@link RenderTask} to be disposed and re-created on the next render. This will also
   * re-inflate the model.
   */
  private final AtomicBoolean myForceInflate = new AtomicBoolean(false);

  private final AtomicBoolean isDisposed = new AtomicBoolean(false);

  /** Counter for user events during the interactive session. */
  private AtomicInteger myInteractiveEventsCounter = new AtomicInteger(0);

  protected static LayoutEditorRenderResult.Trigger getTriggerFromChangeType(@Nullable NlModel.ChangeType changeType) {
    if (changeType == null) {
      return null;
    }

    switch (changeType) {
      case RESOURCE_EDIT:
      case RESOURCE_CHANGED:
        return LayoutEditorRenderResult.Trigger.RESOURCE_CHANGE;
      case EDIT:
      case ADD_COMPONENTS:
      case DELETE:
      case DND_COMMIT:
      case DND_END:
      case DROP:
      case RESIZE_END:
      case RESIZE_COMMIT:
        return LayoutEditorRenderResult.Trigger.EDIT;
      case BUILD:
        return LayoutEditorRenderResult.Trigger.BUILD;
      case CONFIGURATION_CHANGE:
      case UPDATE_HIERARCHY:
        break;
    }

    return null;
  }

  /**
   * Configuration for layout validation from Accessibility Testing Framework through Layoutlib.
   * Based on the configuration layout validation will be turned on or off while rendering.
   */
  @NotNull
  private final LayoutScannerConfiguration myLayoutScannerConfig;

  /**
   * Creates a new LayoutlibSceneManager.
   *
   * @param model                      the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface              the {@link DesignSurface} user to present the result of the renders.
   * @param renderTaskDisposerExecutor {@link Executor} to be used for running the slow {@link #dispose()} calls.
   * @param renderingQueueFactory      a factory to create a {@link RenderingQueue}.
   * @param sceneComponentProvider     a {@link SceneManager.SceneComponentHierarchyProvider} providing the mapping from
   *                                   {@link NlComponent} to {@link SceneComponent}s.
   * @param sceneUpdateListener        a {@link SceneUpdateListener} that allows performing additional operations when updating the scene.
   * @param layoutScannerConfig        a {@link LayoutScannerConfiguration} for layout validation from Accessibility Testing Framework.
   * @param sessionClockFactory        a factory to create a session clock used in the interactive preview.
   */
  protected LayoutlibSceneManager(@NotNull NlModel model,
                                  @NotNull DesignSurface<? extends LayoutlibSceneManager> designSurface,
                                  @NotNull Executor renderTaskDisposerExecutor,
                                  @NotNull Function<Disposable, RenderingQueue> renderingQueueFactory,
                                  @NotNull SceneComponentHierarchyProvider sceneComponentProvider,
                                  @Nullable SceneManager.SceneUpdateListener sceneUpdateListener,
                                  @NotNull LayoutScannerConfiguration layoutScannerConfig,
                                  @NotNull Supplier<SessionClock> sessionClockFactory) {
    super(model, designSurface, sceneComponentProvider, sceneUpdateListener);
    myProgressIndicator = new DesignSurfaceProgressIndicator(designSurface);
    myRenderTaskDisposerExecutor = renderTaskDisposerExecutor;
    myRenderingQueue = renderingQueueFactory.apply(this);
    mySessionClockFactory = sessionClockFactory;
    createSceneView();

    getDesignSurface().getSelectionModel().addListener(mySelectionChangeListener);

    Scene scene = getScene();

    myViewEditor = new ViewEditorImpl(model, scene);

    model.getConfiguration().addListener(myConfigurationChangeListener);

    List<NlComponent> components = model.getComponents();
    if (!components.isEmpty()) {
      NlComponent rootComponent = components.get(0).getRoot();

      boolean previous = getScene().isAnimated();
      scene.setAnimated(false);
      List<SceneComponent> hierarchy = sceneComponentProvider.createHierarchy(this, rootComponent);
      SceneComponent root = hierarchy.isEmpty() ? null : hierarchy.get(0);
      updateFromComponent(root, new HashSet<>());
      scene.setRoot(root);
      updateTargets();
      scene.setAnimated(previous);
    }

    model.addListener(myModelChangeListener);
    myAreListenersRegistered = true;
    myLayoutScannerConfig = layoutScannerConfig;

    // let's make sure the selection is correct
    scene.selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
  }

  /**
   * Creates a new LayoutlibSceneManager with the default settings for running render requests, but with accessibility testing
   * framework scanner disabled.
   * See {@link LayoutlibSceneManager#LayoutlibSceneManager(NlModel, DesignSurface, SceneComponentHierarchyProvider, SceneUpdateListener, Supplier)}
   *
   * @param model                  the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface          the {@link DesignSurface} user to present the result of the renders.
   * @param sceneComponentProvider a {@link SceneManager.SceneComponentHierarchyProvider providing the mapping from {@link NlComponent} to
   *                               {@link SceneComponent}s.
   * @param sceneUpdateListener    a {@link SceneUpdateListener} that allows performing additional operations when updating the scene.
   * @param sessionClockFactory    a factory to create a session clock used in the interactive preview.
   */
  public LayoutlibSceneManager(@NotNull NlModel model,
                               @NotNull DesignSurface<LayoutlibSceneManager> designSurface,
                               @NotNull SceneComponentHierarchyProvider sceneComponentProvider,
                               @NotNull SceneManager.SceneUpdateListener sceneUpdateListener,
                               @NotNull Supplier<SessionClock> sessionClockFactory) {
    this(
      model,
      designSurface,
      AppExecutorUtil.getAppExecutorService(),
      MergingRenderingQueue::new,
      sceneComponentProvider,
      sceneUpdateListener,
      LayoutScannerConfiguration.getDISABLED(),
      sessionClockFactory);
  }

  /**
   * Creates a new LayoutlibSceneManager with the default settings for running render requests.
   * See {@link LayoutlibSceneManager#LayoutlibSceneManager(NlModel, DesignSurface, SceneComponentHierarchyProvider, SceneUpdateListener, Supplier)}
   *
   * @param model the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface the {@link DesignSurface} user to present the result of the renders.
   * @param config configuration for layout validation when rendering.
   */
  public LayoutlibSceneManager(@NotNull NlModel model, @NotNull DesignSurface<LayoutlibSceneManager> designSurface, LayoutScannerConfiguration config) {
    this(
      model,
      designSurface,
      AppExecutorUtil.getAppExecutorService(),
      MergingRenderingQueue::new,
      new LayoutlibSceneManagerHierarchyProvider(),
      null,
      config,
      RealTimeSessionClock::new);
  }

  @NotNull
  public ViewEditor getViewEditor() {
    return myViewEditor;
  }

  @Override
  @NotNull
  public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component) {
    Scene scene = getScene();

    assert scene.getRoot() != null;

    TemporarySceneComponent tempComponent = new TemporarySceneComponent(getScene(), component);
    tempComponent.setTargetProvider(sceneComponent -> ImmutableList.of(new ConstraintDragDndTarget()));
    scene.setAnimated(false);
    scene.getRoot().addChild(tempComponent);
    syncFromNlComponent(tempComponent);
    scene.setAnimated(true);

    return tempComponent;
  }

  @Override
  @NotNull
  public SceneDecoratorFactory getSceneDecoratorFactory() {
    return DECORATOR_FACTORY;
  }

  /**
   * In the layout editor, Scene uses {@link AndroidDpCoordinate}s whereas rendering is done in (zoomed and offset)
   * {@link AndroidCoordinate}s. The scaling factor between them is the ratio of the screen density to the standard density (160).
   */
  @Override
  public float getSceneScalingFactor() {
    return getModel().getConfiguration().getDensity().getDpiValue() / (float)DEFAULT_DENSITY;
  }

  /**
   * Configuration for layout validation from Accessibility Testing Framework through Layoutlib.
   * Based on the configuration layout validation will be turned on or off while rendering.
   */
  @NotNull
  public LayoutScannerConfiguration getLayoutScannerConfig() {
    return myLayoutScannerConfig;
  }

  @Override
  public void dispose() {
    if (isDisposed.getAndSet(true)) {
      return;
    }

    try {
      if (myAreListenersRegistered) {
        NlModel model = getModel();
        getDesignSurface().getSelectionModel().removeListener(mySelectionChangeListener);
        model.getConfiguration().removeListener(myConfigurationChangeListener);
        model.removeListener(myModelChangeListener);
      }
      myRenderListeners.clear();

      myProgressIndicator.stop();
    }
    finally {
      super.dispose();
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        // dispose is called by the project close using the read lock. Invoke the render task dispose later without the lock.
        myRenderTaskDisposerExecutor.execute(this::disposeRenderTask);
      }
      else {
        disposeRenderTask();
      }
    }
  }

  private void updateRenderTask(@Nullable RenderTask newTask) {
    synchronized (myRenderingTaskLock) {
      if (myRenderTask != null && !myRenderTask.isDisposed()) {
        try {
          myRenderTask.dispose();
        } catch (Throwable t) {
          Logger.getInstance(LayoutlibSceneManager.class).warn(t);
        }
      }
      // TODO(b/168445543): move session clock to RenderTask
      mySessionClock = mySessionClockFactory.get();
      myRenderTask = newTask;
    }
  }

  private void disposeRenderTask() {
    RenderTask renderTask;
    synchronized (myRenderingTaskLock) {
      renderTask = myRenderTask;
      myRenderTask = null;
    }
    if (renderTask != null) {
      try {
        renderTask.dispose();
      } catch (Throwable t) {
        Logger.getInstance(LayoutlibSceneManager.class).warn(t);
      }
    }
    updateCachedRenderResult(null);
  }

  @NotNull
  @Override
  protected NlDesignSurface getDesignSurface() {
    return (NlDesignSurface) super.getDesignSurface();
  }

  @NotNull
  @Override
  protected SceneView doCreateSceneView() {
    NlModel model = getModel();

    DesignerEditorFileType type = model.getType();

    if (type == MenuFileType.INSTANCE) {
      return createSceneViewsForMenu();
    }

    SceneView primarySceneView = getDesignSurface().getScreenViewProvider().createPrimarySceneView(getDesignSurface(), this);
    mySecondarySceneView = getDesignSurface().getScreenViewProvider().createSecondarySceneView(getDesignSurface(), this);

    getDesignSurface().updateErrorDisplay();

    return primarySceneView;
  }

  @NotNull
  @Override
  public List<SceneView> getSceneViews() {
    ImmutableList.Builder<SceneView> builder = ImmutableList.<SceneView>builder()
      .addAll(super.getSceneViews());

    if (mySecondarySceneView != null) {
      builder.add(mySecondarySceneView);
    }

    return builder.build();
  }

  private SceneView createSceneViewsForMenu() {
    NlModel model = getModel();
    XmlTag tag = model.getFile().getRootTag();
    SceneView sceneView;

    // TODO See if there's a better way to trigger the NavigationViewSceneView. Perhaps examine the view objects?
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), NavigationViewSceneView.SHOW_IN_ATTRIBUTE_VALUE)) {
      sceneView = ScreenView.newBuilder(getDesignSurface(), this)
        .withLayersProvider((sv) -> ImmutableList.of(new ScreenViewLayer(sv)))
        .withContentSizePolicy(NavigationViewSceneView.CONTENT_SIZE_POLICY)
        .withShapePolicy(SQUARE_SHAPE_POLICY)
        .build();
    }
    else {
      sceneView = ScreenView.newBuilder(getDesignSurface(), this).build();
    }

    getDesignSurface().updateErrorDisplay();
    return sceneView;
  }

  @Nullable
  public SceneView getSecondarySceneView() {
    return mySecondarySceneView;
  }

  public void updateTargets() {
    SceneComponent root = getScene().getRoot();
    if (root != null) {
      updateTargetProviders(root);
      root.updateTargets();
    }
  }

  private static void updateTargetProviders(@NotNull SceneComponent component) {
    ViewHandler handler = SlowOperations.allowSlowOperations(
      () -> NlComponentHelperKt.getViewHandler(component.getNlComponent()));
    component.setTargetProvider(handler);

    for (SceneComponent child : component.getChildren()) {
      updateTargetProviders(child);
    }
  }


  /**
   * Set of {@link com.android.tools.idea.common.model.NlModel.ChangeType}s that, when in Power Save Mode, will not refresh the
   * scene automatically.
   */
  private static final EnumSet<NlModel.ChangeType> powerModeChangesNotTriggeringRefresh = EnumSet.of(
    NlModel.ChangeType.RESOURCE_CHANGED,
    NlModel.ChangeType.RESOURCE_EDIT
  );

  /**
   * Records whether this {@link LayoutlibSceneManager} is out of date and needs to be refreshed.
   * This can happen when Studio is in Power Save mode, changes in {@link #powerModeChangesNotTriggeringRefresh} will not
   * trigger an automatic refresh.
   */
  private final AtomicBoolean isOutOfDate = new AtomicBoolean(false);

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      // After the model derived data is changed, we need to update the selection in Edt thread.
      // Changing selection should run in UI thread to avoid avoid race condition.
      NlDesignSurface surface = getDesignSurface();
      CompletableFuture.runAsync(() -> {
        // Ensure the new derived that is passed to the Scene components hierarchy
        if (!isDisposed.get()) {
          update();
        }

        // Selection change listener should run in UI thread not in the layoublib rendering thread. This avoids race condition.
        mySelectionChangeListener.selectionChanged(surface.getSelectionModel(), surface.getSelectionModel().getSelection());
      }, EdtExecutorService.getInstance());
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      if (PreviewPowerSaveManager.INSTANCE.isInPowerSaveMode() &&
          powerModeChangesNotTriggeringRefresh.contains(model.getLastChangeType())) {
        isOutOfDate.set(true);
        return;
      }

      NlDesignSurface surface = getDesignSurface();
      // The structure might have changed, force a re-inflate
      forceReinflate();
      // If the update is reversed (namely, we update the View hierarchy from the component hierarchy because information about scrolling is
      // located in the component hierarchy and is lost in the view hierarchy) we need to run render again to propagate the change
      // (re-layout) in the scrolling values to the View hierarchy (position, children etc.) and render the updated result.
      AtomicBoolean doubleRender = new AtomicBoolean();
      requestRenderAsync(getTriggerFromChangeType(model.getLastChangeType()), doubleRender)
        .thenCompose(v -> {
          if (doubleRender.get()) {
            return requestRenderAsync(getTriggerFromChangeType(model.getLastChangeType()), new AtomicBoolean());
          } else {
            return CompletableFuture.completedFuture(null);
          }
        }).thenRunAsync(() ->
                        mySelectionChangeListener.selectionChanged(surface.getSelectionModel(), surface.getSelectionModel().getSelection())
          , EdtExecutorService.getInstance());
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!isDisposed.get()) {
          boolean previous = getScene().isAnimated();
          getScene().setAnimated(animate);
          update();
          getScene().setAnimated(previous);
        }
      });
    }

    @Override
    public void modelLiveUpdate(@NotNull NlModel model, boolean animate) {
      requestLayoutAndRenderAsync(animate);
    }
  }

  private class SelectionChangeListener implements SelectionListener {
    @Override
    public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      updateTargets();
      getScene().needsRebuildList();
    }
  }

  /**
   * Adds a new render request to the queue.
   * @param trigger build trigger for reporting purposes
   * @return {@link CompletableFuture} that will be completed once the render has been done.
   */
  @NotNull
  protected CompletableFuture<Void> requestRenderAsync(@Nullable LayoutEditorRenderResult.Trigger trigger,  AtomicBoolean reverseUpdate) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("requestRender after LayoutlibSceneManager has been disposed");
      return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<Void> callback = new CompletableFuture<>();
    synchronized (myFuturesLock) {
      myPendingFutures.add(callback);
      if (myIsCurrentlyRendering) {
        return callback;
      }
      myIsCurrentlyRendering = true;
    }

    myRenderingQueue.queue(createRenderUpdate(trigger, reverseUpdate));

    return callback;
  }

  private Update createRenderUpdate(@Nullable LayoutEditorRenderResult.Trigger trigger, AtomicBoolean reverseUpdate) {
    // This update is low priority so the model updates take precedence
    return new Update("model.render", LOW_PRIORITY) {
      @Override
      public void run() {
        renderAsync(trigger, reverseUpdate);
      }

      @Override
      public boolean canEat(Update update) {
        return this.equals(update);
      }
    };
  }

  private class ConfigurationChangeListener implements ConfigurationListener {
    @Override
    public boolean changed(int flags) {
      myConfigurationUpdatedFlags.getAndUpdate((value) -> value |= flags);
      if ((flags & CFG_DEVICE) != 0) {
        int newDpi = getModel().getConfiguration().getDensity().getDpiValue();
        if (myDpi != newDpi) {
          myDpi = newDpi;
          // Update from the model to update the dpi
          LayoutlibSceneManager.this.update();
        }
      }
      return true;
    }
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestRenderAsync() {
    return requestRenderAsync(getTriggerFromChangeType(getModel().getLastChangeType()), new AtomicBoolean());
  }

  /**
   * Similar to {@link #requestRenderAsync()} but it will be logged as a user initiated action. This is
   * not exposed at SceneManager level since it only makes sense for the Layout editor.
   */
  @NotNull
  public CompletableFuture<Void> requestUserInitiatedRenderAsync() {
    forceReinflate();
    return requestRenderAsync(LayoutEditorRenderResult.Trigger.USER, new AtomicBoolean());
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestLayoutAndRenderAsync(boolean animate) {
    // Don't render if we're just showing the blueprint
    if (getDesignSurface().getScreenViewProvider() == NlScreenViewProvider.BLUEPRINT) {
      return requestLayoutAsync(animate);
    }

    LayoutEditorRenderResult.Trigger trigger = getTriggerFromChangeType(getModel().getLastChangeType());
    if (getDesignSurface().isRenderingSynchronously()) {
      return renderAsync(trigger, new AtomicBoolean()).thenRun(() -> notifyListenersModelLayoutComplete(animate));
    } else {
      // If the update is reversed (namely, we update the View hierarchy from the component hierarchy because information about scrolling is
      // located in the component hierarchy and is lost in the view hierarchy) we need to run render again to propagate the change
      // (re-layout) in the scrolling values to the View hierarchy (position, children etc.) and render the updated result.
      AtomicBoolean doubleRender = new AtomicBoolean();
      return requestRenderAsync(trigger, doubleRender)
        .thenCompose(v -> {
          if (doubleRender.get()) {
            return requestRenderAsync(trigger, new AtomicBoolean());
          } else {
            return CompletableFuture.completedFuture(null);
          }
        })
        .whenCompleteAsync((result, ex) -> notifyListenersModelLayoutComplete(animate), AppExecutorUtil.getAppExecutorService());
    }
  }

  /**
   * Schedule asynchronously model inflating and view hierarchy updating.
   */
  protected void requestModelUpdate() {
    if (isDisposed.get()) {
      return;
    }

    myProgressIndicator.start();

    myRenderingQueue.queue(new Update("model.update", HIGH_PRIORITY) {
      @Override
      public void run() {
        NlModel model = getModel();
        Project project = model.getModule().getProject();
        if (!project.isOpen()) {
          return;
        }
        DumbService.getInstance(project).runWhenSmart(() -> {
          if (model.getVirtualFile().isValid() && !model.getFacet().isDisposed()) {
            updateModelAsync()
              .whenComplete((result, ex) -> {
                isOutOfDate.set(false);
                myProgressIndicator.stop();
              });
          }
          else {
            isOutOfDate.set(false);
            myProgressIndicator.stop();
          }
        });
      }

      @Override
      public boolean canEat(Update update) {
        return equals(update);
      }
    });
  }

  /**
   * Whether we should render just the viewport
   */
  private static boolean ourRenderViewPort;

  public static void setRenderViewPort(boolean state) {
    ourRenderViewPort = state;
  }

  public static boolean isRenderViewPort() {
    return ourRenderViewPort;
  }

  public void setTransparentRendering(boolean enabled) {
    useTransparentRendering = enabled;
  }

  public void setShrinkRendering(boolean enabled) {
    useShrinkRendering = enabled;
  }

  /**
   * If true, register the {@link com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener} which calls
   * {@link #resourcesChanged(Set)} when any resource is changed.
   * By default it is enabled.
   */
  public void setListenResourceChange(boolean enabled) {
    myListenResourceChange = enabled;
  }

  public void setShowDecorations(boolean enabled) {
    if (useShowDecorations != enabled) {
      useShowDecorations = enabled;
      forceReinflate(); // Showing decorations changes the XML content of the render so requires re-inflation
    }
  }

  public void setUpdateAndRenderWhenActivated(boolean enable) {
    myUpdateAndRenderWhenActivated = enable;
  }

  public boolean isShowingDecorations() {
    return useShowDecorations;
  }

  public void setUseImagePool(boolean enabled) {
    useImagePool = enabled;
  }

  public void setQuality(float quality) {
    this.quality = quality;
  }

  public void setLogRenderErrors(boolean enabled) {
    myLogRenderErrors = enabled;
  }

  public void doNotReportOutOfDateUserClasses() {
    this.reportOutOfDateUserClasses = false;
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestLayoutAsync(boolean animate) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("requestLayout after LayoutlibSceneManager has been disposed");
    }

    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return CompletableFuture.completedFuture(null);
      }
      return myRenderTask.layout()
        .thenAccept(result -> {
          if (result != null && !isDisposed.get()) {
            updateHierarchy(result);
            notifyListenersModelLayoutComplete(animate);
          }
        });
    }
  }

  /**
   * Request a layout pass
   *
   * @param animate if true, the resulting layout should be animated
   */
  @Override
  public void layout(boolean animate) {
    try {
      requestLayoutAsync(animate).get(2, TimeUnit.SECONDS);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("Unable to run layout()", e);
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    myRenderResultLock.readLock().lock();
    try {
      return myRenderResult;
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  @Override
  @NotNull
  public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties() {
    myRenderResultLock.readLock().lock();
    try {
      if (myRenderResult == null) {
        return Collections.emptyMap();
      }
      return myRenderResult.getDefaultProperties();
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  @Override
  @NotNull
  public Map<Object, ResourceReference> getDefaultStyles() {
    myRenderResultLock.readLock().lock();
    try {
      if (myRenderResult == null) {
        return Collections.emptyMap();
      }
      return myRenderResult.getDefaultStyles();
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  private boolean updateHierarchy(@Nullable RenderResult result) {
    boolean reverseUpdate = false;
    try {
      myUpdateHierarchyLock.acquire();
      try {
        if (result == null || !result.getRenderResult().isSuccess()) {
          reverseUpdate = NlModelHierarchyUpdater.updateHierarchy(Collections.emptyList(), getModel());
        }
        else {
          reverseUpdate = NlModelHierarchyUpdater.updateHierarchy(result, getModel());
        }
      } finally {
        myUpdateHierarchyLock.release();
      }
      getModel().checkStructure();
    }
    catch (InterruptedException ignored) {
    }
    return reverseUpdate;
  }

  @VisibleForTesting
  protected RenderModelModule createRenderModule(AndroidFacet facet) {
    return new AndroidFacetRenderModelModule(facet);
  }

  /**
   * Synchronously inflates the model and updates the view hierarchy
   *
   * @param force forces the model to be re-inflated even if a previous version was already inflated
   * @returns A {@link CompletableFuture} containing the {@link RenderResult} of the inflate operation or containing null
   * if the model did not need to be re-inflated or could not be re-inflated (like the project been disposed).
   */
  @NotNull
  private CompletableFuture<RenderResult> inflateAsync(boolean force, AtomicBoolean reverseUpdate) {
    Configuration configuration = getModel().getConfiguration();

    Project project = getModel().getProject();
    if (project.isDisposed() || isDisposed.get()) {
      return CompletableFuture.completedFuture(null);
    }

    ResourceNotificationManager resourceNotificationManager = ResourceNotificationManager.getInstance(project);

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParsers.saveFileIfNecessary(getModel().getFile());

    synchronized (myRenderingTaskLock) {
      if (myRenderTask != null && !force) {
        // No need to inflate
        return CompletableFuture.completedFuture(null);
      }
    }

    fireOnInflateStart();
    // Record the current version we're rendering from; we'll use that in #activate to make sure we're picking up any
    // external changes
    AndroidFacet facet = getModel().getFacet();
    myRenderedVersion = resourceNotificationManager.getCurrentVersion(facet, getModel().getFile(), configuration);

    RenderService renderService = StudioRenderService.getInstance(getModel().getProject());
    RenderLogger logger = myLogRenderErrors ? renderService.createLogger(project) : renderService.getNopLogger();
    RenderModelModule renderModule = createRenderModule(facet);
    RenderConfiguration renderConfiguration = new StudioRenderConfiguration(configuration);
    RenderService.RenderTaskBuilder renderTaskBuilder = renderService.taskBuilder(renderModule, renderConfiguration, logger)
      .withPsiFile(getModel().getFile())
      .withLayoutScanner(myLayoutScannerConfig.isLayoutScannerEnabled());
    return setupRenderTaskBuilder(renderTaskBuilder).build()
      .thenCompose(newTask -> {
        if (newTask != null) {
          return newTask.inflate().whenComplete((result, inflateException) -> {
            Throwable exception = null;
            if (inflateException != null) {
              exception = inflateException;
            }
            else {
              if (result != null) {
                exception = result.getRenderResult().getException();
              }
            }

            if (exception != null) {
              if (result == null || !result.getRenderResult().isSuccess()) {
                // Do not ignore ClassNotFoundException on inflate
                if (exception instanceof ClassNotFoundException) {
                  logger.addMessage(RenderProblem.createPlain(ERROR,
                                                              "Error inflating the preview",
                                                              logger.getProject(),
                                                              logger.getLinkManager(), exception, ShowFixFactory.INSTANCE));
                }
                else {
                  logger.error(ILayoutLog.TAG_INFLATE, "Error inflating the preview", exception, null, null);
                }
              }
              Logger.getInstance(LayoutlibSceneManager.class).warn(exception);
            }

            // If the result is not valid, we do not need the task. Also if the project was already disposed
            // while we were creating the task, avoid adding it.
            if (getModel().getModule().isDisposed() || result == null || !result.getRenderResult().isSuccess() || isDisposed.get()) {
              newTask.dispose();
            }
            else {
              updateRenderTask(newTask);
            }
            if (result != null && !result.getRenderResult().isSuccess()) {
              // Erase the previously cached result in case the render has finished, but was not a success. Otherwise, we might end up
              // in a state where the user thinks the render was successful, but it actually failed.
              updateRenderTask(null);
            }
          })
            .handle((result, exception) -> {
              if (project.isDisposed()) return null;
              return result != null ? result : RenderResults.createRenderTaskErrorResult(getModel().getFile(), exception);
            });
        }
        else {
          updateRenderTask(null);

          if (project.isDisposed()) return CompletableFuture.completedFuture(null);
          return CompletableFuture.completedFuture(RenderResults.createRenderTaskErrorResult(getModel().getFile(), logger));
        }
      })
      .thenApply(this::updateCachedRenderResultIfNotNull)
      .thenApply(result -> {
        // Updates hierarchy if applicable or noop
        if (project.isDisposed() || !result.getRenderResult().isSuccess()) {
          return result;
        }

        reverseUpdate.set(updateHierarchy(result));

        return result;
      })
      .thenApply(result -> {
        fireOnInflateComplete();
        return logIfSuccessful(result, null, CommonUsageTracker.RenderResultType.INFLATE);
      })
      .whenCompleteAsync(this::notifyModelUpdateIfSuccessful, AppExecutorUtil.getAppExecutorService());
  }

  @Nullable
  private RenderResult updateCachedRenderResultIfNotNull(@Nullable RenderResult result) {
    if (result != null) {
      return updateCachedRenderResult(result);
    }
    return null;
  }

  @Nullable
  private RenderResult updateCachedRenderResult(@Nullable RenderResult result) {
    myRenderResultLock.writeLock().lock();
    try {
      if (myRenderResult != null && myRenderResult != result) {
        myRenderResult.dispose();
      }
      myRenderResult = result;
      return result;
    }
    finally {
      myRenderResultLock.writeLock().unlock();
    }
  }

  @VisibleForTesting
  @NotNull
  protected RenderService.RenderTaskBuilder setupRenderTaskBuilder(@NotNull RenderService.RenderTaskBuilder taskBuilder) {
    if (!useImagePool) {
      taskBuilder.disableImagePool();
    }

    if (quality < 1f) {
      taskBuilder.withQuality(quality);
    }

    if (!useShowDecorations) {
      taskBuilder.disableDecorations();
    }

    if (useShrinkRendering) {
      taskBuilder.withRenderingMode(SessionParams.RenderingMode.SHRINK);
    }

    if (useTransparentRendering) {
      taskBuilder.useTransparentBackground();
    }

    if (!getDesignSurface().getPreviewWithToolsVisibilityAndPosition()) {
      taskBuilder.disableToolsVisibilityAndPosition();
    }

    if (myUsePrivateClassLoader) {
      taskBuilder.usePrivateClassLoader();
    }

    if (myIsInteractive) {
      taskBuilder.preloadClasses(Arrays.asList(INTERACTIVE_CLASSES_TO_PRELOAD));
    }

    if (!reportOutOfDateUserClasses) {
      taskBuilder.doNotReportOutOfDateUserClasses();
    }

    return taskBuilder;
  }

  private void notifyModelUpdateIfSuccessful(@Nullable RenderResult result, @Nullable Throwable exception) {
    if (exception != null) {
      Logger.getInstance(LayoutlibSceneManager.class).warn(exception);
    }
    if (result != null && result.getRenderResult().isSuccess()) {
      notifyListenersModelUpdateComplete();
    }
  }

  /**
   * Asynchronously update the model. This will inflate the layout and notify the listeners using
   * {@link ModelListener#modelDerivedDataChanged(NlModel)}.
   *
   * Try to use {@link #requestModelUpdate()} if possible. Which schedules the updating in the rendering queue and avoid duplication.
   */
  @NotNull
  public CompletableFuture<Void> updateModelAsync() {
    return updateModelAndProcessResultsAsync(result -> null);
  }

  /**
   * Asynchronously update the model and apply the provided processing to the {@link RenderResult}.
   */
  @NotNull
  public CompletableFuture<Void> updateModelAndProcessResultsAsync(Function<? super RenderResult, Void> resultProcessing) {
    if (isDisposed.get()) {
      return CompletableFuture.completedFuture(null);
    }
    return inflateAsync(true, new AtomicBoolean())
      .thenApply(resultProcessing);
  }

  protected void notifyListenersModelLayoutComplete(boolean animate) {
    getModel().notifyListenersModelChangedOnLayout(animate);
  }

  protected void notifyListenersModelUpdateComplete() {
    getModel().notifyListenersModelDerivedDataChanged();
  }

  private void logConfigurationChange(@NotNull DesignSurface<?> surface) {
    int flags = myConfigurationUpdatedFlags.getAndSet(0);  // Get and reset the saved flags
    if (flags != 0) {
      // usage tracking (we only pay attention to individual changes where only one item is affected since those are likely to be triggered
      // by the user
      NlAnalyticsManager analyticsManager = ((NlDesignSurface)surface).getAnalyticsManager();

      if ((flags & ConfigurationListener.CFG_THEME) != 0) {
        analyticsManager.trackThemeChange();
      }
      if ((flags & ConfigurationListener.CFG_TARGET) != 0) {
        analyticsManager.trackApiLevelChange();
      }
      if ((flags & ConfigurationListener.CFG_LOCALE) != 0) {
        analyticsManager.trackLanguageChange();
      }
      if ((flags & ConfigurationListener.CFG_DEVICE) != 0) {
        analyticsManager.trackDeviceChange();
      }
    }
  }

  @Nullable
  private RenderResult logIfSuccessful(@Nullable RenderResult result,
                                       @Nullable LayoutEditorRenderResult.Trigger trigger,
                                       @NotNull CommonUsageTracker.RenderResultType resultType) {
    if (result != null && result.getRenderResult().isSuccess()) {
      CommonUsageTracker.Companion.getInstance(getDesignSurface()).logRenderResult(trigger, result, resultType);
    }
    return result;
  }

  /**
   * Renders the current model asynchronously. Once the render is complete, the render callbacks will be called.
   * <p/>
   * If the layout hasn't been inflated before, this call will inflate the layout before rendering.
   */
  @NotNull
  protected CompletableFuture<RenderResult> renderAsync(@Nullable LayoutEditorRenderResult.Trigger trigger,
                                                        AtomicBoolean reverseUpdate) {
    if (isDisposed.get()) {
      return CompletableFuture.completedFuture(null);
    }

    synchronized (myFuturesLock) {
      // This is because at the moment render could also be called from requestLayoutAndRender in a synchronous mode
      myIsCurrentlyRendering = true;
      myRenderFutures.addAll(myPendingFutures);
      myPendingFutures.clear();
    }
    try {
      NlDesignSurface surface = getDesignSurface();
      logConfigurationChange(surface);
      getModel().resetLastChange();

      fireOnRenderStart();
      long renderStartTimeMs = System.currentTimeMillis();
      return renderImplAsync(reverseUpdate)
        .thenApply(result -> logIfSuccessful(result, trigger, CommonUsageTracker.RenderResultType.RENDER))
        .thenApply(this::updateCachedRenderResultIfNotNull)
        .thenApply(result -> {
          if (result != null) {
            long renderTimeMs = System.currentTimeMillis() - renderStartTimeMs;
            // In an unlikely event when result is disposed we can still safely request the size of the image
            NlDiagnosticsManager.getWriteInstance(surface).recordRender(renderTimeMs,
                                                                        result.getRenderedImage().getWidth() *
                                                                        result.getRenderedImage().getHeight() *
                                                                        4L);
          }

          return result;
        })
        .thenApplyAsync(result -> {
          if (!isDisposed.get()) {
            update();
          }

          return result;
        }, EdtExecutorService.getInstance())
        .thenApplyAsync(result -> {
          fireOnRenderComplete();
          completeRender();

          return result;
        }, AppExecutorUtil.getAppExecutorService());
    }
    catch (Throwable e) {
      if (!getModel().getFacet().isDisposed()) {
        fireOnRenderFail(e);
        completeRender();
        throw e;
      }
    }
    completeRender();
    return CompletableFuture.completedFuture(null);
  }

  private boolean hasPendingRenders() {
    synchronized(myFuturesLock) {
      return !myPendingFutures.isEmpty();
    }
  }

  /**
   * Completes all the futures created by {@link #requestRenderAsync()} and signals the current render as finished by
   * setting {@link #myIsCurrentlyRendering} to false. Also, it is calling the render callbacks associated with
   * the current render.
   */
  private void completeRender() {
    ImmutableList<CompletableFuture<Void>> callbacks;
    synchronized (myFuturesLock) {
      callbacks = ImmutableList.copyOf(myRenderFutures);
      myRenderFutures.clear();
      myIsCurrentlyRendering = false;
    }
    isOutOfDate.set(false);
    callbacks.forEach(callback -> callback.complete(null));
    // If there are pending futures, we should trigger the render update
    if (hasPendingRenders()) {
      requestRenderAsync(getTriggerFromChangeType(getModel().getLastChangeType()), new AtomicBoolean());
    }
  }

  /**
   * Returns if there are any pending render requests.
   */
  @TestOnly
  public boolean isRendering() {
    synchronized (myFuturesLock) {
      return myIsCurrentlyRendering;
    }
  }

  @NotNull
  private CompletableFuture<RenderResult> renderImplAsync(AtomicBoolean reverseUpdate) {
    return inflateAsync(myForceInflate.getAndSet(false), reverseUpdate)
      .thenCompose(inflateResult -> {
        boolean inflated = inflateResult != null && inflateResult.getRenderResult().isSuccess();
        long elapsedFrameTimeMs = myElapsedFrameTimeMs;

        synchronized (myRenderingTaskLock) {
          if (myRenderTask == null || (inflateResult != null && !inflateResult.getRenderResult().isSuccess())) {
            getDesignSurface().updateErrorDisplay();
            return CompletableFuture.completedFuture(null);
          }
          if (elapsedFrameTimeMs != -1) {
            myRenderTask.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(elapsedFrameTimeMs));
          }
          return myRenderTask.render().thenApply(result -> {
            // When the layout was inflated in this same call, we do not have to update the hierarchy again
            if (result != null && !inflated) {
              reverseUpdate.set(reverseUpdate.get() || updateHierarchy(result));
            }
            return result;
          });
        }
      })
      .handle((result, exception) -> {
        if (exception != null) {
          return RenderResults.createRenderTaskErrorResult(getModel().getFile(), exception);
        }
        return result;
      });
  }

  public void setElapsedFrameTimeMs(long ms) {
    myElapsedFrameTimeMs = ms;
  }

  /**
   * Default {@link SceneManager.SceneComponentHierarchyProvider} for {@link LayoutlibSceneManager}.
   * It provides the functionality to sync the {@link NlComponent} hierarchy and the data from Layoutlib to {@link SceneComponent}.
   */
  protected static class LayoutlibSceneManagerHierarchyProvider extends DefaultSceneManagerHierarchyProvider {
    @Override
    public void syncFromNlComponent(@NotNull SceneComponent sceneComponent) {
      super.syncFromNlComponent(sceneComponent);
      NlComponent component = sceneComponent.getNlComponent();
      boolean animate = sceneComponent.getScene().isAnimated() && !sceneComponent.hasNoDimension();
      SceneManager manager = sceneComponent.getScene().getSceneManager();
      if (animate) {
        long time = System.currentTimeMillis();
        sceneComponent.setPositionTarget(Coordinates.pxToDp(manager, NlComponentHelperKt.getX(component)),
                                         Coordinates.pxToDp(manager, NlComponentHelperKt.getY(component)),
                                         time);
        sceneComponent.setSizeTarget(Coordinates.pxToDp(manager, NlComponentHelperKt.getW(component)),
                                     Coordinates.pxToDp(manager, NlComponentHelperKt.getH(component)),
                                     time);
      }
      else {
        sceneComponent.setPosition(Coordinates.pxToDp(manager, NlComponentHelperKt.getX(component)),
                                   Coordinates.pxToDp(manager, NlComponentHelperKt.getY(component)));
        sceneComponent.setSize(Coordinates.pxToDp(manager, NlComponentHelperKt.getW(component)),
                               Coordinates.pxToDp(manager, NlComponentHelperKt.getH(component)));
      }
    }
  }

  protected void fireOnInflateStart() {
    myRenderListeners.forEach(RenderListener::onInflateStarted);
  }
  protected void fireOnInflateComplete() {
    myRenderListeners.forEach(RenderListener::onInflateCompleted);
  }
  protected void fireOnRenderStart() {
    myRenderListeners.forEach(RenderListener::onRenderStarted);
  }
  protected void fireOnRenderComplete() {
    myRenderListeners.forEach(RenderListener::onRenderCompleted);
  }
  protected void fireOnRenderFail(@NotNull Throwable e) {
    myRenderListeners.forEach(listener -> {
      listener.onRenderFailed(e);
    });
  }

  public void addRenderListener(@NotNull RenderListener listener) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("addRenderListener after LayoutlibSceneManager has been disposed");
    }

    myRenderListeners.add(listener);
  }

  public void removeRenderListener(@NotNull RenderListener listener) {
    myRenderListeners.remove(listener);
  }

  /**
   * This invalidates the current {@link RenderTask}. Next render call will be force to re-inflate the model.
   */
  public void forceReinflate() {
    myForceInflate.set(true);
  }

  /**
   * Triggers execution of the Handler and frame callbacks in the layoutlib
   * @return a boolean future that is completed when callbacks are executed that is true if there are more callbacks to execute
   */
  @NotNull
  public CompletableFuture<ExecuteCallbacksResult> executeCallbacksAsync() {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("executeCallbacks after LayoutlibSceneManager has been disposed");
    }

    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return CompletableFuture.completedFuture(ExecuteCallbacksResult.EMPTY);
      }
      return myRenderTask.executeCallbacks(currentTimeNanos());
    }
  }

  /**
   * Executes the given block under a {@link RenderSession}. This allows the given block to access resources since they are set up
   * before executing it.
   * @return A {@link CompletableFuture} that completes when the block finalizes.
   * @see RenderTask#runAsyncRenderActionWithSession(Runnable) 
   */
  @NotNull
  public CompletableFuture<Void> executeInRenderSessionAsync(@NotNull Runnable block) {
    synchronized (myRenderingTaskLock) {
      if (myRenderTask != null) {
        return myRenderTask.runAsyncRenderActionWithSession(block);
      }
      else {
        return CompletableFuture.completedFuture(null);
      }
    }
  }

  private long currentTimeNanos() {
    synchronized (myRenderingTaskLock) {
      return mySessionClock.getTimeNanos();
    }
  }

  /**
   * Pauses session clock, so that session time stops advancing.
   */
  public void pauseSessionClock() {
    synchronized (myRenderingTaskLock) {
      mySessionClock.pause();
    }
  }

  /**
   * Resumes session clock, so that session time keeps advancing.
   */
  public void resumeSessionClock() {
    synchronized (myRenderingTaskLock) {
      mySessionClock.resume();
    }
  }

  /**
   * Informs layoutlib that there was a (mouse) touch event detected of a particular type at a particular point
   * @param type type of a touch event
   * @param x horizontal android coordinate of the detected touch event
   * @param y vertical android coordinate of the detected touch event
   * @return a future that is completed when layoutlib handled the touch event
   */
  @NotNull
  public CompletableFuture<InteractionEventResult> triggerTouchEventAsync(
    @NotNull RenderSession.TouchEventType type, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("executeCallbacks after LayoutlibSceneManager has been disposed");
    }

    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return CompletableFuture.completedFuture(null);
      }
      myInteractiveEventsCounter.incrementAndGet();
      return myRenderTask.triggerTouchEvent(type, x, y, currentTimeNanos());
    }
  }

  /**
   * Passes a Java KeyEvent from the surface to layoutlib.
   *
   * @return a future that is completed when layoutlib handled the key event
   */
  @NotNull
  public CompletableFuture<InteractionEventResult> triggerKeyEventAsync(@NotNull KeyEvent event) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("executeCallbacks after LayoutlibSceneManager has been disposed");
    }

    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return CompletableFuture.completedFuture(null);
      }
      myInteractiveEventsCounter.incrementAndGet();
      return myRenderTask.triggerKeyEvent(event, currentTimeNanos());
    }
  }

  /**
   * Executes the given {@link Runnable} callback synchronously with a 30ms timeout.
   */
  public boolean executeCallbacksAndRequestRender(@Nullable Runnable callback) {
    return executeCallbacksAndRequestRender(30, TimeUnit.MILLISECONDS, callback);
  }

  /**
   * Executes the given {@link Runnable} callback synchronously with the given timeout. Then calls {@link #executeCallbacksAsync()} and requests
   * render afterwards. Callers must be aware that long timeouts should only be passed when not on EDT, otherwise the UI will freeze.
   * Returns true if the callback was executed successfully and on time, and render was requested.
   */
  public boolean executeCallbacksAndRequestRender(long timeout, TimeUnit timeoutUnit, @Nullable Runnable callback) {
    try {
      if (callback != null) {
        RenderService.getRenderAsyncActionExecutor()
          .runAsyncActionWithTimeout(timeout, timeoutUnit, Executors.callable(callback)).get(timeout, timeoutUnit);
      }
      executeCallbacksAsync().thenCompose(b -> requestRenderAsync());
      return true;
    }
    catch (Exception e) {
      Logger.getInstance(LayoutlibSceneManager.class).debug("executeCallbacksAndRequestRender did not complete successfully", e);
      return false;
    }
  }

  /**
   * Sets interactive mode of the scene.
   * @param interactive true if the scene is interactive, false otherwise.
   */
  public void setInteractive(boolean interactive) {
    myIsInteractive = interactive;
    getSceneViews().forEach(sv -> sv.setAnimated(interactive));
  }

  /**
   * @return true is the scene is interactive, false otherwise.
   */
  public boolean getInteractive() {
    return myIsInteractive;
  }

  /**
   * Sets whether this scene manager should use a private/individual ClassLoader. If two compose previews share the same ClassLoader they
   * share the same compose framework. This way they share the state. In the interactive preview and animation inspector, we would like to
   * control the state of the framework and preview. Shared state makes control impossible. Therefore, in certain situations (currently only
   * in compose) we want to create a dedicated ClassLoader so that the preview has its own compose framework. Having a dedicated ClassLoader
   * also allows for clearing resources right after the preview no longer used. We could apply this approach by default (e.g. for static
   * previews as well) but it might have a negative impact if there are many of them. Therefore, this should be configured by calling this
   * method when we want to use the private ClassLoader, e.g. in interactive previews or animation inspector.
   */
  public void setUsePrivateClassLoader(boolean usePrivateClassLoader) {
    myUsePrivateClassLoader = usePrivateClassLoader;
  }

  /**
   * @return true if this scene is using private ClassLoader, false otherwise.
   */
  public boolean isUsePrivateClassLoader() {
    return myUsePrivateClassLoader;
  }

  @Override
  public boolean activate(@NotNull Object source) {
    boolean active = super.activate(source);

    if (active && myUpdateAndRenderWhenActivated) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getModel().getProject());
      ResourceNotificationManager.ResourceVersion version =
        manager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), getModel().getConfiguration());
      if (!version.equals(myRenderedVersion)) {
        requestModelUpdate();
        getModel().updateTheme();
      }
      else {
        requestLayoutAndRenderAsync(false);
      }
    }

    return active;
  }

  @Override
  public boolean deactivate(@NotNull Object source) {
    boolean deactivated = super.deactivate(source);
    if (deactivated) {
      myRenderingQueue.deactivate();
      completeRender();
      disposeRenderTask();
    }

    return deactivated;
  }

  @Override
  public void resourcesChanged(@NotNull ImmutableSet<ResourceNotificationManager.Reason> reasons) {
    if (myListenResourceChange) {
      super.resourcesChanged(reasons);
    }
  }

  /**
   * Resets the counter of user events received by this scene to 0.
   */
  public void resetInteractiveEventsCounter() {
    myInteractiveEventsCounter.set(0);
  }

  /**
   * @return number of user touch or key events received by this scene since last reset.
   */
  public int getInteractiveEventsCount() {
    return myInteractiveEventsCounter.get();
  }

  @Override
  public boolean isOutOfDate() {
    return isOutOfDate.get();
  }
}
