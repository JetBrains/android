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
package org.jetbrains.android.dom.converters;

import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.impl.ConvertContextFactory;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xml.impl.DomManagerImpl;
import org.jetbrains.android.dom.resources.StyleItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DimensionConverterTest extends LightIdeaTestCase {
  public void test() {
    DimensionConverter converter = new DimensionConverter();
    StyleItem element = createElement("<item>10dp</item>", StyleItem.class);

    DomInvocationHandler handler = DomManagerImpl.getDomInvocationHandler(element);
    assertNotNull(handler);
    ConvertContext context = ConvertContextFactory.createConvertContext(handler);

    List<String> variants = new ArrayList<>(converter.getVariants(context));
    Collections.sort(variants);
    assertEquals(Arrays.asList("10dp", "10in", "10mm", "10pt", "10px", "10sp"), variants);

    // Valid units
    assertEquals("1dip", converter.fromString("1dip", context));
    assertEquals("1dp", converter.fromString("1dp", context));
    assertEquals("1px", converter.fromString("1px", context));
    assertEquals("1in", converter.fromString("1in", context));
    assertEquals("1mm", converter.fromString("1mm", context));
    assertEquals("1sp", converter.fromString("1sp", context));
    assertEquals("1pt", converter.fromString("1pt", context));

    // Invalid dimensions (missing units)
    assertNull(converter.fromString("not_a_dimension", context));
    assertNull(converter.fromString("", context));
    assertEquals("Cannot resolve symbol ''", converter.getErrorMessage("", context));
    assertNull(converter.fromString("1", context));
    assertEquals("Cannot resolve symbol '1'", converter.getErrorMessage("1", context));
    assertNull(converter.fromString("1.5", context));
    assertEquals("Cannot resolve symbol '1.5'", converter.getErrorMessage("1.5", context));

    // Unknown units
    assertNull(converter.fromString("15d", context));
    assertEquals("Unknown unit 'd'", converter.getErrorMessage("15d", context));
    assertNull(converter.fromString("15wrong", context));
    assertEquals("Unknown unit 'wrong'", converter.getErrorMessage("15wrong", context));

    // Normal conversions
    assertEquals("15px", converter.fromString("15px", context));
    assertEquals("15", DimensionConverter.getIntegerPrefix("15px"));
    assertEquals("px", DimensionConverter.getUnitFromValue("15px"));

    // Make sure negative numbers work
    assertEquals("-10px", converter.fromString("-10px", context));
    assertEquals("-10", DimensionConverter.getIntegerPrefix("-10px"));
    assertEquals("px", DimensionConverter.getUnitFromValue("-10px"));

    // Make sure decimals work
    assertEquals("1.5sp", converter.fromString("1.5sp", context));
    assertEquals("1.5", DimensionConverter.getIntegerPrefix("1.5sp"));
    assertEquals("sp", DimensionConverter.getUnitFromValue("1.5sp"));
    assertEquals(".5sp", converter.fromString(".5sp", context));
    assertEquals(".5", DimensionConverter.getIntegerPrefix(".5sp"));
    assertEquals("sp", DimensionConverter.getUnitFromValue(".5sp"));

    // Make sure the right type of decimal separator is used
    assertNull(converter.fromString("1,5sp", context));
    assertEquals("Use a dot instead of a comma as the decimal mark", converter.getErrorMessage("1,5sp", context));

    // Documentation
    assertEquals("<html><body>" +
                 "<b>Density-independent Pixels</b> - an abstract unit that is based on the physical density of the screen." +
                 "</body></html>", converter.getDocumentation("1dp"));
    assertEquals("<html><body>" +
                 "<b>Pixels</b> - corresponds to actual pixels on the screen. Not recommended." +
                 "</body></html>", converter.getDocumentation("-10px"));
    assertEquals("<html><body>" +
                 "<b>Scale-independent Pixels</b> - this is like the dp unit, but " +
                 "it is also scaled by the user's font size preference." +
                 "</body></html>", converter.getDocumentation("1.5sp"));
  }

  // Based on code in com.intellij.util.xml.impl.DomTestCase
  @SuppressWarnings("deprecation")
  protected <T extends DomElement> T createElement(final String xml, final Class<T> aClass) throws IncorrectOperationException {
    final DomManagerImpl domManager = (DomManagerImpl)DomManager.getDomManager(getProject());
    final String name = "a.xml";
    final XmlFile file = (XmlFile)PsiFileFactory.getInstance(domManager.getProject()).createFileFromText(name, xml);
    assertNotNull(file);
    XmlDocument document = file.getDocument();
    assertNotNull(document);
    final XmlTag tag = document.getRootTag();
    final String rootTagName = tag != null ? tag.getName() : "root";
    final T element = domManager.getFileElement(file, aClass, rootTagName).getRootElement();
    assertNotNull(element);
    assertSame(tag, element.getXmlTag());
    return element;
  }
}
