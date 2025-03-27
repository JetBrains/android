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
package com.android.tools.idea.apk.viewer;

import com.android.aapt.Resources;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.utils.XmlUtils;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class ProtoXmlPrettyPrinterImpl implements ProtoXmlPrettyPrinter {
  private static final String HTTP_WWW_W3_ORG_2000_XMLNS_URI = "http://www.w3.org/2000/xmlns/";
  private static final String XMLNS_PREFIX = "xmlns";

  @NotNull
  @Override
  public String prettyPrint(byte @NotNull [] content) throws IOException {
    try (InputStream stream = new ByteArrayInputStream(content)) {
      // Convert the protobuf into the aapt DOM representation
      Resources.XmlNode rootNode = Resources.XmlNode.parseFrom(stream);

      // Convert the aapt DOM representation into a w3c DOM representation
      Document document = processDocument(rootNode);

      // Convert the w3c DOM representation into a string, with formatting/pretty printing using
      // Studio heuristics.
      return XmlPrettyPrinter.prettyPrint(document, true);
    }
    catch (Exception e) {
      throw new IOException("Error decoding XML Resource proto buf", e);
    }
  }

  @NotNull
  private static Document processDocument(@NotNull Resources.XmlNode parser) {
    Document document = XmlUtils.createDocument(true);
    NamespaceScope rootScope = NamespaceScope.createRootScope();
    processXmlNode(parser, document, rootScope);
    return document;
  }

  private static void processXmlNode(@NotNull Resources.XmlNode resourceNode, @NotNull Node parentNode, @NotNull NamespaceScope parentScope) {
    switch(resourceNode.getNodeCase()) {
      case ELEMENT:
        processElement(resourceNode.getElement(), parentNode, parentScope);
        break;
      case TEXT:
        processText(resourceNode, parentNode);
        break;
      case NODE_NOT_SET:
        break;
    }
  }

  private static void processElement(@NotNull Resources.XmlElement resourceElement, @NotNull Node parentNode, @NotNull NamespaceScope parentScope) {
    // New element
    Element newElement = getDocument(parentNode).createElementNS(resourceElement.getNamespaceUri(),
                                                                 parentScope.getQualifiedName(resourceElement.getNamespaceUri(),
                                                                                              resourceElement.getName()));
    NamespaceScope newScope = parentScope.createScope();
    parentNode.appendChild(newElement);

    // Register namespace declarations on the element and in the scope for prefix resolution later
    for (Resources.XmlNamespace x : resourceElement.getNamespaceDeclarationList()) {
      newElement.setAttributeNS(HTTP_WWW_W3_ORG_2000_XMLNS_URI, XMLNS_PREFIX + ":" + x.getPrefix(), x.getUri());
      newScope.declareNamespace(x.getUri(), x.getPrefix());
    }

    // Attributes
    for (Resources.XmlAttribute attr : resourceElement.getAttributeList()) {
      newElement.setAttributeNS(attr.getNamespaceUri(),
                                newScope.getQualifiedName(attr.getNamespaceUri(), attr.getName()),
                                attr.getValue());
    }

    // Children
    for(Resources.XmlNode childNode : resourceElement.getChildList()) {
      processXmlNode(childNode, newElement, newScope);
    }
  }

  @NotNull
  private static Document getDocument(@NotNull Node parentNode) {
    if (parentNode instanceof Document) {
      return (Document)parentNode;
    }
    return parentNode.getOwnerDocument();
  }

  private static void processText(@NotNull Resources.XmlNode resourceNode, @NotNull Node parentNode) {
    Text newElement = getDocument(parentNode).createTextNode(resourceNode.getText());
    parentNode.appendChild(newElement);
  }

  /**
   * Represents a scope of XML namespace definition (URI => prefix) with an optional
   * parent scope.
   */
  private static abstract class NamespaceScope {

    /**
     * Creates the top level namespace scope, immutable.
     */
    @NotNull
    public static NamespaceScope createRootScope() {
      return new RootScope();
    }

    /**
     * Returns the qualified name, i.e. namespace prefix + ":" + name, of the passed in
     * name if the namespace URI has been defined in this scope or any parent scope.
     * If the namespace URI has not been defined, returns the name as is.
     */
    @NotNull
    public abstract String getQualifiedName(@NotNull String uri, @NotNull String name);

    /**
     * Add a prefix alias for the namespace URI that will be used by {@link #getQualifiedName(String, String)} to
     * alias namespace URIs to their corresponding prefix.
     */
    public abstract void declareNamespace(@NotNull String uri, @NotNull String prefix);

    /**
     * Returns a new child scope of this scope.
     */
    @NotNull
    public NamespaceScope createScope() {
      return new NestedScoped(this);
    }

    private static class RootScope extends NamespaceScope {
      @NotNull
      @Override
      public String getQualifiedName(@NotNull String uri, @NotNull String name) {
        return name;
      }

      @Override
      public void declareNamespace(@NotNull String uri, @NotNull String prefix) {
        throw new IllegalStateException("Root namespace scope cannot contain namespace declarations");
      }
    }

    private static class NestedScoped extends NamespaceScope {
      private final NamespaceScope myParentScope;
      private HashMap<String, String> myPrefixes;

      public NestedScoped(@NotNull NamespaceScope parentScope) {
        myParentScope = parentScope;
      }

      @NotNull
      @Override
      public String getQualifiedName(@NotNull String uri, @NotNull String name) {
        if (myPrefixes == null || !myPrefixes.containsKey(uri)) {
          return myParentScope.getQualifiedName(uri, name);
        }

        return myPrefixes.get(uri) + ":" + name;
      }

      @Override
      public void declareNamespace(@NotNull String uri, @NotNull String prefix) {
        if (myPrefixes == null) {
          myPrefixes = new HashMap<>();
        }
        myPrefixes.put(uri, prefix);
      }
    }
  }
}
