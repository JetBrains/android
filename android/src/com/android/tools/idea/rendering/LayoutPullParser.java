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
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.InputStream;
import java.io.Reader;

public abstract class LayoutPullParser implements ILayoutPullParser {
  protected int myParsingState = START_DOCUMENT;

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
  public void setFeature(String name, boolean state) throws XmlPullParserException {
    if (FEATURE_PROCESS_NAMESPACES.equals(name) && state) {
      return;
    }
    if (FEATURE_REPORT_NAMESPACE_ATTRIBUTES.equals(name) && state) {
      return;
    }
    throw new XmlPullParserException("Unsupported feature: " + name);
  }

  // --- basic implementation of IXmlPullParser ---

  @Override
  public int getEventType() {
    return myParsingState;
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
    if (type != getEventType() || (namespace != null &&
                                   !namespace.equals(getNamespace())) || (name != null && !name.equals(getName()))) {
      throw new XmlPullParserException("expected " + TYPES[type] + getPositionDescription());
    }
  }

  protected abstract void onNextFromStartDocument();
  protected abstract void onNextFromStartTag();
  protected abstract void onNextFromEndTag();
}
