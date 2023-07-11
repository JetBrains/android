/*
 * Copyright (C) 2023 The Android Open Source Project
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


import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.codeInsight.hint.ImplementationViewComponent;
import com.intellij.openapi.editor.impl.EditorComponentImpl;
import java.util.Collection;
import javax.swing.JPanel;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JPanelFixture;
import org.jetbrains.annotations.NotNull;


public class DefinitionWindowFixture extends JPanelFixture {


  private static JPanelFixture myPanelFixture;

  public DefinitionWindowFixture(@NotNull Robot robot,
                                 @NotNull JPanel target) {
    super(robot, target);
    myPanelFixture = new JPanelFixture (robot(), robot().finder().findByType(this.target(), ImplementationViewComponent.class));

  }

  public static String getDefinitionContent(IdeFrameFixture ideFrame) {
    Collection<EditorComponentImpl> panels = ideFrame.robot().finder().findAll(Matchers.byType(EditorComponentImpl.class).andIsShowing());

    for(EditorComponentImpl panel : panels){
      if("Editor".equals(panel.getAccessibleContext().getAccessibleName())){
        return panel.getText();
      }
    }
    return null;
  }
}
