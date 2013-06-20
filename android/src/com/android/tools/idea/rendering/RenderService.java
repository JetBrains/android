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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.ResourceResolver;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.RenderContext;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkAdditionalData;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

/**
 * The {@link RenderService} provides rendering and layout information for
 * Android layouts. This is a wrapper around the layout library.
 */
public class RenderService {
  //private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.RenderService");

  @NotNull
  private final Module myModule;

  @NotNull
  private final AndroidFacet myFacet;

  @NotNull
  private final XmlFile myPsiFile;

  @NotNull
  private final RenderLogger myLogger;

  @NotNull
  private final ProjectCallback myProjectCallback;

  private final int myMinSdkVersion;

  private final int myTargetSdkVersion;

  @NotNull
  private final LayoutLibrary myLayoutLib;

  @Nullable
  private IImageFactory myImageFactory;

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

  private long myTimeout;

  @Nullable
  private Set<XmlTag> myExpandNodes;

  @Nullable
  private RenderContext myRenderContext;

  /**
   * Creates a new {@link RenderService} associated with the given editor.
   *
   * @return a {@link RenderService} which can perform rendering services
   */
  @Nullable
  public static RenderService create(@NotNull final AndroidFacet facet,
                                     @NotNull final Module module,
                                     @NotNull final PsiFile psiFile,
                                     @NotNull final Configuration configuration,
                                     @NotNull final RenderLogger logger,
                                     @Nullable final RenderContext renderContext) {

    Project project = module.getProject();
    AndroidPlatform platform = getPlatform(module);
    if (platform == null) {
      if (!AndroidMavenUtil.isMavenizedModule(module)) {
        RenderProblem.Html message = RenderProblem.create(ERROR);
        logger.addMessage(message);
        message.getHtmlBuilder().addLink("No Android SDK found. Please ", "configure", " an Android SDK.",
                                         logger.getLinkManager().createRunnableLink(new Runnable() {
          @Override
          public void run() {
            AndroidSdkUtils.openModuleDependenciesConfigurable(module);
          }
        }));
      }
      else {
        String message = AndroidBundle.message("android.maven.cannot.parse.android.sdk.error", module.getName());
        logger.addMessage(RenderProblem.createPlain(ERROR, message));
      }
      return null;
    }

    IAndroidTarget target = configuration.getTarget();
    if (target == null) {
      logger.addMessage(RenderProblem.createPlain(ERROR, "No render target was chosen"));
      return null;
    }

    RenderServiceFactory factory;
    try {
      factory = platform.getSdkData().getTargetData(target).getRenderServiceFactory(project);
      if (factory == null) {
        String message = AndroidBundle.message("android.layout.preview.cannot.load.library.error");
        logger.addMessage(RenderProblem.createPlain(ERROR, message));
        return null;
      }
    }
    catch (RenderingException e) {
      String message = e.getPresentableMessage();
      message = message != null ? message : AndroidBundle.message("android.layout.preview.default.error.message");
      logger.addMessage(RenderProblem.createPlain(ERROR, message, module.getProject(), logger.getLinkManager(), e));
      return null;
    }
    catch (IOException e) {
      final String message = e.getMessage();
      logger.error(null, "I/O error: " + (message != null ? ": " + message : ""), e);
      return null;
    }

    RenderService service = new RenderService(facet, module, psiFile, configuration, logger, factory);
    if (renderContext != null) {
      service.setRenderContext(renderContext);
    }

    return service;
  }

  /**
   * Use the {@link #create} factory instead
   */
  private RenderService(@NotNull AndroidFacet facet,
                        @NotNull Module module,
                        @NotNull PsiFile psiFile,
                        @NotNull Configuration configuration,
                        @NotNull RenderLogger logger,
                        @NotNull RenderServiceFactory factory) {
    myFacet = facet;
    myModule = module;
    myLogger = logger;
    if (!(psiFile instanceof XmlFile)) {
      throw new IllegalArgumentException("Can only render XML files: " + psiFile.getClass().getName());
    }
    myPsiFile = (XmlFile)psiFile;
    myConfiguration = configuration;

    Device device = configuration.getDevice();
    assert device != null; // Should only attempt render with configuration that has device
    myHardwareConfigHelper = new HardwareConfigHelper(device);

    myHardwareConfigHelper.setOrientation(configuration.getFullConfig().getScreenOrientationQualifier().getValue());
    myLayoutLib = factory.getLibrary(); // TODO: editor.getReadyLayoutLib(true /*displayError*/);
    ProjectResources projectResources = ProjectResources.get(myModule, true);
    myProjectCallback = new ProjectCallback(myLayoutLib, projectResources, myModule, myLogger); // TODO: true: /* reset*/
    myProjectCallback.loadAndParseRClass();
    Pair<Integer, Integer> sdkVersions = getSdkVersions(myFacet);
    myMinSdkVersion = sdkVersions.getFirst();
    myTargetSdkVersion = sdkVersions.getSecond();
  }

