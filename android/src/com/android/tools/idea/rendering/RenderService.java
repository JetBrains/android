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

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.rendering.api.SessionParams.RenderingMode;
import com.android.ide.common.resources.*;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.io.StreamException;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.maven.AndroidMavenUtil;
import org.jetbrains.android.sdk.*;
import org.jetbrains.android.uipreview.ProjectResources;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.android.uipreview.RenderingException;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.BufferingFileWrapper;
import org.jetbrains.android.util.BufferingFolderWrapper;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

import static com.android.SdkConstants.*;
import static com.intellij.lang.annotation.HighlightSeverity.ERROR;

/**
 * The {@link RenderService} provides rendering and layout information for
 * Android layouts. This is a wrapper around the layout library.
 */
public class RenderService {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.RenderService");

  private static final String DEFAULT_APP_LABEL = "Android Application";

  @NotNull
  private final Module myModule;

  @NotNull
  private final AndroidFacet myFacet;

  @NotNull
  private final XmlFile myPsiFile;

  /**
   * IncludeReference to the file being edited
   */
  @NotNull
  private final VirtualFile myLayoutFile;

  /**
   * The actual XML text for the layout
   */
  private final String myLayoutXmlText;

  private final RenderLogger myLogger;

  // The following fields are inferred from the editor and not customizable by the
  // client of the render service:
  @NotNull
  private final ProjectCallback myProjectCallback;
  private ResourceResolver myResourceResolver;
  private final int myMinSdkVersion;
  private final int myTargetSdkVersion;
  private final LayoutLibrary myLayoutLib;

  @Nullable
  private IImageFactory myImageFactory;

  private final HardwareConfigHelper myHardwareConfigHelper;

  // The following fields are optional or configurable using the various chained
  // setters:

  private IncludeReference myIncludedWithin;
  private RenderingMode myRenderingMode = RenderingMode.NORMAL;
  private Integer myOverrideBgColor;
  private boolean myShowDecorations = true;

  @NotNull
  private final Configuration myConfiguration;

  private final FrameworkResources myFrameworkResources;

