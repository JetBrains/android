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

import com.android.builder.model.SourceProvider;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.android.tools.idea.wizard.dynamic.DynamicWizardPath;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightIdeaTestCase;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;

import java.io.File;

import static com.android.tools.idea.templates.ParameterValueResolverTest.getParameterObject;

public final class TemplateParameterStep2Test extends LightIdeaTestCase {
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
                                             "        id=\"p1\"\n" +
                                             "        name=\"p1 name\"\n" +
                                             "        type=\"boolean\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"false\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p2\"\n" +
                                             "        name=\"p2 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"Hello\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p3\"\n" +
                                             "        name=\"p3 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        constraints=\"\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p4\"\n" +
                                             "        name=\"p4 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        suggest=\"${p2}, World\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p5\"\n" +
                                             "        name=\"p5 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        suggest=\"${p4}!\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p6\"\n" +
                                             "        name=\"p6 name\"\n" +
                                             "        type=\"boolean\"\n" +
                                             "        suggest=\"${(p1 = false)?c}\"/>\n" +
                                             "\n" +
                                             "    <execute file=\"recipe.xml.ftl\" />\n" +
                                             "\n" +
                                             "</template>\n";
  private TemplateMetadata myTemplateMetadata;
  private TemplateParameterStep2 myStep;

  public static TemplateParameterStep2 createTemplateParameterStepInWizard(@NotNull final TemplateMetadata templateMetadata,
                                                                           @NotNull Disposable parentDisposable) {
    final TemplateParameterStep2 step =
      new TemplateParameterStep2(FormFactorUtils.FormFactor.MOBILE, ImmutableMap.<String, Object>of(), null,
                                 AddAndroidActivityPath.KEY_PACKAGE_NAME, new SourceProvider[0], "Test Title");
    DynamicWizard dynamicWizard = new DynamicWizard(null, null, "Test Wizard") {
      @Override
      public void init() {
        addPath(new DynamicWizardPath() {
          @Override
          protected void init() {
            myState.put(AddAndroidActivityPath.KEY_SELECTED_TEMPLATE, new TemplateEntry(new File("/"), templateMetadata));
            addStep(step);
          }

          @NotNull
          @Override
          public String getPathName() {
            return "Test Path";
          }

          @Override
          public boolean performFinishingActions() {
            return false;
          }
        });
        super.init();
      }

      @Override
      protected String getWizardActionDescription() {
        return "Test Wizard Completion";
      }

      @Override
      public void performFinishingActions() {
        // Do nothing
      }

      @NotNull
      @Override
      protected String getProgressTitle() {
        return "dummy";
      }
    };
    Disposer.register(parentDisposable, dynamicWizard.getDisposable());
    dynamicWizard.init();
    return step;
  }

  // project leak detected?
  public void disabled_testRefreshParameterDefaults() {
    myStep.updateStateWithDefaults(myTemplateMetadata.getParameters());
    String parameterName = "p2";
    ScopedStateStore.Key<?> p2Key = getKeyForParameter(parameterName);
    assertEquals("Hello", myStep.myState.get(p2Key));
    myStep.myState.unsafePut(p2Key, "Good-bye");
    myStep.updateStateWithDefaults(myTemplateMetadata.getParameters());
    assertEquals("Good-bye, World!", myStep.myState.get(getKeyForParameter("p5")));
    myStep.myState.unsafePut(p2Key, "Good morning");
    myStep.updateStateWithDefaults(myTemplateMetadata.getParameters());
    assertEquals("Good morning, World!", myStep.myState.get(getKeyForParameter("p5")));
  }

  private ScopedStateStore.Key<?> getKeyForParameter(String parameterName) {
    return myStep.getParameterKey(getParameterObject(myTemplateMetadata, parameterName));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Document document = XmlUtils.parseDocumentSilently(METADATA_XML, false);
    assert document != null;
    myTemplateMetadata = new TemplateMetadata(document);
    myStep = createTemplateParameterStepInWizard(myTemplateMetadata, getTestRootDisposable());
  }
}
