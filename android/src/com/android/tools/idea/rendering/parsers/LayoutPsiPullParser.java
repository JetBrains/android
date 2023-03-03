/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering.parsers;

import static com.android.SdkConstants.ANDROID_NS_NAME;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_BACKGROUND;
import static com.android.SdkConstants.ATTR_CLASS;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.SdkConstants.ATTR_LAYOUT;
import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_MARGIN;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_MIN_HEIGHT;
import static com.android.SdkConstants.ATTR_MIN_WIDTH;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_NAV_GRAPH;
import static com.android.SdkConstants.ATTR_PADDING;
import static com.android.SdkConstants.ATTR_PADDING_BOTTOM;
import static com.android.SdkConstants.ATTR_PADDING_HORIZONTAL;
import static com.android.SdkConstants.ATTR_PADDING_LEFT;
import static com.android.SdkConstants.ATTR_PADDING_RIGHT;
import static com.android.SdkConstants.ATTR_PADDING_TOP;
import static com.android.SdkConstants.ATTR_PADDING_VERTICAL;
import static com.android.SdkConstants.ATTR_PARENT_TAG;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.ATTR_SRC_COMPAT;
import static com.android.SdkConstants.ATTR_USE_HANDLER;
import static com.android.SdkConstants.ATTR_VISIBILITY;
import static com.android.SdkConstants.AUTO_URI;
import static com.android.SdkConstants.EXPANDABLE_LIST_VIEW;
import static com.android.SdkConstants.FRAGMENT_CONTAINER_VIEW;
import static com.android.SdkConstants.FRAME_LAYOUT;
import static com.android.SdkConstants.GRID_VIEW;
import static com.android.SdkConstants.IMAGE_BUTTON;
import static com.android.SdkConstants.IMAGE_VIEW;
import static com.android.SdkConstants.LIST_VIEW;
import static com.android.SdkConstants.SAMPLE_PREFIX;
import static com.android.SdkConstants.SPINNER;
import static com.android.SdkConstants.TAG_DATA;
import static com.android.SdkConstants.TAG_LAYOUT;
import static com.android.SdkConstants.TOOLS_PREFIX;
import static com.android.SdkConstants.TOOLS_SAMPLE_PREFIX;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.SdkConstants.VALUE_FILL_PARENT;
import static com.android.SdkConstants.VALUE_MATCH_PARENT;
import static com.android.SdkConstants.VIEW_FRAGMENT;
import static com.android.SdkConstants.VIEW_INCLUDE;
import static com.android.SdkConstants.VIEW_MERGE;
import static com.android.SdkConstants.XMLNS_PREFIX;
import static com.android.ide.common.resources.sampledata.SampleDataManager.SUBARRAY_SEPARATOR;
import static com.android.support.FragmentTagUtil.isFragmentTag;
import static com.android.tools.idea.rendering.RenderTask.AttributeFilter;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.rendering.api.ILayoutLog;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ValueXmlHelper;
import com.android.ide.common.resources.sampledata.SampleDataManager;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.rendering.IRenderLogger;
import com.android.tools.idea.rendering.LayoutMetadata;
import com.android.tools.idea.rendering.RenderLogger;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.jetbrains.android.dom.navigation.NavXmlHelperKt;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParserException;

/**
 * {@link ILayoutPullParser} implementation on top of the PSI {@link XmlTag}.
 * <p/>
 * It's designed to work on layout files, and will not work on other resource files (no text event
 * support for example).
 * <p/>
 * This pull parser generates {@link com.android.ide.common.rendering.api.ViewInfo}s whose keys
 * are of type {@link XmlTag}.
 */
public class LayoutPsiPullParser extends LayoutPullParser implements AaptAttrParser {
  @Nullable final ResourceResolver myResourceResolver;

  /**
   * Set of views that support the use of the app:srcCompat attribute when the support library is being used. This list must contain
   * ImageView and all the framework views that inherit from ImageView and support srcCompat.
   */
  private static final ImmutableSet<String> TAGS_SUPPORTING_SRC_COMPAT = ImmutableSet.of(IMAGE_BUTTON, IMAGE_VIEW);

  /**
   * Synthetic tag used when the parser can not read any contents from the passed XML file so layoutlib can render
   * something in the preview.
   */
  private static final TagSnapshot EMPTY_LAYOUT = TagSnapshot.createSyntheticTag(null, "LinearLayout", ANDROID_NS_NAME, ANDROID_URI,
                                                                                 ImmutableList.of(
                                                                                   new AttributeSnapshot(ANDROID_URI, ANDROID_NS_NAME,
                                                                                                         ATTR_LAYOUT_WIDTH,
                                                                                                         VALUE_MATCH_PARENT),
                                                                                   new AttributeSnapshot(ANDROID_URI, ANDROID_NS_NAME,
                                                                                                         ATTR_LAYOUT_HEIGHT,
                                                                                                         VALUE_MATCH_PARENT)
                                                                                 ),
                                                                                 ImmutableList.of(), null);

