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

import static com.android.SdkConstants.ANDROIDX_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_PKG_PREFIX;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.CALENDAR_VIEW;
import static com.android.AndroidXConstants.CLASS_RECYCLER_VIEW_ADAPTER;
import static com.android.AndroidXConstants.CLASS_RECYCLER_VIEW_LAYOUT_MANAGER;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.EXPANDABLE_LIST_VIEW;
import static com.android.SdkConstants.FD_RES_DRAWABLE;
import static com.android.SdkConstants.FD_RES_LAYOUT;
import static com.android.SdkConstants.FD_RES_MENU;
import static com.android.SdkConstants.FQCN_GRID_VIEW;
import static com.android.SdkConstants.FQCN_SPINNER;
import static com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.LAYOUT_RESOURCE_PREFIX;
import static com.android.SdkConstants.LIST_VIEW;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.intellij.lang.annotation.HighlightSeverity.WARNING;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.annotations.NonNull;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.rendering.api.ActionBarCallback;
import com.android.ide.common.rendering.api.AdapterBinding;
import com.android.ide.common.rendering.api.DataBindingItem;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutlibCallback;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.Result;
import com.android.ide.common.resources.ProtoXmlPullParser;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceVisitor;
import com.android.ide.common.resources.ResourcesUtil;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.support.AndroidxName;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.fonts.DownloadableFontCacheService;
import com.android.tools.idea.fonts.ProjectFonts;
import com.android.tools.idea.layoutlib.LayoutLibrary;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.Namespacing;
import com.android.tools.idea.projectsystem.FilenameConstants;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.parsers.AaptAttrParser;
import com.android.tools.idea.rendering.parsers.ILayoutPullParserFactory;
import com.android.tools.idea.rendering.parsers.LayoutFilePullParser;
import com.android.tools.idea.rendering.parsers.LayoutPsiPullParser;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.res.FileResourceReader;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceIdManager;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.util.DependencyManagementUtil;
import com.android.tools.idea.util.FileExtensions;
import com.android.tools.lint.detector.api.Lint;
import com.android.utils.HtmlBuilder;
import com.android.utils.SdkUtils;
import com.android.utils.XmlUtils;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.uipreview.ViewLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kxml2.io.KXmlParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * Loader for Android Project class in order to use them in the layout editor.
 */
public class LayoutlibCallbackImpl extends LayoutlibCallback {
  private static final Logger LOG = Logger.getInstance("#com.android.tools.idea.rendering.LayoutlibCallback");

  /** Maximum number of getParser calls in a render before we suspect and investigate potential include cycles */
  private static final int MAX_PARSER_INCLUDES = 50;
  private static final AndroidxName CLASS_WINDOR_DECOR_ACTION_BAR =
    new AndroidxName("android.support.v7.internal.app.WindowDecorActionBar", "androidx.appcompat.internal.app.WindowDecorActionBar");
  /** Class names that are not a view. When instantiating them, errors should be logged by LayoutLib. */
  private static final Set<String> NOT_VIEW = ImmutableSet.of(CLASS_RECYCLER_VIEW_ADAPTER.oldName(),
                                                              CLASS_RECYCLER_VIEW_ADAPTER.newName(),
                                                              CLASS_RECYCLER_VIEW_LAYOUT_MANAGER.oldName(),
                                                              CLASS_RECYCLER_VIEW_LAYOUT_MANAGER.newName(),
                                                              CLASS_WINDOR_DECOR_ACTION_BAR.oldName(),
                                                              CLASS_WINDOR_DECOR_ACTION_BAR.newName());
  /** Directory name for the bundled layoutlib installation */
  public static final String FD_LAYOUTLIB = "layoutlib";
  /** Directory name for the gradle build-cache. Exploded AARs will end up there when using build cache */
  public static final String BUILD_CACHE = "build-cache";

