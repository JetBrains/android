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
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.res2.ValueXmlHelper;
import com.android.resources.Density;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParserException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.RenderTask.AttributeFilter;

/**
 * {@link ILayoutPullParser} implementation on top of the PSI {@link XmlTag}.
 * <p/>
 * It's designed to work on layout files, and will not work on other resource files (no text event
 * support for example).
 * <p/>
 * This pull parser generates {@link com.android.ide.common.rendering.api.ViewInfo}s whose keys
 * are of type {@link XmlTag}.
 */
public class LayoutPsiPullParser extends LayoutPullParser {
  /**
   * Set of views that support the use of the app:srcCompat attribute when the support library is being used. This list must contain
   * ImageView and all the framework views that inherit from ImageView and support srcCompat.
   */
  private static final ImmutableSet<String> TAGS_SUPPORTING_SRC_COMPAT = ImmutableSet.of(IMAGE_BUTTON, IMAGE_VIEW);

  @NotNull
  private final LayoutLog myLogger;

  @NotNull
  private final List<TagSnapshot> myNodeStack = new ArrayList<TagSnapshot>();

  @Nullable
  protected final TagSnapshot myRoot;

  @Nullable
  private String myToolsPrefix;

  @Nullable
  protected String myAndroidPrefix;

  protected boolean myProvideViewCookies = true;

  /** If true, the parser will use app:srcCompat instead of android:src for the tags specified in {@link #TAGS_SUPPORTING_SRC_COMPAT} */
  private boolean myUseSrcCompat;

  /**
   * Constructs a new {@link LayoutPsiPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files.
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param logger       The logger to emit warnings too, such as missing fragment associations
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file, @NotNull RenderLogger logger) {
    if (ResourceHelper.getFolderType(file) == ResourceFolderType.MENU) {
      return new MenuPsiPullParser(file, logger);
    }
    return new LayoutPsiPullParser(file, logger);
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
   * @param density      the density factor for the screen.
   */
  @NotNull
  public static LayoutPsiPullParser create(@NotNull XmlFile file,
                                           @NotNull RenderLogger logger,
                                           @Nullable Set<XmlTag> explodeNodes,
                                           @NotNull Density density) {
    if (explodeNodes != null && !explodeNodes.isEmpty()) {
      return new PaddingLayoutPsiPullParser(file, logger, explodeNodes, density);
    } else {
      return new LayoutPsiPullParser(file, logger);
    }
  }

  @NotNull
  public static LayoutPsiPullParser create(@Nullable final AttributeFilter filter,
                                           @NotNull XmlTag root,
                                           @NotNull RenderLogger logger) {
    return new AttributeFilteredLayoutParser(root, logger, filter);
  }

  /** Use one of the {@link #create} factory methods instead */
  protected LayoutPsiPullParser(@NotNull XmlFile file, @NotNull LayoutLog logger) {
    this(AndroidPsiUtils.getRootTagSafely(file), logger);
  }

  protected LayoutPsiPullParser(@Nullable final XmlTag root, @NotNull LayoutLog logger) {
    myLogger = logger;

    if (root != null) {
      if (ApplicationManager.getApplication().isReadAccessAllowed()) {
        if (root.isValid()) {
          myAndroidPrefix = root.getPrefixByNamespace(ANDROID_URI);
          myToolsPrefix = root.getPrefixByNamespace(TOOLS_URI);
          myRoot = createSnapshot(root);
        } else {
          myRoot = null;
        }
      } else {
        myRoot = ApplicationManager.getApplication().runReadAction(new Computable<TagSnapshot>() {

          @Override
          public TagSnapshot compute() {
            if (root.isValid()) {
              myAndroidPrefix = root.getPrefixByNamespace(ANDROID_URI);
              myToolsPrefix = root.getPrefixByNamespace(TOOLS_URI);
              return createSnapshot(root);
            } else {
              return null;
            }
          }
        });
      }
    } else {
      myRoot = null;
    }
  }

