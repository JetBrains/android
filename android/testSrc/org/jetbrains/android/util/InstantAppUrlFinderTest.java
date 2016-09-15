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
package org.jetbrains.android.util;

import com.google.common.collect.ImmutableList;
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

import static com.google.common.truth.Truth.assertThat;

public class InstantAppUrlFinderTest {

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  private static Node createXMLContent(String innerXML) throws Exception {
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

  private static Collection<Element> createDummyValidData() throws Exception {
    return ImmutableList.of(
      (Element)createXMLContent("<activity>" +
                                "  <intent-filter instant:order=\"3\">" +
                                "    <data android:host=\"domainA\"\n" +
                                "          android:pathPattern=\"pathPatternA\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainA\"\n" +
                                "          android:pathPattern=\"pathPatternA\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "  <intent-filter instant:order=\"1\">" +
                                "    <data android:host=\"domainB\"\n" +
                                "          android:pathPattern=\"pathPatternB\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainB\"\n" +
                                "          android:pathPattern=\"pathPatternB\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "</activity>"),
      (Element)createXMLContent("<activity>" +
                                "  <intent-filter instant:order=\"4\">" +
                                "    <data android:host=\"domainC\"\n" +
                                "          android:pathPattern=\"pathPatternC\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainC\"\n" +
                                "          android:pathPattern=\"pathPatternC\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "  <intent-filter instant:order=\"2\">" +
                                "    <data android:host=\"domainD\"\n" +
                                "          android:pathPattern=\"pathPatternD\"\n" +
                                "          android:scheme=\"scheme1\" />" +
                                "    <data android:host=\"domainD\"\n" +
                                "          android:pathPattern=\"pathPatternD\"\n" +
                                "          android:scheme=\"scheme2\" />" +
                                "  </intent-filter>" +
                                "</activity>")
    );
  }

  @Test
  public void getAllUrls() throws Exception {
    InstantAppUrlFinder finder = new InstantAppUrlFinder(createDummyValidData());

    assertThat(finder.getAllUrls().size()).isEqualTo(8);
    Iterator<String> iterator = finder.getAllUrls().iterator();
    assertThat(iterator.next()).isEqualTo("scheme1://domainB/pathPatternB");
    assertThat(iterator.next()).isEqualTo("scheme2://domainB/pathPatternB");
    assertThat(iterator.next()).isEqualTo("scheme1://domainD/pathPatternD");
    assertThat(iterator.next()).isEqualTo("scheme2://domainD/pathPatternD");
    assertThat(iterator.next()).isEqualTo("scheme1://domainA/pathPatternA");
    assertThat(iterator.next()).isEqualTo("scheme2://domainA/pathPatternA");
    assertThat(iterator.next()).isEqualTo("scheme1://domainC/pathPatternC");
    assertThat(iterator.next()).isEqualTo("scheme2://domainC/pathPatternC");
  }

  @Test
  public void getAllUrls_invalidInput() throws Exception {
    InstantAppUrlFinder invalidfinder = new InstantAppUrlFinder(ImmutableList.of((Element)createXMLContent("<intent-filter/>")));

    assertThat(invalidfinder.getAllUrls().size()).isEqualTo(0);
  }

  @Test
  public void getDefaultUrl() throws Exception {
    InstantAppUrlFinder finder = new InstantAppUrlFinder(createDummyValidData());

    assertThat(finder.getDefaultUrl()).isEqualTo("scheme1://domainB/pathPatternB");
  }

  @Test
  public void getDefaultUrl_invalidInput() throws Exception {
    InstantAppUrlFinder invalidfinder = new InstantAppUrlFinder(ImmutableList.of((Element)createXMLContent("<intent-filter/>")));

    assertThat(invalidfinder.getDefaultUrl()).isEqualTo("");
  }

  @Test
  public void InstantAppIntentFilterWrapper() throws Exception {
    Element intentWithData = (Element)createXMLContent("<intent-filter instant:order=\"3\">" +
                                                       "<data android:host=\"domain\"\n" +
                                                       "      android:pathPattern=\"pathPattern\"\n" +
                                                       "      android:scheme=\"scheme1\" />" +
                                                       "<data android:host=\"domain\"\n" +
                                                       "      android:pathPattern=\"pathPattern\"\n" +
                                                       "      android:scheme=\"scheme2\" />" +
                                                       "</intent-filter>");

    InstantAppUrlFinder.InstantAppIntentFilterWrapper wrapper = InstantAppUrlFinder.InstantAppIntentFilterWrapper.of(intentWithData);

    assertThat(wrapper.getAllUrlData().size()).isEqualTo(2);
    assertThat(wrapper.getOrder()).isEqualTo(3);
    Iterator<InstantAppUrlFinder.UrlData> iterator = wrapper.getAllUrlData().iterator();
    assertThat(iterator.next().getUrl()).isEqualTo("scheme1://domain/pathPattern");
    assertThat(iterator.next().getUrl()).isEqualTo("scheme2://domain/pathPattern");
  }

  @Test
  public void InstantAppIntentFilterWrapper_getElement_wrongName() throws Exception {
    Node wrongNameNode = createXMLContent("<something-filter foo=\"bar\"/>");

    exception.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(wrongNameNode);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getElement_notAnElement() throws Exception {
    Node notAnElement = createXMLContent("<something-filter foo=\"bar\"/>").getAttributes().item(0);

    exception.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(notAnElement);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getElement_Valid() throws Exception {
    Node intentFilterNode = createXMLContent("<intent-filter/>");

    assertThat(InstantAppUrlFinder.InstantAppIntentFilterWrapper.getElement(intentFilterNode)).isEqualTo(intentFilterNode);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getOrder_Valid() throws Exception {
    Element intentWithOrder = (Element)createXMLContent("<intent-filter instant:order=\"7\"/>");

    assertThat(InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(intentWithOrder)).isEqualTo(7);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getOrder_negativeValue() throws Exception {
    Element negativeValuesAreInvalid = (Element)createXMLContent("<intent-filter instant:order=\"-3\"/>");

    exception.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(negativeValuesAreInvalid);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getOrder_nonNumericalValue() throws Exception {
    Element notANumber = (Element)createXMLContent("<intent-filter instant:order=\"foo\"/>");

    exception.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(notANumber);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getOrder_wrongNamespace() throws Exception {
    Element badNamespace = (Element)createXMLContent("<intent-filter android:order=\"3\"/>");

    exception.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(badNamespace);
  }

  @Test
  public void InstantAppIntentFilterWrapper_getOrder_missingAttribute() throws Exception {
    Element missingAttribute = (Element)createXMLContent("<intent-filter foo=\"3\"/>");

    exception.expect(IllegalArgumentException.class);
    InstantAppUrlFinder.InstantAppIntentFilterWrapper.getOrder(missingAttribute);
  }

  @Test
  public void UrlData_urlDataValidInput() throws Exception {

    Node dataNode = createXMLContent("<data android:host=\"domain\"\n" +
                                     "      android:pathPattern=\"pathPattern\"\n" +
                                     "      android:scheme=\"scheme\" />");
    InstantAppUrlFinder.UrlData urlData = InstantAppUrlFinder.UrlData.of(dataNode);

    assertThat(urlData.isValid()).isTrue();
  }

  @Test
  public void UrlData_wrongNode() throws Exception {
    Node wrongNode = createXMLContent("<datum android:host=\"domain\"\n" +
                                      "       instant:pathPattern=\"pathPattern\"\n" +
                                      "       android:scheme=\"scheme\" />");

    assertThat(InstantAppUrlFinder.UrlData.of(wrongNode).isValid()).isFalse();
  }

  @Test
  public void UrlData_wrongNamespace() throws Exception {
    Node wrongNamespace = createXMLContent("<data android:host=\"domain\"\n" +
                                           "      instant:pathPattern=\"pathPattern\"\n" +
                                           "      android:scheme=\"scheme\" />");

    assertThat(InstantAppUrlFinder.UrlData.of(wrongNamespace).isValid()).isFalse();
  }

  @Test
  public void UrlData_notAnElement() throws Exception {
    Node notAnElement = createXMLContent("<data test=\"test\" />").getAttributes().item(0);

    assertThat(InstantAppUrlFinder.UrlData.of(notAnElement).isValid()).isFalse();
  }

  @Test
  public void UrlData_missingData() throws Exception {
    Node missingData = createXMLContent("<data android:host=\"domain\"\n" +
                                        "      android:pathPattern=\"pathPattern\"\n/>");

    assertThat(InstantAppUrlFinder.UrlData.of(missingData).isValid()).isFalse();
  }

  @Test
  public void UrlData_getUrl() {
    assertThat(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "pattern").getUrl()).isEqualTo("scheme://domain.url/pattern");
    assertThat(new InstantAppUrlFinder.UrlData("scheme", "domain.url", ".*/X/Y").getUrl()).isEqualTo("scheme://domain.url/parameter/X/Y");
  }

  @Test
  public void UrlData_isValid() {
    assertThat(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "pattern").isValid()).isTrue();
    assertThat(new InstantAppUrlFinder.UrlData("scheme", "domain.url", "").isValid()).isFalse();
    assertThat(new InstantAppUrlFinder.UrlData("scheme", "", "pattern").isValid()).isFalse();
    assertThat(new InstantAppUrlFinder.UrlData("", "domain.url", "pattern").isValid()).isFalse();
  }

  @Test
  public void UrlData_convertPatternToExample() {
    assertThat(InstantAppUrlFinder.UrlData.convertPatternToExample(".*")).isEqualTo("parameter");
    assertThat(InstantAppUrlFinder.UrlData.convertPatternToExample("?")).isEqualTo("X");
    assertThat(InstantAppUrlFinder.UrlData.convertPatternToExample(".*/?/.*/?/test")).isEqualTo("parameter/X/parameter/X/test");
  }

}