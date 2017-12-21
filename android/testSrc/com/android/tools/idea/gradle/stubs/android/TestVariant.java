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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.TestedTargetVariant;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.ide.common.gradle.model.IdeBaseArtifact;
import com.android.ide.common.gradle.model.IdeJavaArtifact;
import com.android.ide.common.gradle.model.IdeVariant;
import com.android.ide.common.gradle.model.stubs.VariantStub;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

import static com.android.builder.model.AndroidProject.ARTIFACT_ANDROID_TEST;
import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;

public class TestVariant extends VariantStub implements IdeVariant {
  @NotNull private final TestAndroidArtifact myInstrumentationTestArtifact;

  /**
   * Creates a new {@link TestVariant}.
   *
   * @param name          the name of the variant.
   * @param buildType     the name of the build type.
   * @param fileStructure the file structure of the Gradle project this variant belongs to.
   */
  TestVariant(@NotNull String name, @NotNull String buildType, @NotNull FileStructure fileStructure) {
    super(name, buildType, new TestAndroidArtifact(ARTIFACT_MAIN, buildType, buildType, fileStructure), Lists.newArrayList());
    myInstrumentationTestArtifact = new TestAndroidArtifact(ARTIFACT_ANDROID_TEST, "androidTest/" + buildType, buildType, fileStructure);
    getExtraAndroidArtifacts().add(myInstrumentationTestArtifact);
    TestJavaArtifact unitTestArtifact = new TestJavaArtifact(AndroidProject.ARTIFACT_UNIT_TEST, "test/" + buildType, buildType,
                                                             fileStructure);
    getExtraJavaArtifacts().add(unitTestArtifact);
  }

  @Override
  @NotNull
  public TestAndroidArtifact getMainArtifact() {
    return (TestAndroidArtifact)super.getMainArtifact();
  }

  @NotNull
  public TestAndroidArtifact getInstrumentTestArtifact() {
    return myInstrumentationTestArtifact;
  }

  @Override
  @NotNull
  public Collection<TestedTargetVariant> getTestedTargetVariants() {
    return Collections.emptyList();
  }

  @Nullable
  @Override
  public IdeAndroidArtifact getAndroidTestArtifact() {
    for (AndroidArtifact artifact : getExtraAndroidArtifacts()) {
      if (artifact instanceof IdeAndroidArtifact) {
        IdeAndroidArtifact ideArtifact = (IdeAndroidArtifact)artifact;
        if (ideArtifact.isTestArtifact()) {
          return ideArtifact;
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public IdeJavaArtifact getUnitTestArtifact() {
    return null;
  }

  @Override
  @NotNull
  public Collection<IdeBaseArtifact> getTestArtifacts() {
    return Collections.emptyList();
  }
}
