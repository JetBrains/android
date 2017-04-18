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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.BaseConfig;
import com.android.builder.model.ClassField;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.BaseConfigStub;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.ClassFieldStub;
import com.google.common.collect.Lists;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.android.tools.idea.gradle.project.model.ide.android.CopyVerification.assertEqualsOrSimilar;

/**
 * Tests for {@link IdeBaseConfig}.
 */
public class IdeBaseConfigTest {
  @Test
  public void constructor() throws Throwable {
    BaseConfig original = createStub();
    assertEqualsOrSimilar(original, new IdeBaseConfig(original, new ModelCache()) {});
  }

  @NotNull
  private static BaseConfigStub createStub() {
    Map<String, ClassField> values = new HashMap<>();
    values.put("name", new ClassFieldStub());

    Collection<File> proguardFiles = Lists.newArrayList(new File("proguardFile"));
    Collection<File> consumerProguardFiles = Lists.newArrayList(new File("consumerProguardFile"));

    Map<String, Object> placeHolders = new HashMap<>();
    placeHolders.put("key", "value");

    return new BaseConfigStub("name", values, proguardFiles, consumerProguardFiles, placeHolders, "one", "two");
  }

  @Test
  public void equalsAndHashCode() {
    EqualsVerifier.forClass(IdeBaseConfig.class).withRedefinedSubclass(IdeBuildType.class).verify();
  }
}