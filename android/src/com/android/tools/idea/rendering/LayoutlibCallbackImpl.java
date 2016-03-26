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

import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.RenderSecurityManager;
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.res.AppResourceRepository;
import com.android.tools.idea.res.LocalResourceRepository;
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
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.manifest.Manifest;
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

import static com.android.SdkConstants.*;
import static com.android.ide.common.rendering.RenderParamsFlags.FLAG_KEY_APPLICATION_PACKAGE;
import static com.android.ide.common.rendering.RenderParamsFlags.FLAG_KEY_RECYCLER_VIEW_SUPPORT;
import static com.android.ide.common.rendering.RenderParamsFlags.FLAG_KEY_XML_FILE_PARSER_SUPPORT;
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
  private static final Set<String> NOT_VIEW = Collections.unmodifiableSet(Sets.newHashSet(RecyclerViewHelper.CN_RV_ADAPTER,
                                                                                          RecyclerViewHelper.CN_RV_LAYOUT_MANAGER,
                                                                                          "android.support.v7.internal.app.WindowDecorActionBar"));

  @NotNull private final Module myModule;
  @NotNull private final AppResourceRepository myProjectRes;
  @NotNull final private LayoutLibrary myLayoutLib;
  @Nullable private final Object myCredential;
  private final boolean myHasAppCompat;
  @Nullable private String myNamespace;
  @Nullable private RenderLogger myLogger;
  @NotNull private final ViewLoader myClassLoader;
  @Nullable private String myLayoutName;
  @Nullable private ILayoutPullParser myLayoutEmbeddedParser;
  @Nullable private ResourceResolver myResourceResolver;
  @Nullable private final ActionBarHandler myActionBarHandler;
  @Nullable private final RenderTask myRenderTask;
  private boolean myUsed = false;
  private Set<File> myParserFiles;
  private int myParserCount;
  private ParserFactory myParserFactory;

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
   */
  public LayoutlibCallbackImpl(@Nullable RenderTask renderTask,
                               @NotNull LayoutLibrary layoutLib,
                               @NotNull AppResourceRepository projectRes,
                               @NotNull Module module,
                               @NotNull AndroidFacet facet,
                               @NotNull RenderLogger logger,
                               @Nullable Object credential,
                               @Nullable ActionBarHandler actionBarHandler) {
    myRenderTask = renderTask;
    myLayoutLib = layoutLib;
    myProjectRes = projectRes;
    myModule = module;
    myCredential = credential;
    myClassLoader = new ViewLoader(myLayoutLib, facet, logger, credential);
    myActionBarHandler = actionBarHandler;

    AndroidGradleModel androidModel = AndroidGradleModel.get(facet);
    myHasAppCompat = androidModel != null && GradleUtil.dependsOn(androidModel, APPCOMPAT_LIB_ARTIFACT);
  }

  /** Resets the callback state for another render */
  void reset() {
    myParserCount = 0;
    myParserFiles = null;
    myLayoutName = null;
    myLayoutEmbeddedParser = null;
  }

  /**
   * Sets the {@link LayoutLog} logger to use for error messages during problems
   *
   * @param logger the new logger to use, or null to clear it out
   */
  public void setLogger(@Nullable RenderLogger logger) {
    myLogger = logger;
    myClassLoader.setLogger(logger);
  }

  /**
   * Returns the {@link LayoutLog} logger used for error messages, or null
   *
   * @return the logger being used, or null if no logger is in use
   */
  @Nullable
  public LayoutLog getLogger() {
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
  // TODO: Remove
  @Override
  public String getNamespace() {
    // TODO: use
    //     final String namespace = AndroidXmlSchemaProvider.getLocalXmlNamespace(facet);
    // instead!
    if (myNamespace == null) {
      String javaPackage = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Nullable
        @Override
        public String compute() {
          final AndroidFacet facet = AndroidFacet.getInstance(myModule);
          if (facet == null) {
            return null;
          }

          final Manifest manifest = facet.getManifest();
          if (manifest == null) {
            return null;
          }

          return manifest.getPackage().getValue();
        }
      });

      if (javaPackage != null) {
        myNamespace = String.format(NS_CUSTOM_RESOURCES_S, javaPackage);
      }
    }

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
  @Override
  public XmlPullParser getXmlFileParser(String fileName) {
    boolean token = RenderSecurityManager.enterSafeRegion(myCredential);
    try {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName);
      if (virtualFile != null) {
        PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myModule.getProject(), virtualFile);
        if (psiFile != null) {
          try {
            XmlPullParser parser = getParserFactory().createParser(fileName);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(new StringReader(psiFile.getText()));
            return parser;
          }
          catch (XmlPullParserException e) {
            LOG.warn("Could not create parser for " + fileName);
          }
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
    return getParser(layoutResource.getName(), layoutResource.isFramework(), new File(layoutResource.getValue()));
  }

  @Nullable
  private ILayoutPullParser getParser(@NotNull String layoutName, boolean isFramework, @Nullable File xml) {
    if (myParserFiles != null && myParserFiles.contains(xml)) {
      if (myParserCount > MAX_PARSER_INCLUDES) {
        // Unlikely large number of includes. Look for cyclic dependencies in the available
        // files.
        if (findCycles()) {
          throw new RuntimeException("Aborting rendering");
        }

        // Also reset counter to 0 so we don't check on every subsequent iteration.
        myParserCount = 0;
      }
    } else {
      if (myParserFiles == null) {
        myParserFiles = Sets.newHashSet();
      }
      myParserFiles.add(xml);
    }
    myParserCount++;

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
      if (parent != null) {
        String parentName = parent.getName();
        if (parentName.startsWith(FD_RES_LAYOUT) || parentName.startsWith(FD_RES_MENU)) {
          VirtualFile file = LocalFileSystem.getInstance().findFileByIoFile(xml);
          if (file != null) {
            PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myModule.getProject(), file);
            if (psiFile instanceof XmlFile) {
              assert myLogger != null;
              LayoutPsiPullParser parser = LayoutPsiPullParser.create((XmlFile)psiFile, myLogger);
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
    Map<File,String> fileToLayout = Maps.newHashMap();
    Map<String,File> layoutToFile = Maps.newHashMap();
    Multimap<String,String> includeMap = ArrayListMultimap.create();
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
    if (includeMap.size() > 0) {
      for (String from : includeMap.keySet()) {
        Set<String> visiting = Sets.newHashSetWithExpectedSize(includeMap.size());
        List<String> chain = dfs(from, visiting, includeMap);
        if (chain != null) {
          if (myLogger != null) {
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
          }
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
    if (includes != null && includes.size() > 0) {
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

    if (viewAttribute == ViewAttribute.TEXT && ((String)defaultValue).length() == 0) {
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
    if (adapterCookie instanceof XmlTag) {
      XmlTag uiNode = (XmlTag)adapterCookie;
      AdapterBinding binding = LayoutMetadata.getNodeBinding(viewObject, uiNode);
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
    return null;
  }

  @Nullable
  private String getPackage() {
    AndroidModuleInfo info = AndroidModuleInfo.get(myModule);
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

    public NamedParser(@Nullable String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return myName != null ? myName : super.toString();
    }
  }
}