  private static final Consumer<TagSnapshot> TAG_SNAPSHOT_DECORATOR = (tagSnapshot) -> {
    if ("com.google.android.gms.ads.AdView".equals(tagSnapshot.tagName) || "com.google.android.gms.maps.MapView".equals(tagSnapshot.tagName)) {
      tagSnapshot.setAttribute(ATTR_MIN_WIDTH, TOOLS_URI, TOOLS_PREFIX, "50dp", false);
      tagSnapshot.setAttribute(ATTR_MIN_HEIGHT, TOOLS_URI, TOOLS_PREFIX, "50dp", false);
      tagSnapshot.setAttribute(ATTR_BACKGROUND, TOOLS_URI, TOOLS_PREFIX, "#AAA", false);
    } else if (tagSnapshot.tagName.equals(LIST_VIEW) ||
        tagSnapshot.tagName.equals(EXPANDABLE_LIST_VIEW) ||
        tagSnapshot.tagName.equals(GRID_VIEW) ||
        tagSnapshot.tagName.equals(SPINNER)) {
      // Ensure that root tags that qualify for adapter binding specify an id attribute, since that is required for
      // attribute binding to work. (Without this, a <ListView> at the root level will not show Item 1, Item 2, etc.
      if (tagSnapshot.getAttribute(ATTR_ID, ANDROID_URI) == null) {
        String prefix = tagSnapshot.tag != null ? tagSnapshot.tag.getPrefixByNamespace(ANDROID_URI) : null;
        if (prefix != null) {
          tagSnapshot.setAttribute(ATTR_ID, ANDROID_URI, prefix, "@+id/_dynamic");
        }
      }
    }
  };

  @NotNull
  private final ILayoutLog myLogger;

  @NotNull
  private final List<TagSnapshot> myNodeStack = new ArrayList<>();

  @Nullable
  protected final TagSnapshot myRoot;

  /** Mapping from URI to namespace prefix for android, app and tools URIs */
  @NotNull
  protected final ImmutableMap<String, String> myNamespacePrefixes;

  private final ResourceNamespace myLayoutNamespace;

  protected boolean myProvideViewCookies = true;

  /** If true, the parser will use app:srcCompat instead of android:src for the tags specified in {@link #TAGS_SUPPORTING_SRC_COMPAT} */
  private boolean myUseSrcCompat;

  /**
   * When false, the parser will ignore 'visibility', 'layout_editor_absoluteX' and 'layout_editor_absoluteY' tools namespaced attributes.
   */
  private boolean myUseToolsPositionAndVisibility;

  private HashSet<String> myToolsPositionAndVisibilityAttributes = new HashSet<>(
    Arrays.asList(SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_X, SdkConstants.ATTR_LAYOUT_EDITOR_ABSOLUTE_Y, ATTR_VISIBILITY));

  private final ImmutableMap<String, TagSnapshot> myDeclaredAaptAttrs;

  private int mySampleDataCounter;
  private final Map<String, AtomicInteger> mySampleDataCounterMap = new HashMap<>();

  private final Consumer<TagSnapshot> mySampleDataProcessing = (tagSnapshot) -> {
    for (AttributeSnapshot attributeSnapshot : tagSnapshot.attributes) {
      String resourceUrl = attributeSnapshot.value;
      if (resourceUrl != null && (resourceUrl.startsWith(SAMPLE_PREFIX) || resourceUrl.startsWith(TOOLS_SAMPLE_PREFIX))) {
        String resourceName = SampleDataManager.getResourceNameFromSampleReference(resourceUrl);
        AtomicInteger position = mySampleDataCounterMap.get(resourceName);
        if (position == null) {
          position = new AtomicInteger(mySampleDataCounter);
          mySampleDataCounterMap.put(resourceName, position);
        }
        attributeSnapshot.value = getSampleDataResourceUrl(resourceUrl, position);
      }
    }
  };

  /**
   * Constructs a new {@link LayoutPsiPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files.
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   * @param sampleDataCounter start index for displaying sample data
   * @param resourceRepositoryManager for namespace
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file,
                                           @NotNull IRenderLogger logger,
                                           boolean honorMergeParentTag,
                                           @Nullable ResourceResolver resolver,
                                           @Nullable ResourceRepositoryManager resourceRepositoryManager,
                                           int sampleDataCounter) {
    if (IdeResourcesUtil.getFolderType(file) == ResourceFolderType.MENU) {
      return new MenuPsiPullParser(file, logger, resourceRepositoryManager);
    }
    return new LayoutPsiPullParser(file, logger, honorMergeParentTag, resolver, resourceRepositoryManager, sampleDataCounter);
  }

  /**
   * Constructs a new {@link LayoutPsiPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files.
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file, @NotNull IRenderLogger logger, @Nullable ResourceRepositoryManager resourceRepositoryManager) {
    return create(file, logger, true, null, resourceRepositoryManager, 0);
  }

  /**
   * Constructs a new {@link LayoutPsiPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files, and handling "exploded rendering" - adding padding on views
   * to make them easier to see and operate on.
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   * @param explodeNodes A set of individual nodes that should be assigned a fixed amount of
   *                     padding ({@link PaddingLayoutPsiPullParser#FIXED_PADDING_VALUE}).
   *                     This is intended for use with nodes that (without padding) would be
   *                     invisible.
   * @param resourceResolver Optional {@link ResourceResolver} that will be used by the parser to
   *                         resolve any resources.
   * @param density      the density factor for the screen.
   * @param useToolsPositionAndVisibility When false, 'visibility', 'layout_editor_absoluteX' and 'layout_editor_absoluteY' tools namespaced
   *                                     attributes will be ignored by the parser.
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file,
                                           @NotNull IRenderLogger logger,
                                           @Nullable Set<XmlTag> explodeNodes,
                                           @NotNull Density density,
                                           @Nullable ResourceResolver resourceResolver,
                                           @Nullable ResourceRepositoryManager resourceRepositoryManager,
                                           @NotNull Boolean useToolsPositionAndVisibility) {
    if (explodeNodes != null && !explodeNodes.isEmpty()) {
      return new PaddingLayoutPsiPullParser(file, logger, explodeNodes, density, resourceRepositoryManager);
    } else {
      // This method is only called to create layouts from the preview/editor (not inflated by custom components) so we always honor
      // the tools:parentTag
      return new LayoutPsiPullParser(file, logger, true, resourceResolver, resourceRepositoryManager, useToolsPositionAndVisibility);
    }
  }

  @NotNull
  public static LayoutPsiPullParser create(@Nullable final AttributeFilter filter,
                                           @NotNull XmlTag root,
                                           @NotNull IRenderLogger logger,
                                           @Nullable ResourceRepositoryManager resourceRepositoryManager) {
    return new AttributeFilteredLayoutParser(root, logger, filter, resourceRepositoryManager);
  }

  @NotNull
  public static LayoutPsiPullParser create(@NotNull TagSnapshot root, @NotNull ResourceNamespace layoutNamespace, @NotNull ILayoutLog log) {
    return new LayoutPsiPullParser(root, layoutNamespace, log);
  }

  /**
   * Use one of the {@link #create} factory methods instead
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   */
  protected LayoutPsiPullParser(@NotNull XmlFile file, @NotNull ILayoutLog logger, boolean honorMergeParentTag, @Nullable ResourceRepositoryManager resourceRepositoryManager) {
    this(file, logger, honorMergeParentTag, null, resourceRepositoryManager, 0 );
  }

