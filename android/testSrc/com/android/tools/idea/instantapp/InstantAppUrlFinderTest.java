/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.instantapp;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Collection;
import java.util.Iterator;

import static org.junit.Assert.*;

public class InstantAppUrlFinderTest {

  @Rule
  public final ExpectedException myException = ExpectedException.none();

  @Test
  public void getAllUrls() throws Exception {
    InstantAppUrlFinder finder = new InstantAppUrlFinder(createDummyValidData());

    assertEquals(8, finder.getAllUrls().size());
    Iterator<String> iterator = finder.getAllUrls().iterator();
    assertEquals("scheme1://domainB/pathPatternB", iterator.next());
    assertEquals("scheme2://domainB/pathPatternB", iterator.next());
    assertEquals("scheme1://domainD/", iterator.next());
    assertEquals("scheme2://domainD/", iterator.next());
    assertEquals("scheme1://domainA/pathA", iterator.next());
    assertEquals("scheme2://domainA/pathA", iterator.next());
    assertEquals("scheme1://domainC/pathPrefixC/example", iterator.next());
    assertEquals("scheme2://domainC/pathPrefixC/example", iterator.next());
  }

  @Test
  public void getAllUrls_invalidInput() throws Exception {
    InstantAppUrlFinder invalidFinder = new InstantAppUrlFinder(ImmutableList.of((Element)createXMLContent("<intent-filter/>")));

    assertEquals(0, invalidFinder.getAllUrls().size());
  }

  @Test
  public void getDefaultUrl() throws Exception {
    InstantAppUrlFinder finder = new InstantAppUrlFinder(createDummyValidData());

    assertEquals("scheme1://domainB/pathPatternB", finder.getDefaultUrl());
  }

