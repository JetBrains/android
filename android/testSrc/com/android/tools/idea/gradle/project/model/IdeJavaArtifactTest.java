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

import static com.android.tools.idea.gradle.project.model.IdeModelTestUtils.assertEqualsOrSimilar;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.tools.idea.gradle.model.IdeArtifactName;
import com.android.tools.idea.gradle.model.IdeJavaArtifact;
import com.android.tools.idea.gradle.model.impl.IdeDependenciesImpl;
import com.android.tools.idea.gradle.model.impl.IdeJavaArtifactImpl;
import com.android.tools.idea.gradle.model.stubs.JavaArtifactStub;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Tests for {@link IdeJavaArtifact}. */
public class IdeJavaArtifactTest {

    @Test
    public void constructor() throws Throwable {
        JavaArtifact original = new JavaArtifactStub();
        IdeJavaArtifact copy = new IdeJavaArtifactImpl(
          convertArtifactName(original.getName()),
          original.getCompileTaskName(),
          original.getAssembleTaskName(),
          original.getClassesFolder(),
          original.getAdditionalClassesFolders(),
          original.getJavaResourcesFolder(),
          null,
          null,
          original.getIdeSetupTaskNames(),
          (List)original.getGeneratedSourceFolders(),
          false,
          new IdeDependenciesImpl(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
          original.getMockablePlatformJar()
    );
        assertEqualsOrSimilar(original, copy);
    }

  private IdeArtifactName convertArtifactName(String name) {
      switch (name) {
        case AndroidProject.ARTIFACT_MAIN:
          return IdeArtifactName.MAIN;
        case AndroidProject.ARTIFACT_ANDROID_TEST:
          return IdeArtifactName.ANDROID_TEST;
        case AndroidProject.ARTIFACT_UNIT_TEST:
          return IdeArtifactName.UNIT_TEST;
      }
      return null;
  }
}
