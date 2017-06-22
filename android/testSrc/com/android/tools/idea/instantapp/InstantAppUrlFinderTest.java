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
import org.hamcrest.CoreMatchers;
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

import static org.junit.Assert.*;

public class InstantAppUrlFinderTest {

  @Rule
  public final ExpectedException myException = ExpectedException.none();

  @Test
  public void testGetAllUrls() throws Exception {
    InstantAppUrlFinder finder = new InstantAppUrlFinder(createDummyValidData());

    assertEquals(4, finder.getAllUrls().size());
    Collection<String> urls = finder.getAllUrls();
    assertThat(urls, CoreMatchers.hasItem(CoreMatchers.anyOf(
      CoreMatchers.equalTo("scheme1://domainA/pathA"),
      CoreMatchers.equalTo("scheme2://domainA/pathA"))));
    assertThat(urls, CoreMatchers.hasItem("scheme1://domainB/pathPatternB"));
    assertThat(urls, CoreMatchers.hasItem(CoreMatchers.anyOf(
      CoreMatchers.equalTo("scheme1://domainC/pathPrefixC/example"),
      CoreMatchers.equalTo("scheme2://domainC/pathPrefixC/example"))));
    assertThat(urls, CoreMatchers.hasItem(CoreMatchers.anyOf(
      CoreMatchers.equalTo("scheme1://domainD/"),
      CoreMatchers.equalTo("scheme2://domainD/"))));
  }

  @Test
  public void testGetAllUrlsInvalidInput() throws Exception {
    InstantAppUrlFinder invalidFinder = new InstantAppUrlFinder(ImmutableList.of((Element)createXMLContent("<intent-filter/>")));

    assertEquals(0, invalidFinder.getAllUrls().size());
  }

