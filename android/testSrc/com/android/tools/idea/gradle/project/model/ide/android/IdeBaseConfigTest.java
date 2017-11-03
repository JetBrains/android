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
import com.android.tools.idea.gradle.project.model.ide.android.stubs.BaseConfigStub;
import com.google.common.truth.Truth;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Map;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.createEqualsVerifier;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.verifyUsageOfImmutableCollections;

/**
 * Tests for {@link IdeBaseConfig}.
 */
public class IdeBaseConfigTest {
  @Test
  public void constructor() throws Throwable {
    BaseConfig original = new BaseConfigStub();
    IdeBaseConfig copy = new IdeBaseConfig(original, new ModelCache()) {};
    assertEqualsOrSimilar(original, copy);
    verifyUsageOfImmutableCollections(copy);
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeBaseConfig.class).withRedefinedSubclass(IdeBuildType.class).verify();
    createEqualsVerifier(IdeBaseConfig.class).withRedefinedSubclass(IdeProductFlavor.class).verify();
  }
}