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
package com.android.tools.idea.gradle.structure.dependencies.android;

import com.android.tools.idea.gradle.model.IdeArtifactName;
import java.util.Objects;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class Configuration {
  static final Configuration MAIN = new Configuration("Main", IdeArtifactName.MAIN);
  static final Configuration ANDROID_TEST = new Configuration(AndroidBundle.message("android.test.run.configuration.type.name"),
                                                              IdeArtifactName.ANDROID_TEST);
  static final Configuration UNIT_TEST = new Configuration("Local Unit Tests", IdeArtifactName.UNIT_TEST);
  static final Configuration TEST_FIXTURES = new Configuration("Android Test Fixtures",
                                                              IdeArtifactName.TEST_FIXTURES);

  // TODO(karimai): add support for ScreenshotTest configurations.

  @NotNull private final String myName;
  @Nullable private final IdeArtifactName myArtifactName;

  Configuration(@NotNull String name, @Nullable IdeArtifactName artifactName) {
    myName = name;
    myArtifactName = artifactName;
  }

  @NotNull
  String getName() {
    return myName;
  }

  @Nullable
  IdeArtifactName getArtifactName() {
    return myArtifactName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Configuration that = (Configuration)o;
    return Objects.equals(myName, that.myName) && Objects.equals(myArtifactName, that.myArtifactName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myName, myArtifactName);
  }

  @Override
  public String toString() {
    return myName;
  }
}