  @NotNull private final AndroidFacet myFacet;
  @NotNull private final Module myModule;
  @NotNull private final ResourceIdManager myIdManager;
  @NotNull final private LayoutLibrary myLayoutLib;
  @Nullable private final Object myCredential;
  private final boolean myHasLegacyAppCompat;
  private final boolean myHasAndroidXAppCompat;
  private final Namespacing myNamespacing;
  @NotNull private IRenderLogger myLogger;
  @NotNull private final ViewLoader myClassLoader;
  @Nullable private String myLayoutName;
  @Nullable private ILayoutPullParser myLayoutEmbeddedParser;
  @Nullable private final ActionBarHandler myActionBarHandler;
  @NotNull private final RenderTask myRenderTask;
  @NotNull private final DownloadableFontCacheService myFontCacheService;
  private boolean myUsed;
  private Set<PathString> myParserFiles;
  private int myParserCount;
  @NotNull public ImmutableMap<String, TagSnapshot> myAaptDeclaredResources = ImmutableMap.of();
  private final Map<String, ResourceValue> myFontFamilies;
  private ProjectFonts myProjectFonts;
  private String myAdaptiveIconMaskPath;
  @Nullable private final ILayoutPullParserFactory myLayoutPullParserFactory;
  @NotNull private final ResourceNamespace.Resolver myImplicitNamespaces;
  /**
   * This stores the current sample data offset for sample data to use when parsing a given layout.
   * Each time a layout that contains references to sample data is parsed, we want to use a new sample,
   * but in a way that keeps all the elements inside that layout consistent.
   * Using this counter as a base index, all sample data inside a given layout can keep in sync.
   * Increasing the counter for each parsing of a given layout ensures that, if a layout is used several times
   * (e.g. as an item in a recycler view), each version will use different elements from the sample data.
   */
  private final Map<String, AtomicInteger> myLayoutCounterForSampleData = new HashMap<>();

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
   * @param moduleClassLoader the {@link ClassLoader} to use for loading classes from Layoutlib.
   */
  public LayoutlibCallbackImpl(@NotNull RenderTask renderTask,
                               @NotNull LayoutLibrary layoutLib,
                               @NotNull LocalResourceRepository projectRes,
                               @NotNull Module module,
                               @NotNull AndroidFacet facet,
                               @NotNull IRenderLogger logger,
                               @Nullable Object credential,
                               @Nullable ActionBarHandler actionBarHandler,
                               @Nullable ILayoutPullParserFactory parserFactory,
                               @NotNull ClassLoader moduleClassLoader) {
    myRenderTask = renderTask;
    myLayoutLib = layoutLib;
    myIdManager = ResourceIdManager.get(module);
    myFacet = facet;
    myModule = module;
    myLogger = logger;
    myCredential = credential;
    myClassLoader = new ViewLoader(myLayoutLib, facet, logger, credential, moduleClassLoader);
    myActionBarHandler = actionBarHandler;
    myLayoutPullParserFactory = parserFactory;
    myHasLegacyAppCompat = DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.APP_COMPAT_V7);
    myHasAndroidXAppCompat = DependencyManagementUtil.dependsOn(module, GoogleMavenArtifactId.ANDROIDX_APP_COMPAT_V7);

    myNamespacing = ResourceRepositoryManager.getInstance(facet).getNamespacing();
    if (myNamespacing == Namespacing.DISABLED) {
      myImplicitNamespaces = ResourceNamespace.Resolver.TOOLS_ONLY;
    } else {
      myImplicitNamespaces = ResourceNamespace.Resolver.EMPTY_RESOLVER;
    }

    myFontCacheService = DownloadableFontCacheService.getInstance();
    ImmutableMap.Builder<String, ResourceValue> fontBuilder = ImmutableMap.builder();
    projectRes.accept(
        new ResourceVisitor() {
          @Override
          @NotNull
          public VisitResult visit(@NotNull ResourceItem resourceItem) {
            ResourceValue resourceValue = resourceItem.getResourceValue();
            if (resourceValue != null) {
              String rawXml = resourceValue.getRawXmlValue();
              if (rawXml != null && rawXml.endsWith(DOT_XML)) {
                fontBuilder.put(rawXml, resourceValue);
              }
            }
            return VisitResult.CONTINUE;
          }

          @Override
          public boolean shouldVisitResourceType(@NotNull ResourceType resourceType) {
            return resourceType == ResourceType.FONT;
          }
        });
    myFontFamilies = fontBuilder.build();
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
   * Sets the {@link ILayoutLog} logger to use for error messages during problems.
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
  @Override
  @Nullable
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
  @Nullable
  public ResourceReference resolveResourceId(int id) {
    return myIdManager.findById(id);
  }

