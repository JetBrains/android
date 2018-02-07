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

import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.android.tools.idea.fonts.ProjectFonts;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.lint.detector.api.LintUtils;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.*;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.RecyclerViewHelper;
import org.jetbrains.android.uipreview.ViewLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.layoutlib.RenderParamsFlags.*;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;

/**
 * Loader for Android Project class in order to use them in the layout editor.
 * <p/>This implements {@code com.android.ide.common.rendering.api.IProjectCallback} for the old and new API through
 * {@link LayoutlibCallback}
 */
public class LayoutlibCallbackImpl extends LayoutlibCallback {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.LayoutlibCallback");

  /** Maximum number of getParser calls in a render before we suspect and investigate potential include cycles */
  private static final int MAX_PARSER_INCLUDES = 50;
  /** Class names that are not a view. When instantiating them, errors should be logged by LayoutLib. */
  private static final Set<String> NOT_VIEW = ImmutableSet.of(RecyclerViewHelper.CN_RV_ADAPTER,
                                                              RecyclerViewHelper.CN_RV_LAYOUT_MANAGER,
                                                             "android.support.v7.internal.app.WindowDecorActionBar");
  /** Directory name for the bundled layoutlib installation */
  public static final String FD_LAYOUTLIB = "layoutlib";
  /** Directory name for the gradle build-cache. Exploded AARs will end up there when using build cache */
  public static final String BUILD_CACHE = "build-cache";

  @NotNull private final Module myModule;
  @NotNull private final AppResourceRepository myProjectRes;
  @NotNull final private LayoutLibrary myLayoutLib;
  @Nullable private final Object myCredential;
  private final boolean myHasAppCompat;
  @Nullable private String myNamespace;
  @NotNull private IRenderLogger myLogger;
  @NotNull private final ViewLoader myClassLoader;
  @Nullable private String myLayoutName;
  @Nullable private ILayoutPullParser myLayoutEmbeddedParser;
  @Nullable private ResourceResolver myResourceResolver;
  @Nullable private final ActionBarHandler myActionBarHandler;
  @Nullable private final RenderTask myRenderTask;
  @NotNull private final DownloadableFontCacheService myFontCacheService;
  private boolean myUsed;
  private Set<File> myParserFiles;
  private int myParserCount;
  private ParserFactory myParserFactory;
  @NotNull public ImmutableMap<String, TagSnapshot> myAaptDeclaredResources = ImmutableMap.of();
  private final Map<String, ResourceValue> myFontFamilies;
  private ProjectFonts myProjectFonts;
  private String myAdaptiveIconMaskPath;
  @Nullable private final ILayoutPullParserFactory myLayoutPullParserFactory;

  /**
   * Creates a new {@link LayoutlibCallbackImpl} to be used with the layout lib.
   *
   * @param renderTask The associated render task
   * @param layoutLib  The layout library this callback is going to be invoked from
   * @param projectRes the {@link LocalResourceRepository} for the project.
   * @param module     the module
   * @param facet      the facet
   * @param logger     the render logger
   * @param credential the sandbox credential
   * @param actionBarHandler An {@link ActionBarHandler} instance.
   * @param parserFactory an optional factory for creating XML parsers.
   */
  public LayoutlibCallbackImpl(@Nullable RenderTask renderTask,
                               @NotNull LayoutLibrary layoutLib,
                               @NotNull AppResourceRepository projectRes,
                               @NotNull Module module,
                               @NotNull AndroidFacet facet,
                               @NotNull IRenderLogger logger,
                               @Nullable Object credential,
                               @Nullable ActionBarHandler actionBarHandler,
                               @Nullable ILayoutPullParserFactory parserFactory) {
    myRenderTask = renderTask;
    myLayoutLib = layoutLib;
    myProjectRes = projectRes;
    myModule = module;
    myLogger = logger;
    myCredential = credential;
    myLogger = logger;
    myClassLoader = new ViewLoader(myLayoutLib, facet, logger, credential);
    myActionBarHandler = actionBarHandler;
    myLayoutPullParserFactory = parserFactory;
    myHasAppCompat = DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.APP_COMPAT_V7);

