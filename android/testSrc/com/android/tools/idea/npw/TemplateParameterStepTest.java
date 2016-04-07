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

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.intellij.openapi.util.io.FileUtil;
import icons.AndroidIcons;
import org.jetbrains.android.AndroidTestCase;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.StringReader;

import static org.mockito.Mockito.*;

/**
 *
 */
public class TemplateParameterStepTest extends AndroidTestCase {

  private TemplateWizardState myState;
  private TemplateParameterStep myStep;
  private TemplateMetadata myTemplateMetadata;
  private Template myTemplate;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myTemplateMetadata = mock(TemplateMetadata.class);
    myTemplate = mock(Template.class);

    myState = spy(new TemplateWizardState());
    myStep = new TemplateParameterStep(myState, getProject(), null, null, TemplateWizardStep.NONE);

    when(myState.getTemplateMetadata()).thenReturn(myTemplateMetadata);
    when(myState.getTemplate()).thenReturn(myTemplate);
  }

  public void testDeriveValuesNullThumb() throws Exception {
    when(myTemplateMetadata.getThumbnailPath(myState)).thenReturn(null);
    myStep.deriveValues();
    assertEquals(AndroidIcons.Wizards.DefaultTemplate256, myStep.myPreview.getIcon());
  }

  public void testDeriveValuesEmptyThumb() throws Exception {
    when(myTemplateMetadata.getThumbnailPath(myState)).thenReturn("");
    myStep.deriveValues();
    // Preview shouldn't have been changed
    assertNull(myStep.myPreview.getIcon());
  }

  public void testDeriveValuesSameThumb() throws Exception {
    myStep.myCurrentThumb = "foo";
    when(myTemplateMetadata.getThumbnailPath(myState)).thenReturn("foo");
    myStep.deriveValues();
    // Preview shouldn't have been changed
    assertNull(myStep.myPreview.getIcon());
  }

  public void testDeriveValuesValidThumb() throws Exception {
    File imageFolder = new File(TemplateManager.getTemplateRootFolder(),
                                                 FileUtil.join("activities", "TabbedActivity"));
    File imageFile = new File(imageFolder, "template_blank_activity_dropdown.png");

    assertTrue(imageFile.exists());
    when(myTemplateMetadata.getThumbnailPath(myState)).thenReturn("template_blank_activity_dropdown.png");
    when(myTemplate.getRootPath()).thenReturn(imageFolder);

    myStep.deriveValues();
    assertNotNull(myStep.myPreview.getIcon());
  }

  public void testValidate() throws Exception {

    String documentText = "<?xml version=\"1.0\"?> <template>" +
        "<parameter id=\"layout1\" type=\"string\" " +
        "constraints=\"layout|unique|nonempty\" /> " +
        "<parameter id=\"layout2\" type=\"string\" " +
        "constraints=\"layout|unique|nonempty\" /> </template>";

    StringReader source = new StringReader(documentText);
    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(source));

    TemplateMetadata metadata = new TemplateMetadata(document);

    when(myTemplateMetadata.getParameters()).thenReturn(metadata.getParameters());

    myState.put("layout1", "foo");
    myState.put("layout2", "foo");

    assertValidationError("Layout name foo is already in use. Please choose a unique name.");

    myState.put("layout1", "bar");

    assertTrue(myStep.validate());

    myState.put("layout2", "bar");

    assertValidationError("Layout name bar is already in use. Please choose a unique name.");
  }

  private void assertValidationError(String error) throws Exception {
    assertFalse(myStep.validate());
    assertEquals(error, myStep.getError().getText().replaceAll("<.*?>", ""));
  }
}
