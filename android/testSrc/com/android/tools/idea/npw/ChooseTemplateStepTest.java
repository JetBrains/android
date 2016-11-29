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
package com.android.tools.idea.npw;

import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.android.AndroidTestCase;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Tests for the {@link ChooseTemplateStep} wizard step.
 */
public class ChooseTemplateStepTest extends AndroidTestCase {

  public void testGetTemplateList() throws Exception {
    TemplateWizardState state = new TemplateWizardState();

    // First, list all templates
    List<ChooseTemplateStep.MetadataListItem> otherList = ChooseTemplateStep.getTemplateList(state, Template.CATEGORY_OTHER, null);

    List<File> templateDirFiles = TemplateManager.getInstance().getTemplates(Template.CATEGORY_OTHER);
    int expectedCount = templateDirFiles.size();

    // Make sure we have the right number of templates detected
    assertEquals(expectedCount, otherList.size());

    // Make sure the contents are equal
    for (ChooseTemplateStep.MetadataListItem listItem : otherList) {
      assertContainsElements(templateDirFiles, listItem.getTemplateFile());
    }

    // Check for sorted order
    for (int i = 1; i < otherList.size(); i++) {
      assertTrue(otherList.get(i).compareTo(otherList.get(i -1)) >= 0);
    }

    // Now let's exclude one
    Set<String> EXCLUDED = ImmutableSet.of("Notification");
    otherList = ChooseTemplateStep.getTemplateList(state, Template.CATEGORY_OTHER, EXCLUDED);
    expectedCount -= 1;

    // Make sure this is a valid thing to exclude (i.e. is a template in the given folder)
    boolean hasNotificationTemplate = false;
    for (File f : templateDirFiles) {
      if (f.getName().equals("Notification")) {
        hasNotificationTemplate = true;
      }
    }
    assertTrue(hasNotificationTemplate);
    assertEquals(expectedCount, otherList.size());
  }

  @Test
  public void testValidate() throws Exception {
    // TODO: Refactor validate() so that it can be unit-tested.
  }
}
