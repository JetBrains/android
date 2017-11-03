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
package com.android.tools.idea.gradle.project.sync.compatibility;

import com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader;
import com.intellij.openapi.util.JDOMUtil;
import org.intellij.lang.annotations.Language;
import org.jdom.Element;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader.ANDROID_GRADLE_PLUGIN;
import static com.android.tools.idea.gradle.project.sync.compatibility.version.ComponentVersionReader.GRADLE;
import static com.android.tools.idea.gradle.project.sync.compatibility.version.VersionRangeSubject.versionRange;
import static com.android.tools.idea.project.messages.MessageType.ERROR;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * Test for {@link CompatibilityChecksMetadata}.
 */
public class CompatibilityChecksMetadataTest {
  @Test
  public void loadMetadata() throws Exception {
    @Language("XML")
    String metadataText = "<compatibility version='1'>\n" +
                          "  <check failureType='error'>\n" +
                          "    <component name='gradle' version='[2.4, +)'>\n" +
                          "      <requires name='android-gradle-plugin' version='[1.5.0, +]'>\n" +
                          "        <failureMsg>\n" +
                          "           <![CDATA[\n" +
                          "Please use Android Gradle plugin 1.5.0 or newer.\n" +
                          "]]>\n" +
                          "        </failureMsg>\n" +
                          "      </requires>\n" +
                          "    </component>\n" +
                          "  </check>\n" +
                          "</compatibility>";
    Element root = JDOMUtil.load(metadataText);
    CompatibilityChecksMetadata metadata = CompatibilityChecksMetadata.load(root);

    List<CompatibilityCheck> compatibilityChecks = metadata.getCompatibilityChecks();
    assertThat(compatibilityChecks).hasSize(1);

    CompatibilityCheck compatibilityCheck = compatibilityChecks.get(0);
    assertSame(ERROR, compatibilityCheck.getType());

    Component component = compatibilityCheck.getComponent();
    assertEquals("gradle", component.getName());

    // @formatter:off
    assertAbout(versionRange()).that(component.getVersionRange()).hasMinVersion("2.4")
                                                                 .hasMaxVersion(null)
                                                                 .isMinVersionInclusive(true)
                                                                 .isMaxVersionInclusive(false);
    // @formatter:on

    List<Component> requirements = component.getRequirements();
    assertThat(requirements).hasSize(1);

    Component requirement = requirements.get(0);
    assertEquals("android-gradle-plugin", requirement.getName());

    // @formatter:off
    assertAbout(versionRange()).that(requirement.getVersionRange()).hasMinVersion("1.5.0")
                                                                   .hasMaxVersion(null)
                                                                   .isMinVersionInclusive(true)
                                                                   .isMaxVersionInclusive(true);
    // @formatter:on

    assertEquals("Please use Android Gradle plugin 1.5.0 or newer.", requirement.getFailureMessage());

    Map<String, ComponentVersionReader> readers = metadata.getReadersByComponentName();
    assertSame(GRADLE, readers.get("gradle"));
    assertSame(ANDROID_GRADLE_PLUGIN, readers.get("android-gradle-plugin"));
  }
}