    String javaPackage = MergedManifest.get(myModule).getPackage();
    if (javaPackage != null && !javaPackage.isEmpty()) {
      myNamespace = URI_PREFIX + javaPackage;
    } else {
      myNamespace = AUTO_URI;
    }
    myFontCacheService = DownloadableFontCacheService.getInstance();
    myFontFamilies = projectRes.getAllResourceItems().stream()
      .filter(r -> r.getType() == ResourceType.FONT)
      .map(r -> r.getResourceValue())
      .filter(value -> value.getRawXmlValue().endsWith(DOT_XML))
      .collect(Collectors.toMap(ResourceValue::getRawXmlValue, (ResourceValue value) -> value));
  }

  /** Resets the callback state for another render */
  void reset() {
    myParserCount = 0;
    myParserFiles = null;
    myLayoutName = null;
    myLayoutEmbeddedParser = null;
    myAaptDeclaredResources = ImmutableMap.of();
  }

  /**
   * Sets the {@link LayoutLog} logger to use for error messages during problems.
   *
   * @param logger the new logger to use
   */
  public void setLogger(@NotNull IRenderLogger logger) {
    myLogger = logger;
    myClassLoader.setLogger(logger);
  }

  /**
   * Returns the {@link ILayoutLog} logger used for error messages.
   *
   * @return the logger being used
   */
  @NotNull
  public ILayoutLog getLogger() {
    return myLogger;
  }

  /**
   * {@inheritDoc}
   * <p/>
   * This implementation goes through the output directory of the project and loads the
   * <code>.class</code> file directly.
   */
  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public Object loadView(@NotNull String className, @NotNull Class[] constructorSignature, @NotNull Object[] constructorParameters)
      throws ClassNotFoundException {
    myUsed = true;
    if (NOT_VIEW.contains(className)) {
      return myClassLoader.loadClass(className, constructorSignature, constructorParameters);
    }
    return myClassLoader.loadView(className, constructorSignature, constructorParameters);
  }

  @Override
  public Object loadClass(@NotNull String name, @Nullable Class[] constructorSignature, @Nullable Object[] constructorArgs)
      throws ClassNotFoundException {
    myUsed = true;
    return myClassLoader.loadClass(name, constructorSignature, constructorArgs);
  }

  @Override
  public boolean supports(int ideFeature) {
    return ideFeature <= Features.LAST_FEATURE;
  }

  /**
   * Returns the namespace for the project. The namespace contains a standard part + the
   * application package.
   *
   * @return The package namespace of the project or null in case of error.
   */
  @Nullable
  @Override
  public String getNamespace() {
    return myNamespace;
  }

  @SuppressWarnings({"UnnecessaryFullyQualifiedName", "deprecation"}) // Required by IProjectCallback
  @Nullable
  @Override
  public com.android.util.Pair<ResourceType, String> resolveResourceId(int id) {
    return myProjectRes.resolveResourceId(id);
  }

  @Nullable
  @Override
  public String resolveResourceId(int[] id) {
    return myProjectRes.resolveStyleable(id);
  }

  @Nullable
  @Override
  public Integer getResourceId(ResourceType type, String name) {
    return myProjectRes.getResourceId(type, name);
  }

  /**
   * Returns whether the loader has received requests to load custom views. Note that
   * the custom view loading may not actually have succeeded; this flag only records
   * whether it was <b>requested</b>.
   * <p/>
   * This allows to efficiently only recreate when needed upon code change in the
   * project.
   *
   * @return true if the loader has been asked to load custom views
   */
  public boolean isUsed() {
    return myUsed;
  }

  @Nullable
  private static XmlPullParser getParserFromText(@NotNull ParserFactory factory, String fileName, @NotNull String text) {
    try {
      XmlPullParser parser = factory.createParser(fileName);
      parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      parser.setInput(new StringReader(text));
      return parser;
    }
    catch (XmlPullParserException e) {
      LOG.warn("Could not create parser for " + fileName);
    }

    return null;
  }

  @Nullable
  @Override
  public XmlPullParser getXmlFileParser(String fileName) {
    // No need to generate a PSI-based parser (which can read edited/unsaved contents) for files in build outputs or
    // layoutlib built-in directories
    if (fileName.contains(FilenameConstants.EXPLODED_AAR) || fileName.contains(FD_LAYOUTLIB) || fileName.contains(BUILD_CACHE)) {
      return null;
    }

    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      if (virtualFile != null) {
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myModule.getProject(), virtualFile);
        if (psiFile != null) {
          ResourceValue resourceValue = myFontFamilies.get(fileName);
          if (resourceValue != null) {
            // This is a font-family XML. Now check if it defines a downloadable font. If it is, this is a special case where we generate
            // a synthetic font-family XML file that points to the cached fonts downloaded by the DownloadableFontCacheService
            if (myProjectFonts == null && myResourceResolver != null) {
              myProjectFonts = new ProjectFonts(myResourceResolver);
            }

            if (myProjectFonts != null) {
              FontFamily family = myProjectFonts.getFont(resourceValue.getResourceUrl().toString());
              String fontFamilyXml = myFontCacheService.toXml(family);
              if (fontFamilyXml == null) {
                myFontCacheService.download(family);
                return null;
              }

              return getParserFromText(getParserFactory(), fileName, fontFamilyXml);
            }
          }

          String psiText = ApplicationManager.getApplication().isReadAccessAllowed()
                           ? psiFile.getText()
                           : ApplicationManager.getApplication().runReadAction((Computable<String>)psiFile::getText);
          return getParserFromText(getParserFactory(), fileName, psiText);
        }
      }
      return null;
    }
    finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }

  public void setLayoutParser(@Nullable String layoutName, @Nullable ILayoutPullParser layoutParser) {
    myLayoutName = layoutName;
    myLayoutEmbeddedParser = layoutParser;
  }

  public void setAaptDeclaredResources(@NotNull Map<String, TagSnapshot> resources) {
    myAaptDeclaredResources = ImmutableMap.copyOf(resources);
  }

  @Nullable
  public ILayoutPullParser getLayoutEmbeddedParser() {
    return myLayoutEmbeddedParser;
  }

  @SuppressWarnings("deprecation") // Required by IProjectCallback
  @Nullable
  @Override
  public ILayoutPullParser getParser(@NotNull String layoutName) {
    // Try to compute the ResourceValue for this layout since layoutlib
    // must be an older version which doesn't pass the value:
    if (myResourceResolver != null) {
      ResourceValue value = myResourceResolver.getProjectResource(ResourceType.LAYOUT, layoutName);
      if (value != null) {
        return getParser(value);
      }
    }

    return getParser(layoutName, false, null);
  }

  @Nullable
  @Override
  public ILayoutPullParser getParser(@NotNull ResourceValue layoutResource) {
    String value = layoutResource.getValue();
    ILayoutPullParser parser;
    if (value != null && !myAaptDeclaredResources.isEmpty() && value.startsWith(AAPT_ATTR_PREFIX)) {
      TagSnapshot aaptResource = myAaptDeclaredResources.get(StringUtil.trimStart(layoutResource.getValue(), AAPT_ATTR_PREFIX));
      parser = LayoutPsiPullParser.create(aaptResource, myLogger);
    }
    else {
      parser = getParser(layoutResource.getName(), layoutResource.isFramework(), new File(layoutResource.getValue()));
    }

    if (parser instanceof AaptAttrParser) {
      ImmutableMap<String, TagSnapshot> declared = ((AaptAttrParser)parser).getAaptDeclaredAttrs();

      if (!declared.isEmpty()) {
        // For parser of elements included in this parser, publish any aapt declared values
        myAaptDeclaredResources = ImmutableMap.<String, TagSnapshot>builder()
          .putAll(((AaptAttrParser)parser).getAaptDeclaredAttrs())
          .putAll(myAaptDeclaredResources)
          .build();
      }
    }

    return parser;
  }

  @Nullable
  private ILayoutPullParser getParser(@NotNull String layoutName, boolean isFramework, @Nullable File xml) {
    if (myParserFiles != null && myParserFiles.contains(xml)) {
      if (myParserCount > MAX_PARSER_INCLUDES) {
        // Unlikely large number of includes. Look for cyclic dependencies in the available files.
        if (findCycles()) {
          throw new RuntimeException("Aborting rendering");
        }

        // Also reset counter to 0 so we don't check on every subsequent iteration.
        myParserCount = 0;
      }
    } else {
      if (myParserFiles == null) {
        myParserFiles = new HashSet<>();
      }
      myParserFiles.add(xml);
    }
    myParserCount++;

    if (myLayoutPullParserFactory != null) {
      ILayoutPullParser parser = myLayoutPullParserFactory.create(xml, this);
      if (parser != null) {
        return parser;
      }
    }

    if (layoutName.equals(myLayoutName) && !isFramework) {
      ILayoutPullParser parser = myLayoutEmbeddedParser;
      // The parser should only be used once!! If it is included more than once,
      // subsequent includes should just use a plain pull parser that is not tied
      // to the XML model
      myLayoutEmbeddedParser = null;
      return parser;
    }

    // See if we can find a corresponding PSI file for this included layout, and
    // if so directly reuse the PSI parser, such that we pick up the live, edited
    // contents rather than the most recently saved file contents.
    if (xml != null && xml.isFile()) {
      File parent = xml.getParentFile();
      String path = xml.getPath();
      // No need to generate a PSI-based parser (which can read edited/unsaved contents) for files in build outputs or
      // layoutlib built-in directories
      if (parent != null && !path.contains(FilenameConstants.EXPLODED_AAR) && !path.contains(FD_LAYOUTLIB) && !path.contains(BUILD_CACHE)) {
        String parentName = parent.getName();
        if (parentName.startsWith(FD_RES_LAYOUT) || parentName.startsWith(FD_RES_DRAWABLE) || parentName.startsWith(FD_RES_MENU)) {
          VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(xml);
          if (file != null) {
            PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myModule.getProject(), file);
            if (psiFile instanceof XmlFile) {
              // Do not honor the merge tag for layouts that are inflated via this call. This is just being inflated as part of a different
              // layout so we already have a parent.
              LayoutPsiPullParser parser = LayoutPsiPullParser.create((XmlFile)psiFile, myLogger, false);
              parser.setUseSrcCompat(myHasAppCompat);
              if (parentName.startsWith(FD_RES_LAYOUT)) {
                // For included layouts, we don't normally see view cookies; we want the leaf to point back to the include tag
                parser.setProvideViewCookies(myRenderTask != null && myRenderTask.getProvideCookiesForIncludedViews());
              }
              return parser;
            }
          }
        }
      }

      // For included layouts, create a LayoutFilePullParser such that we get the
      // layout editor behavior in included layouts as well - which for example
      // replaces <fragment> tags with <include>.
      try {
        return LayoutFilePullParser.create(this, xml);
      }
      catch (XmlPullParserException e) {
        LOG.error(e);
      }
      catch (FileNotFoundException e) {
        // Shouldn't happen since we check isFile() above
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }

    return null;
  }

  /**
   * Searches for cycles in the {@code <include>} tag graph of the layout files we've
   * been asked to provide parsers for
   */
  private boolean findCycles() {
    Map<File, String> fileToLayout = new HashMap<>();
    Map<String, File> layoutToFile = new HashMap<>();
    Multimap<String, String> includeMap = ArrayListMultimap.create();
    for (File file : myParserFiles) {
      String layoutName = LintUtils.getLayoutName(file);
      layoutToFile.put(layoutName, file);
      fileToLayout.put(file, layoutName);
      try {
        String xml = Files.toString(file, Charsets.UTF_8);
        Document document = XmlUtils.parseDocumentSilently(xml, true);
        if (document != null) {
          NodeList includeNodeList = document.getElementsByTagName(VIEW_INCLUDE);
          for (int i = 0, n = includeNodeList.getLength(); i < n; i++) {
            Element include = (Element)includeNodeList.item(i);
            String included = include.getAttribute(ATTR_LAYOUT);
            if (included.startsWith(LAYOUT_RESOURCE_PREFIX)) {
              String resource = included.substring(LAYOUT_RESOURCE_PREFIX.length());
              includeMap.put(layoutName, resource);
            }
          }

          // Deals with tools:layout attribute from fragments
          NodeList fragmentNodeList = document.getElementsByTagName(VIEW_FRAGMENT);
          for (int i = 0, n = fragmentNodeList.getLength(); i < n; i++) {
            Element fragment = (Element)fragmentNodeList.item(i);
            String included = fragment.getAttributeNS(TOOLS_URI, ATTR_LAYOUT);
            if (included.startsWith(LAYOUT_RESOURCE_PREFIX)) {
              String resource = included.substring(LAYOUT_RESOURCE_PREFIX.length());
              includeMap.put(layoutName, resource);
            }
          }
        }
      }
      catch (IOException e) {
        LOG.warn("Could not check file " + file + " for cyclic dependencies", e);
      }
    }

    // We now have a DAG over the include dependencies in the layouts
    // Do a DFS to detect cycles

    // Perform DFS on the include graph and look for a cycle; if we find one, produce
    // a chain of includes on the way back to show to the user
    if (!includeMap.isEmpty()) {
      for (String from : includeMap.keySet()) {
        Set<String> visiting = Sets.newHashSetWithExpectedSize(includeMap.size());
        List<String> chain = dfs(from, visiting, includeMap);
        if (chain != null) {
          RenderProblem.Html problem = RenderProblem.create(WARNING);
          HtmlBuilder builder = problem.getHtmlBuilder();
          builder.add("Found cyclical <include> chain: ");
          boolean first = true;
          Collections.reverse(chain);
          for (String layout : chain) {
            if (first) {
              first = false;
            } else {
              builder.add(" includes ");
            }
            File file = layoutToFile.get(layout);
            if (file != null) {
              try {
                String url = SdkUtils.fileToUrlString(file);
                builder.addLink(layout, url);
              }
              catch (MalformedURLException e) {
                builder.add(layout);
              }
            } else {
              builder.add(layout);
            }
          }

          myLogger.addMessage(problem);
          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static List<String> dfs(String from, Set<String> visiting, Multimap<String,String> includeMap) {
    visiting.add(from);
    Collection<String> includes = includeMap.get(from);
    if (includes != null && !includes.isEmpty()) {
      for (String include : includes) {
        if (visiting.contains(include)) {
          List<String> list = Lists.newLinkedList();
          list.add(include);
          list.add(from);
          return list;
        }
        List<String> chain = dfs(include, visiting, includeMap);
        if (chain != null) {
          chain.add(from);
          return chain;
        }
      }
    }
    visiting.remove(from);
    return null;
  }

  @Nullable
  @Override
  public Object getAdapterItemValue(ResourceReference adapterView,
                                    Object adapterCookie,
                                    ResourceReference itemRef,
                                    int fullPosition,
                                    int typePosition,
                                    int fullChildPosition,
                                    int typeChildPosition,
                                    ResourceReference viewRef,
                                    ViewAttribute viewAttribute,
                                    Object defaultValue) {

    // Special case for the palette preview
    if (viewAttribute == ViewAttribute.TEXT && adapterView.getName().startsWith("android_widget_")) { //$NON-NLS-1$
      String name = adapterView.getName();
      if (viewRef.getName().equals("text2")) { //$NON-NLS-1$
        return "Sub Item";
      }
      if (fullPosition == 0) {
        String viewName = name.substring("android_widget_".length());
        if (viewName.equals(EXPANDABLE_LIST_VIEW)) {
          return "ExpandableList"; // ExpandableListView is too wide, character-wraps
        }
        return viewName;
      }
      else {
        return "Next Item";
      }
    }

    if (itemRef.isFramework()) {
      // Special case for list_view_item_2 and friends
      if (viewRef.getName().equals("text2")) { //$NON-NLS-1$
        return "Sub Item " + (fullPosition + 1);
      }
    }

    if (viewAttribute == ViewAttribute.TEXT && ((String)defaultValue).isEmpty()) {
      return "Item " + (fullPosition + 1);
    }

    return null;
  }

  /**
   * For the given class, finds and returns the nearest super class which is a ListView
   * or an ExpandableListView or a GridView (which uses a list adapter), or returns null.
   *
   * @param clz the class of the view object
   * @return the fully qualified class name of the list ancestor, or null if there
   *         is no list view ancestor
   */
  @Nullable
  public static String getListAdapterViewFqcn(@NotNull Class<?> clz) {
    String fqcn = clz.getName();
    if (fqcn.endsWith(LIST_VIEW)  // including EXPANDABLE_LIST_VIEW
        || fqcn.equals(FQCN_GRID_VIEW) || fqcn.equals(FQCN_SPINNER)) {
      return fqcn;
    }
    else if (fqcn.startsWith(ANDROID_PKG_PREFIX)) {
      return null;
    }
    Class<?> superClass = clz.getSuperclass();
    if (superClass != null) {
      return getListAdapterViewFqcn(superClass);
    }
    else {
      // Should not happen; we would have encountered android.view.View first,
      // and it should have been covered by the ANDROID_PKG_PREFIX case above.
      return null;
    }
  }

  /**
   * Looks at the parent-chain of the view and if it finds a custom view, or a
   * CalendarView, within the given distance then it returns true. A ListView within a
   * CalendarView should not be assigned a custom list view type because it sets its own
   * and then attempts to cast the layout to its own type which would fail if the normal
   * default list item binding is used.
   */
  private boolean isWithinIllegalParent(@NotNull Object viewObject, int depth) {
    String fqcn = viewObject.getClass().getName();
    if (fqcn.endsWith(CALENDAR_VIEW) || !(fqcn.startsWith(ANDROID_PKG_PREFIX)
                                            // ActionBar at the root level
                                          || fqcn.startsWith("com.android.internal.widget."))) {
      return true;
    }

    if (depth > 0) {
      Result result = myLayoutLib.getViewParent(viewObject);
      if (result.isSuccess()) {
        Object parent = result.getData();
        if (parent != null) {
          return isWithinIllegalParent(parent, depth - 1);
        }
      }
    }

    return false;
  }

  @Nullable
  @Override
  public AdapterBinding getAdapterBinding(final ResourceReference adapterView, final Object adapterCookie, final Object viewObject) {
    // Look for user-recorded preference for layout to be used for previews
    if (adapterCookie instanceof TagSnapshot) {
      AdapterBinding binding = LayoutMetadata.getNodeBinding(viewObject, (TagSnapshot)adapterCookie);
      if (binding != null) {
        return binding;
      }
    }
    else if (adapterCookie instanceof XmlTag) {
      AdapterBinding binding =
        LayoutMetadata.getNodeBinding(viewObject, TagSnapshot.createTagSnapshotWithoutChildren((XmlTag)adapterCookie));
      if (binding != null) {
        return binding;
      }
    }
    else if (adapterCookie instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked") Map<String, String> map = (Map<String, String>)adapterCookie;
      AdapterBinding binding = LayoutMetadata.getNodeBinding(viewObject, map);
      if (binding != null) {
        return binding;
      }
    }

    if (viewObject == null) {
      return null;
    }

    // Is this a ListView or ExpandableListView? If so, return its fully qualified
    // class name, otherwise return null. This is used to filter out other types
    // of AdapterViews (such as Spinners) where we don't want to use the list item
    // binding.
    String listFqcn = getListAdapterViewFqcn(viewObject.getClass());
    if (listFqcn == null) {
      return null;
    }

    // Is this ListView nested within an "illegal" container, such as a CalendarView?
    // If so, don't change the bindings below. Some views, such as CalendarView, and
    // potentially some custom views, might be doing specific things with the ListView
    // that could break if we add our own list binding, so for these leave the list
    // alone.
    if (isWithinIllegalParent(viewObject, 2)) {
      return null;
    }

    int count = listFqcn.endsWith(GRID_VIEW) ? 24 : 12;
    AdapterBinding binding = new AdapterBinding(count);
    if (listFqcn.endsWith(EXPANDABLE_LIST_VIEW)) {
      binding.addItem(new DataBindingItem(LayoutMetadata.DEFAULT_EXPANDABLE_LIST_ITEM, true /* isFramework */, 1));
    }
    else if (listFqcn.equals(FQCN_SPINNER)) {
      binding.addItem(new DataBindingItem(LayoutMetadata.DEFAULT_SPINNER_ITEM, true /* isFramework */, 1));
    }
    else {
      binding.addItem(new DataBindingItem(LayoutMetadata.DEFAULT_LIST_ITEM, true /* isFramework */, 1));
    }

    return binding;
  }

  /**
   * Sets the {@link ResourceResolver} to be used when looking up resources
   *
   * @param resolver the resolver to use
   */
  public void setResourceResolver(@Nullable ResourceResolver resolver) {
    myResourceResolver = resolver;
  }

  /**
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly
   */
  public void loadAndParseRClass() {
    myClassLoader.loadAndParseRClassSilently();
  }

  @Override
  public ActionBarCallback getActionBarCallback() {
    return myActionBarHandler;
  }

  @Nullable
  public ActionBarHandler getActionBarHandler() {
    return myActionBarHandler;
  }

  @Nullable
  @Override
  @SuppressWarnings("unchecked")
  public <T> T getFlag(@NotNull SessionParams.Key<T> key) {
    if (key.equals(FLAG_KEY_APPLICATION_PACKAGE)) {
      return (T)getPackage();
    }
    if (key.equals(FLAG_KEY_RECYCLER_VIEW_SUPPORT)) {
      return (T)Boolean.TRUE;
    }
    if (key.equals(FLAG_KEY_XML_FILE_PARSER_SUPPORT)) {
      return (T)Boolean.TRUE;
    }
    if (key.equals(FLAG_KEY_ADAPTIVE_ICON_MASK_PATH)) {
      return (T)myAdaptiveIconMaskPath;
    }
    return null;
  }

  @Nullable
  private String getPackage() {
    AndroidModuleInfo info = AndroidModuleInfo.getInstance(myModule);
    return info == null ? null : info.getPackage();
  }

  @NotNull
  @Override
  public ParserFactory getParserFactory() {
    if (myParserFactory == null) {
      myParserFactory = new ParserFactoryImpl();
    }
    return myParserFactory;
  }

  @NotNull
  @Override
  public Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
    try {
      Class<?> aClass = myClassLoader.loadClass(name, false);
      if (aClass != null) {
        return aClass;
      }
      throw new ClassNotFoundException(name + " not found.");
    }
    catch (InconvertibleClassError e) {
      throw new ClassNotFoundException(name + " not found.", e);
    }

  }

  public void setAdaptiveIconMaskPath(@NotNull String adaptiveIconMaskPath) {
    myAdaptiveIconMaskPath = adaptiveIconMaskPath;
  }

  private static class ParserFactoryImpl extends ParserFactory {
    @NotNull
    @Override
    public XmlPullParser createParser(@Nullable String debugName) throws XmlPullParserException {
      return new NamedParser(debugName);
    }
  }

  private static class NamedParser extends KXmlParser {
    @Nullable
    private final String myName;
    /**
     * Attribute that caches whether the tools prefix has been defined or not. This allow us to save unnecessary checks in the most common
     * case ("tools" is not defined).
     */
    private boolean hasToolsNamespace;

    public NamedParser(@Nullable String name) {
      myName = name;
    }

    @Override
    public int next() throws XmlPullParserException, IOException {
      int tagType = super.next();

      // We check if the tools namespace is still defined in two cases:
      // - If it's a start tag and it was defined by a previous tag
      // - If it WAS defined by a previous tag, and we are closing a tag (going out of scope)
      if ((!hasToolsNamespace && tagType == XmlPullParser.START_TAG) ||
          (hasToolsNamespace && tagType == XmlPullParser.END_TAG)) {
        hasToolsNamespace = getNamespace("tools") != null;
      }

      return tagType;
    }

    @Override
    public String getAttributeValue(String namespace, String name) {
      if (hasToolsNamespace && ANDROID_URI.equals(namespace)) {
        // Only for "android:" attribute, we will check if there is a "tools:" version overriding the value
        String toolsValue = super.getAttributeValue(TOOLS_URI, name);
        if (toolsValue != null) {
          return toolsValue;
        }
      }

      return super.getAttributeValue(namespace, name);
    }

    @Override
    public String toString() {
      return myName != null ? myName : super.toString();
    }
  }
}
