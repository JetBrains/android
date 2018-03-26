/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.run;

import com.android.tools.idea.run.tasks.DynamicAppDeployTaskContext;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

public class DynamicAppDeployTaskContextTest {
  @Test
  public void getArtifactsWorks() {
    List<ApkFileUnit> files = new ArrayList<>();
    files.add(new ApkFileUnit("app", new File("app.apk")));
    files.add(new ApkFileUnit("feature1", new File("feature1.apk")));
    files.add(new ApkFileUnit("feature2", new File("feature2.apk")));
    ApkInfo apkInfo = new ApkInfo(files, "myAppId");

    List<String> disabledFeatures = new ArrayList<>();

    DynamicAppDeployTaskContext context = new DynamicAppDeployTaskContext(apkInfo, disabledFeatures);

    assertThat(context.getApplicationId()).isEqualTo("myAppId");
    assertThat(context.isPatchBuild()).isEqualTo(false);
    assertThat(context.getArtifacts()).containsExactly(new File("app.apk"), new File("feature1.apk"), new File("feature2.apk"));
  }

  @Test
  public void getArtifactsFiltersDisabledFeatures() {
    List<ApkFileUnit> files = new ArrayList<>();
    files.add(new ApkFileUnit("app", new File("app.apk")));
    files.add(new ApkFileUnit("feature1", new File("feature1.apk")));
    files.add(new ApkFileUnit("feature2", new File("feature2.apk")));
    ApkInfo apkInfo = new ApkInfo(files, "myAppId");

    List<String> disabledFeatures = new ArrayList<>();
    disabledFeatures.add("feature1");

    DynamicAppDeployTaskContext context = new DynamicAppDeployTaskContext(apkInfo, disabledFeatures);

    assertThat(context.getApplicationId()).isEqualTo("myAppId");
    assertThat(context.isPatchBuild()).isEqualTo(false);
    assertThat(context.getArtifacts()).containsExactly(new File("app.apk"), new File("feature2.apk"));
  }
}
