/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.template;

import static com.android.tools.idea.npw.template.ChooseActivityTypeStep.validateTemplate;
import static com.google.common.truth.Truth.assertThat;
import static org.jetbrains.android.util.AndroidBundle.message;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.android.tools.idea.templates.TemplateMetadata;
import org.junit.Test;

/**
 * Tests for {@link NamedModuleTemplate}.
 */
public class ChooseActivityTypeStepTest {
  @Test
  public void testNoTemplateForExistingModule() {
    assertThat(validateTemplate(null, 5, 5, false, false)).isEqualTo("No activity template was selected");
  }

  @Test
  public void testNoTemplateForNewModule() {
    assertThat(validateTemplate(null, 5, 5, true, false)).isEqualTo("");
  }

  @Test
  public void testTemplateWithMinSdkHigherThanModule() {
    TemplateMetadata template = mock(TemplateMetadata.class);
    when(template.getMinSdk()).thenReturn(9);

    assertThat(validateTemplate(template, 5, 5, true, true)).isEqualTo(message("android.wizard.activity.invalid.min.sdk", 9));
  }

  @Test
  public void testTemplateWithMinBuildSdkHigherThanModule() {
    TemplateMetadata template = mock(TemplateMetadata.class);
    when(template.getMinBuildApi()).thenReturn(9);

    assertThat(validateTemplate(template, 5, 5, true, true)).isEqualTo(message("android.wizard.activity.invalid.min.build", 9));
  }

  @Test
  public void testTemplateRequiringAndroidX() {
    TemplateMetadata template = mock(TemplateMetadata.class);
    when(template.getAndroidXRequired()).thenReturn(true);

    assertThat(validateTemplate(template, 5, 5, false, false)).isEqualTo(message("android.wizard.activity.invalid.androidx"));
  }
}
