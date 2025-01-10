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
package com.android.tools.idea.npw.assetstudio;

import com.android.tools.idea.npw.NewProjectWizardTestUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class NotificationIconGeneratorTest extends AndroidTestCase {
  public NotificationIconGeneratorTest() {
    super(NewProjectWizardTestUtils.getAndroidVersion());
  }

  private void checkGraphic(@NotNull IconGeneratorTestUtil.SourceType sourceType)
      throws IOException {
    NotificationIconGenerator generator = new NotificationIconGenerator(getProject(), 14, null);
    disposeOnTearDown(generator);
    List<String> expectedFolders =
        sourceType == IconGeneratorTestUtil.SourceType.PNG
            ? ImmutableList.of(
                "drawable-xxxhdpi",
                "drawable-xxhdpi",
                "drawable-xhdpi",
                "drawable-hdpi",
                "drawable-mdpi")
            : ImmutableList.of(
                "drawable-anydpi-v24",
                "drawable-xxhdpi",
                "drawable-xhdpi",
                "drawable-hdpi",
                "drawable-mdpi");
    IconGeneratorTestUtil.checkGraphic(
        generator, sourceType, "ic_stat_1", 0, expectedFolders, "notification");
  }

  public void testPngSource() throws Exception {
    checkGraphic(IconGeneratorTestUtil.SourceType.PNG);
  }

  public void testSvgSource() throws Exception {
    checkGraphic(IconGeneratorTestUtil.SourceType.SVG);
  }
}