  @Nullable
  protected final TagSnapshot getCurrentNode() {
    if (myNodeStack.size() > 0) {
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

  /**
   * Legacy method required by {@link com.android.layoutlib.api.IXmlPullParser}
   */
  @SuppressWarnings("deprecation")
  @Nullable
  @Override
  public Object getViewKey() {
    return getViewCookie();
  }

  /**
   * This implementation does nothing for now as all the embedded XML will use a normal KXML
   * parser.
   */
  @Nullable
  @Override
  public ILayoutPullParser getParser(String layoutName) {
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
    if (tag != null) {
      if (ATTR_LAYOUT.equals(localName) && VIEW_FRAGMENT.equals(tag.tagName)) {
        String layout = tag.getAttribute(LayoutMetadata.KEY_FRAGMENT_LAYOUT, TOOLS_URI);
        if (layout != null) {
          return layout;
        }
      } else if (myUseSrcCompat && ATTR_SRC.equals(localName) && TAGS_SUPPORTING_SRC_COMPAT.contains(tag.tagName)) {
        String srcCompatValue = tag.getAttribute("srcCompat", AUTO_URI);
        if (srcCompatValue != null) {
          return srcCompatValue;
        }
      }

      String value = null;
      if (namespace == null) {
        value = tag.getAttribute(localName);
      } else if (namespace.equals(ANDROID_URI)) {
        if (myAndroidPrefix != null) {
          if (myToolsPrefix != null) {
            //noinspection ForLoopReplaceableByForEach
            for (int i = 0, n = tag.attributes.size(); i < n; i++) {
              AttributeSnapshot attribute = tag.attributes.get(i);
              if (localName.equals(attribute.name)) {
                if (myToolsPrefix.equals(attribute.prefix)) {
                  value = attribute.value;
                  if (value != null && value.isEmpty()) {
                    // Empty when there is a runtime attribute set means unset the runtime attribute
                    value = tag.getAttribute(localName, ANDROID_URI) != null ? null : value;
                  }
                  break;
                } else if (myAndroidPrefix.equals(attribute.prefix)) {
                  value = attribute.value;
                  // Don't break: continue searching in case we find a tools design time attribute
                }
              }
            }
          } else {
            value = tag.getAttribute(localName, ANDROID_URI);
          }
        } else {
          value = tag.getAttribute(localName, namespace);
        }
      } else {
        // Temporary conversion: ConstraintLayout 1.0 is looking in the app namespace for attributes stored
        // in tools namespace in the XML
        if ((ATTR_LAYOUT_EDITOR_ABSOLUTE_X.equals(localName) || ATTR_LAYOUT_EDITOR_ABSOLUTE_Y.equals(localName)) &&
            AUTO_URI.equals(namespace)) {
          return getAttributeValue(TOOLS_URI, localName);
        }

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

      if (value != null) {
        // on the fly convert match_parent to fill_parent for compatibility with older
        // platforms.
        if (VALUE_MATCH_PARENT.equals(value) &&
            (ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName)) &&
            ANDROID_URI.equals(namespace)) {
          return VALUE_FILL_PARENT;
        }

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

    return null;
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
      if (name.equals(VIEW_FRAGMENT)) {
        // Temporarily translate <fragment> to <include> (and in getAttribute
        // we will also provide a layout-attribute for the corresponding
        // fragment name attribute)
        String layout = currentNode.getAttribute(LayoutMetadata.KEY_FRAGMENT_LAYOUT, TOOLS_URI);
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
          myLogger.warning(RenderLogger.TAG_MISSING_FRAGMENT, "Missing fragment association", fragmentId);
        }
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

  @Nullable
  private static TagSnapshot createSnapshot(@NotNull XmlTag tag) {
    // <include> tags can't be at the root level; handle <fragment> rewriting here such that we don't
    // need to handle it as a tag name rewrite (where it's harder to change the structure)
    // https://code.google.com/p/android/issues/detail?id=67910
    tag = getRootTag(tag);
    if (tag == null) {
      // Rely on code inspection to log errors in the layout.
      return null;
    }

    String rootTag = tag.getName();
    if (rootTag.equals(VIEW_FRAGMENT)) {
      XmlAttribute[] psiAttributes = tag.getAttributes();
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

      TagSnapshot include = TagSnapshot.createSyntheticTag(null, VIEW_FRAGMENT, "", "", includeAttributes,
                                                           Collections.<TagSnapshot>emptyList());
      return TagSnapshot.createSyntheticTag(tag, FRAME_LAYOUT, "", "", attributes, Collections.singletonList(include));
    } else if (rootTag.equals(FRAME_LAYOUT)) {
      TagSnapshot root = TagSnapshot.createTagSnapshot(tag);

      // tools:layout on a <FrameLayout> acts like an <include> child. This
      // lets you preview runtime additions on FrameLayouts.
      String layout = tag.getAttributeValue(ATTR_LAYOUT, TOOLS_URI);
      if (layout != null && root.children.isEmpty()) {
        String prefix = tag.getPrefixByNamespace(ANDROID_URI);
        if (prefix != null) {
          List<TagSnapshot> children = Lists.newArrayList();
          root.children = children;
          List<AttributeSnapshot> attributes = Lists.newArrayListWithExpectedSize(3);
          attributes.add(new AttributeSnapshot("", "", ATTR_LAYOUT, layout));
          attributes.add(new AttributeSnapshot(ANDROID_URI, prefix, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT));
          attributes.add(new AttributeSnapshot(ANDROID_URI, prefix, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT));
          TagSnapshot element = TagSnapshot.createSyntheticTag(null, VIEW_INCLUDE, "", "", attributes,
                                                               Collections.<TagSnapshot>emptyList());
          children.add(element);
        }
      }

      // Allow <FrameLayout tools:visibleChildren="1,3,5"> to make all but the given children visible
      String visibleChild = tag.getAttributeValue("visibleChildren", TOOLS_URI);
      if (visibleChild != null) {
        Set<Integer> indices = Sets.newHashSet();
        for (String s : Splitter.on(',').trimResults().omitEmptyStrings().split(visibleChild)) {
          try {
            indices.add(Integer.parseInt(s));
          } catch (NumberFormatException e) {
            // ignore metadata if it's incorrect
          }
        }
        String prefix = tag.getPrefixByNamespace(ANDROID_URI);
        if (prefix != null) {
          for (int i = 0, n = root.children.size(); i < n; i++) {
            TagSnapshot child = root.children.get(i);
            boolean visible = indices.contains(i);
            child.setAttribute(ATTR_VISIBILITY, ANDROID_URI, prefix, visible ? "visible" : "gone");
          }
        }
      }

      return root;
    } else {
      TagSnapshot root = TagSnapshot.createTagSnapshot(tag);

      // Ensure that root tags that qualify for adapter binding specify an id attribute, since that is required for
      // attribute binding to work. (Without this, a <ListView> at the root level will not show Item 1, Item 2, etc.
      if (rootTag.equals(LIST_VIEW) || rootTag.equals(EXPANDABLE_LIST_VIEW) || rootTag.equals(GRID_VIEW) || rootTag.equals(SPINNER)) {
        XmlAttribute id = tag.getAttribute(ATTR_ID, ANDROID_URI);
        if (id == null) {
          String prefix = tag.getPrefixByNamespace(ANDROID_URI);
          if (prefix != null) {
            root.setAttribute(ATTR_ID, ANDROID_URI, prefix, "@+id/_dynamic");
          }
        }
      }

      return root;
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

    public AttributeFilteredLayoutParser(@NotNull XmlTag root, @NotNull LayoutLog logger, @Nullable AttributeFilter filter) {
      super(root, logger);
      this.myFilter = filter;
    }

    public AttributeFilteredLayoutParser(@NotNull XmlFile file, @NotNull LayoutLog logger, @Nullable AttributeFilter filter) {
      super(file, logger);
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
              value = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
                @Override
                @Nullable
                public String compute() {
                  return myFilter.getAttribute(tag, namespace, localName);
                }
              });
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
