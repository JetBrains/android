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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.error.IssuePanel;
import com.android.tools.idea.uibuilder.model.AttributesTransaction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.Computable;
import com.intellij.ui.JBSplitter;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.driver.JComponentDriver;
import org.fest.swing.fixture.AbstractContainerFixture;
import org.fest.swing.fixture.JComponentFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.util.regex.Pattern;

/**
 * Fixture for {@link com.android.tools.idea.uibuilder.error.IssuePanel}
 */
public class IssuePanelFixture extends AbstractContainerFixture<IssuePanelFixture, IssuePanel, JComponentDriver<JPanel>>
  implements JComponentFixture<IssuePanelFixture> {

  public IssuePanelFixture(@Nonnull Robot robot) {
    super(IssuePanelFixture.class, robot, IssuePanel.ISSUE_PANEL_NAME, IssuePanel.class);
  }

  public IssuePanelFixture(@Nonnull Robot robot, IssuePanel target) {
    super(IssuePanelFixture.class, robot, target);
  }

  public IssuePanelFixture extandToHalf() {
    ((JBSplitter)robot().finder().find(c -> c instanceof JBSplitter)).setProportion(0.5f);
    return this;
  }

  public void addConstraints(NlEditorFixture layoutEditor, NlComponentFixture componentFixture) throws Throwable {
    AttributesTransaction transaction = componentFixture.getComponent().startAttributeTransaction();
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, "parent");
    transaction.setAttribute(SdkConstants.SHERPA_URI, SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, "parent");
    WriteCommandAction.runWriteCommandAction(layoutEditor.getSurface().target().getProject(), (Computable<Boolean>)transaction::commit);
    layoutEditor.waitForRenderToFinish(Wait.seconds(10));
  }

  @Nullable
  @Override
  public Object clientProperty(@Nonnull Object key) {
    return driver().clientProperty(target(), key);
  }

  @Nonnull
  @Override
  public IssuePanelFixture requireToolTip(@Nullable String expected) {
    driver().requireToolTip(target(), expected);
    return this;
  }

  @Nonnull
  @Override
  public IssuePanelFixture requireToolTip(@Nonnull Pattern pattern) {
    driver().requireToolTip(target(), pattern);
    return this;
  }

  @Nonnull
  @Override
  protected JComponentDriver<JPanel> createDriver(@Nonnull Robot robot) {
    return new JComponentDriver<>(robot);
  }

  public JLabelFixture issueLabel(String issueTitle) throws NullPointerException {
    return label(new GenericTypeMatcher<JLabel>(JLabel.class, true) {
      @Override
      protected boolean isMatching(@NotNull JLabel component) {
        return component.getText().equalsIgnoreCase(issueTitle);
      }
    });
  }

  public void clickFixButton() {
    button(new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return component.getText().equalsIgnoreCase("fix");
      }
    }).click();
  }

  public boolean hasRenderError() {
    return target().getIssueModel().hasRenderError() && target().getTitleText().matches(".*[Ee]rror.*");
  }

  public boolean containsText(String text) {
    return target().containsErrorWithText(text);
  }
}
