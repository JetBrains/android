/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.targetmapbuilder;

import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.blazePackageForLabel;
import static com.google.idea.blaze.android.targetmapbuilder.NbTargetMapUtils.workspacePathForLabel;

import com.google.common.truth.Truth;
import com.google.idea.blaze.base.model.primitives.WorkspacePath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for static utility methods in {@link NbTargetMapUtils}. */
@RunWith(JUnit4.class)
public class NbTargetMapUtilsTest {
  @Test
  public void testWorkspacePathForLabel() {
    WorkspacePath blazePackage = WorkspacePath.createIfValid("com/google/foo");
    Truth.assertThat(workspacePathForLabel(blazePackage, "Foo.java"))
        .isEqualTo(workspacePathForLabel(blazePackage, "//com/google/foo/Foo.java"));
  }

  @Test
  public void testBlazePackageForlabel() {
    String label = "//com/google/foo:foobar";
    WorkspacePath blazePackageForLabel = WorkspacePath.createIfValid("com/google/foo");

    Truth.assertThat(blazePackageForLabel(label)).isEqualTo(blazePackageForLabel);
  }
}