  @Override
  public int getOrGenerateResourceId(@NotNull ResourceReference resource) {
    return myIdManager.getOrGenerateId(resource);
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
  private static XmlPullParser getParserFromText(String fileName, @NotNull String text) {
    try {
      XmlPullParser parser = new NamedXmlParser(fileName);
      parser.setInput(new StringReader(text));
      return parser;
    }
    catch (XmlPullParserException e) {
      LOG.warn("Could not create parser for " + fileName);
    }

    return null;
  }

  @Override
  @Nullable
  public XmlPullParser createXmlParserForPsiFile(@NotNull String fileName) {
    // No need to generate a PSI-based parser (which can read edited/unsaved contents) for files
    // in build outputs or layoutlib built-in directories.
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
            // This is a font-family XML. Now check if it defines a downloadable font. If it is,
            // this is a special case where we generate a synthetic font-family XML file that points
            // to the cached fonts downloaded by the DownloadableFontCacheService.
            if (myProjectFonts == null) {
              myProjectFonts = new ProjectFonts(myFacet);
            }

            FontFamily family = myProjectFonts.getFont(resourceValue.getResourceUrl().toString());
            String fontFamilyXml = myFontCacheService.toXml(family);
            if (fontFamilyXml == null) {
              return null;
            }

            return getParserFromText(fileName, fontFamilyXml);
          }

          String psiText = ApplicationManager.getApplication().isReadAccessAllowed()
                           ? psiFile.getText()
                           : ApplicationManager.getApplication().runReadAction((Computable<String>)psiFile::getText);
          return getParserFromText(fileName, psiText);
        }
      }
      return null;
    }
    finally {
      RenderSecurityManager.exitSafeRegion(token);
    }
  }

  @Override
  @Nullable
  public XmlPullParser createXmlParserForFile(@NotNull String fileName) {
    try {
      ByteArrayInputStream stream = new ByteArrayInputStream(FileResourceReader.readBytes(fileName));
      // Instantiate an XML pull parser based on the contents of the stream.
      XmlPullParser parser;
      if (XmlUtils.isProtoXml(stream)) {
        parser = new NamedProtoXmlParser(fileName); // Parser for proto XML used in AARs.
      } else {
        parser = new NamedXmlParser(fileName); // Parser for regular text XML.
      }
      parser.setInput(stream, null);
      return parser;
    } catch (IOException | XmlPullParserException e) {
      return null;
    }
  }

  @Override
  @NotNull
  public XmlPullParser createXmlParser() {
    return new NamedXmlParser(null);
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

  @Override
  @Nullable
  public ILayoutPullParser getParser(@NotNull ResourceValue layoutResource) {
    String value = layoutResource.getValue();
    if (value == null) {
      return null;
    }
    ILayoutPullParser parser;
    if (!myAaptDeclaredResources.isEmpty() && layoutResource.getResourceType() == ResourceType.AAPT) {
      TagSnapshot aaptResource = myAaptDeclaredResources.get(layoutResource.getValue());
      // TODO(namespaces, b/74003372): figure out where to get the namespace from.
      parser = LayoutPsiPullParser.create(aaptResource, ResourceNamespace.TODO(), myLogger);
    }
    else {
      PathString pathString = ResourcesUtil.toFileResourcePathString(value);
      if (pathString == null) {
        return null;
      }
      parser = getParser(layoutResource.getName(), layoutResource.getNamespace(), pathString);
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
  private ILayoutPullParser getParser(@NotNull String layoutName, @NotNull ResourceNamespace namespace, @NotNull PathString xml) {
    if (myParserFiles != null && myParserFiles.contains(xml)) {
      if (myParserCount > MAX_PARSER_INCLUDES) {
        // Unlikely large number of includes. Look for cyclic dependencies in the available files.
        if (findCycles()) {
          throw new RuntimeException(
            String.format("Cycle found (count=%3$d) evaluating '%1$s' with path '%2$s' (parserFiles=%4$s)",
                          layoutName, xml.toDebugString(), myParserCount, StringUtil.join(myParserFiles, ", ")));
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

    if (layoutName.equals(myLayoutName) && namespace != ResourceNamespace.ANDROID) {
      ILayoutPullParser parser = myLayoutEmbeddedParser;
      // The parser should only be used once!! If it is included more than once,
      // subsequent includes should just use a plain pull parser that is not tied
      // to the XML model.
      myLayoutEmbeddedParser = null;
      return parser;
    }

    // See if we can find a corresponding PSI file for this included layout, and
    // if so directly reuse the PSI parser, such that we pick up the live, edited
    // contents rather than the most recently saved file contents.
    if (xml.getFilesystemUri().getScheme().equals("file")) {
      AtomicInteger sampleDataCounter = myLayoutCounterForSampleData.get(layoutName);
      if (sampleDataCounter == null) {
        sampleDataCounter = new AtomicInteger(0);
        myLayoutCounterForSampleData.put(layoutName, sampleDataCounter);
      }

      String parentName = xml.getParentFileName();
      String path = xml.getRawPath();
      // No need to generate a PSI-based parser (which can read edited/unsaved contents) for files in build outputs or
      // layoutlib built-in directories.
      if (parentName != null
          && !path.contains(FilenameConstants.EXPLODED_AAR) && !path.contains(FD_LAYOUTLIB) && !path.contains(BUILD_CACHE)
          && (parentName.startsWith(FD_RES_LAYOUT) || parentName.startsWith(FD_RES_DRAWABLE) || parentName.startsWith(FD_RES_MENU))) {
        VirtualFile file = FileExtensions.toVirtualFile(xml);
        if (file != null) {
          PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myModule.getProject(), file);
          if (psiFile instanceof XmlFile) {
            ResourceResolver resourceResolver = myRenderTask.getContext().getConfiguration().getResourceResolver();
            // Do not honor the merge tag for layouts that are inflated via this call. This is just being inflated as part of a different
            // layout so we already have a parent.
            LayoutPsiPullParser parser =
              LayoutPsiPullParser.create((XmlFile)psiFile, myLogger, false, resourceResolver, sampleDataCounter.getAndIncrement());
            parser.setUseSrcCompat(myHasLegacyAppCompat || myHasAndroidXAppCompat);
            if (parentName.startsWith(FD_RES_LAYOUT)) {
              // For included layouts, we don't normally see view cookies; we want the leaf to point back to the include tag.
              parser.setProvideViewCookies(myRenderTask.getProvideCookiesForIncludedViews());
            }
            return parser;
          }
        }
      }
    }

    // For included layouts, create a LayoutFilePullParser such that we get the
    // layout editor behavior in included layouts as well - which for example
    // replaces <fragment> tags with <include>.
    return LayoutFilePullParser.create(xml, namespace);
  }

  /**
   * Searches for cycles in the {@code <include>} tag graph of the layout files we've
   * been asked to provide parsers for.
   */
  private boolean findCycles() {
    if (myParserFiles.size() == 1) {
      // The file is probably including itself. This can happen when a NavHostFragment is included in an activity that is an entry point
      // in the nav graph.
      return true;
    }

    Map<String, File> layoutToFile = new HashMap<>();
    Multimap<String, String> includeMap = ArrayListMultimap.create();
    for (PathString path : myParserFiles) {
      File file = path.toFile();
      if (file == null || !file.exists()) {
        continue;
      }
      String layoutName = Lint.getLayoutName(file);
      layoutToFile.put(layoutName, file);
      try {
        String xml = Files.toString(file, UTF_8);
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

          // Deals with tools:layout attribute from fragments.
          NodeList fragmentNodeList = document.getElementsByTagName(FRAGMENT_CONTAINER_VIEW);
          if (fragmentNodeList.getLength() == 0) {
            // There was no FragmentContainerView, try with the old <fragment> tag.
            fragmentNodeList = document.getElementsByTagName(VIEW_FRAGMENT);
          }
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

    // We now have a DAG over the include dependencies in the layouts.
    // Do a DFS to detect cycles.

    // Perform DFS on the include graph and look for a cycle; if we find one, produce
    // a chain of includes on the way back to show to the user.
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

  @Override
  @Nullable
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
    // Special case for the palette preview.
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

    if (itemRef.getNamespace() == ResourceNamespace.ANDROID) {
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
    if (fqcn.endsWith(CALENDAR_VIEW) || !(fqcn.startsWith(ANDROID_PKG_PREFIX) || fqcn.startsWith(ANDROIDX_PKG_PREFIX)
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

  @Override
  @Nullable
  public AdapterBinding getAdapterBinding(final Object viewObject, final Map<String, String> attributes) {
    AdapterBinding binding = LayoutMetadata.getNodeBinding(viewObject, attributes);
    if (binding != null) {
      return binding;
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
    binding = new AdapterBinding(count);
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
   * Load and parse the R class such that resource references in the layout rendering can refer
   * to local resources properly.
   *
   * <p>This only needs to be done if the build system compiles code of the given module against R.java files generated with final fields,
   * which will cause the chosen numeric resource ids to be inlined into the consuming code. In this case we treat the R class bytecode as
   * the source of truth for mapping resources to numeric ids.
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
  public String getResourcePackage() {
    return ProjectSystemUtil.getModuleSystem(myModule).getPackageName();
  }

  @Nullable
  public String getApplicationId() {
    try {
      return RenderSecurityManager.runInSafeRegion(myCredential, () -> {
        // This section might access system properties or access disk but it does not leak information back to Layoutlib so it can be
        // executed in safe mode.
        AndroidModuleInfo info = AndroidModuleInfo.getInstance(myModule);
        return info == null ? null : info.getPackage();
      });
    }
    catch (Exception e) {
      LOG.warn(e);
      return null;
    }
  }

  @NotNull
  @Override
  public Class<?> findClass(@NotNull String name) throws ClassNotFoundException {
    Class<?> aClass = myClassLoader.loadClass(name, false);
    if (aClass != null) {
      return aClass;
    }
    throw new ClassNotFoundException(name + " not found.");
  }

  @Override
  public boolean isClassLoaded(@NonNull String name) {
    return myClassLoader.isClassLoaded(name);
  }

  @NotNull
  @Override
  public ResourceNamespace.Resolver getImplicitNamespaces() {
    return myImplicitNamespaces;
  }

  public void setAdaptiveIconMaskPath(@NotNull String adaptiveIconMaskPath) {
    myAdaptiveIconMaskPath = adaptiveIconMaskPath;
  }

  @Override
  public boolean hasLegacyAppCompat() {
    return myHasLegacyAppCompat;
  }

  @Override
  public boolean hasAndroidXAppCompat() {
    return myHasAndroidXAppCompat;
  }

  @Override
  public boolean isResourceNamespacingRequired() {
    return myNamespacing == Namespacing.REQUIRED;
  }

  @Override
  public void error(@NotNull String message, @NotNull String... details) {
    LOG.error(message, details);
  }

  @Override
  public void error(@NotNull String message, @Nullable Throwable t) {
    LOG.error(message, t);
  }

  @Override
  public void error(@NotNull Throwable t) {
    LOG.error(t);
  }

  /**
   * Returns true if the given class has been loaded by the class loader.
   */
  boolean hasLoadedClass(@NotNull String classFqn) {
    return myClassLoader.hasLoadedClass(classFqn);
  }

  private static class NamedXmlParser extends KXmlParser {
    @Nullable
    private final String myName;
    /**
     * Attribute that caches whether the tools prefix has been defined or not. This allows us to save
     * unnecessary checks in the most common case ("tools" is not defined).
     */
    private boolean hasToolsNamespace;

    NamedXmlParser(@Nullable String name) {
      myName = name;
      try {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      } catch (XmlPullParserException e) {
        throw new Error("Internal error", e);
      }
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
    public String getAttributeValue(@Nullable String namespace, @NotNull String name) {
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

  private static class NamedProtoXmlParser extends ProtoXmlPullParser {
    @Nullable
    private final String myName;
    /**
     * Attribute that caches whether the tools prefix has been defined or not. This allows us to save
     * unnecessary checks in the most common case ("tools" is not defined).
     */
    private boolean hasToolsNamespace;

    NamedProtoXmlParser(@Nullable String name) {
      myName = name;
      try {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
      } catch (XmlPullParserException e) {
        throw new Error(e);
      }
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
    public String getAttributeValue(@Nullable String namespace, @NotNull String name) {
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