  /**
   * Use one of the {@link #create} factory methods instead
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   * @param resolver Optional {@link ResourceResolver} that will be used by the parser to
   *                         resolve any resources.
   * @param sampleDataCounter start index for displaying sample data
   */
  protected LayoutPsiPullParser(@NotNull XmlFile file,
                                @NotNull ILayoutLog logger,
                                boolean honorMergeParentTag,
                                @Nullable ResourceResolver resolver,
                                @Nullable ResourceRepositoryManager resourceRepositoryManager,
                                int sampleDataCounter) {
    this(AndroidPsiUtils.getRootTagSafely(file), logger, honorMergeParentTag, resolver, resourceRepositoryManager, sampleDataCounter, true);
  }

  /**
   * Use one of the {@link #create} factory methods instead
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   * @param resourceResolver Optional {@link ResourceResolver} that will be used by the parser to
   *                         resolve any resources.
   * @param useToolsPositionAndVisibility When false, 'visibility', 'layout_editor_absoluteX' and 'layout_editor_absoluteY' tools namespaced
   *                                     attributes will be ignored by the parser.
   */
  protected LayoutPsiPullParser(@NotNull XmlFile file,
                                @NotNull ILayoutLog logger,
                                boolean honorMergeParentTag,
                                @Nullable ResourceResolver resourceResolver,
                                @Nullable ResourceRepositoryManager resourceRepositoryManager,
                                boolean useToolsPositionAndVisibility) {
    this(AndroidPsiUtils.getRootTagSafely(file), logger, honorMergeParentTag, resourceResolver, resourceRepositoryManager, 0, useToolsPositionAndVisibility);
  }