  @Nullable
  public AndroidPlatform getPlatform() {
    return getPlatform(myModule);
  }

  @Nullable
  private static AndroidPlatform getPlatform(@NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      return null;
    }
    return data.getAndroidPlatform();
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


  public void dispose() {
    myProjectCallback.setLogger(null);
    myProjectCallback.setResourceResolver(null);
  }

  @NotNull
  private static Pair<Integer, Integer> getSdkVersions(@NotNull final AndroidFacet facet) {
    final XmlTag manifestTag = ApplicationManager.getApplication().runReadAction(new Computable<XmlTag>() {
      @Nullable
      @Override
      public XmlTag compute() {
        final Manifest manifest = facet.getManifest();
        return manifest != null ? manifest.getXmlTag() : null;
      }
    });
    int minSdkVersion = 1;
    int targetSdkVersion = 1;
    if (manifestTag != null) {
      for (XmlTag usesSdkTag : manifestTag.findSubTags(TAG_USES_SDK)) {
        int candidate = AndroidUtils.getIntAttrValue(usesSdkTag, ATTR_MIN_SDK_VERSION);
        if (candidate >= 0) {
          minSdkVersion = candidate;
        }
        candidate = AndroidUtils.getIntAttrValue(usesSdkTag, ATTR_TARGET_SDK_VERSION);
        if (candidate >= 0) {
          minSdkVersion = candidate;
        }
      }
    }
    return Pair.create(minSdkVersion, targetSdkVersion);
  }


  /**
   * Overrides the width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(com.android.ide.common.rendering.api.SessionParams.RenderingMode)} is {@link com.android.ide.common.rendering.api.SessionParams.RenderingMode#FULL_EXPAND}.
   * <p/>
   * A value of -1 will make the rendering use the normal width and height coming from the
   * {@link Configuration#getDevice()} object.
   *
   * @param overrideRenderWidth  the width in pixels of the layout to be rendered
   * @param overrideRenderHeight the height in pixels of the layout to be rendered
   * @return this (such that chains of setters can be stringed together)
   */
  public RenderService setOverrideRenderSize(int overrideRenderWidth, int overrideRenderHeight) {
    myHardwareConfigHelper.setOverrideRenderSize(overrideRenderWidth, overrideRenderHeight);
    return this;
  }

  /**
   * Sets the max width and height to be used during rendering (which might be adjusted if
   * the {@link #setRenderingMode(com.android.ide.common.rendering.api.SessionParams.RenderingMode)} is {@link com.android.ide.common.rendering.api.SessionParams.RenderingMode#FULL_EXPAND}.
   * <p/>
   * A value of -1 will make the rendering use the normal width and height coming from the
   * {@link Configuration#getDevice()} object.
   *
   * @param maxRenderWidth  the max width in pixels of the layout to be rendered
   * @param maxRenderHeight the max height in pixels of the layout to be rendered
   * @return this (such that chains of setters can be stringed together)
   */
  public RenderService setMaxRenderSize(int maxRenderWidth, int maxRenderHeight) {
    myHardwareConfigHelper.setMaxRenderSize(maxRenderWidth, maxRenderHeight);
    return this;
  }

  /**
   * Sets the {@link com.android.ide.common.rendering.api.SessionParams.RenderingMode} to be used during rendering. If none is specified,
   * the default is {@link com.android.ide.common.rendering.api.SessionParams.RenderingMode#NORMAL}.
   *
   * @param renderingMode the rendering mode to be used
   * @return this (such that chains of setters can be stringed together)
   */
  public RenderService setRenderingMode(@NotNull RenderingMode renderingMode) {
    myRenderingMode = renderingMode;
    return this;
  }

  /** Returns the {@link RenderingMode} to be used */
  @NotNull
  public RenderingMode getRenderingMode() {
    return myRenderingMode;
  }