  @NotNull
  private static Collection<Element> createDummyValidData() throws Exception {
    return ImmutableList.of(
      (Element)createXMLContent("<activity>" +
                                "  <intent-filter android:order=\"3\">" +
                                "    <data android:host=\"domainA\"\n" +
                                "          android:path=\"/pathA\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainA\"\n" +
                                "          android:path=\"/pathA\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "  <intent-filter android:order=\"1\">" +
                                "    <data android:host=\"domainB\"\n" +
                                "          android:pathPattern=\"/pathPatternB\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainB\"\n" +
                                "          android:pathPattern=\"/pathPatternB\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "</activity>"),
      (Element)createXMLContent("<activity>" +
                                "  <intent-filter android:order=\"4\">" +
                                "    <data android:host=\"domainC\"\n" +
                                "          android:pathPrefix=\"/pathPrefixC\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainC\"\n" +
                                "          android:pathPrefix=\"/pathPrefixC\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "  <intent-filter android:order=\"2\">" +
                                "    <data android:host=\"domainD\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainD\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "</activity>")
    );
  }

  @Test
  public void getDefaultUrl_invalidInput() throws Exception {
    InstantAppUrlFinder invalidFinder = new InstantAppUrlFinder(ImmutableList.of((Element)createXMLContent("<intent-filter/>")));

    assertEquals("", invalidFinder.getDefaultUrl());
  }

  @Test
  public void instantAppIntentFilterWrapper() throws Exception {
    Element intentWithData = (Element)createXMLContent("<intent-filter android:order=\"3\">" +
                                                       "<data android:host=\"domain\"\n" +
                                                       "      android:pathPrefix=\"/pathPrefix\"\n" +
                                                       "      android:scheme=\"scheme1\" />" +
                                                       "<data android:host=\"domain\"\n" +
                                                       "      android:pathPattern=\"/pathPattern\"\n" +
                                                       "      android:scheme=\"scheme2\" />" +
                                                       "</intent-filter>");

    InstantAppUrlFinder.InstantAppIntentFilterWrapper wrapper = InstantAppUrlFinder.InstantAppIntentFilterWrapper.of(intentWithData);

    assertEquals(2, wrapper.getAllUrlData().size());
    assertEquals(3, wrapper.getOrder());
    Iterator<InstantAppUrlFinder.UrlData> iterator = wrapper.getAllUrlData().iterator();
    assertEquals("scheme1://domain/pathPrefix/example", iterator.next().getUrl());
    assertEquals("scheme2://domain/pathPattern", iterator.next().getUrl());
  }

  @Test
  public void instantAppIntentFilterWrapper_getElement_wrongName() throws Exception {
    Node wrongNameNode = createXMLContent("<something-filter foo=\"bar\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(wrongNameNode);
  }

  @Test
  public void instantAppIntentFilterWrapper_getElement_notAnElement() throws Exception {
    Node notAnElement = createXMLContent("<something-filter foo=\"bar\"/>").getAttributes().item(0);

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(notAnElement);
  }

  @Test
  public void instantAppIntentFilterWrapper_getElement_Valid() throws Exception {
    Node intentFilterNode = createXMLContent("<intent-filter/>");

    assertEquals(intentFilterNode, InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(intentFilterNode));
  }

  @Test
  public void instantAppIntentFilterWrapper_getOrder_Valid() throws Exception {
    Element intentWithOrder = (Element)createXMLContent("<intent-filter android:order=\"7\"/>");

    assertEquals(7, InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(intentWithOrder));
  }

  @Test
  public void instantAppIntentFilterWrapper_getOrder_negativeValue() throws Exception {
    Element negativeValuesAreInvalid = (Element)createXMLContent("<intent-filter android:order=\"-3\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(negativeValuesAreInvalid);
  }

  @Test
  public void instantAppIntentFilterWrapper_getOrder_nonNumericalValue() throws Exception {
    Element notANumber = (Element)createXMLContent("<intent-filter android:order=\"foo\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(notANumber);
  }

  @Test
  public void instantAppIntentFilterWrapper_getOrder_nonIntegerValue() throws Exception {
    Element notANumber = (Element)createXMLContent("<intent-filter android:order=\"2.5\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(notANumber);
  }

  @Test
  public void instantAppIntentFilterWrapper_getOrder_missingAttribute() throws Exception {
    Element missingAttribute = (Element)createXMLContent("<intent-filter foo=\"3\"/>");

    assertEquals(0, InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(missingAttribute));
  }

  @Test
  public void urlData_urlDataValidInput() throws Exception {

    Node dataNode = createXMLContent("<data android:host=\"domain\"\n" +
                                     "      android:pathPattern=\"/pathPattern\"\n" +
                                     "      android:scheme=\"scheme\" />");
    InstantAppUrlFinder.UrlData urlData = InstantAppUrlFinder.UrlData.of(dataNode);

    assertTrue(urlData.isValid());
  }

  @Test
  public void urlData_wrongNode() throws Exception {
    Node wrongNode = createXMLContent("<datum android:host=\"domain\"\n" +
                                      "       instant:pathPattern=\"/pathPattern\"\n" +
                                      "       android:scheme=\"scheme\" />");

    assertFalse(InstantAppUrlFinder.UrlData.of(wrongNode).isValid());
  }

  @Test
  public void urlData_wrongNamespace() throws Exception {
    Node wrongNamespace = createXMLContent("<data android:host=\"domain\"\n" +
                                           "      android:pathPattern=\"/pathPattern\"\n" +
                                           "      instant:scheme=\"scheme\" />");

    assertFalse(InstantAppUrlFinder.UrlData.of(wrongNamespace).isValid());
  }

  @Test
  public void urlData_notAnElement() throws Exception {
    Node notAnElement = createXMLContent("<data test=\"test\" />").getAttributes().item(0);

    assertFalse(InstantAppUrlFinder.UrlData.of(notAnElement).isValid());
  }

  @Test
  public void urlData_missingData() throws Exception {
    Node missingData = createXMLContent("<data android:host=\"domain\"\n" +
                                        "      android:pathPattern=\"/pathPattern\"\n/>");

    assertFalse(InstantAppUrlFinder.UrlData.of(missingData).isValid());
  }

  @NotNull
  private static Node createXMLContent(@NotNull String innerXML) throws Exception {
    String xmlString = "<?xml version=\"1.0\"?>" +
                       "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "          xmlns:instant=\"http://schemas.android.com/instantapps\">" +
                       innerXML +
                       "</manifest>";
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    DocumentBuilder builder;
    factory.setNamespaceAware(true);
    builder = factory.newDocumentBuilder();
    Document document = builder.parse(new InputSource(new StringReader(xmlString)));
    return document.getFirstChild().getFirstChild();
  }

  @Test
  public void urlData_getUrl_withPath() {
    assertEquals("scheme://domain.url/full/path", new InstantAppUrlFinder.UrlData("scheme", "domain.url", "/full/path", "", "").getUrl());
  }

  @Test
  public void urlData_getUrl_withPrefix() {
    assertEquals("scheme://domain.url/prefix/example", new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "/prefix", "").getUrl());
  }

  @Test
  public void urlData_getUrl_withPattern() {
    assertEquals("scheme://domain.url/example/X/Y", new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "", "/.*/X/Y").getUrl());
  }

  @Test
  public void urlData_getUrl_hostOnly() {
    assertEquals("scheme://domain.url/", new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "", "").getUrl());
  }

  @Test
  public void urlData_isValid_validInputPath() {
    assertTrue(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "/path", "", "").isValid());
  }

  @Test
  public void urlData_isValid_validInputPrefix() {
    assertTrue(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "/prefix", "").isValid());
  }

  @Test
  public void urlData_isValid_validInputPattern() {
    assertTrue(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "", "/pattern").isValid());
  }

  @Test
  public void urlData_isValid_validInputHostOnly() {
    assertTrue(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "", "").isValid());
  }

  @Test
  public void urlData_isValid_missingForwardSlashInPath() {
    assertFalse(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "path", "", "").isValid());
  }

  @Test
  public void urlData_isValid_missingForwardSlashInPrefix() {
    assertFalse(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "prefix", "").isValid());
  }

  @Test
  public void urlData_isValid_missingForwardSlashInPattern() {
    assertFalse(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "", "", "pattern").isValid());
  }

  @Test
  public void urlData_isValid_missingHost() {
    assertFalse(new InstantAppUrlFinder.UrlData("scheme", "", "", "", "/pattern").isValid());
  }

  @Test
  public void urlData_isValid_missingScheme() {
    assertFalse(new InstantAppUrlFinder.UrlData("", "domain.url", "", "", "/pattern").isValid());
  }

  @Test
  public void urlData_convertPatternToExample() {
    assertEquals("example", InstantAppUrlFinder.UrlData.convertPatternToExample(".*"));
  }
}