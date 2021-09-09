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
package com.android.tools.idea.uibuilder.handlers.motion.editor.ui;

import junit.framework.TestCase;

public class OverviewPanelTest extends TestCase {

  OverviewPanel.DerivedSetLine[] mDerivedLines = new OverviewPanel.DerivedSetLine[10];
  int baseTotalDerivedLines = 1;
  int totalDerivedLines = 5;
  String baseExpectedPathYOffset = "20";
  String expectedPathYOffset = "20,32,20,44,32";

  @Override
  public void setUp() throws Exception {
    for (int i = 0; i < mDerivedLines.length; i++) {
      mDerivedLines[i] = new OverviewPanel.DerivedSetLine();
    }

    mDerivedLines[0].mSrcX = 277;
    mDerivedLines[0].mDstX = 137;
    mDerivedLines[1].mSrcX = 347;
    mDerivedLines[1].mDstX = 207;
    mDerivedLines[2].mSrcX = 417;
    mDerivedLines[2].mDstX = 277;
    mDerivedLines[3].mSrcX = 487;
    mDerivedLines[3].mDstX = 137;
    mDerivedLines[4].mSrcX = 557;
    mDerivedLines[4].mDstX = 207;
  }

  private String toString(OverviewPanel.DerivedSetLine[] derivedLines, int totalLines) {
    StringBuilder str = new StringBuilder();
    for (int i = 0; i < totalLines; i++) {
      str.append(derivedLines[i].mPathYOffset);
      if (i < totalLines - 1) {
        str.append(",");
      }
    }
    return str.toString();
  }

  public void testBaseLineLevelOptimization() {
    OverviewPanel.optimizeLines(mDerivedLines, baseTotalDerivedLines);
    assertEquals(toString(mDerivedLines, baseTotalDerivedLines), baseExpectedPathYOffset);
  }

  public void testBaseLineLevelLocallyOptimization() {
    OverviewPanel.locallyOptimizeLines(mDerivedLines, baseTotalDerivedLines, 3);
    assertEquals(toString(mDerivedLines, baseTotalDerivedLines), baseExpectedPathYOffset);
  }

  public void testLineLevelOptimization() {
    OverviewPanel.optimizeLines(mDerivedLines, totalDerivedLines);
    assertEquals(toString(mDerivedLines, totalDerivedLines), expectedPathYOffset);
  }

  public void testLineLevelLocallyOptimization() {
    OverviewPanel.locallyOptimizeLines(mDerivedLines, totalDerivedLines, 3);
    assertEquals(toString(mDerivedLines, totalDerivedLines), expectedPathYOffset);
  }

}