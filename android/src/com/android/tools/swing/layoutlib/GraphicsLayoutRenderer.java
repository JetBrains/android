/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.swing.layoutlib;

import com.android.SdkConstants;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.RenderParamsFlags;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.AssetRepositoryImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Class to render layouts to a {@link Graphics} instance. This renderer does not allow for much customization of the device and does not
 * include any kind of frames.
 * <p/>
 * <p/>This class will render a layout to a {@link Graphics} object and can be used to paint in controls that require live updates.
 * <p/>
 * <p/>Note: This class is not thread safe.
 */
public class GraphicsLayoutRenderer {
  private static final Logger LOG = Logger.getInstance(GraphicsLayoutRenderer.class);

  private static final int MIN_LAYOUTLIB_API_VERSION = 15;

  private final FakeImageFactory myImageFactory;
  private final DynamicHardwareConfig myHardwareConfig;
  private final Object myCredential;
  private final RenderSecurityManager mySecurityManager;
  /**
   * Invalidate the layout in the next render call
   */
  private boolean myInvalidate;
  /**
   * Contains the list of resource lookups required in the last render call. This is useful for clients
   * to know which styles and resources were used to render the layout.
   */
  private final List<ResourceValue> myResourceLookupChain;

  private final ReentrantReadWriteLock myRenderSessionLock = new ReentrantReadWriteLock();
  /*
   * The render session is lazily initialized. We need to wait until we have a valid Graphics2D
   * instance to launch it.
   */
  private RenderSession myRenderSession;

  private double myScale = 1.0;

  private GraphicsLayoutRenderer(@NotNull LayoutLibrary layoutLib,
                                 @NotNull SessionParams sessionParams,
                                 @NotNull RenderSecurityManager securityManager,
                                 @NotNull DynamicHardwareConfig hardwareConfig,
                                 @NotNull List<ResourceValue> resourceLookupChain,
                                 @NotNull Object credential) {
    mySecurityManager = securityManager;
    myHardwareConfig = hardwareConfig;
    myImageFactory = new FakeImageFactory();
    myResourceLookupChain = resourceLookupChain;
    myCredential = credential;

    sessionParams.setFlag(RenderParamsFlags.FLAG_KEY_DISABLE_BITMAP_CACHING, Boolean.TRUE);
    sessionParams.setImageFactory(myImageFactory);

    myRenderSession = initRenderSession(layoutLib, sessionParams, mySecurityManager, myCredential);
  }

