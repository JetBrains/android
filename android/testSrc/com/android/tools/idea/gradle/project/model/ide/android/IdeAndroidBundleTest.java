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

import com.android.builder.model.AndroidBundle;
import com.android.builder.model.JavaLibrary;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.AndroidBundleStub;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.Collection;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.createEqualsVerifier;
import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.verifyUsageOfImmutableCollections;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link IdeAndroidBundle}.
 */
public class IdeAndroidBundleTest {
  @Test
  public void constructor() throws Throwable {
    AndroidBundle original = new AndroidBundleStub();
    IdeAndroidBundle copy = new IdeAndroidBundle(original, new ModelCache()) {};
    assertEqualsOrSimilar(original, copy);
    verifyUsageOfImmutableCollections(copy);
  }

  @Test
  public void model1_dot_5() {
    AndroidBundle original = new AndroidBundleStub() {
      @Override
      @NotNull
      public Collection<? extends JavaLibrary> getJavaDependencies() {
        throw new UnsupportedMethodException("Unsupported method");
      }
    };
    IdeAndroidBundle bundle = new IdeAndroidBundle(original, new ModelCache()) {};
    assertThat(bundle.getJavaDependencies()).isEmpty();
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeAndroidBundle.class).withRedefinedSuperclass().withRedefinedSubclass(IdeAndroidLibrary.class).verify();
  }
}