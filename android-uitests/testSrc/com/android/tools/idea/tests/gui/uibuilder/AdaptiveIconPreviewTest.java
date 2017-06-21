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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPreviewFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRunner.class)
public class AdaptiveIconPreviewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void densitySelector() throws IOException {
    NlPreviewFixture preview =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_launcher_adaptive.xml")
        .getLayoutPreview(true);
    NlConfigurationToolbarFixture<NlPreviewFixture> toolbar = preview.getConfigToolbar();
    toolbar.chooseDensity("mdpi");
    assertThat(preview.getCenterLeftPixelColor()).isEqualTo(-65536);
    toolbar.chooseDensity("hdpi");
    assertThat(preview.getCenterLeftPixelColor()).isEqualTo(-16776961);
    toolbar.chooseDensity("xhdpi");
    assertThat(preview.getCenterLeftPixelColor()).isEqualTo(-16776961);
    toolbar.chooseDensity("xxhdpi");
    assertThat(preview.getCenterLeftPixelColor()).isEqualTo(-16711936);
    toolbar.chooseDensity("xxxhdpi");
    assertThat(preview.getCenterLeftPixelColor()).isEqualTo(-16711936);
  }
}
