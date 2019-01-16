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
package com.android.tools.idea.res.aar;

import com.android.aapt.Resources;
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.XMLNS;
import static com.android.SdkConstants.XMLNS_URI;
import static com.intellij.xml.util.XmlUtil.XML_NAMESPACE_URI;

/**
 * Implementation of {@link XmlPullParser} for XML in a proto format defined in android/frameworks/base/tools/aapt2/Resources.proto.
 *
 * <p>See Also: <a href="http://www.xmlpull.org/">XML Pull Parsing</a>
 * @see Resources.XmlNode
 */
public class ProtoXmlPullParser implements XmlPullParser {
  private InputStream myStream;
  private int myEventType;
  /** A stack of XML nodes reflecting a path from the root to the current node. */
  private final List<Resources.XmlNode> myNodeStack = new ArrayList<>();
  /** A stack of current child indices. Always the same size as {@link #myNodeStack}. */
  private final TIntArrayList myCurrentChildIndices = new TIntArrayList();

  @Override
  public void setFeature(@NotNull String feature, boolean state) throws XmlPullParserException {
    if (XmlPullParser.FEATURE_PROCESS_NAMESPACES.equals(feature) && !state) {
      throw new XmlPullParserException("Cannot turn off namespace processing");
    }
    throw new XmlPullParserException("Unsupported feature: " + feature);
  }

  @Override
  public boolean getFeature(@NotNull String feature) {
    if (XmlPullParser.FEATURE_PROCESS_NAMESPACES.equals(feature)) {
      return true;
    }
    return false;
  }

  @Override
  public void setProperty(@NotNull String property, @Nullable Object value) throws XmlPullParserException {
    throw new XmlPullParserException("Unsupported property: " + property);
  }

  @Override
  @Nullable
  public Object getProperty(@NotNull String property) {
    return null;
  }

  @Override
  public void setInput(@NotNull Reader in) {
    throw new UnsupportedOperationException("Use setInput(InputStream, String) instead");
  }

  @Override
  public void setInput(@NotNull InputStream inputStream, @Nullable String inputEncoding) {
    myStream = inputStream;
    myNodeStack.clear();
    myCurrentChildIndices.clear();
    myEventType = START_DOCUMENT;
  }

  @Override
  @Nullable
  public String getInputEncoding() {
    return null;
  }

  @Override
  public void defineEntityReplacementText(String entityName, String replacementText) {
    throw new UnsupportedOperationException();
  }

  @Override
  public final int getDepth() {
    return myNodeStack.size();
  }

  @Override
  public final int getLineNumber() {
    if (myEventType == START_DOCUMENT) {
      return 1;
    }
    Resources.XmlNode node = getCurrentNode();
    if (node != null && node.hasSource()) {
      return node.getSource().getLineNumber();
    }
    return -1;
  }

  @Override
  public final int getColumnNumber() {
    if (myEventType == START_DOCUMENT) {
      return 0;
    }
    Resources.XmlNode node = getCurrentNode();
    if (node != null && node.hasSource()) {
      return node.getSource().getColumnNumber();
    }
    return -1;
  }

