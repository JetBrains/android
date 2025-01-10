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
import com.android.tools.idea.npw.assetstudio.ActionBarIconGenerator.Theme;
import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class ActionBarIconGeneratorTest extends AndroidTestCase {
  public ActionBarIconGeneratorTest() {
    super(NewProjectWizardTestUtils.getAndroidVersion());
  }

  private void checkGraphic(@NotNull String baseName,
                            @NotNull IconGeneratorTestUtil.SourceType sourceType,
                            @NotNull Theme theme) throws IOException {
    ActionBarIconGenerator generator = new ActionBarIconGenerator(getProject(), 15, null);
    disposeOnTearDown(generator);
    generator.theme().set(theme);
    if (theme == Theme.CUSTOM) {
      generator.customColor().set(Color.BLACK);
    }
    List<String> expectedFolders =
      sourceType == IconGeneratorTestUtil.SourceType.PNG ?
            ImmutableList.of("drawable-xxxhdpi", "drawable-xxhdpi", "drawable-xhdpi", "drawable-hdpi", "drawable-mdpi") :
            ImmutableList.of("drawable-anydpi", "drawable-xxhdpi", "drawable-xhdpi", "drawable-hdpi", "drawable-mdpi");
    IconGeneratorTestUtil.checkGraphic(generator, sourceType, baseName, 0, expectedFolders, "actions");
  }

  public void testPngDark() throws Exception {
    checkGraphic("ic_action_dark", IconGeneratorTestUtil.SourceType.PNG, Theme.HOLO_DARK);
  }

  public void testPngLight() throws Exception {
    checkGraphic("ic_action_light", IconGeneratorTestUtil.SourceType.PNG, Theme.HOLO_LIGHT);
  }

  public void testSvgLight() throws Exception {
    checkGraphic("ic_action_light", IconGeneratorTestUtil.SourceType.SVG, Theme.HOLO_LIGHT);
  }

  public void testSvgCustom() throws Exception {
    checkGraphic("ic_action_custom", IconGeneratorTestUtil.SourceType.SVG, Theme.CUSTOM);
  }
}
