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
import com.android.ide.common.rendering.legacy.ILegacyPullParser;
import com.android.resources.Density;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

/**
 * {@link com.android.ide.common.rendering.api.ILayoutPullParser} implementation on top of
 * the PSI {@link XmlTag}.
 * <p/>
 * It's designed to work on layout files, and will not work on other resource files (no text event
 * support for example).
 * <p/>
 * This pull parser generates {@link com.android.ide.common.rendering.api.ViewInfo}s whose keys
 * are of type {@link XmlTag}.
 */
public class XmlTagPullParser implements ILegacyPullParser {
  private final static Pattern FLOAT_PATTERN = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)(.*)"); //$NON-NLS-1$
  private final static int PADDING_VALUE = 10;

  private int myParsingState = START_DOCUMENT;

  @NotNull
  private final RenderLogger myLogger;

  @NotNull
  private final List<XmlTag> myNodeStack = new ArrayList<XmlTag>();

  @Nullable
  private final XmlTag myRoot;

  private boolean myZeroAttributeIsPadding = false;
  private boolean myIncreaseExistingPadding = false;

  @NotNull
  private final Density myDensity;

  /**
   * Number of pixels to pad views with in exploded-rendering mode.
   */
  private static final String DEFAULT_PADDING_VALUE = PADDING_VALUE + UNIT_PX;

  /**
   * Number of pixels to pad exploded individual views with. (This is HALF the width of the
   * rectangle since padding is repeated on both sides of the empty content.)
   */
  private static final String FIXED_PADDING_VALUE = "20px"; //$NON-NLS-1$

  /**
   * Set of nodes that we want to auto-pad using {@link #FIXED_PADDING_VALUE} as the padding
   * attribute value. Can be null, which is the case when we don't want to perform any
   * <b>individual</b> node exploding.
   */
  @Nullable
  private final Set<XmlTag> myExplodeNodes;

  /**
   * Constructs a new {@link XmlTagPullParser}, a parser dedicated to the special case of
   * parsing a layout resource files, and handling "exploded rendering" - adding padding on views
   * to make them easier to see and operate on.
   *
   * @param file         The {@link XmlTag} for the root node.
   * @param explodeNodes A set of individual nodes that should be assigned a fixed amount of
   *                     padding ({@link #FIXED_PADDING_VALUE}). This is intended for use with nodes that
   *                     (without padding) would be invisible. This parameter can be null, in which case
   *                     nodes are not individually exploded (but they may all be exploded with the
   *                     explodeRendering parameter.
   * @param density      the density factor for the screen.
   */
  public XmlTagPullParser(@NotNull XmlFile file, @Nullable Set<XmlTag> explodeNodes, @NotNull Density density,
                          @NotNull RenderLogger logger) {
    myRoot = file.getRootTag();
    myExplodeNodes = explodeNodes;
    myDensity = density;
    myLogger = logger;
  }

  @Nullable
  protected XmlTag getCurrentNode() {
    if (myNodeStack.size() > 0) {
      return myNodeStack.get(myNodeStack.size() - 1);
    }

    return null;
  }

  @Nullable
  private XmlAttribute getAttribute(int i) {
    if (myParsingState != START_TAG) {
      throw new IndexOutOfBoundsException();
    }

    // get the current uiNode
    XmlTag uiNode = getCurrentNode();
    if (uiNode != null) {
      return uiNode.getAttributes()[i];
    }

    return null;
  }

  private void push(@NotNull XmlTag node) {
    myNodeStack.add(node);

    myZeroAttributeIsPadding = false;
    myIncreaseExistingPadding = false;
  }

  @NotNull
  private XmlTag pop() {
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
    return getCurrentNode();
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
    XmlTag node = getCurrentNode();

    if (node != null) {
      int count = node.getAttributes().length;
      return count + (myZeroAttributeIsPadding ? 1 : 0);
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
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        return ATTR_PADDING;
      }
      else {
        i--;
      }
    }

    XmlAttribute attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.getLocalName();
    }

    return null;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public String getAttributeNamespace(int i) {
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        return ANDROID_URI;
      }
      else {
        i--;
      }
    }

    XmlAttribute attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.getNamespace();
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
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        assert myRoot != null;
        return myRoot.getPrefixByNamespace(ANDROID_URI);
      }
      else {
        i--;
      }
    }

    XmlAttribute attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.getNamespacePrefix();
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
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        return DEFAULT_PADDING_VALUE;
      }
      else {
        i--;
      }
    }

    XmlAttribute attribute = getAttribute(i);
    if (attribute != null) {
      String value = attribute.getValue();
      if (value != null && myIncreaseExistingPadding && ATTR_PADDING.equals(attribute.getLocalName()) &&
          ANDROID_URI.equals(attribute.getNamespace())) {
        // add the padding and return the value
        return addPaddingToValue(value);
      }
      return value;
    }

    return null;
  }

  /*
   * This is the main method used by the LayoutInflater to query for attributes.
   */
  @Nullable
  @Override
  public String getAttributeValue(String namespace, String localName) {
    if (myExplodeNodes != null && ATTR_PADDING.equals(localName) &&
        ANDROID_URI.equals(namespace)) {
      XmlTag node = getCurrentNode();
      if (node != null && myExplodeNodes.contains(node)) {
        return FIXED_PADDING_VALUE;
      }
    }

    if (myZeroAttributeIsPadding && ATTR_PADDING.equals(localName) &&
        ANDROID_URI.equals(namespace)) {
      return DEFAULT_PADDING_VALUE;
    }

    // get the current uiNode
    XmlTag uiNode = getCurrentNode();
    if (uiNode != null) {
      if (ATTR_LAYOUT.equals(localName) && VIEW_FRAGMENT.equals(uiNode.getName())) {
        String layout = LayoutMetadata.getFragmentLayout(uiNode);
        if (layout != null) {
          return layout;
        }
      }

      XmlAttribute attribute = uiNode.getAttribute(localName, namespace);

      // Auto-convert http://schemas.android.com/apk/res-auto resources. The lookup
      // will be for the current application's resource package, e.g.
      // http://schemas.android.com/apk/res/foo.bar, but the XML document will
      // be using http://schemas.android.com/apk/res-auto in library projects:
      if (attribute == null && namespace != null && !namespace.equals(ANDROID_URI)) {
        attribute = uiNode.getAttribute(localName, AUTO_URI);
      }

      if (attribute != null) {
        String value = attribute.getValue();
        if (value != null) {
          if (value.isEmpty()) {
            return null;
          }

          if (myIncreaseExistingPadding && ATTR_PADDING.equals(localName) &&
              ANDROID_URI.equals(namespace)) {
            // add the padding and return the value
            return addPaddingToValue(value);
          }

          // on the fly convert match_parent to fill_parent for compatibility with older
          // platforms.
          if (VALUE_MATCH_PARENT.equals(value) &&
              (ATTR_LAYOUT_WIDTH.equals(localName) || ATTR_LAYOUT_HEIGHT.equals(localName)) &&
              ANDROID_URI.equals(namespace)) {
            return VALUE_FILL_PARENT;
          }

          // The PSI XML model doesn't decode the XML escapes
          if (value.indexOf('&') != -1) {
            value = StringUtil.unescapeXml(value);
          }

          // Handle unicode escapes
          if (value.indexOf('\\') != -1) {
            value = replaceUnicodeEscapes(value);
          }
        }

        return value;
      }
    }

    return null;
  }

  /**
   * Replaces any {@code \\uNNNN} references in the given string with the corresponding
   * unicode characters.
   *
   * @param s the string to perform replacements in
   * @return the string with unicode escapes replaced with actual characters
   */
  @SuppressWarnings("AssignmentToForLoopParameter")
  @NotNull
  public static String replaceUnicodeEscapes(@NotNull String s) {
    // Handle unicode escapes
    if (s.contains("\\u")) { //$NON-NLS-1$
      StringBuilder sb = new StringBuilder(s.length());
      for (int i = 0, n = s.length(); i < n; i++) {
        char c = s.charAt(i);
        if (c == '\\' && i < n - 1) {
          char next = s.charAt(i + 1);
          if (next == 'u' && i < n - 5) { // case sensitive
            String hex = s.substring(i + 2, i + 6);
            try {
              int unicodeValue = Integer.parseInt(hex, 16);
              sb.append((char)unicodeValue);
              i += 5;
            }
            catch (NumberFormatException nufe) {
              // Invalid escape: Just proceed to literally transcribe it
              sb.append(c);
            }
          }
          else {
            sb.append(c);
            sb.append(next);
            i++;
          }
        }
        else {
          sb.append(c);
        }
      }
      s = sb.toString();
    }

    return s;
  }

  @Override
  public int getDepth() {
    return myNodeStack.size();
  }

  @Nullable
  @Override
  public String getName() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      XmlTag currentNode = getCurrentNode();
      assert currentNode != null; // Should only be called when START_TAG
      String name = currentNode.getLocalName();
      if (name.equals(VIEW_FRAGMENT)) {
        // Temporarily translate <fragment> to <include> (and in getAttribute
        // we will also provide a layout-attribute for the corresponding
        // fragment name attribute)
        String layout = LayoutMetadata.getFragmentLayout(currentNode);
        if (layout != null) {
          return VIEW_INCLUDE;
        } else {
          String fragmentId = currentNode.getAttributeValue(ATTR_CLASS);
          if (fragmentId == null || fragmentId.isEmpty()) {
            fragmentId = currentNode.getAttributeValue(ATTR_NAME, ANDROID_URI);
            if (fragmentId == null || fragmentId.isEmpty()) {
              fragmentId = currentNode.getAttributeValue(ATTR_ID, ANDROID_URI);
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
      XmlTag currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.getNamespace();
    }

    return null;
  }

  @Nullable
  @Override
  public String getPrefix() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      XmlTag currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      currentNode.getNamespacePrefix();
    }

    return null;
  }

  @Override
  public boolean isEmptyElementTag() throws XmlPullParserException {
    if (myParsingState == START_TAG) {
      XmlTag currentNode = getCurrentNode();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.isEmpty();
    }

    throw new XmlPullParserException("Call to isEmptyElementTag while not in START_TAG", this, null);
  }

  private void onNextFromStartDocument() {
    if (myRoot != null) {
      push(myRoot);
      myParsingState = START_TAG;
    } else {
      myParsingState = END_DOCUMENT;
    }
  }

  private void onNextFromStartTag() {
    // get the current node, and look for text or children (children first)
    XmlTag node = getCurrentNode();
    assert node != null;  // Should only be called when START_TAG
    XmlTag[] children = node.getSubTags();
    if (children.length > 0) {
      // move to the new child, and don't change the state.
      push(children[0]);

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

  private void onNextFromEndTag() {
    // look for a sibling. if no sibling, go back to the parent
    XmlTag node = getCurrentNode();
    assert node != null;  // Should only be called when END_TAG

    PsiElement sibling = node.getNextSibling();
    while (sibling != null && !(sibling instanceof XmlTag)) {
      sibling = sibling.getNextSibling();
    }
    if (sibling != null) {
      node = (XmlTag)sibling;
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

  // ------- TypedValue stuff
  // This is adapted from com.android.layoutlib.bridge.ResourceHelper
  // (but modified to directly take the parsed value and convert it into pixel instead of
  // storing it into a TypedValue)
  // this was originally taken from platform/frameworks/base/libs/utils/ResourceTypes.cpp

  private static final class DimensionEntry {
    final String name;
    final int type;

    DimensionEntry(String name, int unit) {
      this.name = name;
      this.type = unit;
    }
  }

  /**
   * {@link DimensionEntry} complex unit: Value is raw pixels.
   */
  private static final int COMPLEX_UNIT_PX = 0;
  /**
   * {@link DimensionEntry} complex unit: Value is Device Independent
   * Pixels.
   */
  private static final int COMPLEX_UNIT_DIP = 1;
  /**
   * {@link DimensionEntry} complex unit: Value is a scaled pixel.
   */
  private static final int COMPLEX_UNIT_SP = 2;
  /**
   * {@link DimensionEntry} complex unit: Value is in points.
   */
  private static final int COMPLEX_UNIT_PT = 3;
  /**
   * {@link DimensionEntry} complex unit: Value is in inches.
   */
  private static final int COMPLEX_UNIT_IN = 4;
  /**
   * {@link DimensionEntry} complex unit: Value is in millimeters.
   */
  private static final int COMPLEX_UNIT_MM = 5;

  private final static DimensionEntry[] DIMENSIONS =
    new DimensionEntry[]{new DimensionEntry(UNIT_PX, COMPLEX_UNIT_PX), new DimensionEntry(UNIT_DIP, COMPLEX_UNIT_DIP),
      new DimensionEntry(UNIT_DP, COMPLEX_UNIT_DIP), new DimensionEntry(UNIT_SP, COMPLEX_UNIT_SP),
      new DimensionEntry(UNIT_PT, COMPLEX_UNIT_PT), new DimensionEntry(UNIT_IN, COMPLEX_UNIT_IN),
      new DimensionEntry(UNIT_MM, COMPLEX_UNIT_MM),};

  /**
   * Adds padding to an existing dimension.
   * <p/>This will resolve the attribute value (which can be px, dip, dp, sp, pt, in, mm) to
   * a pixel value, add the padding value ({@link #PADDING_VALUE}),
   * and then return a string with the new value as a px string ("42px");
   * If the conversion fails, only the special padding is returned.
   */
  private String addPaddingToValue(@Nullable String s) {
    if (s == null) {
      return DEFAULT_PADDING_VALUE;
    }
    int padding = PADDING_VALUE;
    if (stringToPixel(s)) {
      padding += myLastPixel;
    }

    return padding + UNIT_PX;
  }

  /** Out value from {@link #stringToPixel(String)}: the integer pixel value */
  private int myLastPixel;

  /**
   * Convert the string into a pixel value, and puts it in {@link #myLastPixel}
   *
   * @param s the dimension value from an XML attribute
   * @return true if success.
   */
  private boolean stringToPixel(String s) {
    // remove the space before and after
    s = s.trim();
    int len = s.length();

    if (len <= 0) {
      return false;
    }

    // check that there's no non ASCII characters.
    char[] buf = s.toCharArray();
    for (int i = 0; i < len; i++) {
      if (buf[i] > 255) {
        return false;
      }
    }

    // check the first character
    if (buf[0] < '0' && buf[0] > '9' && buf[0] != '.') {
      return false;
    }

    // now look for the string that is after the float...
    Matcher m = FLOAT_PATTERN.matcher(s);
    if (m.matches()) {
      String f_str = m.group(1);
      String end = m.group(2);

      float f;
      try {
        f = Float.parseFloat(f_str);
      }
      catch (NumberFormatException e) {
        // this shouldn't happen with the regexp above.
        return false;
      }

      if (end.length() > 0 && end.charAt(0) != ' ') {
        // We only support dimension-type values, so try to parse the unit for dimension
        DimensionEntry dimension = parseDimension(end);
        if (dimension != null) {
          // convert the value into pixel based on the dimension type
          // This is similar to TypedValue.applyDimension()
          switch (dimension.type) {
            case COMPLEX_UNIT_PX:
              // do nothing, value is already in px
              break;
            case COMPLEX_UNIT_DIP:
            case COMPLEX_UNIT_SP: // intended fall-through since we don't
              // adjust for font size
              f *= (float)myDensity.getDpiValue() / Density.DEFAULT_DENSITY;
              break;
            case COMPLEX_UNIT_PT:
              f *= myDensity.getDpiValue() * (1.0f / 72);
              break;
            case COMPLEX_UNIT_IN:
              f *= myDensity.getDpiValue();
              break;
            case COMPLEX_UNIT_MM:
              f *= myDensity.getDpiValue() * (1.0f / 25.4f);
              break;
          }

          // store result (converted to int)
          myLastPixel = (int)(f + 0.5);

          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static DimensionEntry parseDimension(String str) {
    str = str.trim();

    for (DimensionEntry d : DIMENSIONS) {
      if (d.name.equals(str)) {
        return d;
      }
    }

    return null;
  }

  // --- basic implementation of IXmlPullParser ---

  @Override
  public void setFeature(String name, boolean state) throws XmlPullParserException {
    if (FEATURE_PROCESS_NAMESPACES.equals(name) && state) {
      return;
    }
    if (FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name) && state) {
      return;
    }
    throw new XmlPullParserException("Unsupported feature: " + name);
  }

  @Override
  public boolean getFeature(String name) {
    if (FEATURE_PROCESS_NAMESPACES.equals(name)) {
      return true;
    }
    if (FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name)) {
      return true;
    }
    return false;
  }

  @Override
  public void setProperty(String name, Object value) throws XmlPullParserException {
    throw new XmlPullParserException("setProperty() not supported");
  }

  @Nullable
  @Override
  public Object getProperty(String name) {
    return null;
  }

  @Override
  public void setInput(Reader in) throws XmlPullParserException {
    throw new XmlPullParserException("setInput() not supported");
  }

  @Override
  public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException {
    throw new XmlPullParserException("setInput() not supported");
  }

  @Override
  public void defineEntityReplacementText(String entityName, String replacementText) throws XmlPullParserException {
    throw new XmlPullParserException("defineEntityReplacementText() not supported");
  }

  @Override
  public String getNamespacePrefix(int pos) throws XmlPullParserException {
    throw new XmlPullParserException("getNamespacePrefix() not supported");
  }

  @Override
  public String getInputEncoding() {
    return "UTF-8";
  }

  @Override
  public String getNamespace(String prefix) {
    throw new RuntimeException("getNamespace() not supported");
  }

  @Override
  public int getNamespaceCount(int depth) throws XmlPullParserException {
    throw new XmlPullParserException("getNamespaceCount() not supported");
  }

  @Override
  public String getNamespaceUri(int pos) throws XmlPullParserException {
    throw new XmlPullParserException("getNamespaceUri() not supported");
  }

  @Override
  public int getColumnNumber() {
    return -1;
  }

  @Override
  public int getLineNumber() {
    return -1;
  }

  @Override
  public String getAttributeType(int arg0) {
    return "CDATA";
  }

  @Override
  public int getEventType() {
    return myParsingState;
  }

  @Override
  public String getText() {
    return "";
  }

  @Nullable
  @Override
  public char[] getTextCharacters(int[] arg0) {
    return null;
  }

  @Override
  public boolean isAttributeDefault(int arg0) {
    return false;
  }

  @Override
  public boolean isWhitespace() {
    return false;
  }

  @Override
  public int next() throws XmlPullParserException {
    switch (myParsingState) {
      case END_DOCUMENT:
        throw new XmlPullParserException("Nothing after the end");
      case START_DOCUMENT:
        onNextFromStartDocument();
        break;
      case START_TAG:
        onNextFromStartTag();
        break;
      case END_TAG:
        onNextFromEndTag();
        break;
      case TEXT:
        // not used
        break;
      case CDSECT:
        // not used
        break;
      case ENTITY_REF:
        // not used
        break;
      case IGNORABLE_WHITESPACE:
        // not used
        break;
      case PROCESSING_INSTRUCTION:
        // not used
        break;
      case COMMENT:
        // not used
        break;
      case DOCDECL:
        // not used
        break;
    }

    return myParsingState;
  }

  @Override
  public int nextTag() throws XmlPullParserException {
    int eventType = next();
    if (eventType != START_TAG && eventType != END_TAG && eventType != END_DOCUMENT) {
      throw new XmlPullParserException("expected start or end tag: " + XmlPullParser.TYPES[eventType], this, null);
    }
    return eventType;
  }

  @Override
  public String nextText() throws XmlPullParserException {
    if (getEventType() != START_TAG) {
      throw new XmlPullParserException("parser must be on START_TAG to read next text", this, null);
    }
    int eventType = next();
    if (eventType == TEXT) {
      String result = getText();
      eventType = next();
      if (eventType != END_TAG) {
        throw new XmlPullParserException("event TEXT it must be immediately followed by END_TAG", this, null);
      }
      return result;
    }
    else if (eventType == END_TAG) {
      return "";
    }
    else {
      throw new XmlPullParserException("parser must be on START_TAG or TEXT to read text", this, null);
    }
  }

  @Override
  public int nextToken() throws XmlPullParserException {
    return next();
  }

  @Override
  public void require(int type, String namespace, String name) throws XmlPullParserException {
    if (type != getEventType() || (namespace != null && !namespace.equals(getNamespace())) || (name != null && !name.equals(getName()))) {
      throw new XmlPullParserException("expected " + TYPES[type] + getPositionDescription());
    }
  }
}