  /**
   * Returns the declared namespaces in the passed {@link TagSnapshot}. If the passed root is null, an empty map is returned.
   * This method will run in a read action if needed.
   */
  @NotNull
  private static ImmutableMap<String, String> buildNamespacesMap(@Nullable TagSnapshot root) {
    if (!ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<ImmutableMap<String, String>>)() -> buildNamespacesMap(root));
    }

    XmlTag rootTag = root != null ? root.tag : null;
    if (rootTag == null || !rootTag.isValid()) {
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, String> prefixesBuilder = ImmutableMap.builder();
    for (String uri : new String[]{ANDROID_URI, TOOLS_URI, AUTO_URI}) {
      String prefix = rootTag.getPrefixByNamespace(uri);
      if (prefix != null) {
        prefixesBuilder.put(uri, prefix);
      }
    }

    return prefixesBuilder.build();
  }

  /**
   * Use one of the {@link #create} factory methods instead
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   */
  protected LayoutPsiPullParser(@Nullable final XmlTag root,
                                @NotNull ILayoutLog logger,
                                boolean honorMergeParentTag,
                                @Nullable ResourceResolver resourceResolver,
                                @Nullable ResourceRepositoryManager resourceRepositoryManager) {
    this(root, logger, honorMergeParentTag, resourceResolver, resourceRepositoryManager, 0, true);
  }

  /**
   * Use one of the {@link #create} factory methods instead
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   * @param sampleDataCounter start index for displaying sample data
   */
  protected LayoutPsiPullParser(@Nullable final XmlTag root,
                                @NotNull ILayoutLog logger,
                                boolean honorMergeParentTag,
                                @Nullable ResourceResolver resourceResolver,
                                @Nullable ResourceRepositoryManager repositoryManager,
                                int sampleDataCounter,
                                boolean useToolsPositionAndVisibility) {
    myResourceResolver = resourceResolver;
    myLogger = logger;
    mySampleDataCounter = sampleDataCounter;
    myUseToolsPositionAndVisibility = useToolsPositionAndVisibility;

    Ref<TagSnapshot> myRootRef = new Ref<>(EMPTY_LAYOUT);
    Ref<ResourceNamespace> myLayoutNamespaceRef = new Ref<>(ResourceNamespace.RES_AUTO);
    ReadAction.run(() -> {
      if (root != null && root.isValid()) {
        myRootRef.set(createSnapshot(root, honorMergeParentTag, mySampleDataProcessing));
        if (repositoryManager != null) {
          myLayoutNamespaceRef.set(repositoryManager.getNamespace());
        }
      }
    });

    myRoot = myRootRef.get();
    myLayoutNamespace = myLayoutNamespaceRef.get();
    myNamespacePrefixes = buildNamespacesMap(myRoot);
    // Obtain a list of all the aapt declared attributes
    myDeclaredAaptAttrs = findDeclaredAaptAttrs(myRoot);
  }

  protected LayoutPsiPullParser(@NotNull TagSnapshot root, @NotNull ResourceNamespace layoutNamespace, @NotNull ILayoutLog log) {
    myResourceResolver = null;
    myLogger = log;
    myDeclaredAaptAttrs = ImmutableMap.of();
    myRoot = ApplicationManager.getApplication().runReadAction((Computable<TagSnapshot>)() -> {
      if (root.tag != null && root.tag.isValid()) {
        return root;
      } else {
        return null;
      }
    });

    myNamespacePrefixes = buildNamespacesMap(myRoot);
    myLayoutNamespace = layoutNamespace;
  }

  /**
   * Assigns a specific index for sample data resources that do not have one already.
   * @param resourceUrl The resource url consists of the resource name plus an optional array
   *     selector. Arrays selectors can point at specific elements [4] or use string matching
   *     [biking.png] (this will match elements ending with "biking.png"). Selectors also allow
   *     for sub-arrays like:
   *     <ul>
   *       <li>[:3] Selects elements from 0 up to the 3rd element
   *       <li>[2:] Selects the elements from the second position on
   *       <li>[10:20] Selects elements from position 10 up to position 20
   *     </ul>
   * @param offset Offset for the index of the sample data element to select
   * @return A resource url selecting one single element:
   *         <ul>
   *            <li>If the original resource url was already selecting one specific element, this returns is as such
   *            <li>If the original resource url was specifying a sub-array, this chooses the element in that sub-array with the correct offset
   *            <li>If the original resource url did not specify anything, this picks the element with the offset as index
   *         </ul>
   */
  @NotNull
  private static String getSampleDataResourceUrl(@NotNull String resourceUrl, @NotNull AtomicInteger offset) {
    int start = resourceUrl.indexOf('[');
    if (start != -1) {
      int separator = resourceUrl.indexOf(SUBARRAY_SEPARATOR, start);
      if (separator != -1) {
        int end = resourceUrl.indexOf(']');
        List<String> indices = Splitter.on(SUBARRAY_SEPARATOR).limit(2).splitToList(resourceUrl.substring(start + 1, end));
        String bottom = indices.get(0);
        String top = indices.get(1);
        try {
          int low = (bottom.isEmpty() ? 0 : Integer.parseUnsignedInt(bottom));
          int positionIndex = offset.getAndIncrement();
          if (!top.isEmpty()) {
            positionIndex %= Integer.parseUnsignedInt(top) + 1 - low;
          }
          positionIndex += low;
          return resourceUrl.substring(0, start + 1) + positionIndex + "]";
        }
        catch (Throwable ignored) {
        }
      }
      return resourceUrl;
    }
    else {
      return resourceUrl + "[" + offset.getAndIncrement() + "]";
    }
  }

  /**
   * Returns a {@link Map} that contains all the aapt:attr elements declared in this or any children parsers. This list can be used
   * to resolve @aapt/_aapt references into this parser.
   */
  @Override
  @NotNull
  public ImmutableMap<String, TagSnapshot> getAaptDeclaredAttrs() {
    return myDeclaredAaptAttrs;
  }

  @NonNull
  @Override
  public ResourceNamespace getLayoutNamespace() {
    return myLayoutNamespace;
  }

  /**
   * Method that walks the snapshot and finds all the aapt:attr elements declared.
   */
  @NotNull
  private static ImmutableMap<String, TagSnapshot> findDeclaredAaptAttrs(@Nullable TagSnapshot tag) {
    if (tag == null || !tag.hasDeclaredAaptAttrs) {
      // Nor tag or any of the children has any aapt:attr declarations, we can stop here.
      return ImmutableMap.of();
    }

    ImmutableMap.Builder<String, TagSnapshot> builder = ImmutableMap.builder();
    tag.attributes.stream()
      .filter(attr -> attr instanceof AaptAttrAttributeSnapshot)
      .map(attr -> (AaptAttrAttributeSnapshot)attr)
      .forEach(attr -> {
        TagSnapshot bundledTag = attr.getBundledTag();
        builder.put(attr.getId(), bundledTag);
        for (TagSnapshot child : bundledTag.children) {
          builder.putAll(findDeclaredAaptAttrs(child));
        }
      });
    for (TagSnapshot child : tag.children) {
      builder.putAll(findDeclaredAaptAttrs(child));
    }

    return builder.build();
  }

  @Nullable
  protected final TagSnapshot getCurrentNode() {
    if (!myNodeStack.isEmpty()) {
      return myNodeStack.get(myNodeStack.size() - 1);
    }

    return null;
  }

  @Nullable
  protected final TagSnapshot getPreviousNode() {
    if (myNodeStack.size() > 1) {
      return myNodeStack.get(myNodeStack.size() - 2);
    }

    return null;
  }

  @Nullable
  protected final AttributeSnapshot getAttribute(int i) {
    if (myParsingState != START_TAG) {
      throw new IndexOutOfBoundsException();
    }

    // get the current uiNode
    TagSnapshot uiNode = getCurrentNode();
    if (uiNode != null) {
      return uiNode.attributes.get(i);
    }

    return null;
  }

  protected void push(@NotNull TagSnapshot node) {
    myNodeStack.add(node);
  }

  @NotNull
  protected TagSnapshot pop() {
    return myNodeStack.remove(myNodeStack.size() - 1);
  }

  // ------------- IXmlPullParser --------

  /**
   * {@inheritDoc}
   * <p/>
   * This implementation returns the underlying DOM node of type {@link XmlTag}.
   * Note that the link between the GLE and the parsing code depends on this being the actual
   * type returned, so you can't just randomly change it here.
   */
  @Nullable
  @Override
  public Object getViewCookie() {
    if (myProvideViewCookies) {
      return getCurrentNode();
    }

    return null;
  }

  // ------------- XmlPullParser --------

  @Override
  public String getPositionDescription() {
    return "XML DOM element depth:" + myNodeStack.size();
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public int getAttributeCount() {
    TagSnapshot node = getCurrentNode();

    if (node != null) {
      return node.attributes.size();
    }

    return 0;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributeName(int i) {
    AttributeSnapshot attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.name;
    }

    return null;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public String getAttributeNamespace(int i) {
    AttributeSnapshot attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.namespace;
    }
    return ""; //$NON-NLS-1$
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributePrefix(int i) {
    AttributeSnapshot attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.prefix;
    }
    return null;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributeValue(int i) {
    AttributeSnapshot attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.value;
    }

    return null;
  }

  /*
   * This is the main method used by the LayoutInflater to query for attributes.
   */
  @Nullable
  @Override
  public String getAttributeValue(String namespace, String localName) {
    // get the current uiNode
    TagSnapshot tag = getCurrentNode();
    if (tag == null) {
      return null;
    }

    if (ATTR_LAYOUT.equals(localName) && isFragmentTag(tag.tagName)) {
      String layout = tag.getAttribute(LayoutMetadata.KEY_FRAGMENT_LAYOUT, TOOLS_URI);
      if (layout != null) {
        return layout;
      }
      String navGraph = tag.getAttribute(ATTR_NAV_GRAPH, AUTO_URI);
      if (navGraph != null) {
        return NavXmlHelperKt.getStartDestLayoutId(navGraph, myRoot.tag.getProject(), myResourceResolver);
      }
    }
    else if (myUseSrcCompat && ATTR_SRC.equals(localName) && TAGS_SUPPORTING_SRC_COMPAT.contains(tag.tagName)) {
      String srcCompatValue = getAttributeValue(AUTO_URI, ATTR_SRC_COMPAT);
      if (srcCompatValue != null) {
        return srcCompatValue;
      }
    }
    else if (ATTR_PADDING_LEFT.equals(localName) || ATTR_PADDING_RIGHT.equals(localName)) {
      String horizontal = getAttributeValue(ANDROID_URI, ATTR_PADDING_HORIZONTAL);
      if (horizontal != null) {
        return horizontal;
      }
    }
    else if (ATTR_PADDING_TOP.equals(localName) || ATTR_PADDING_BOTTOM.equals(localName)) {
      String vertical = getAttributeValue(ANDROID_URI, ATTR_PADDING_VERTICAL);
      if (vertical != null) {
        return vertical;
      }
    }

    String value = null;
    if (namespace == null) {
      value = tag.getAttribute(localName);
    }
    else if (!myUseToolsPositionAndVisibility && namespace.equals(TOOLS_URI)) {
      value = null;
    }
    else if (namespace.equals(ANDROID_URI) || namespace.equals(AUTO_URI)) {
      // tools attributes can override both android and app namespace attributes
      String toolsPrefix = myNamespacePrefixes.get(TOOLS_URI);
      if (toolsPrefix == null || (!myUseToolsPositionAndVisibility && myToolsPositionAndVisibilityAttributes.contains(localName))) {
        // tools namespace is not declared
        value = tag.getAttribute(localName, namespace);
      }
      else {
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, n = tag.attributes.size(); i < n; i++) {
          AttributeSnapshot attribute = tag.attributes.get(i);
          if (localName.equals(attribute.name)) {
            if (toolsPrefix.equals(attribute.prefix)) {
              value = attribute.value;
              if (value != null && value.isEmpty()) {
                // Empty when there is a runtime attribute set means unset the runtime attribute
                value = tag.getAttribute(localName, ANDROID_URI) != null ? null : value;
              }
              break;
            }
            else if (namespace.equals(attribute.namespace)) {
              value = attribute.value;
              // Don't break: continue searching in case we find a tools design time attribute
            }
          }
        }
      }
    }
    else {
      // The namespace is not android, app or null
      if (!TOOLS_URI.equals(namespace)) {
        // Auto-convert http://schemas.android.com/apk/res-auto resources. The lookup
        // will be for the current application's resource package, e.g.
        // http://schemas.android.com/apk/res/foo.bar, but the XML document will
        // be using http://schemas.android.com/apk/res-auto in library projects:
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0, n = tag.attributes.size(); i < n; i++) {
          AttributeSnapshot attribute = tag.attributes.get(i);
          if (localName.equals(attribute.name) && (namespace.equals(attribute.namespace) ||
                                                   AUTO_URI.equals(attribute.namespace))) {
            value = attribute.value;
            break;
          }
        }
      }
      else {
        // We are asked specifically to return a tools attribute
        value = tag.getAttribute(localName, namespace);
      }
    }

    if (value != null) {
      // on the fly convert match_parent to fill_parent for compatibility with older
      // platforms.
      if (VALUE_MATCH_PARENT.equals(value) &&
          (ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName)) &&
          ANDROID_URI.equals(namespace)) {
        return VALUE_FILL_PARENT;
      }

      // Replace all whitespace characters with a standard space
      value = value.replaceAll("\\s", " ");

      // Handle unicode and XML escapes
      for (int i = 0, n = value.length(); i < n; i++) {
        char c = value.charAt(i);
        if (c == '&' || c == '\\') {
          value = ValueXmlHelper.unescapeResourceString(value, true, false);
          break;
        }
      }
    }

    return value;
  }

  @Override
  public int getDepth() {
    return myNodeStack.size();
  }

  @Nullable
  @Override
  public String getName() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      TagSnapshot currentNode = getCurrentNode();
      assert currentNode != null; // Should only be called when START_TAG
      String name = currentNode.tagName;

      String viewHandlerTag = currentNode.getAttribute(ATTR_USE_HANDLER, TOOLS_URI);
      if (StringUtil.isNotEmpty(viewHandlerTag)) {
        name = viewHandlerTag;
      }

      if (isFragmentTag(name)) {
        // Temporarily translate <fragment>/FragmentContainerView to <include> (and in getAttribute
        // we will also provide a layout-attribute for the corresponding
        // fragment name attribute)
        String layout = currentNode.getAttribute(LayoutMetadata.KEY_FRAGMENT_LAYOUT, TOOLS_URI);
        if (layout == null) {
          String navGraph = currentNode.getAttribute(ATTR_NAV_GRAPH, AUTO_URI);
          if (navGraph != null && myResourceResolver != null) {
            layout = NavXmlHelperKt.getStartDestLayoutId(navGraph, myRoot.tag.getProject(), myResourceResolver);
          }
        }
        if (layout != null) {
          return VIEW_INCLUDE;
        } else {
          String fragmentId = currentNode.getAttribute(ATTR_CLASS);
          if (fragmentId == null || fragmentId.isEmpty()) {
            fragmentId = currentNode.getAttribute(ATTR_NAME, ANDROID_URI);
            if (fragmentId == null || fragmentId.isEmpty()) {
              fragmentId = currentNode.getAttribute(ATTR_ID, ANDROID_URI);
            }
          }
          myLogger.warning(RenderLogger.TAG_MISSING_FRAGMENT, "Missing fragment association", null, fragmentId);
        }
      } else if (name.endsWith("Compat") && name.indexOf('.') == -1) {
        return name.substring(0, name.length() - "Compat".length());
      }

      return name;
    }

    return null;
  }

  @Nullable
  @Override
  public String getNamespace() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      TagSnapshot currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.namespace;
    }

    return null;
  }

  @Nullable
  @Override
  public String getPrefix() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      TagSnapshot currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.prefix;
    }

    return null;
  }

  @Override
  public String getNamespace(String prefix) {
    for (int i = myNodeStack.size() - 1; i >= 0; i--) {
      String uri = myNodeStack.get(i).namespaceDeclarations.get(prefix);
      if (uri != null) {
        return uri;
      }
    }
    return null;
  }

  @Override
  public boolean isEmptyElementTag() throws XmlPullParserException {
    if (myParsingState == START_TAG) {
      TagSnapshot currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      // This isn't quite right; if layoutlib starts needing this, stash XmlTag#isEmpty() in snapshot
      return currentNode.children.isEmpty();
    }

    throw new XmlPullParserException("Call to isEmptyElementTag while not in START_TAG", this, null);
  }

  @Override
  protected void onNextFromStartDocument() {
    if (myRoot != null) {
      push(myRoot);
      myParsingState = START_TAG;
    } else {
      myParsingState = END_DOCUMENT;
    }
  }

  @Override
  protected void onNextFromStartTag() {
    // get the current node, and look for text or children (children first)
    TagSnapshot node = getCurrentNode();
    assert node != null;  // Should only be called when START_TAG
    List<TagSnapshot> children = node.children;
    if (!children.isEmpty()) {
      // move to the new child, and don't change the state.
      push(children.get(0));

      // in case the current state is CURRENT_DOC, we set the proper state.
      myParsingState = START_TAG;
    }
    else {
      if (myParsingState == START_DOCUMENT) {
        // this handles the case where there's no node.
        myParsingState = END_DOCUMENT;
      }
      else {
        myParsingState = END_TAG;
      }
    }
  }

  @Override
  protected void onNextFromEndTag() {
    // look for a sibling. if no sibling, go back to the parent
    TagSnapshot node = getCurrentNode();
    assert node != null;  // Should only be called when END_TAG

    TagSnapshot sibling = node.getNextSibling();
    if (sibling != null) {
      node = sibling;
      // to go to the sibling, we need to remove the current node,
      pop();
      // and add its sibling.
      push(node);
      myParsingState = START_TAG;
    }
    else {
      // move back to the parent
      pop();

      // we have only one element left (myRoot), then we're done with the document.
      if (myNodeStack.isEmpty()) {
        myParsingState = END_DOCUMENT;
      }
      else {
        myParsingState = END_TAG;
      }
    }
  }

  /** Sets whether this parser will provide view cookies */
  public void setProvideViewCookies(boolean provideViewCookies) {
    myProvideViewCookies = provideViewCookies;
  }

  /**
   * Returns the distance from the given tag to the parent {@code layout} tag or -1 if there is no {@code layout} tag
   */
  private static int distanceToLayoutTag(@NotNull XmlTag tag) {
    int distance = 0;

    while ((tag = tag.getParentTag()) != null) {
      String tagName = tag.getName();
      // The merge tag does not count for the distance to the layout tag since it will be
      if (!VIEW_MERGE.equals(tagName)) {
        distance++;
      }

      if (TAG_LAYOUT.equals(tagName)) {
        break;
      }
    }

    // We only count the distance to the layout tag
    return tag != null ? distance : -1;
  }

  /**
   * Creates a {@link TagSnapshot} for the given {@link XmlTag} and all its children.
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   */
  @Nullable
  private static TagSnapshot createSnapshot(@NotNull XmlTag tag, boolean honorMergeParentTag, @NotNull Consumer<TagSnapshot> tagPostProcessor) {
    Consumer<TagSnapshot> tagDecorator = TAG_SNAPSHOT_DECORATOR.andThen(tagPostProcessor);
    if (tag.getName().equals(TAG_LAYOUT)) {
      // If we are creating a snapshot of a databinding layout (the root tag is <layout>), we need to emulate some post-processing that
      // the databinding code does in the layouts.
      // For all the children of the root tag, it adds a tag that identifies. The tag is "layout/layout_name_<number>"
      final String layoutRootName = tag.getContainingFile().getVirtualFile().getNameWithoutExtension();
      tagDecorator = tagDecorator.andThen(new Consumer<TagSnapshot>() {
        int counter = 0;
        @Override
        public void accept(TagSnapshot snapshot) {
          if (snapshot.tag == null) {
            return;
          }

          if (distanceToLayoutTag(snapshot.tag) == 1) {
            // The tag attribute is only applied to the root immediate children
            snapshot.setAttribute("tag", ANDROID_URI, ANDROID_NS_NAME, "layout/" + layoutRootName + "_" + counter++, false);
          }
        }
      });
    }


    // <include> tags can't be at the root level; handle <fragment> rewriting here such that we don't
    // need to handle it as a tag name rewrite (where it's harder to change the structure)
    // https://code.google.com/p/android/issues/detail?id=67910
    tag = getRootTag(tag);
    if (tag == null || (tag.isEmpty() && tag.getName().isEmpty())) {
      // Rely on code inspection to log errors in the layout but return something that layoutlib can paint.
      return EMPTY_LAYOUT;
    }

    String rootTag = tag.getName();
    switch (rootTag) {
      case FRAGMENT_CONTAINER_VIEW:
      case VIEW_FRAGMENT:
        return createSnapshotForViewFragment(rootTag, tag, tagPostProcessor);

      case FRAME_LAYOUT:
        return createSnapshotForFrameLayout(tag, tagDecorator);

      case VIEW_MERGE:
        return createSnapshotForMerge(tag, honorMergeParentTag, tagDecorator);

      default:
        return TagSnapshot.createTagSnapshot(tag, tagDecorator);
    }
  }

  @NotNull
  private static TagSnapshot createSnapshotForViewFragment(@NotNull String rootTagName, @NotNull XmlTag rootTag, @NotNull Consumer<TagSnapshot> tagPostProcessor) {
    XmlAttribute[] psiAttributes = rootTag.getAttributes();
    List<AttributeSnapshot> attributes = Lists.newArrayListWithCapacity(psiAttributes.length);
    for (XmlAttribute psiAttribute : psiAttributes) {
      AttributeSnapshot attribute = AttributeSnapshot.createAttributeSnapshot(psiAttribute);
      if (attribute != null) {
        attributes.add(attribute);
      }
    }

    List<AttributeSnapshot> includeAttributes = Lists.newArrayListWithCapacity(psiAttributes.length);
    for (XmlAttribute psiAttribute : psiAttributes) {
      String name = psiAttribute.getName();
      if (name.startsWith(XMLNS_PREFIX)) {
        continue;
      }
      String localName = psiAttribute.getLocalName();
      if (localName.startsWith(ATTR_LAYOUT_MARGIN) || localName.startsWith(ATTR_PADDING) ||
          localName.equals(ATTR_ID)) {
        continue;
      }
      AttributeSnapshot attribute = AttributeSnapshot.createAttributeSnapshot(psiAttribute);
      if (attribute != null) {
        includeAttributes.add(attribute);
      }
    }

    TagSnapshot include = TagSnapshot.createSyntheticTag(null, rootTagName, "", "", includeAttributes,
                                                         Collections.emptyList(), null);
    return TagSnapshot.createSyntheticTag(rootTag, FRAME_LAYOUT, "", "", attributes, Collections.singletonList(include), tagPostProcessor);
  }

  @NotNull
  private static TagSnapshot createSnapshotForFrameLayout(@NotNull XmlTag rootTag, @NotNull Consumer<TagSnapshot> tagDecorator) {
    TagSnapshot root = TagSnapshot.createTagSnapshot(rootTag, tagDecorator);

    // tools:layout on a <FrameLayout> acts like an <include> child. This
    // lets you preview runtime additions on FrameLayouts.
    String layout = rootTag.getAttributeValue(ATTR_LAYOUT, TOOLS_URI);
    if (layout != null && root.children.isEmpty()) {
      String prefix = rootTag.getPrefixByNamespace(ANDROID_URI);
      if (prefix != null) {
        List<TagSnapshot> children = new ArrayList<>();
        root.children = children;
        List<AttributeSnapshot> attributes = Lists.newArrayListWithExpectedSize(3);
        attributes.add(new AttributeSnapshot("", "", ATTR_LAYOUT, layout));
        attributes.add(new AttributeSnapshot(ANDROID_URI, prefix, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT));
        attributes.add(new AttributeSnapshot(ANDROID_URI, prefix, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT));
        TagSnapshot element = TagSnapshot.createSyntheticTag(null, VIEW_INCLUDE, "", "", attributes, Collections.emptyList(), null);
        children.add(element);
      }
    }

    // Allow <FrameLayout tools:visibleChildren="1,3,5"> to make all but the given children visible
    String visibleChild = rootTag.getAttributeValue("visibleChildren", TOOLS_URI);
    if (visibleChild != null) {
      Set<Integer> indices = Sets.newHashSet();
      for (String s : Splitter.on(',').trimResults().omitEmptyStrings().split(visibleChild)) {
        try {
          indices.add(Integer.parseInt(s));
        } catch (NumberFormatException e) {
          // ignore metadata if it's incorrect
        }
      }
      String prefix = rootTag.getPrefixByNamespace(ANDROID_URI);
      if (prefix != null) {
        for (int i = 0, n = root.children.size(); i < n; i++) {
          TagSnapshot child = root.children.get(i);
          boolean visible = indices.contains(i);
          child.setAttribute(ATTR_VISIBILITY, ANDROID_URI, prefix, visible ? "visible" : "gone");
        }
      }
    }

    return root;
  }

  /**
   * Creates a {@link TagSnapshot} for the given {@link XmlTag}.
   * @param honorMergeParentTag if true, this method will look into the {@code tools:parentTag} to replace the root {@code <merge>} tag.
   */
  @NotNull
  private static TagSnapshot createSnapshotForMerge(@NotNull XmlTag rootTag,
                                                    boolean honorMergeParentTag,
                                                    @NotNull Consumer<TagSnapshot> tagDecorator) {
    TagSnapshot root = TagSnapshot.createTagSnapshot(rootTag, tagDecorator);
    String parentTag = honorMergeParentTag ? rootTag.getAttributeValue(ATTR_PARENT_TAG, TOOLS_URI) : null;
    if (parentTag == null) {
      return root;
    }
    List<AttributeSnapshot> attributes = AttributeSnapshot.createAttributesForTag(rootTag);
    addAttributeIfMissing(rootTag, ATTR_LAYOUT_WIDTH, attributes);
    addAttributeIfMissing(rootTag, ATTR_LAYOUT_HEIGHT, attributes);
    return TagSnapshot.createSyntheticTag(rootTag, parentTag, "", "", attributes, root.children, null);
  }

  private static void addAttributeIfMissing(@NotNull XmlTag tag, @NotNull String attrName, @NotNull List<AttributeSnapshot> attributes) {
    String value = tag.getAttributeValue(attrName, ANDROID_URI);
    if (value == null) {
      value = tag.getAttributeValue(attrName, TOOLS_URI);
    }
    if (value == null) {
      attributes.add(new AttributeSnapshot(ANDROID_URI, tag.getPrefixByNamespace(ANDROID_URI), attrName, VALUE_MATCH_PARENT));
    }
  }

  @Nullable
  public static XmlTag getRootTag(@NotNull XmlTag tag) {
    if (tag.getName().equals(TAG_LAYOUT)) {
      for (XmlTag subTag : tag.getSubTags()) {
        String subTagName = subTag.getName();
        if (!subTagName.equals(TAG_DATA)) {
          return subTag;
        }
      }
      return null;
    }
    return tag;
  }

  public void setUseSrcCompat(boolean useSrcCompat) {
    myUseSrcCompat = useSrcCompat;
  }

  static class AttributeFilteredLayoutParser extends LayoutPsiPullParser {
    @Nullable
    private final AttributeFilter myFilter;

    public AttributeFilteredLayoutParser(@NotNull XmlTag root,
                                         @NotNull ILayoutLog logger,
                                         @Nullable AttributeFilter filter,
                                         @Nullable ResourceRepositoryManager resourceRepositoryManager) {
      super(root, logger, true, null, resourceRepositoryManager);
      this.myFilter = filter;
    }

    public AttributeFilteredLayoutParser(@NotNull XmlFile file,
                                         @NotNull ILayoutLog logger,
                                         @Nullable AttributeFilter filter,
                                         @Nullable ResourceRepositoryManager resourceRepositoryManager) {
      super(file, logger, true, resourceRepositoryManager);
      this.myFilter = filter;
    }

    @Nullable
    @Override
    public String getAttributeValue(final String namespace, final String localName) {
      if (myFilter != null) {
        TagSnapshot element = getCurrentNode();
        if (element != null) {
          final XmlTag tag = element.tag;
          if (tag != null) {
            String value;
            if (ApplicationManager.getApplication().isReadAccessAllowed()) {
              value = myFilter.getAttribute(tag, namespace, localName);
            }
            else {
              value = ApplicationManager.getApplication()
                .runReadAction((Computable<String>)() -> myFilter.getAttribute(tag, namespace, localName));
            }
            if (value != null) {
              if (value.isEmpty()) { // empty means unset
                return null;
              }
              return value;
            }
            // null means no preference, not "unset".
          }
        }
      }

      return super.getAttributeValue(namespace, localName);
    }
  }
}