  @Test
  public void testGetDefaultUrl() throws Exception {
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
                                "    <data android:host=\"domainB\" />" +
                                "    <data android:pathPattern=\"/pathPatternB\" />" +
                                "    <data android:scheme=\"scheme1\" />" +
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
  public void testGetDefaultUrlInvalidInput() throws Exception {
    InstantAppUrlFinder invalidFinder = new InstantAppUrlFinder(ImmutableList.of((Element)createXMLContent("<intent-filter/>")));

    assertEquals("", invalidFinder.getDefaultUrl());
  }

  @Test
  public void testInstantAppIntentFilterWrapper() throws Exception {
    Element intentWithData = (Element)createXMLContent("<intent-filter android:order=\"3\">" +
                                                       "<data android:host=\"domain\"\n" +
                                                       "      android:pathPrefix=\"/pathPrefix\"\n" +
                                                       "      android:scheme=\"scheme1\" />" +
                                                       "<data android:host=\"domain\"\n" +
                                                       "      android:pathPattern=\"/pathPattern\"\n" +
                                                       "      android:scheme=\"scheme2\" />" +
                                                       "</intent-filter>");

    InstantAppUrlFinder.InstantAppIntentFilterWrapper wrapper = InstantAppUrlFinder.InstantAppIntentFilterWrapper.of(intentWithData);

    assertNotNull(wrapper.getUrlData());
    assertTrue(wrapper.getUrlData().isValid());
    assertEquals(3, wrapper.getOrder());
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetElementWrongName() throws Exception {
    Node wrongNameNode = createXMLContent("<something-filter foo=\"bar\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(wrongNameNode);
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetElementNotAnElement() throws Exception {
    Node notAnElement = createXMLContent("<something-filter foo=\"bar\"/>").getAttributes().item(0);

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(notAnElement);
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetElementValid() throws Exception {
    Node intentFilterNode = createXMLContent("<intent-filter/>");

    assertEquals(intentFilterNode, InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(intentFilterNode));
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetOrderValid() throws Exception {
    Element intentWithOrder = (Element)createXMLContent("<intent-filter android:order=\"7\"/>");

    assertEquals(7, InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(intentWithOrder));
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetOrderNegativeValue() throws Exception {
    Element negativeValuesAreInvalid = (Element)createXMLContent("<intent-filter android:order=\"-3\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(negativeValuesAreInvalid);
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetOrderNonNumericalValue() throws Exception {
    Element notANumber = (Element)createXMLContent("<intent-filter android:order=\"foo\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(notANumber);
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetOrderNonIntegerValue() throws Exception {
    Element notANumber = (Element)createXMLContent("<intent-filter android:order=\"2.5\"/>");

    myException.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(notANumber);
  }

  @Test
  public void testInstantAppIntentFilterWrapperGetOrderMissingAttribute() throws Exception {
    Element missingAttribute = (Element)createXMLContent("<intent-filter foo=\"3\"/>");

    assertEquals(0, InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(missingAttribute));
  }

  @Test
  public void testUrlDataValidInput() throws Exception {
    Node dataNode = createXMLContent("<data android:host=\"domain\"\n" +
                                     "      android:pathPattern=\"/pathPattern\"\n" +
                                     "      android:scheme=\"scheme\" />");
    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromNode(dataNode);

    assertTrue(urlData.isValid());
  }

  @Test
  public void testUrlDataValidInput2() throws Exception {
    Node dataNode1 = createXMLContent("<data android:host=\"domain\" />");
    Node dataNode2 = createXMLContent("<data android:pathPattern=\"/pathPattern\" />");
    Node dataNode3 = createXMLContent("<data android:scheme=\"scheme\" />");
    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromNode(dataNode1);
    urlData.addFromNode(dataNode2);
    urlData.addFromNode(dataNode3);

    assertTrue(urlData.isValid());
  }

  @Test
  public void testUrlDataWrongNode() throws Exception {
    Node wrongNode = createXMLContent("<datum android:host=\"domain\"\n" +
                                      "       instant:pathPattern=\"/pathPattern\"\n" +
                                      "       android:scheme=\"scheme\" />");

    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromNode(wrongNode);

    assertFalse(urlData.isValid());
  }

  @Test
  public void testUrlDataWrongNamespace() throws Exception {
    Node wrongNamespace = createXMLContent("<data android:host=\"domain\"\n" +
                                           "      android:pathPattern=\"/pathPattern\"\n" +
                                           "      instant:scheme=\"scheme\" />");

    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromNode(wrongNamespace);

    assertFalse(urlData.isValid());
  }

  @Test
  public void testUrlDataNotAnElement() throws Exception {
    Node notAnElement = createXMLContent("<data test=\"test\" />").getAttributes().item(0);

    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromNode(notAnElement);

    assertFalse(urlData.isValid());
  }

  @Test
  public void urlData_missingData() throws Exception {
    Node missingData = createXMLContent("<data android:host=\"domain\"\n" +
                                        "      android:pathPattern=\"/pathPattern\"\n/>");

    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromNode(missingData);

    assertFalse(urlData.isValid());
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
  public void testUrlDataGetUrlWithPath() {
    assertEquals("scheme://domain.url/full/path", urlDataFromStrings("scheme", "domain.url", "/full/path", "", "").getUrl());
  }

  @Test
  public void testUrlDataGetUrlWithPrefix() {
    assertEquals("scheme://domain.url/prefix/example", urlDataFromStrings("scheme", "domain.url", "", "/prefix", "").getUrl());
  }

  @Test
  public void testUrlDataGetUrlWithPattern() {
    assertEquals("scheme://domain.url/example/X/Y", urlDataFromStrings("scheme", "domain.url", "", "", "/.*/X/Y").getUrl());
  }

  @Test
  public void urlData_getUrl_hostOnly() {
    assertEquals("scheme://domain.url/", urlDataFromStrings("scheme", "domain.url", "", "", "").getUrl());
  }

  @Test
  public void testUrlDataIsValidValidInputPath() {
    assertTrue(urlDataFromStrings("scheme", "domain.url", "/path", "", "").isValid());
  }

  @Test
  public void testUrlDataIsValidValidInputPrefix() {
    assertTrue(urlDataFromStrings("scheme", "domain.url", "", "/prefix", "").isValid());
  }

  @Test
  public void testUrlDataIsValidValidInputPattern() {
    assertTrue(urlDataFromStrings("scheme", "domain.url", "", "", "/pattern").isValid());
  }

  @Test
  public void testUrlDataIsValidValidInputHostOnly() {
    assertTrue(urlDataFromStrings("scheme", "domain.url", "", "", "").isValid());
  }

  @Test
  public void testUrlDataIsValidMissingForwardSlashInPath() {
    assertFalse(urlDataFromStrings("scheme", "domain.url", "path", "", "").isValid());
  }

  @Test
  public void testUrlDataIsValidMissingForwardSlashInPrefix() {
    assertFalse(urlDataFromStrings("scheme", "domain.url", "", "prefix", "").isValid());
  }

  @Test
  public void testUrlDataIsValidMissingForwardSlashInPattern() {
    assertFalse(urlDataFromStrings("scheme", "domain.url", "", "", "pattern").isValid());
  }

  @Test
  public void testUrlDataIsValidMissingHost() {
    assertFalse(urlDataFromStrings("scheme", "", "", "", "/pattern").isValid());
  }

  @Test
  public void testUrlDataIsValidMissingScheme() {
    assertFalse(urlDataFromStrings("", "domain.url", "", "", "/pattern").isValid());
  }

  @Test
  public void testUrlDataConvertPatternToExample() {
    assertEquals("example", InstantAppUrlFinder.UrlData.convertPatternToExample(".*"));
  }

  @Test
  public void testUrlDataMatch() {
    assertTrue(urlDataFromStrings("http", "example.com", "", "", "").matchesUrl("http://example.com"));
    assertTrue(urlDataFromStrings("http", "example.com", "", "", "").matchesUrl("http://example.com/"));
    assertFalse(urlDataFromStrings("http", "example.com", "/test", "", "").matchesUrl("http://example.com"));
    assertTrue(urlDataFromStrings("http", "example.com", "/test", "", "").matchesUrl("http://example.com/test"));
    assertFalse(urlDataFromStrings("http", "example.com", "/test", "/*", "").matchesUrl("http://example.com"));
    assertTrue(urlDataFromStrings("http", "example.com", "", "/test", "").matchesUrl("http://example.com/test/other"));
    assertFalse(urlDataFromStrings("http", "example.com", "", "/test", "").matchesUrl("http://example.com/other"));
    assertTrue(urlDataFromStrings("http", "example.com", "", "/test", "/.*").matchesUrl("http://example.com/other"));
    assertTrue(urlDataFromStrings("http", "example.com", "", "", "/.*").matchesUrl("http://example.com/anything"));
    assertTrue(urlDataFromStrings("http", "example.com", "", "", "/.*").matchesUrl("http://example.com/any/thing"));
    assertFalse(urlDataFromStrings("http", "example.com", "", "", "/test/.*").matchesUrl("http://example.com/any/thing"));
  }

  @NotNull
  private static InstantAppUrlFinder.UrlData urlDataFromStrings(@NotNull String scheme, @NotNull String host, @NotNull String path, @NotNull String pathPrefix, @NotNull String pathPattern) {
    InstantAppUrlFinder.UrlData urlData = new InstantAppUrlFinder.UrlData();
    urlData.addFromStrings(scheme, host, path, pathPrefix, pathPattern);
    return urlData;
  }
}