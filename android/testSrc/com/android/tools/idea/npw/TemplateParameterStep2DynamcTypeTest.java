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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.utils.XmlUtils;
import com.google.common.base.Strings;
import com.intellij.openapi.extensions.DefaultPluginDescriptor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;

import static com.android.tools.idea.npw.ParameterDefaultValueComputerTest.getParameterObject;

public final class TemplateParameterStep2DynamcTypeTest extends LightIdeaTestCase {
  private static final String METADATA_XML = "<?xml version=\"1.0\"?>\n" +
                                             "<template\n" +
                                             "    format=\"4\"\n" +
                                             "    revision=\"2\"\n" +
                                             "    name=\"Android Manifest File\"\n" +
                                             "    description=\"Creates an Android Manifest XML File.\"\n" +
                                             "    >\n" +
                                             "\n" +
                                             "    <category value=\"Other\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"customtypeId\"\n" +
                                             "        name=\"custom type name\"\n" +
                                             "        type=\"mycustomtype\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"TestString1\" />\n" +
                                             "\n" +
                                             "    <execute file=\"recipe.xml.ftl\" />\n" +
                                             "\n" +
                                             "</template>\n";
  private TemplateMetadata myTemplateMetadata;

  private TemplateParameterStep2 myStep;
  private static final String testString1 = "TestString1";
  private static final String testString2 = "TestString2";
  private static final Element element = readElement("  <extensions defaultExtensionNs=\"org.jetbrains.android\">\n" +
                                                     "    <wizardParameterFactory implementation=\"" +
                                                     TestDynamicWizardContent.class.getName() +
                                                     "\"/>\n  </extensions>");

  // project leak detected?
  public void disabled_testCustomControlAndBinding() {
    final String parameterName = "customtypeId";
    ScopedStateStore.Key<?> p2Key = getKeyForParameter(parameterName);

    // 1st assert ensures that the extension has returned its text field and its initialized with the correct default
    myStep.myState.put(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE, new TemplateEntry(new File(""), myTemplateMetadata));
    assertEquals(testString1, TestDynamicWizardContent.getTextField().getText());

    // 2nd assert ensures that updates to the ui get reflected in the state.
    TestDynamicWizardContent.getTextField().setText(testString2);
    assertEquals(testString2, myStep.myState.get(p2Key));
  }

  private ScopedStateStore.Key<?> getKeyForParameter(String parameterName) {
    return myStep.getParameterKey(getParameterObject(myTemplateMetadata, parameterName));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    WizardParameterFactory[] customWizardUIFactories = Extensions.getExtensions(WizardParameterFactory.EP_NAME);
    boolean isMissingTestFactory = true;
    for (WizardParameterFactory factory : customWizardUIFactories) {
      if (factory instanceof TestDynamicWizardContent) {
        isMissingTestFactory = false;
      }
    }

    if (isMissingTestFactory) {
      ExtensionsArea root = Extensions.getArea(null);
      root.registerExtension(new DefaultPluginDescriptor(PluginId.getId("org.jetbrains.android")),
                             element.getChild("wizardParameterFactory"));
    }

    Document document = XmlUtils.parseDocumentSilently(METADATA_XML, false);
    assert document != null;
    myTemplateMetadata = new TemplateMetadata(document);
    myStep = TemplateParameterStep2Test.createTemplateParameterStepInWizard(myTemplateMetadata, getTestRootDisposable());
    myStep.init();
  }

  static Element readElement(String text) {
    Element extensionElement1 = null;
    try {
      extensionElement1 = new SAXBuilder().build(new StringReader(text)).getRootElement();
    }
    catch (JDOMException e) {
      throw new RuntimeException(e);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    Element extensionElement = extensionElement1;
    return extensionElement;
  }

  public static class TestDynamicWizardContent implements WizardParameterFactory {
    private static MyJTextField myJTextField;

    public TestDynamicWizardContent() {
      myJTextField = new MyJTextField();
    }
    @Override
    public String[] getSupportedTypes() {
      return new String[]{"mycustomtype"};
    }

    @Override
    public JComponent createComponent(String type, Parameter parameter) {
      return myJTextField;
    }

    public static MyJTextField getTextField() {
      return myJTextField;
    }

    @Override
    public ScopedDataBinder.ComponentBinding<String, JComponent> createBinding(JComponent component, Parameter parameter) {
      return new ScopedDataBinder.ComponentBinding<String, JComponent>() {
        @Override
        public void setValue(@Nullable String newValue, @NotNull JComponent component) {
          ((JTextField)component).setText(Strings.nullToEmpty(newValue));
        }

        @Nullable
        @Override
        public String getValue(@NotNull JComponent component) {
          return ((JTextField)component).getText();
        }
      };
    }
  }

  public static class MyJTextField extends JTextField {
  }
}
