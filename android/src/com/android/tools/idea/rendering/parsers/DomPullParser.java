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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.utils.XmlUtils;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmlpull.v1.XmlPullParserException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.AUTO_URI;

/**
 * Simple wrapper around an XML document which provides its contents as a pull parser.
 * Most of this is based on the {@link LayoutRenderPullParser} but
 * with DOM nodes instead of PSI elements as the data model
 */
public class DomPullParser extends LayoutPullParser {
  @NotNull
  private final List<Element> myNodeStack;

  @Nullable
  private final Element myRoot;

  @Nullable
  private Map<Element, ?> myViewCookies;

  /**
   * Constructs a new {@link DomPullParser}, a parser which wraps an XML DOM and provides a pull parser interface
   *
   * @param root the root element
   */
  private DomPullParser(@Nullable Element root, @Nullable Map<Element, ?> viewCookies) {
    myRoot = root;
    myNodeStack = new ArrayList<>();
    myViewCookies = viewCookies;
  }

  @Nullable
  @VisibleForTesting
  public Element getRoot() {
    return myRoot;
  }

  @Nullable
  protected Element getCurrentElement() {
    if (!myNodeStack.isEmpty()) {
      return myNodeStack.get(myNodeStack.size() - 1);
    }

    return null;
  }

  @Nullable
  private Attr getAttribute(int i) {
    if (myParsingState != START_TAG) {
      throw new IndexOutOfBoundsException();
    }

    Element element = getCurrentElement();
    if (element != null) {
      return (Attr)element.getAttributes().item(i);
    }

    return null;
  }

  private void push(@NotNull Element node) {
    myNodeStack.add(node);
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  private Element pop() {
    return myNodeStack.remove(myNodeStack.size() - 1);
  }

  // ------------- IXmlPullParser --------

  /**
   * {@inheritDoc}
   * <p/>
   * This implementation returns the underlying DOM node of type {@link Element}.
   * Note that the link between the layout editor and the parsing code depends on this being the actual
   * type returned, so you can't just randomly change it here.
   */
  @Nullable
  @Override
  public Object getViewCookie() {
    Element element = getCurrentElement();
    if (myViewCookies != null) {
      return myViewCookies.get(element);
    }
    return element;
  }

  @NonNull
  @Override
  public ResourceNamespace getLayoutNamespace() {
    if (myRoot == null) {
      return ResourceNamespace.RES_AUTO;
    }
    String uri = myRoot.getNamespaceURI();
    if (uri == null) {
      return ResourceNamespace.RES_AUTO;
    }
    ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(uri);
    return namespace != null ? namespace : ResourceNamespace.RES_AUTO;
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
    Element node = getCurrentElement();

    if (node != null) {
      return node.getAttributes().getLength();
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
    Attr attribute = getAttribute(i);
    if (attribute != null) {
      String localName = attribute.getLocalName();
      if (localName == null) {
        return attribute.getName();
      }
      return localName;
    }

    return null;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public String getAttributeNamespace(int i) {
    Attr attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.getNamespaceURI();
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
    Attr attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.getPrefix();
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
    Attr attribute = getAttribute(i);
    if (attribute != null) {
      return attribute.getValue();
    }

    return null;
  }

  /*
   * This is the main method used by the LayoutInflater to query for attributes.
   */
  @Nullable
  @Override
  public String getAttributeValue(String namespace, String localName) {
    Element element = getCurrentElement();
    if (element != null) {
      Attr attribute = element.getAttributeNodeNS(namespace, localName);

      // Auto-convert http://schemas.android.com/apk/res-auto resources. The lookup
      // will be for the current application's resource package, e.g.
      // http://schemas.android.com/apk/res/foo.bar, but the XML document will
      // be using http://schemas.android.com/apk/res-auto in library projects:
      if (attribute == null && namespace != null && !namespace.equals(ANDROID_URI)) {
        attribute = element.getAttributeNodeNS(AUTO_URI, localName);
      }

      if (attribute != null) {
        return attribute.getValue();
      }
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
      Element currentNode = getCurrentElement();
      assert currentNode != null; // Should only be called when START_TAG
      return currentNode.getTagName();
    }

    return null;
  }

  @Nullable
  @Override
  public String getNamespace() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      Element currentNode = getCurrentElement();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.getNamespaceURI();
    }

    return null;
  }

  @Nullable
  @Override
  public String getPrefix() {
    if (myParsingState == START_TAG || myParsingState == END_TAG) {
      Element currentNode = getCurrentElement();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.getPrefix();
    }

    return null;
  }

  @Override
  public String getNamespace(String prefix) {
    Element currentNode = getCurrentElement();
    return currentNode != null ? currentNode.lookupNamespaceURI(prefix) : null;
  }

  @Override
  public boolean isEmptyElementTag() throws XmlPullParserException {
    if (myParsingState == START_TAG) {
      Element currentNode = getCurrentElement();
      assert currentNode != null;  // Should only be called when START_TAG
      return currentNode.getChildNodes().getLength() == 0;
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
    Element node = getCurrentElement();
    assert node != null;  // Should only be called when START_TAG
    Element first = XmlUtils.getFirstSubTag(node);
    if (first != null) {
      // move to the new child, and don't change the state.
      push(first);

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
    Element node = getCurrentElement();
    assert node != null;  // Should only be called when END_TAG

    Node sibling = node.getNextSibling();
    while (sibling != null && !(sibling instanceof Element)) {
      sibling = sibling.getNextSibling();
    }
    if (sibling != null) {
      node = (Element)sibling;
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

  /**
   * Creates an empty new document builder.
   * <p>
   * The new documents will not validate, will ignore comments, and will
   * support namespaces.
   *
   * @return the new document builder
   */
  @Nullable
  private static DocumentBuilder createNewDocumentBuilder() {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
    factory.setNamespaceAware(true);
    factory.setValidating(false);
    factory.setIgnoringComments(true);
    try {
      return factory.newDocumentBuilder();
    } catch (ParserConfigurationException e) {
      Logger.getInstance(DomPullParser.class).error(e);
    }
    return null;
  }

  /**
   * Creates an empty plain XML document.
   * <p>
   * The new document will not validate, will ignore comments, and will
   * support namespaces.
   *
   * @return the new document
   */
  @Nullable
  public static Document createEmptyPlainDocument() {
    DocumentBuilder builder = createNewDocumentBuilder();
    return builder != null ? builder.newDocument() : null;
  }

  /**
   * Constructs a new {@link DomPullParser}, a parser which wraps an XML DOM and provides a pull parser interface
   *
   * @param root the root element
   */
  @NotNull
  public static ILayoutPullParser createFromDocument(@NotNull Document document) {
    return new DomPullParser(document.getDocumentElement(), null);
  }

  /**
   * Constructs a new {@link DomPullParser}, a parser which wraps an XML DOM and provides a pull parser interface
   *
   * @param root the root element
   */
  @NotNull
  public static ILayoutPullParser createFromDocument(@NotNull Document document, @NotNull Map<Element, ?> viewCookies) {
    return new DomPullParser(document.getDocumentElement(), viewCookies);
  }
}
