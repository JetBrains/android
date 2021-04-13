/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.LintOptions;
import com.android.tools.idea.gradle.model.IdeLintOptions;
import com.android.tools.idea.gradle.model.impl.IdeLintOptionsImpl;
import com.android.tools.idea.gradle.model.stubs.LintOptionsStub;
import org.junit.Test;

/** Tests for {@link IdeLintOptions}. */
public class IdeLintOptionsTest {

    @Test
    public void constructor() {
        LintOptions original = new LintOptionsStub();
        IdeLintOptions copy = new IdeLintOptionsImpl(
          original.getBaselineFile(),
          original.getLintConfig(),
          original.getSeverityOverrides(),
          false,
          false,
          original.getDisable(),
          original.getEnable(),
          original.getCheck(),
          true,
          true,
          false,
          false,
          false,
          false,
          false,
          false,
          false,
          true,
          true,
          false,
          false,
          original.getTextOutput(),
          true,
          original.getHtmlOutput(),
          true,
          original.getXmlOutput(),
          false,
          null);
        assertThat(copy.getBaselineFile()).isEqualTo(original.getBaselineFile());
        assertThat(copy.getLintConfig()).isEqualTo(original.getLintConfig());
        assertThat(copy.getSeverityOverrides()).isEqualTo(original.getSeverityOverrides());
        assertThat(copy.isCheckTestSources()).isEqualTo(original.isCheckTestSources());
        assertThat(copy.isCheckDependencies()).isEqualTo(original.isCheckDependencies());
    }
}
