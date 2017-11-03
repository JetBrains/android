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

import com.android.build.VariantOutput;
import com.android.tools.idea.gradle.project.model.ide.android.stubs.VariantOutputStub;
import org.junit.Test;

import static com.android.tools.idea.gradle.project.model.ide.android.IdeModelTestUtils.*;

/**
 * Tests for {@link IdeVariantOutput}.
 */
public class IdeVariantOutputTest {
  @Test
  public void constructor() throws Throwable {
    VariantOutput original = new VariantOutputStub();
    IdeVariantOutput copy = new IdeVariantOutput(original, new ModelCache()) {};
    assertEqualsOrSimilar(original, copy);
    verifyUsageOfImmutableCollections(copy);
  }

  @Test
  public void equalsAndHashCode() {
    createEqualsVerifier(IdeVariantOutput.class).withRedefinedSubclass(IdeAndroidArtifactOutput.class).verify();
  }

}