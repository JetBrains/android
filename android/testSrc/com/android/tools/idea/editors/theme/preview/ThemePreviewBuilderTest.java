/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.preview;

import com.android.SdkConstants;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import junit.framework.TestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.HashSet;
import java.util.List;

import static com.android.tools.idea.editors.theme.preview.ThemePreviewBuilder.*;

public class ThemePreviewBuilderTest extends TestCase {
  // Xpath to search all the components
  static final String COMPONENTS_XPATH = "/LinearLayout/" + THEME_PREVIEW_LAYOUT + "/FrameLayout/LinearLayout/*";
  static final String GROUP_LABELS_XPATH = "/LinearLayout/" + THEME_PREVIEW_LAYOUT + "/FrameLayout/TextView/";

  public void testEmptyPreview() throws ParserConfigurationException, XPathExpressionException {
    Document document = new ThemePreviewBuilder().build();

    XPath xPath = XPathFactory.newInstance().newXPath();

    assertEquals("One root layout expected", 1, document.getChildNodes().getLength());
    Node rootNode = document.getChildNodes().item(0);
    assertEquals(SdkConstants.LINEAR_LAYOUT, rootNode.getNodeName());
    assertNotNull(rootNode.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH));
    assertNotNull(rootNode.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT));

    NodeList nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(0, nodeList.getLength());
  }

  public void testBasicComponentList() throws ParserConfigurationException, XPathExpressionException {
    Document document = new ThemePreviewBuilder().addAllComponents(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS).build();

    XPath xPath = XPathFactory.newInstance().newXPath();

    assertEquals("One root layout expected", 1, document.getChildNodes().getLength());
    Node rootNode = document.getChildNodes().item(0);
    assertEquals(SdkConstants.LINEAR_LAYOUT, rootNode.getNodeName());
    assertNotNull(rootNode.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_WIDTH));
    assertNotNull(rootNode.getAttributes().getNamedItemNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_LAYOUT_HEIGHT));

    // Find if any elements doesn't have layout_with or layout_height
    NodeList nodeList =
      (NodeList)xPath.evaluate("//*[not(@*[local-name() = 'layout_width'])]", document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals("All components must have 'layout_with'", 0, nodeList.getLength());
    nodeList =
      (NodeList)xPath.evaluate("//*[not(@*[local-name() = 'layout_height'])]", document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals("All components must have 'layout_height'", 0, nodeList.getLength());
  }

  public void testComponentList() throws ParserConfigurationException, XPathExpressionException {
    Document document = new ThemePreviewBuilder().addAllComponents(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS).build();

    XPath xPath = XPathFactory.newInstance().newXPath();

    NodeList nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS.size(), nodeList.getLength());

    ThemePreviewBuilder customComponentBuilder = new ThemePreviewBuilder()
      .addAllComponents(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS)
      .addComponent(new ThemePreviewBuilder.ComponentDefinition("Test", ThemePreviewBuilder.ComponentGroup.CUSTOM, "Test").setApiLevel(15));
    document = customComponentBuilder.build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS.size() + 1, nodeList.getLength());

    // This shouldn't filter our custom component
    Predicate<ThemePreviewBuilder.ComponentDefinition> api15Filter = new Predicate<ThemePreviewBuilder.ComponentDefinition>() {
      @Override
      public boolean apply(ThemePreviewBuilder.ComponentDefinition input) {
        return input.apiLevel <= 15;
      }
    };
    document = customComponentBuilder.addComponentFilter(api15Filter).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    Iterable filteredBaseComponents =
      Iterables.filter(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS, new Predicate<ThemePreviewBuilder.ComponentDefinition>() {
        @Override
        public boolean apply(ThemePreviewBuilder.ComponentDefinition input) {
          return input.apiLevel <= 15;
        }
      });
    assertEquals(Iterables.size(filteredBaseComponents) + 1, nodeList.getLength());

    // This should filter the custom component
    Predicate<ThemePreviewBuilder.ComponentDefinition> api14Filter = new Predicate<ThemePreviewBuilder.ComponentDefinition>() {
      @Override
      public boolean apply(ThemePreviewBuilder.ComponentDefinition input) {
        return input.apiLevel <= 14;
      }
    };
    document = customComponentBuilder.addComponentFilter(api14Filter).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    filteredBaseComponents =
      Iterables.filter(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS, new Predicate<ThemePreviewBuilder.ComponentDefinition>() {
        @Override
        public boolean apply(ThemePreviewBuilder.ComponentDefinition input) {
          return input.apiLevel <= 14;
        }
      });
    assertEquals(Iterables.size(filteredBaseComponents), nodeList.getLength());
  }

  public void testGroups() throws ParserConfigurationException, XPathExpressionException {
    Document document = new ThemePreviewBuilder().addAllComponents(ThemePreviewBuilder.AVAILABLE_BASE_COMPONENTS).build();

    XPath xPath = XPathFactory.newInstance().newXPath();

    NodeList nodeList =
      (NodeList)xPath.evaluate(GROUP_LABELS_XPATH + "@*[local-name() = 'text']", document.getDocumentElement(), XPathConstants.NODESET);
    HashSet<String> headerTitles = new HashSet<>();
    for (int i = 0; i < nodeList.getLength(); i++) {
      String title = nodeList.item(i).getNodeValue();
      if (headerTitles.contains(title)) {
        fail(String.format("Header '%s' exists more than once", title));
      }

      headerTitles.add(title);
    }
    assertFalse(headerTitles.isEmpty());
    assertFalse("'Custom' header shouldn't be present when there are no custom components",
                headerTitles.contains(ThemePreviewBuilder.ComponentGroup.CUSTOM.name()));

    // Check the group id attribute. This attribute is needed to handle clicks on the preview
    // Check all components have a group id.
    nodeList =
      (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node node = nodeList.item(i);
      assertFalse(node.getAttributes().getNamedItemNS(BUILDER_URI, BUILDER_ATTR_GROUP).getNodeValue().isEmpty());
    }

    // Add custom component
    document = new ThemePreviewBuilder()
      .addComponent(new ThemePreviewBuilder.ComponentDefinition("Test", ThemePreviewBuilder.ComponentGroup.CUSTOM, "Test")).build();
    nodeList =
      (NodeList)xPath.evaluate(GROUP_LABELS_XPATH + "@*[local-name() = 'text']", document.getDocumentElement(), XPathConstants.NODESET);
    headerTitles.clear();
    for (int i = 0; i < nodeList.getLength(); i++) {
      headerTitles.add(nodeList.item(i).getNodeValue());
    }
    assertFalse(headerTitles.isEmpty());
    assertTrue("'Custom' header should be present", headerTitles.contains(ThemePreviewBuilder.ComponentGroup.CUSTOM.name));

    nodeList =
      (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    for (int i = 0; i < nodeList.getLength(); i++) {
      Node node = nodeList.item(i);
      assertFalse(node.getAttributes().getNamedItemNS(BUILDER_URI, BUILDER_ATTR_GROUP).getNodeValue().isEmpty());
    }
  }

  public void testSearchFilter() throws ParserConfigurationException, XPathExpressionException {
    List<ThemePreviewBuilder.ComponentDefinition> componentDefinitionList = ImmutableList.of(
      new ThemePreviewBuilder.ComponentDefinition("Spinner", ThemePreviewBuilder.ComponentGroup.TEXT, "Spinner"),
      new ThemePreviewBuilder.ComponentDefinition("Custom_Component", ThemePreviewBuilder.ComponentGroup.CUSTOM, "CustomComponent")
        .addAlias("Spinner")
        .addAlias("ABC")
        .addAlias("DEF")
    );

    XPath xPath = XPathFactory.newInstance().newXPath();
    ThemePreviewBuilder customComponentBuilder = new ThemePreviewBuilder().addAllComponents(componentDefinitionList);

    // Check the search "spinner" returns both the actual spinner control and the custom component with the alias
    Document document = customComponentBuilder.addComponentFilter(new ThemePreviewBuilder.SearchFilter("SPINNER")).build();
    NodeList nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(2, nodeList.getLength());

    // Test matching the name
    document = customComponentBuilder.addComponentFilter(new ThemePreviewBuilder.SearchFilter("CustomCOMPONENT")).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());

    // Test matching the description
    document = customComponentBuilder.addComponentFilter(new ThemePreviewBuilder.SearchFilter("Custom_COMPONENT")).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());

    // Test searching for an alias that only matches our custom component
    document = customComponentBuilder.addComponentFilter(new ThemePreviewBuilder.SearchFilter("AbC")).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());

    // Test partial match
    document = customComponentBuilder.addComponentFilter(new ThemePreviewBuilder.SearchFilter("EF")).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(1, nodeList.getLength());

    // Test case sensitive search
    document = customComponentBuilder.addComponentFilter(new ThemePreviewBuilder.SearchFilter("AbC", true)).build();
    nodeList = (NodeList)xPath.evaluate(COMPONENTS_XPATH, document.getDocumentElement(), XPathConstants.NODESET);
    assertEquals(0, nodeList.getLength());
  }
}