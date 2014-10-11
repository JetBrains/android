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
package com.android.tools.idea.editors.navigation.model;

import org.xml.sax.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

public class XMLReader {
  private final InputStream in;

  public XMLReader(InputStream in) {
    this.in = in;
  }

  public Object read() {
    try {
      SAXParserFactory factory = SAXParserFactory.newInstance();
      SAXParser parser = factory.newSAXParser();
      org.xml.sax.XMLReader reader = parser.getXMLReader();

      ErrorHandler errorHandler = reader.getErrorHandler();
      if (errorHandler == null) {
        errorHandler = new ErrorHandler() {
          @Override
          public void warning(SAXParseException e) throws SAXException {
            System.err.println("Warning, line " + e.getLineNumber() + ": " + e);
          }

          @Override
          public void error(SAXParseException e) throws SAXException {
            System.err.print("Error on line " + e.getLineNumber());
            throw new SAXException(e);
          }

          @Override
          public void fatalError(SAXParseException e) throws SAXException {
            System.err.print("Fatal error on line " + e.getLineNumber());
            throw new SAXException(e);
          }
        };
      }
      ReflectiveHandler handler = new ReflectiveHandler(errorHandler);
      reader.setContentHandler(handler);
      reader.parse(new InputSource(in));
      return handler.result;
    }
    catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
    catch (SAXException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