  @NotNull
  protected static GraphicsLayoutRenderer create(@NotNull AndroidFacet facet,
                                                 @NotNull AndroidPlatform platform,
                                                 @NotNull Project project,
                                                 @NotNull Configuration configuration,
                                                 @NotNull ILayoutPullParser parser,
                                                 @Nullable Color backgroundColor,
                                                 @NotNull SessionParams.RenderingMode renderingMode) throws InitializationException {
    AndroidModuleInfo moduleInfo = AndroidModuleInfo.get(facet);
    LayoutLibrary layoutLib;
    try {
      IAndroidTarget latestTarget = configuration.getConfigurationManager().getHighestApiTarget();
      if (latestTarget == null) {
        throw new UnsupportedLayoutlibException("GraphicsLayoutRenderer requires at least layoutlib version " + MIN_LAYOUTLIB_API_VERSION);
      }

      layoutLib = platform.getSdkData().getTargetData(latestTarget).getLayoutLibrary(project);
      if (layoutLib == null) {
        throw new InitializationException("getLayoutLibrary() returned null");
      }
    }
    catch (RenderingException e) {
      throw new InitializationException(e);
    }
    catch (IOException e) {
      throw new InitializationException(e);
    }

    if (layoutLib.getApiLevel() < MIN_LAYOUTLIB_API_VERSION) {
      throw new UnsupportedLayoutlibException("GraphicsLayoutRenderer requires at least layoutlib version " + MIN_LAYOUTLIB_API_VERSION);
    }

    AppResourceRepository appResources = AppResourceRepository.getAppResources(facet, true);

    final Module module = facet.getModule();
    // Security token used to disable the security manager. Only objects that have a reference to it are allowed to disable it.
    Object credential = new Object();
    RenderLogger logger = new RenderLogger("theme_editor", module, credential);
    final ActionBarCallback actionBarCallback = new ActionBarCallback();
    // TODO: Remove LayoutlibCallback dependency.
    //noinspection ConstantConditions
    final LayoutlibCallbackImpl layoutlibCallback =
      new LayoutlibCallbackImpl(null, layoutLib, appResources, module, facet, logger, credential, null) {
        @Override
        public ActionBarCallback getActionBarCallback() {
          return actionBarCallback;
        }
      };

    // Load the local project R identifiers.
    boolean loadRResult = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        // create can run from a different thread so we need to run this in a read action to make sure the module hasn't been disposed
        // half way.
        if (module.isDisposed()) {
          return false;
        }
        layoutlibCallback.loadAndParseRClass();
        return true;
      }
    });
    if (!loadRResult) {
      throw new AlreadyDisposedException("Module was already disposed");
    }

    IAndroidTarget target = configuration.getTarget();
    if (target == null) {
      throw new InitializationException("Unable to get IAndroidTarget");
    }

    Device device = configuration.getDevice();
    assert device != null;
    HardwareConfigHelper hardwareConfigHelper = new HardwareConfigHelper(device);
    DynamicHardwareConfig hardwareConfig = new DynamicHardwareConfig(hardwareConfigHelper.getConfig());
    List<ResourceValue> resourceLookupChain = new ArrayList<ResourceValue>();
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    assert resourceResolver != null;
    // Create a resource resolver that will save the lookups on the passed List<>
    ResourceResolver recordingResourceResolver = resourceResolver.createRecorder(resourceLookupChain);
    final SessionParams params =
      new SessionParams(parser, renderingMode, module, hardwareConfig, recordingResourceResolver,
                        layoutlibCallback, moduleInfo.getMinSdkVersion().getApiLevel(), moduleInfo.getTargetSdkVersion().getApiLevel(),
                        logger, target instanceof CompatibilityRenderTarget ? target.getVersion().getApiLevel() : 0);
    params.setForceNoDecor();
    params.setAssetRepository(new AssetRepositoryImpl(facet));
    // The App Label needs to be not null
    params.setAppLabel("");

    if (backgroundColor != null) {
      params.setOverrideBgColor(backgroundColor.getRGB());
    }

    RenderSecurityManager mySecurityManager = RenderSecurityManagerFactory.create(module, platform);
    return new GraphicsLayoutRenderer(layoutLib, params, mySecurityManager, hardwareConfig, resourceLookupChain, credential);
  }

  /**
   * Creates a new {@link GraphicsLayoutRenderer}.
   * @param configuration The configuration to use when rendering.
   * @param parser A layout pull-parser.
   * @param backgroundColor If not null, this will be use to set the global Android window background
   * @throws AlreadyDisposedException if the module is disposed while create is running
   * @throws InitializationException if layoutlib fails to initialize.
   * @throws UnsupportedLayoutlibException if the used layoutlib version is too old to run with this class
   */
  @NotNull
  public static GraphicsLayoutRenderer create(@NotNull Configuration configuration,
                                              @NotNull ILayoutPullParser parser,
                                              @Nullable Color backgroundColor,
                                              boolean hasHorizontalScroll,
                                              boolean hasVerticalScroll) throws InitializationException {
    Module module = configuration.getModule();
    if (module.isDisposed()) {
      throw new AlreadyDisposedException("Module was already disposed");
    }

    AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
    if (facet == null) {
      throw new InitializationException("Unable to get AndroidFacet");
    }

    AndroidPlatform platform = AndroidPlatform.getInstance(module);
    if (platform == null) {
      throw new UnsupportedLayoutlibException("No Android SDK found.");
    }

    SessionParams.RenderingMode renderingMode;
    if (hasVerticalScroll && hasHorizontalScroll) {
      renderingMode = SessionParams.RenderingMode.FULL_EXPAND;
    } else if (hasVerticalScroll) {
      renderingMode = SessionParams.RenderingMode.V_SCROLL;
    } else if (hasHorizontalScroll) {
      renderingMode = SessionParams.RenderingMode.H_SCROLL;
    } else {
      renderingMode = SessionParams.RenderingMode.NORMAL;
    }

    return create(facet, platform, module.getProject(), configuration, parser, backgroundColor, renderingMode);
  }

  /**
   * Converts a dimension from model coordinates to view coordinates (possibly scaled).
   */
  @NotNull
  private Dimension modelToView(int width, int height) {
    return new Dimension((int)(width * myScale), (int)(height * myScale));
  }

  /**
   * Converts a dimension from view coordinates (possibly scaled) to model coordinates.
   */
  @NotNull
  private Dimension viewToModel(int width, int height) {
    return new Dimension((int)(width / myScale), (int)(height / myScale));
  }

  /**
   * Converts a point from view coordinates (possibly scaled) to model coordinates.
   */
  @NotNull
  private Point viewToModel(@NotNull Point p) {
    return new Point((int)(p.x / myScale), (int)(p.y / myScale));
  }

  /**
   * Sets the rendering scale. If scale is greater than 1.0, the rendered result will be bigger, while if it's less than 1.0 it will be
   * smaller. The scale does not affect the size set in {@link #setSize} so the resulting image will have the same width and height.
   */
  public void setScale(double scale) {
    myScale = scale;

    // Adjust screen size to new scale.
    setSize(new Dimension(myHardwareConfig.getScreenWidth(), myHardwareConfig.getScreenHeight()));
  }


  public void setSize(int width, int height) {
    Dimension dimen = viewToModel(width, height);
    // The minimum render size we allow is 1,1 since 0,0 wouldn't render anything.
    myHardwareConfig.setScreenSize(Math.max(dimen.width, 1), Math.max(dimen.height, 1));
    myInvalidate = true;
  }

  public void setSize(Dimension dimen) {
    setSize(dimen.width, dimen.height);
  }

  /**
   * Render the layout to the passed {@link Graphics2D} instance using the defined viewport.
   * <p/>
   * <p/>Please note that this method is not thread safe so, if used from multiple threads, it's the caller's responsibility to synchronize
   * the access to it.
   */
  public boolean render(@NotNull final Graphics2D graphics) {
    myRenderSessionLock.readLock().lock();
    try {
      if (!SystemInfo.isMac) {
        // Do not enable anti-aliasing on MAC. It doesn't improve much and causes has performance issues when filling the background using
        // alpha values.
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }
      myImageFactory.setGraphics(graphics);

      AffineTransform oldTransform = graphics.getTransform();
      if (myScale != 1.0) {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        AffineTransform scaleTransform = new AffineTransform(oldTransform);
        scaleTransform.scale(myScale, myScale);
        graphics.setTransform(scaleTransform);
      }

      Result result = null;

      try {
        result = RenderService.runRenderAction(new Callable<Result>() {
          @Override
          public Result call() {
            mySecurityManager.setActive(true, myCredential);
            try {
              return myRenderSession.render(RenderParams.DEFAULT_TIMEOUT, myInvalidate);
            }
            finally {
              mySecurityManager.setActive(false, myCredential);
            }
          }
        });
      }
      catch (Exception e) {
        LOG.warn("Exception running render action", e);
      }


      if (myScale != 1.0) {
        graphics.setTransform(oldTransform);
      }

      // We need to log the errors after disabling the security manager since the logger will cause a security exception when trying to
      // access the system properties.
      if (result != null && result.getStatus() != Result.Status.SUCCESS) {
        //noinspection ThrowableResultOfMethodCallIgnored
        if (result.getException() != null) {
          LOG.warn(result.getException());
        }
        else {
          LOG.warn("Render error (no exception). Status=" + result.getStatus().name());
        }
        return false;
      }

      myInvalidate = false;
      return true;
    } finally {
      myRenderSessionLock.readLock().unlock();
    }
  }

  /**
   * Returns the initialised render session. This method is called only once during the {@link GraphicsLayoutRenderer} initialization and
   * will also do the an initial render of the layout.
   */
  @Nullable
  private static RenderSession initRenderSession(@NotNull final LayoutLibrary layoutLibrary,
                                                 @NotNull final SessionParams sessionParams,
                                                 @NotNull final RenderSecurityManager securityManager,
                                                 final @NotNull Object credential) {
    try {
      RenderSession session = RenderService.runRenderAction(new Callable<RenderSession>() {
        @Override
        public RenderSession call() {
          securityManager.setActive(true, credential);
          try {
            // createSession() might access the PSI tree so we need to run it inside as a read action.
            return ApplicationManager.getApplication().runReadAction(new Computable<RenderSession>() {
              @Override
              public RenderSession compute() {
                // createSession will also render the layout for the first time.
                return layoutLibrary.createSession(sessionParams);

              }
            });
          }
          finally {
            securityManager.setActive(false, credential);
          }
        }
      });

      if (session == null) {
        LOG.warn("initRenderSession failed (returned null)");
        return null;
      }

      if (layoutLibrary.supports(Features.SYSTEM_TIME)) {
        session.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(500));
      }
      Result result = session.getResult();
      if (result != null && result.getStatus() != Result.Status.SUCCESS) {
        //noinspection ThrowableResultOfMethodCallIgnored
        if (result.getException() != null) {
          LOG.warn(result.getException());
        }
        else {
          LOG.warn("Render error (no exception). Status=" + result.getStatus().name());
        }
      }
      return session;
    }
    catch (Exception e) {
      LOG.warn("initRenderSession failed", e);
      return null;
    }
  }

  /**
   * Returns the list of attribute names used to render the layout
   */
  @NotNull
  public Set<String> getUsedAttrs() {
    HashSet<String> usedAttrs = new HashSet<String>();
    for(ResourceValue value : myResourceLookupChain) {
      if (!(value instanceof ItemResourceValue) || value.getName() == null) {
        // Only selects resources that are also attributes
        continue;
      }
      ItemResourceValue itemValue = (ItemResourceValue)value;
      usedAttrs.add((itemValue.isFrameworkAttr() ? SdkConstants.PREFIX_ANDROID : "") + itemValue.getName());
    }

    return Collections.unmodifiableSet(usedAttrs);
  }

  @Nullable
  private static ViewInfo viewAtPoint(@NotNull Point parentPosition, @NotNull ViewInfo view, @NotNull Point p) {
    ViewInfo bounds = RenderService.getSafeBounds(view);
    int x = parentPosition.x + bounds.getLeft();
    int y = parentPosition.y + bounds.getTop();
    Rectangle rect = new Rectangle(x,
                                   y,
                                   bounds.getRight() - bounds.getLeft(),
                                   bounds.getBottom() - bounds.getTop());
    if (rect.contains(p)) {
      for (ViewInfo childView : view.getChildren()) {
        if (childView.getCookie() == null) {
          continue;
        }

        ViewInfo hitView = viewAtPoint(rect.getLocation(), childView, p);

        if (hitView != null) {
          return hitView;
        }
      }

      return view;
    }

    return null;
  }

  /**
   * Find the view at a given point.
   */
  @Nullable
  public ViewInfo findViewAtPoint(@NotNull Point p) {
    myRenderSessionLock.readLock().lock();
    try {
      if (myRenderSession == null) {
        return null;
      }

      p = viewToModel(p);

      List<ViewInfo> rootViews = myRenderSession.getRootViews();
      if (rootViews == null) {
        return null;
      }

      Point base = new Point();
      for (ViewInfo view : rootViews) {
        ViewInfo hitView = viewAtPoint(base, view, p);
        if (hitView != null) {
          return hitView;
        }
      }

      return null;
    } finally {
      myRenderSessionLock.readLock().unlock();
    }
  }

  public Dimension getPreferredSize() {
    return modelToView(myImageFactory.getRequestedWidth(), myImageFactory.getRequestedHeight());
  }

  public void dispose() {
    // We could get the readLock here first to check myRenderSession but this is a method that is rarely called
    // so we can optimistically get the writeLock.
    myRenderSessionLock.writeLock().lock();
    try {
      if (myRenderSession != null) {
        myImageFactory.setGraphics(null);
        myRenderSession.dispose();
        myRenderSession = null;
      }
    }
    finally {
      myRenderSessionLock.writeLock().unlock();
    }
  }

  /**
   * {@link HardwareConfig} that allows changing the screen size of the device on the fly.
   * <p/>
   * <p/>This allows to pass the HardwareConfig to the LayoutLib and then dynamically modify the size
   * for every render call.
   */
  static class DynamicHardwareConfig extends HardwareConfig {
    private int myWidth;
    private int myHeight;

    public DynamicHardwareConfig(HardwareConfig delegate) {
      super(delegate.getScreenWidth(), delegate.getScreenHeight(), delegate.getDensity(), delegate.getXdpi(), delegate.getYdpi(),
            delegate.getScreenSize(), delegate.getOrientation(), delegate.getScreenRoundness(), delegate.hasSoftwareButtons());

      myWidth = delegate.getScreenWidth();
      myHeight = delegate.getScreenHeight();
    }

    public void setScreenSize(int width, int height) {
      myWidth = width;
      myHeight = height;
    }

    @Override
    public int getScreenWidth() {
      return myWidth;
    }

    @Override
    public int getScreenHeight() {
      return myHeight;
    }
  }
}
