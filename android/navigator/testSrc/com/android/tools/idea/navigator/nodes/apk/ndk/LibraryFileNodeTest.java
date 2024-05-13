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
package com.android.tools.idea.navigator.nodes.apk.ndk;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.ui.JBColor.GRAY;
import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_WAVED;
import static org.mockito.Mockito.mock;

import com.android.tools.idea.apk.debugging.NativeLibrary;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.ui.SimpleTextAttributes;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link LibraryFileNode}.
 */
public class LibraryFileNodeTest extends HeavyPlatformTestCase {
  private NativeLibrary myLibrary;
  private PresentationData myPresentation;
  private LibraryFileNode myNode;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myLibrary = new NativeLibrary("Library1");
    myPresentation = new PresentationData();
    myNode = new LibraryFileNode(getProject(), myLibrary, mock(ViewSettings.class));
  }

  public void testUpdateWithDebugSymbols() {
    myLibrary.hasDebugSymbols = true;

    myNode.update(myPresentation);
    assertNull(myPresentation.getTooltip());

    List<PresentableNodeDescriptor.ColoredFragment> allText = myPresentation.getColoredText();
    assertThat(allText).hasSize(1);

    PresentableNodeDescriptor.ColoredFragment text = allText.get(0);
    assertEquals(myLibrary.name, text.getText());

    assertSame(REGULAR_ATTRIBUTES, text.getAttributes());
  }

  public void testUpdateWithoutDebugSymbols() {
    myLibrary.hasDebugSymbols = false;

    myNode.update(myPresentation);
    assertEquals("Library does not have debug symbols", myPresentation.getTooltip());

    List<PresentableNodeDescriptor.ColoredFragment> allText = myPresentation.getColoredText();
    assertThat(allText).hasSize(1);

    PresentableNodeDescriptor.ColoredFragment text = allText.get(0);
    assertEquals(myLibrary.name, text.getText());

    verifyHasGrayWave(text);
  }

  public void testUpdateWithMissingPathMappings() {
    myLibrary.hasDebugSymbols = true;
    myLibrary.pathMappings.put("abc.so", "");

    myNode.update(myPresentation);
    assertEquals("Library is missing path mappings", myPresentation.getTooltip());

    List<PresentableNodeDescriptor.ColoredFragment> allText = myPresentation.getColoredText();
    assertThat(allText).hasSize(1);

    PresentableNodeDescriptor.ColoredFragment text = allText.get(0);
    assertEquals(myLibrary.name, text.getText());

    verifyHasGrayWave(text);
  }

  private static void verifyHasGrayWave(@NotNull PresentableNodeDescriptor.ColoredFragment text) {
    SimpleTextAttributes attributes = text.getAttributes();
    assertEquals(STYLE_WAVED, attributes.getStyle());
    assertEquals(GRAY, attributes.getWaveColor());
  }
}