  public RenderService setTimeout(long timeout) {
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
  public RenderService setOverrideBgColor(Integer overrideBgColor) {
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
  public RenderService setDecorations(boolean showDecorations) {
    myShowDecorations = showDecorations;
    return this;
  }

  /**
   * Gets the context for the usage of this {@link RenderService}, which can
   * control for example how {@code <fragment/>} tags are processed when missing
   * preview data
   */
  @Nullable
  public RenderContext getRenderContext() {
    return myRenderContext;
  }

  /**
   * Sets the context for the usage of this {@link RenderService}, which can
   * control for example how {@code <fragment/>} tags are processed when missing
   * preview data
   *
   * @param renderContext the render context
   * @return this, for constructor chaining
   */
  @Nullable
  public RenderService setRenderContext(@Nullable RenderContext renderContext) {
    myRenderContext = renderContext;
    return this;
  }

  /**
   * Sets the nodes to expand during rendering. These will be padded with approximately
   * 20 pixels. The default is null.
   *
   * @param nodesToExpand the nodes to be expanded
   * @return this (such that chains of setters can be stringed together)
   */
  @NotNull
  public RenderService setNodesToExpand(@Nullable Set<XmlTag> nodesToExpand) {
    myExpandNodes = nodesToExpand;
    return this;
  }

  /**
   * Sets the {@link IncludeReference} to an outer layout that this layout should be rendered
   * within. The outer layout <b>must</b> contain an include tag which points to this
   * layout. The default is null.
   *
   * @param includedWithin a reference to an outer layout to render this layout within
   * @return this (such that chains of setters can be stringed together)
   */
  public RenderService setIncludedWithin(IncludeReference includedWithin) {
    myIncludedWithin = includedWithin;
    return this;
  }

  /**
   * Renders the model and returns the result as a {@link com.android.ide.common.rendering.api.RenderSession}.
   *
   * @return the {@link com.android.ide.common.rendering.api.RenderSession} resulting from rendering the current model
   */
  @Nullable
  public RenderSession createRenderSession() {
    ResourceResolver resolver = getResourceResolver();
    if (resolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    ApplicationManager.getApplication().assertReadAccessAllowed();
    XmlTagPullParser modelParser = new XmlTagPullParser(myPsiFile, myExpandNodes, hardwareConfig.getDensity(), myLogger);
    ILayoutPullParser topParser = modelParser;

    // Code to support editing included layout
    // first reset the layout parser just in case.
    myProjectCallback.setLayoutParser(null, null);

    if (myIncludedWithin != null) {
      // Outer layout name:
      String contextLayoutName = myIncludedWithin.getName();

      // Find the layout file.
      ResourceValue contextLayout = resolver.findResValue(LAYOUT_RESOURCE_PREFIX + contextLayoutName, false  /* forceFrameworkOnly*/);
      if (contextLayout != null) {
        File layoutFile = new File(contextLayout.getValue());
        if (layoutFile.isFile()) {
          try {
            // Get the name of the layout actually being edited, without the extension
            // as it's what IXmlPullParser.getParser(String) will receive.
            String queryLayoutName = ResourceHelper.getResourceName(myPsiFile);
            myProjectCallback.setLayoutParser(queryLayoutName, modelParser);
            topParser = new ContextPullParser(myProjectCallback);
            topParser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            String xmlText = Files.toString(layoutFile, Charsets.UTF_8);
            topParser.setInput(new StringReader(xmlText));
          }
          catch (IOException e) {
            //LOG.error(e);
            myLogger.error(null, String.format("Could not read layout file %1$s", layoutFile), e);
          }
          catch (XmlPullParserException e) {
            //LOG.error(e);
            myLogger.error(null, String.format("XML parsing error: %1$s", e.getMessage()), e.getDetail() != null ? e.getDetail() : e);
          }
        }
      }
    }

    // todo: support caching

    myLayoutLib.clearCaches(myModule);
    final SessionParams params =
      new SessionParams(topParser, myRenderingMode, myModule /* projectKey */, hardwareConfig, resolver, myProjectCallback,
                        myMinSdkVersion, myTargetSdkVersion, myLogger);

    // Request margin and baseline information.
    // TODO: Be smarter about setting this; start without it, and on the first request
    // for an extended view info, re-render in the same session, and then set a flag
    // which will cause this to create extended view info each time from then on in the
    // same session
    params.setExtendedViewInfoMode(true);

    if (!myShowDecorations) {
      params.setForceNoDecor();
    }
    else {
      ManifestInfo manifestInfo = ManifestInfo.get(myModule);
      try {
        params.setAppLabel(manifestInfo.getApplicationLabel());
        params.setAppIcon(manifestInfo.getApplicationIcon());
      }
      catch (Exception e) {
        // ignore.
      }
    }

    if (myOverrideBgColor != null) {
      params.setOverrideBgColor(myOverrideBgColor.intValue());
    }

    if (myImageFactory != null) {
      params.setImageFactory(myImageFactory);
    }

    if (myTimeout > 0) {
      params.setTimeout(myTimeout);
    }

    try {
      myProjectCallback.setLogger(myLogger);
      myProjectCallback.setResourceResolver(resolver);

      return ApplicationManager.getApplication().runReadAction(new Computable<RenderSession>() {
        @Nullable
        @Override
        public RenderSession compute() {
          int retries = 0;
          RenderSession session = null;
          while (retries < 10) {
            session = myLayoutLib.createSession(params);
            if (session.getResult().getStatus() != Result.Status.ERROR_TIMEOUT) {
              break;
            }
            retries++;
          }

          return session;
        }
      });
    }
    catch (RuntimeException t) {
      // Exceptions from the bridge
      myLogger.error(null, t.getLocalizedMessage(), t, null);
      throw t;
    }
  }

  @Nullable
  public RenderResult render() {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    RenderResult renderResult;
    try {
      RenderSession session = createRenderSession();
      renderResult = new RenderResult(this, session, myPsiFile, myLogger);
      if (session != null) {
        addDiagnostics(session);
      }
    } catch (final Exception e) {
      String message = e.getMessage();
      if (message == null) {
        message = e.toString();
      }
      myLogger.addMessage(RenderProblem.createPlain(ERROR, message, myModule.getProject(), myLogger.getLinkManager(), e));
      renderResult = new RenderResult(this, null, myPsiFile, myLogger);
    }

    return renderResult;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private void addDiagnostics(RenderSession session) {
    Result r = session.getResult();
    if (!myLogger.hasProblems() && !r.isSuccess()) {
      if (r.getException() != null || r.getErrorMessage() != null) {
        myLogger.error(null, r.getErrorMessage(), r.getException(), null);
      } else if (r.getStatus() == Result.Status.ERROR_TIMEOUT) {
        myLogger.error(null, "Rendering timed out.", null);
      } else {
        myLogger.error(null, "Unknown render problem: " + r.getStatus(), null);
      }
    }
  }

  /**
   * Renders the given resource value (which should refer to a drawable) and returns it
   * as an image
   *
   * @param drawableResourceValue the drawable resource value to be rendered, or null
   * @return the image, or null if something went wrong
   */
  @Nullable
  public BufferedImage renderDrawable(ResourceValue drawableResourceValue) {
    if (drawableResourceValue == null) {
      return null;
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    DrawableParams params =
      new DrawableParams(drawableResourceValue, myModule, hardwareConfig, getResourceResolver(), myProjectCallback, myMinSdkVersion,
                         myTargetSdkVersion, myLogger);
    params.setForceNoDecor();
    Result result = myLayoutLib.renderDrawable(params);
    if (result != null && result.isSuccess()) {
      Object data = result.getData();
      if (data instanceof BufferedImage) {
        return (BufferedImage)data;
      }
    }

    return null;
  }

//    private static final String DEFAULT_APP_LABEL = "Android Application";
//    private static String getAppLabelToShow(final AndroidFacet facet) {
//        return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
//            @Override
//            public String compute() {
//                final Manifest manifest = facet.getManifest();
//                if (manifest != null) {
//                    final Application application = manifest.getApplication();
//                    if (application != null) {
//                        final String label = application.getLabel().getStringValue();
//                        if (label != null) {
//                            return label;
//                        }
//                    }
//                }
//                return DEFAULT_APP_LABEL;
//            }
//        });
//    }

  @NotNull
  public ProjectCallback getProjectCallback() {
    return myProjectCallback;
  }

  @NotNull
  public XmlFile getPsiFile() {
    return myPsiFile;
  }

  public static boolean supportsCapability(@NotNull final Module module, @NotNull IAndroidTarget target, @NotNull Capability capability) {
    Project project = module.getProject();
    AndroidPlatform platform = getPlatform(module);
    if (platform != null) {
      try {
        RenderServiceFactory factory = platform.getSdkData().getTargetData(target).getRenderServiceFactory(project);
        if (factory != null) {
          LayoutLibrary library = factory.getLibrary();
          if (library != null) {
            return library.supports(capability);
          }
        }
      }
      catch (RenderingException e) {
        // Ignore: if service can't be found, that capability isn't available
      }
      catch (IOException e) {
        // Ditto
      }
    }
    return false;
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
   * @param rootTag the tag, if any
   */
  public void useDesignMode(@Nullable XmlTag rootTag) {
    if (rootTag != null) {
      String tagName = rootTag.getName();
      if (SCROLL_VIEW.equals(tagName)) {
        setRenderingMode(RenderingMode.V_SCROLL);
        setDecorations(false);
      } else if (HORIZONTAL_SCROLL_VIEW.equals(tagName)) {
        setRenderingMode(RenderingMode.H_SCROLL);
        setDecorations(false);
      }
    }
  }
}