  /**
   * Creates a new {@link RenderService} associated with the given editor.
   *
   * @return a {@link RenderService} which can perform rendering services
   */
  @Nullable
  public static RenderService create(@NotNull final AndroidFacet facet,
                                     @NotNull final Module module,
                                     @NotNull final PsiFile psiFile,
                                     @NotNull final String layoutXmlText,
                                     @Nullable final VirtualFile layoutXmlFile,
                                     @NotNull final Configuration configuration,
                                     @NotNull final RenderLogger logger) {

    Project project = module.getProject();
    AndroidPlatform platform = getPlatform(module);
    if (platform == null) {
      if (!AndroidMavenUtil.isMavenizedModule(module)) {
        RenderProblem.Html message = RenderProblem.create(ERROR);
        logger.addMessage(message);
        message.getHtmlBuilder().addLink("Please ", "configure", " Android SDK",
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

    return new RenderService(facet, module, psiFile, layoutXmlText, layoutXmlFile, configuration, logger, factory);
  }

  /**
   * Use the {@link #create} factory instead
   */
  private RenderService(@NotNull AndroidFacet facet,
                        @NotNull Module module,
                        @NotNull PsiFile psiFile,
                        @NotNull String layoutXmlText,
                        @Nullable VirtualFile layoutXmlFile,
                        @NotNull Configuration configuration,
                        @NotNull RenderLogger logger,
                        @NotNull RenderServiceFactory factory) {
    myFacet = facet;
    myModule = module;
    myLayoutFile = layoutXmlFile;
    myLayoutXmlText = layoutXmlText;
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
    myFrameworkResources = factory.getFrameworkResources();

    ProjectResources projectResources = gather(myModule, myFacet, myLayoutXmlText, myLayoutFile);
    myProjectCallback = new ProjectCallback(myLayoutLib, projectResources, myModule, myLogger); // TODO: true: /* reset*/

// TODO: The resource resolver and the project callback for an editor
//    final Pair<RenderResources, RenderResources> pair =
//            factory.createResourceResolver(facet, folderConfig, projectResources,
//                    theme.getName(), theme.isProjectTheme());
//    myResourceResolver = pair.getFirst();
//
//
//    // should be cached on each file
//    myResourceResolver = editor.getResourceResolver();


    myProjectCallback.loadAndParseRClass();
    myResourceResolver = getResourceResolver();

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

  private Map<ResourceType, Map<String, ResourceValue>> myConfiguredFrameworkRes;
  private Map<ResourceType, Map<String, ResourceValue>> myConfiguredProjectRes;

  /**
   * Returns the {@link ResourceResolver} for this editor
   *
   * @return the resolver used to resolve resources for the current configuration of
   *         this editor, or null
   */
  public ResourceResolver getResourceResolver() {
    if (myResourceResolver == null) {
      String themeStyle = myConfiguration.getTheme();
      if (themeStyle == null) {
        LOG.error("Missing theme.");
        return null;
      }
      boolean isProjectTheme = myConfiguration.isProjectTheme();
      String theme = ResourceHelper.styleToTheme(themeStyle);

      Map<ResourceType, Map<String, ResourceValue>> configuredProjectRes = getConfiguredProjectResources();

      // Get the framework resources
      Map<ResourceType, Map<String, ResourceValue>> frameworkResources = getConfiguredFrameworkResources();
      myResourceResolver = ResourceResolver.create(configuredProjectRes, frameworkResources, theme, isProjectTheme);
    }

    return myResourceResolver;
  }

  @NotNull
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredFrameworkResources() {
    if (myConfiguredFrameworkRes == null) {
      ResourceRepository frameworkRes = getFrameworkResources();

      if (frameworkRes == null) {
        LOG.error("Failed to get ProjectResource for the framework");
        myConfiguredFrameworkRes = Collections.emptyMap();
      }
      else {
        // get the framework resource values based on the current config
        myConfiguredFrameworkRes = frameworkRes.getConfiguredResources(myConfiguration.getFullConfig());
      }
    }

    return myConfiguredFrameworkRes;
  }

  @NotNull
  public Map<ResourceType, Map<String, ResourceValue>> getConfiguredProjectResources() {
    if (myConfiguredProjectRes == null) {
      ProjectResources project = getProjectResources();

      // get the project resource values based on the current config
      myConfiguredProjectRes = project != null ? project.getConfiguredResources(myConfiguration.getFullConfig())
                                               : Collections.<ResourceType, Map<String, ResourceValue>>emptyMap();
    }

    return myConfiguredProjectRes;
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  /**
   * Returns a {@link ProjectResources} for the framework resources based on the current
   * configuration selection.
   *
   * @return the framework resources or null if not found.
   */
  @Nullable
  public ResourceRepository getFrameworkResources() {
    IAndroidTarget target = myConfiguration.getTarget();
    if (target != null) {
      return getFrameworkResources(target, myModule);
    }

    return null;
  }

  public void dispose() {
    myProjectCallback.setLogger(null);
    myProjectCallback.setResourceResolver(null);
    myConfiguredFrameworkRes = null;
    myConfiguredProjectRes = null;
    myResourceResolver = null;
  }

  /**
   * Returns a {@link ProjectResources} for the framework resources of a given
   * target.
   *
   * @param target the target for which to return the framework resources.
   * @return the framework resources or null if not found.
   */
  @Nullable
  private static ResourceRepository getFrameworkResources(@NotNull IAndroidTarget target, @NotNull Module module) {
    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof AndroidSdkType)) {
      return null;
    }
    AndroidSdkAdditionalData data = (AndroidSdkAdditionalData)sdk.getSdkAdditionalData();
    if (data == null) {
      return null;
    }
    AndroidPlatform platform = data.getAndroidPlatform();
    if (platform == null) {
      return null;
    }

    AndroidTargetData targetData = platform.getSdkData().getTargetData(target);
    try {
      Project project = module.getProject();
      RenderServiceFactory factory = targetData.getRenderServiceFactory(project);
      if (factory != null) {
        return factory.getFrameworkResources();
      }
    }
    catch (RenderingException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }

    return null;
  }

  @Nullable
  public ProjectResources getProjectResources() {
    try {
      return gather(myModule, myFacet, myLayoutXmlText, myLayoutFile);
    }
    catch (Exception e) {
      LOG.error(e);
    }

    // TODO: Cache on project: ResourceManager manager = ResourceManager.getInstance();

    return null;
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


  private static ProjectResources gather(Module module, AndroidFacet facet, String layoutXmlText, VirtualFile layoutXmlFile) {
    // THIS IS HACKY! It should be cached on the editor from render to render, and besides,
    // it should be built from the PSI rather than parsing XML text each time!
    List<AndroidFacet> allLibraries = AndroidUtils.getAllAndroidDependencies(module, true);
    List<ProjectResources> libResources = new ArrayList<ProjectResources>();
    List<ProjectResources> emptyResList = Collections.emptyList();

    for (AndroidFacet libFacet : allLibraries) {
      if (!libFacet.equals(facet)) {
        libResources.add(loadProjectResources(libFacet, null, null, emptyResList));
      }
    }
    return loadProjectResources(facet, layoutXmlText, layoutXmlFile, libResources);
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
  public RenderService setRenderingMode(RenderingMode renderingMode) {
    myRenderingMode = renderingMode;
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

//    /**
//     * Sets the nodes to expand during rendering. These will be padded with approximately
//     * 20 pixels and also highlighted by the {@link EmptyViewsOverlay}. The default is an
//     * empty collection.
//     *
//     * @param nodesToExpand the nodes to be expanded
//     * @return this (such that chains of setters can be stringed together)
//     */
//    public RenderService setNodesToExpand(Set<UiElementNode> nodesToExpand) {
//        mExpandNodes = nodesToExpand;
//        return this;
//    }

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
    if (myResourceResolver == null) {
      // Abort the rendering if the resources are not found.
      return null;
    }

    HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();

    XmlTagPullParser modelParser = new XmlTagPullParser(myPsiFile, null /*mExplodedNodes*/, hardwareConfig.getDensity());
    ILayoutPullParser topParser = modelParser;

    // Code to support editing included layout
    // first reset the layout parser just in case.
    myProjectCallback.setLayoutParser(null, null);

    if (myIncludedWithin != null) {
      // Outer layout name:
      String contextLayoutName = myIncludedWithin.getName();

      // Find the layout file.
      ResourceValue contextLayout =
        myResourceResolver.findResValue(LAYOUT_RESOURCE_PREFIX + contextLayoutName, false  /* forceFrameworkOnly*/);
      if (contextLayout != null) {
        File layoutFile = new File(contextLayout.getValue());
        if (layoutFile.isFile()) {
          try {
            // Get the name of the layout actually being edited, without the extension
            // as it's what IXmlPullParser.getParser(String) will receive.
            String queryLayoutName = myLayoutFile.getNameWithoutExtension();
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
      new SessionParams(topParser, myRenderingMode, myModule /* projectKey */, hardwareConfig, myResourceResolver, myProjectCallback,
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

    try {
      myProjectCallback.setLogger(myLogger);
      myProjectCallback.setResourceResolver(myResourceResolver);

      return ApplicationManager.getApplication().runReadAction(new Computable<RenderSession>() {
        @Override
        public RenderSession compute() {
          return myLayoutLib.createSession(params);
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
    RenderResult renderResult = RenderResult.NONE;
    try {
      RenderSession session = createRenderSession();
      if (session == null) {
        return RenderResult.NONE;
      }

      renderResult = new RenderResult(this, session, myPsiFile, myLogger);
      addDiagnostics(session);
    } catch (final Exception e) {
      String message = e.getMessage();
      if (message == null) {
        message = e.toString();
      }
      myLogger.addMessage(RenderProblem.createPlain(ERROR, message, myModule.getProject(), myLogger.getLinkManager(), e));
    }

    return renderResult;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  private void addDiagnostics(RenderSession session) {
    Result r = session.getResult();
    if (!myLogger.hasProblems() && !r.isSuccess()) {
      if (r.getException() != null || r.getErrorMessage() != null) {
        myLogger.error(null, r.getErrorMessage(), r.getException(), null);
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
      new DrawableParams(drawableResourceValue, myModule, hardwareConfig, myResourceResolver, myProjectCallback, myMinSdkVersion,
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

//  /**
//   * Measure the children of the given parent node, applying the given filter to the
//   * pull parser's attribute values.
//   *
//   * @param parent the parent node to measure children for
//   * @param filter the filter to apply to the attribute values
//   * @return a map from node children of the parent to new bounds of the nodes
//   */
//    @Nullable
//    public Map<INode, Rect> measureChildren(INode parent,
//            final IClientRulesEngine.AttributeFilter filter) {
//        HardwareConfig hardwareConfig = myHardwareConfigHelper.getConfig();
//
//        final NodeFactory mNodeFactory = mEditor.getCanvasControl().getNodeFactory();
//        XmlTag parentNode = ((NodeProxy) parent).getNode();
//        XmlTagPullParser topParser = new XmlTagPullParser(parentNode,
//                false, Collections.<UiElementNode>emptySet(), hardwareConfig.getDensity(),
//                mProject) {
//            @Override
//            public String getAttributeValue(String namespace, String localName) {
//                if (filter != null) {
//                    Object cookie = getViewCookie();
//                    if (cookie instanceof XmlTag) {
//                        NodeProxy node = mNodeFactory.create((XmlTag) cookie);
//                        if (node != null) {
//                            String value = filter.getAttribute(node, namespace, localName);
//                            if (value != null) {
//                                return value;
//                            }
//                            // null means no preference, not "unset".
//                        }
//                    }
//                }
//
//                return super.getAttributeValue(namespace, localName);
//            }
//
//
//            // The parser usually assumes that the top level node is a document node that
//            // should be skipped, and that's not the case when we render in the middle of
//            // the tree, so override {@link XmlTagPullParser#onNextFromStartDocument}
//            // to change this behavior
//            @Override
//            public void onNextFromStartDocument() {
//                myParsingState = START_TAG;
//            }
//        };
//
//        SessionParams params = new SessionParams(
//                topParser,
//                RenderingMode.FULL_EXPAND,
//                myModule,
//                hardwareConfig,
//                myResourceResolver,
//                myProjectCallback,
//                myMinSdkVersion,
//                myTargetSdkVersion,
//                myLogger);
//        params.setLayoutOnly();
//        params.setForceNoDecor();
//
//        RenderSession session = null;
//        try {
//            myProjectCallback.setLogger(myLogger);
//            myProjectCallback.setResourceResolver(myResourceResolver);
//            session = myLayoutLib.createSession(params);
//            if (session.getResult().isSuccess()) {
//                assert session.getRootViews().size() == 1;
//                ViewInfo root = session.getRootViews().get(0);
//                List<ViewInfo> children = root.getChildren();
//                Map<INode, Rect> map = new HashMap<INode, Rect>(children.size());
//                for (ViewInfo info : children) {
//                    if (info.getCookie() instanceof XmlTag) {
//                        XmlTag uiNode = (XmlTag) info.getCookie();
//                        INode node = mNodeFactory.create(uiNode);
//                        map.put(node, new Rect(info.getLeft(), info.getTop(),
//                                info.getRight() - info.getLeft(),
//                                info.getBottom() - info.getTop()));
//                    }
//                }
//
//                return map;
//            }
//        } catch (RuntimeException t) {
//            // Exceptions from the bridge
//            myLogger.error(null, t.getLocalizedMessage(), t, null);
//            throw t;
//        } finally {
//            myProjectCallback.setLogger(null);
//            myProjectCallback.setResourceResolver(null);
//            if (session != null) {
//                session.dispose();
//            }
//        }
//
//        return null;
//        throw new UnsupportedOperationException("Not yet implemented");
//    }
  @NotNull
  private static ProjectResources loadProjectResources(@NotNull AndroidFacet facet,
                                                       @Nullable String layoutXmlText,
                                                       @Nullable VirtualFile layoutXmlFile,
                                                       @NotNull List<ProjectResources> libResources) {
    final VirtualFile resourceDir = facet.getLocalResourceManager().getResourceDir();

    if (resourceDir != null) {
      final IAbstractFolder resFolder = new BufferingFolderWrapper(new File(FileUtil.toSystemDependentName(resourceDir.getPath())));
      final ProjectResources projectResources = new ProjectResources(resFolder, libResources);
      loadResources(projectResources, layoutXmlText, layoutXmlFile, resFolder);
      return projectResources;
    }
    return new ProjectResources(new NullFolderWrapper(), libResources);
  }

//    @NotNull
//    private static Set<String> getSdkNamesFromModules(@NotNull Collection<Module> modules) {
//        final Set<String> result = new HashSet<String>();
//
//        for (Module module : modules) {
//            final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
//
//            if (sdk != null) {
//                result.add(sdk.getName());
//            }
//        }
//        return result;
//    }
//
//    @NotNull
//    private static <T> List<T> getNonNullValues(@NotNull Map<?, T> map) {
//        final List<T> result = new ArrayList<T>();
//
//        for (Map.Entry<?, T> entry : map.entrySet()) {
//            final T value = entry.getValue();
//            if (value != null) {
//                result.add(value);
//            }
//        }
//        return result;
//    }
//
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

  private static void loadResources(@NotNull ResourceRepository repository,
                                   @Nullable final String layoutXmlFileText,
                                   @Nullable VirtualFile layoutXmlFile,
                                   @NotNull IAbstractFolder... rootFolders) {
    final ScanningContext scanningContext = new ScanningContext(repository);

    for (IAbstractFolder rootFolder : rootFolders) {
      for (IAbstractResource file : rootFolder.listMembers()) {
        if (!(file instanceof IAbstractFolder)) {
          continue;
        }

        final IAbstractFolder folder = (IAbstractFolder)file;
        final ResourceFolder resFolder = repository.processFolder(folder);

        if (resFolder != null) {
          for (final IAbstractResource childRes : folder.listMembers()) {

            if (childRes instanceof IAbstractFile) {
              final VirtualFile vFile;

              if (childRes instanceof BufferingFileWrapper) {
                final BufferingFileWrapper fileWrapper = (BufferingFileWrapper)childRes;
                final String filePath = FileUtil.toSystemIndependentName(fileWrapper.getOsLocation());
                vFile = LocalFileSystem.getInstance().findFileByPath(filePath);

                if (vFile != null && Comparing.equal(vFile, layoutXmlFile) && layoutXmlFileText != null) {
                  resFolder.processFile(new MyFileWrapper(layoutXmlFileText, childRes), ResourceDeltaKind.ADDED, scanningContext);
                }
                else {
                  resFolder.processFile((IAbstractFile)childRes, ResourceDeltaKind.ADDED, scanningContext);
                }
              }
              else {
                LOG.error("childRes must be instance of " + BufferingFileWrapper.class.getName());
              }
            }
          }
        }
      }
    }

    final List<String> errors = scanningContext.getErrors();
    if (errors != null && errors.size() > 0) {
      LOG.debug(new RenderingException(merge(errors)));
    }
  }

  private static String merge(@NotNull Collection<String> strs) {
    final StringBuilder result = new StringBuilder();
    for (Iterator<String> it = strs.iterator(); it.hasNext(); ) {
      String str = it.next();
      result.append(str);
      if (it.hasNext()) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  //@Nullable
  //public static String getRClassName(@NotNull final Module module) {
  //  return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
  //    @Nullable
  //    @Override
  //    public String compute() {
  //      final AndroidFacet facet = AndroidFacet.getInstance(module);
  //      if (facet == null) {
  //        return null;
  //      }
  //
  //      final Manifest manifest = facet.getManifest();
  //      if (manifest == null) {
  //        return null;
  //      }
  //
  //      final String aPackage = manifest.getPackage().getValue();
  //      return aPackage == null ? null : aPackage + ".R";
  //    }
  //  });
  //}

  @NotNull
  public ProjectCallback getProjectCallback() {
    return myProjectCallback;
  }

  @NotNull
  public XmlFile getPsiFile() {
    return myPsiFile;
  }

  private static class MyFileWrapper implements IAbstractFile {
    private final String myLayoutXmlFileText;
    private final IAbstractResource myChildRes;

    public MyFileWrapper(String layoutXmlFileText, IAbstractResource childRes) {
      myLayoutXmlFileText = layoutXmlFileText;
      myChildRes = childRes;
    }

    @Override
    public InputStream getContents() throws StreamException {
      return new ByteArrayInputStream(myLayoutXmlFileText.getBytes());
    }

    @Override
    public void setContents(InputStream source) throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getOutputStream() throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PreferredWriteMode getPreferredWriteMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getModificationStamp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return myChildRes.getName();
    }

    @Override
    public String getOsLocation() {
      return myChildRes.getOsLocation();
    }

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public IAbstractFolder getParentFolder() {
      return myChildRes.getParentFolder();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }
  }

  private static class NullFolderWrapper implements IAbstractFolder {
    @Override
    public boolean hasFile(String name) {
      return false;
    }

    @Nullable
    @Override
    public IAbstractFile getFile(String name) {
      return null;
    }

    @Nullable
    @Override
    public IAbstractFolder getFolder(String name) {
      return null;
    }

    @Override
    public IAbstractResource[] listMembers() {
      return new IAbstractResource[0];
    }

    @Override
    public String[] list(FilenameFilter filter) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    @Override
    public String getName() {
      return "stub_name";
    }

    @Override
    public String getOsLocation() {
      return "stub_os_location";
    }

    @Override
    public boolean exists() {
      return false;
    }

    @Nullable
    @Override
    public IAbstractFolder getParentFolder() {
      return null;
    }

    @Override
    public boolean delete() {
      return false;
    }
  }
}