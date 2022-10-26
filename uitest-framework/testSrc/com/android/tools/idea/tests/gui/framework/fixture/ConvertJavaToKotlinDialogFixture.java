/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.Assert.assertTrue;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import javax.swing.JDialog;
import org.fest.swing.finder.DialogFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.timing.Wait;

public class ConvertJavaToKotlinDialogFixture {
  @NotNull private final IdeFrameFixture myIdeFrame;
  @NotNull private final DialogFixture myDialog;
  @NotNull static final String TITLE = "Convert Java to Kotlin";

  public static ConvertJavaToKotlinDialogFixture find(IdeFrameFixture ideFrame) {
    DialogFixture convertCodeFromJavaDialog = findDialog(withTitle("Convert Java to Kotlin"))
      .withTimeout(SECONDS.toMillis(300)).using(ideFrame.robot());
    return new ConvertJavaToKotlinDialogFixture(ideFrame, convertCodeFromJavaDialog);
  }

  private ConvertJavaToKotlinDialogFixture(@NotNull IdeFrameFixture ideFrameFixture,
                                           DialogFixture dialog) {
    myIdeFrame = ideFrameFixture;
    myDialog = dialog;
  }

  public void clickYes() {
    myDialog.button(withText("Yes"))
      .click();
  }
}
