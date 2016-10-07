/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.google.common.base.Charsets;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.AndroidTestCase;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

/**
 * Tests for Template Globals
 */
public class TypedVariableTest extends AndroidTestCase {

  public void testParse() throws Exception {
    assertEquals(TypedVariable.parse(TypedVariable.Type.BOOLEAN, "true"), Boolean.TRUE);
    assertEquals(TypedVariable.parse(TypedVariable.Type.BOOLEAN, "false"), Boolean.FALSE);
    assertEquals(TypedVariable.parse(TypedVariable.Type.BOOLEAN, "maybe"), null);
    assertEquals(TypedVariable.parse(TypedVariable.Type.BOOLEAN, null), null);

    assertEquals(TypedVariable.parse(TypedVariable.Type.INTEGER, "123"), Integer.valueOf(123));
    assertEquals(TypedVariable.parse(TypedVariable.Type.INTEGER, "one-two-three"), null);
    assertEquals(TypedVariable.parse(TypedVariable.Type.INTEGER, null), null);

    assertEquals(TypedVariable.parse(TypedVariable.Type.STRING, "pass through"), "pass through");
    assertEquals(TypedVariable.parse(TypedVariable.Type.STRING, null), null);
  }

  public void testParseGlobal() throws Exception {
    final Map<String, Object> paramMap = new HashMap<>();

    File xmlFile = new File(FileUtil.join(getTestDataPath(), "templates", "globals.xml"));
    String xml = TemplateUtils.readTextFromDisk(xmlFile);
    ByteArrayInputStream inputStream = new ByteArrayInputStream(xml.getBytes(Charsets.UTF_8.toString()));
    Reader reader = new InputStreamReader(inputStream, Charsets.UTF_8.toString());
    InputSource inputSource = new InputSource(reader);
    inputSource.setEncoding(Charsets.UTF_8.toString());
    SAXParserFactory.newInstance().newSAXParser().parse(inputSource, new DefaultHandler() {
      @Override
      public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
        if (Template.TAG_GLOBAL.equals(name)) {
          String id = attributes.getValue(Template.ATTR_ID);
          if (!paramMap.containsKey(id)) {
            paramMap.put(id, TypedVariable.parseGlobal(attributes));
          }
        }
      }
    });

    assertEquals("I am a string", paramMap.get("thisIsAnImplicitString"));
    assertEquals("I'm a real string!", paramMap.get("thisIsAPinnochioString"));
    assertEquals("I get interpreted as a string", paramMap.get("thisIsAStringByDefault"));
    assertEquals(Integer.valueOf(128), paramMap.get("thisIsAnInteger"));
    assertEquals("123abc", paramMap.get("thisIsAMalformedInteger"));
    assertEquals(Boolean.TRUE, paramMap.get("thisIsATrueBoolean"));
    assertEquals(Boolean.FALSE, paramMap.get("thisIsAFalseBoolean"));
  }
}