  @Override
  public boolean isWhitespace() throws XmlPullParserException {
    String text = getText();
    if (text == null) {
      throw new XmlPullParserException("Illegal state - no current text");
    }
    for (int i = 0; i < text.length(); i++) {
      if (!Character.isWhitespace(text.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  @Nullable
  public String getText() {
    Resources.XmlNode node = getCurrentNode();
    if (node != null && node.getNodeCase() == Resources.XmlNode.NodeCase.TEXT) {
      return node.getText();
    }
    return null;
  }

  @Override
  @Nullable
  public char[] getTextCharacters(@NotNull int[] holderForStartAndLength) {
    String text = getText();
    if (text == null) {
      return null;
    }
    holderForStartAndLength[0] = 0;
    holderForStartAndLength[1] = text.length();
    return text.toCharArray();
  }

  @Override
  @Nullable
  public String getNamespace() {
    Resources.XmlElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return element.getNamespaceUri();
  }

  @Override
  @Nullable
  public String getName() {
    Resources.XmlElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return getNameWithoutPrefix(element.getName());
  }

  @Override
  @Nullable
  public String getPrefix() {
    Resources.XmlElement element = getCurrentElement();
    if (element == null) {
      return null;
    }
    return getPrefix(element.getName());
  }

  @Override
  public boolean isEmptyElementTag() throws XmlPullParserException {
    Resources.XmlElement element = getCurrentElement();
    if (element == null) {
      throw new XmlPullParserException("Illegal state - no current element");
    }
    return element.getAttributeCount() == 0 && element.getChildCount() == 0;
  }

  @Override
  public int getAttributeCount() {
    Resources.XmlElement element = getCurrentElement();
    if (element == null || myEventType != START_TAG) {
      return -1;
    }
    return element.getAttributeCount();
  }

  @Override
  @NotNull
  public String getAttributeNamespace(int index) {
    return getAttribute(index).getNamespaceUri();
  }

  @Override
  @NotNull
  public String getAttributeName(int index) {
    return getNameWithoutPrefix(getAttribute(index).getName());
  }

  @Override
  @Nullable
  public String getAttributePrefix(int index) {
    return getPrefixFromNamespace(getAttribute(index).getNamespaceUri());
  }

  @Override
  @NotNull
  public String getAttributeType(int index) {
    return "CDATA";
  }

  @Override
  public boolean isAttributeDefault(int index) {
    return false;
  }

  @Override
  @NotNull
  public String getAttributeValue(int index) {
    return getAttribute(index).getValue();
  }

  @Override
  @Nullable
  public String getAttributeValue(@Nullable String namespace, @NotNull String name) {
    Resources.XmlElement element = getCurrentElement();
    if (element == null || myEventType != START_TAG) {
      throw new IndexOutOfBoundsException();
    }

    for (Resources.XmlAttribute attribute : element.getAttributeList()) {
      if (attribute.getName().equals(name)) {
        if (namespace == null) {
          if (attribute.getNamespaceUri().isEmpty()) {
            return attribute.getValue();
          }
        }
        else if (attribute.getNamespaceUri().equals(namespace)) {
          return attribute.getValue();
        }
      }
    }

    return null;
  }

  @Override
  public final int getEventType() {
    return myEventType;
  }

  @Override
  public int next() throws XmlPullParserException, IOException {
    if (myStream == null) {
      throw new XmlPullParserException("Input is not set");
    }

    switch (myEventType) {
      case END_DOCUMENT:
        break;

      case START_DOCUMENT:
        assert myNodeStack.isEmpty();
        assert myCurrentChildIndices.isEmpty();
        Resources.XmlNode rootNode = Resources.XmlNode.parseFrom(myStream);
        myNodeStack.add(rootNode);
        myCurrentChildIndices.add(0);
        myEventType = START_TAG;
        break;

      case START_TAG:
        nextChild();
        break;

      case TEXT:
      case END_TAG:
        myNodeStack.remove(myNodeStack.size() - 1);
        myCurrentChildIndices.remove(myCurrentChildIndices.size() - 1);
        if (myNodeStack.isEmpty()) {
          myEventType = END_DOCUMENT;
        } else {
          nextChild();
        }
        break;
    }

    return myEventType;
  }

  private void nextChild() {
    Resources.XmlElement element = getCurrentElement();
    assert element != null;
    int childIndex = myCurrentChildIndices.get(myCurrentChildIndices.size() - 1);
    if (childIndex < element.getChildCount()) {
      Resources.XmlNode node = element.getChild(childIndex);
      myCurrentChildIndices.set(myCurrentChildIndices.size() - 1, childIndex + 1);
      myNodeStack.add(node);
      myCurrentChildIndices.add(0);
      if (node.hasElement()) {
        myEventType = START_TAG;
      } else {
        if (node.getText().isEmpty()) {
          myEventType = END_TAG;
        } else {
          myEventType = TEXT;
        }
      }
    } else {
      myEventType = END_TAG;
    }
  }

  @Override
  public int nextToken() throws XmlPullParserException, IOException {
    return next();
  }

  @Override
  @NotNull
  public String nextText() throws XmlPullParserException, IOException {
    if (myEventType != START_TAG) {
      throw new XmlPullParserException("Precondition: START_TAG");
    }

    next();

    String text;
    if (myEventType == TEXT) {
      text = getText();
      assert text != null;
      next();
    } else {
      text = "";
    }

    if (myEventType != END_TAG) {
      throw new XmlPullParserException("Expected END_TAG");
    }

    return text;
  }

  @Override
  public int nextTag() throws XmlPullParserException, IOException {
    int myEventType = next();
    if (myEventType == TEXT && isWhitespace()) {
      myEventType = next();
    }
    if (myEventType != START_TAG && myEventType != END_TAG) {
      throw new XmlPullParserException("Expected START_TAG or END_TAG", this, null);
    }
    return myEventType;
  }

  @Override
  public int getNamespaceCount(int depth) {
    if (depth > myNodeStack.size()) {
      throw new IllegalArgumentException("Requested depth (" + depth + ") is greater than current (" + myNodeStack.size() + ")");
    }
    int count = 0;
    for (int i = 0; i < depth; i++) {
      Resources.XmlNode node = myNodeStack.get(i);
      if (node.hasElement()) {
        count += node.getElement().getNamespaceDeclarationCount();
      }
    }
    return count;
  }

  @Override
  @Nullable
  public String getNamespacePrefix(int pos) {
    for (Resources.XmlNode node : myNodeStack) {
      if (node.hasElement()) {
        Resources.XmlElement element = node.getElement();
        int namespaceCount = element.getNamespaceDeclarationCount();
        if (pos < namespaceCount) {
          return element.getNamespaceDeclaration(pos).getPrefix();
        }
        pos -= namespaceCount;
      }
    }
    throw new IndexOutOfBoundsException();
  }

  @Override
  @NotNull
  public String getNamespaceUri(int pos) {
    for (Resources.XmlNode node : myNodeStack) {
      if (node.hasElement()) {
        Resources.XmlElement element = node.getElement();
        int namespaceCount = element.getNamespaceDeclarationCount();
        if (pos < namespaceCount) {
          return element.getNamespaceDeclaration(pos).getUri();
        }
        pos -= namespaceCount;
      }
    }
    throw new IndexOutOfBoundsException();
  }

  @Override
  @Nullable
  public String getNamespace(@Nullable String prefix) {
    if ("xml".equals(prefix)) {
      return XML_NAMESPACE_URI;
    }
    if (XMLNS.equals(prefix)) {
      return XMLNS_URI;
    }

    for (int i = myNodeStack.size(); --i >= 0;) {
      Resources.XmlNode node = myNodeStack.get(i);
      if (node.hasElement()) {
        Resources.XmlElement element = node.getElement();
        for (int j = element.getNamespaceDeclarationCount(); --j >= 0;) {
          Resources.XmlNamespace namespace = element.getNamespaceDeclaration(j);
          if (namespace.getPrefix().equals(prefix)) {
            return namespace.getUri();
          }
        }
      }
    }
    return null;
  }

  @Nullable
  private String getPrefixFromNamespace(@NotNull String namespaceUri) {
    if (namespaceUri.isEmpty()) {
      return null;
    }
    if (XML_NAMESPACE_URI.equals(namespaceUri)) {
      return "xml";
    }
    if (XMLNS_URI.equals(namespaceUri)) {
      return XMLNS;
    }

    for (int i = myNodeStack.size(); --i >= 0;) {
      Resources.XmlNode node = myNodeStack.get(i);
      if (node.hasElement()) {
        Resources.XmlElement element = node.getElement();
        for (int j = element.getNamespaceDeclarationCount(); --j >= 0;) {
          Resources.XmlNamespace namespace = element.getNamespaceDeclaration(j);
          if (namespace.getUri().equals(namespaceUri)) {
            return namespace.getPrefix();
          }
        }
      }
    }
    return null;
  }

  /**
   * Returns a text string describing the current event, the contents of the current node and its source location.
   * For debugging use only.
   *
   * @see {@link XmlPullParser#getPositionDescription()}
   */
  @Override
  public String getPositionDescription() {
    StringBuilder buf = new StringBuilder(myEventType < TYPES.length ? TYPES[myEventType] : "unknown");
    buf.append(' ');

    if (myEventType == START_TAG || myEventType == END_TAG) {
      buf.append('<');
      if (myEventType == END_TAG) {
        buf.append('/');
      }

      String prefix = getPrefix();
      if (prefix != null) {
        buf.append(prefix).append(':');
      }
      buf.append(getName());

      Resources.XmlElement element = getCurrentElement();
      assert element != null;
      int attributeCount = getAttributeCount();
      for (int i = 0; i < attributeCount; i++) {
        buf.append(' ');
        Resources.XmlAttribute attribute = element.getAttribute(i);
        prefix = getPrefixFromNamespace(attribute.getNamespaceUri());
        if (prefix != null) {
          buf.append(prefix).append(':');
        }
        buf.append(attribute.getName());
        buf.append("=\"");
        buf.append(attribute.getValue());
        buf.append('"');
      }

      buf.append('>');
    } else if (myEventType == TEXT) {
      String text = getText();
      assert text != null;
      if (text.length() <= 16) {
        buf.append(text);
      } else {
        buf.append(text, 0, 16).append("...");
      }
    }

    int line = getLineNumber();
    int column = getColumnNumber();
    if (line >= 1) {
      buf.append(" @").append(line);
      if (column >= 0) {
        buf.append(':').append(column);
      }
    }
    return buf.toString();
  }

  @Override
  public void require(int type, @Nullable String namespace, @Nullable String name) throws XmlPullParserException {
    if (type != getEventType()
        || (namespace != null && !namespace.equals(getNamespace()))
        || (name != null && !name.equals(getName()))) {
      throw new XmlPullParserException("Expected: " + TYPES[type] + " {" + namespace + "}" + name);
    }
  }

  @Nullable
  private Resources.XmlNode getCurrentNode() {
    return myNodeStack.isEmpty() ? null : myNodeStack.get(myNodeStack.size() - 1);
  }

  @Nullable
  private Resources.XmlElement getCurrentElement() {
    Resources.XmlNode node = getCurrentNode();
    if (node != null && node.hasElement()) {
      return node.getElement();
    }
    return null;
  }

  @NotNull
  private Resources.XmlAttribute getAttribute(int index) {
    Resources.XmlElement element = getCurrentElement();
    if (element == null || myEventType != START_TAG) {
      throw new IndexOutOfBoundsException();
    }
    return element.getAttribute(index);
  }

  @Nullable
  private static String getPrefix(@NotNull String fullName) {
    int pos = fullName.indexOf(':');
    return pos >= 0 ? fullName.substring(0, pos) : null;
  }

  @NotNull
  private static String getNameWithoutPrefix(@NotNull String fullName) {
    int pos = fullName.indexOf(':');
    return pos >= 0 ? fullName.substring(pos + 1) : fullName;
  }
}
