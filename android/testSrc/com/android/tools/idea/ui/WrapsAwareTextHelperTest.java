/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.intellij.ui.SimpleTextAttributes;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.util.*;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Denis Zhdanov
 * @since 10/09/14
 */
public class WrapsAwareTextHelperTest {

  public static final  Font DUMMY_FONT    = new Font("", Font.PLAIN, 16);
  private static final int  SYMBOL_WIDTH  = 10;
  private static final int  SYMBOL_HEIGHT = 10;
  private static final String LINE_BREAK_MARKER;
  static {
    List<String> buffer = new ArrayList<String>();
    WrapsAwareTextHelper.appendLineBreak(buffer);
    LINE_BREAK_MARKER = buffer.get(0);
  }

  @NotNull WrapsAwareTextHelper.DimensionCalculator myDefaultDimensionCalculator = new WrapsAwareTextHelper.DimensionCalculator() {
    @Override
    public void calculate(@NotNull String inText, @NotNull Font inFont, @NotNull Dimension outDimension) {
      outDimension.width = inText.length() * SYMBOL_WIDTH;
      outDimension.height = SYMBOL_HEIGHT;
    }
  };

  @NotNull private final TIntIntHashMap myMinimumWidths = new TIntIntHashMap();
  @NotNull private WrapsAwareTextHelper myCalculator;

  @Before
  public void setUp() {
    myCalculator = new WrapsAwareTextHelper(myDefaultDimensionCalculator);
    myMinimumWidths.clear();
  }

  @Test
  public void wrap_singleLine_singleToken_noWraps() {
    doWrapTest(Collections.singletonList("abc"), Collections.singletonList("abc"), 3);
  }

  @Test
  public void wrap_singleLine_twoTokens_noWraps() {
    doWrapTest(Arrays.asList("ab", "c"), Collections.singletonList("abc"), 3);
  }

  @Test
  public void wrap_singleLine_twoTokens_singleWrap() {
    doWrapTest(Arrays.asList("ab", "cd"), Arrays.asList("ab", "cd"), 2);
    doWrapTest(Arrays.asList("ab", "cd"), Arrays.asList("abc", "d"), 3);
  }

  @Test
  public void wrap_singleLine_manyTokens_manyWraps() {
    doWrapTest(Arrays.asList("123", "4567", "8"), Arrays.asList("1234", "5678"), 4);
    doWrapTest(Arrays.asList("123", "4567", "89"), Arrays.asList("1234", "5678", "9"), 4);
  }

  @Test
  public void wrap_singleLine_singleToken_singleWrap() {
    doWrapTest(Collections.singletonList("abc"), Arrays.asList("ab", "c"), 2);
  }

  @Test
  public void wrap_singleLine_singleToken_twoWraps() {
    doWrapTest(Collections.singletonList("12345678"), Arrays.asList("123", "456", "78"), 3);
    doWrapTest(Collections.singletonList("123456789"), Arrays.asList("123", "456", "789"), 3);
  }

  @Test
  public void wrap_noWidthLimit() {
    doWrapTest(Arrays.asList("abc"), Arrays.asList("abc"), 0);
    doWrapTest(Arrays.asList("abc"), Arrays.asList("abc"), -1);
    doWrapTest(Arrays.asList("abc"), Arrays.asList("abc"), Integer.MIN_VALUE);
  }

  @Test
  public void wrap_twoLines() {
    doWrapTest(Arrays.asList("1234", LINE_BREAK_MARKER, "567"), Arrays.asList("123", "4", "567"), 3);
  }

  @Test
  public void map_singleLine_singleFragment() {
    doMapTest(Arrays.asList("abc"), 3, 0, 0, 0);
    doMapTest(Arrays.asList("abc"), 3, 0, 1, 0);
    doMapTest(Arrays.asList("abc"), 3, 0, 2, 0);
    doMapTest(Arrays.asList("abc"), 3, 0, 3, -1);
    doMapTest(Arrays.asList("abc"), 3, 1, 1, -1);
  }

  @Test
  public void map_singleLine_multipleFragments_multipleWraps() {
    doMapTest(Arrays.asList("abc", "def"), 2, 0, 0, 0);
    doMapTest(Arrays.asList("abc", "def"), 2, 0, 1, 0);
    doMapTest(Arrays.asList("abc", "def"), 2, 0, 2, -1);
    doMapTest(Arrays.asList("abc", "def"), 2, 1, 0, 0);
    doMapTest(Arrays.asList("abc", "def"), 2, 1, 2, -1);
    doMapTest(Arrays.asList("abc", "def"), 2, 2, 0, 1);
    doMapTest(Arrays.asList("abc", "def"), 2, 2, 1, 1);
    doMapTest(Arrays.asList("abc", "def"), 2, 2, 2, -1);
    doMapTest(Arrays.asList("abc", "def"), 2, 3, 0, -1);
    doMapTest(Arrays.asList("abc", "def"), 2, 3, 1, -1);
    doMapTest(Arrays.asList("abc", "def"), 2, 4, 1, -1);
  }

  @Test
  public void map_lineBreak() {
    doMapTest(Arrays.asList("ab", LINE_BREAK_MARKER, "de"), 3, 0, 0, 0);
    doMapTest(Arrays.asList("ab", LINE_BREAK_MARKER, "de"), 3, 0, 1, 0);
    doMapTest(Arrays.asList("ab", LINE_BREAK_MARKER, "de"), 3, 0, 2, -1);
    doMapTest(Arrays.asList("ab", LINE_BREAK_MARKER, "de"), 3, 1, 0, 2);
    doMapTest(Arrays.asList("ab", LINE_BREAK_MARKER, "de"), 3, 1, 1, 2);
    doMapTest(Arrays.asList("ab", LINE_BREAK_MARKER, "de"), 3, 1, 2, -1);
  }

  @Test
  public void map_minimumWidth() {
    myMinimumWidths.put(0, 3 * SYMBOL_WIDTH);
    doMapTest(Arrays.asList("ab", "cd"), 10, 0, 0, 0);
    doMapTest(Arrays.asList("ab", "cd"), 10, 0, 1, 0);
    doMapTest(Arrays.asList("ab", "cd"), 10, 0, 2, -1);
    doMapTest(Arrays.asList("ab", "cd"), 10, 0, 3, 1);
    doMapTest(Arrays.asList("ab", "cd"), 10, 0, 4, 1);
    doMapTest(Arrays.asList("ab", "cd"), 10, 0, 5, -1);
    doMapTest(Arrays.asList("ab", "cd"), 10, 1, 0, -1);
  }

  private void doWrapTest(@NotNull List<String> fragments, @NotNull List<String> expectedLines, int availableWidthInSymbols) {
    // Verify that given data is consistent.
    verifyTestData(fragments, expectedLines);

    // Calculate the data.
    List<SimpleTextAttributes> textAttributes = Collections.nCopies(fragments.size(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    Dimension dimension = new Dimension();
    TIntObjectHashMap<TIntArrayList> breakOffsets = new TIntObjectHashMap<TIntArrayList>();
    TIntIntHashMap lineHeights = new TIntIntHashMap();
    int widthLimit = availableWidthInSymbols * SYMBOL_WIDTH;
    myCalculator.wrap(fragments, textAttributes, DUMMY_FONT, myMinimumWidths, widthLimit, dimension, breakOffsets, lineHeights);

    // Check calculated dimension vs expected.
    int expectedWidthInPixels = 0;
    for (String line : expectedLines) {
      expectedWidthInPixels = Math.max(expectedWidthInPixels, line.length() * SYMBOL_WIDTH);
    }
    assertEquals("Target text dimension width doesn't match", expectedWidthInPixels, dimension.width);
    assertEquals("Target text dimension height doesn't match", expectedLines.size() * SYMBOL_HEIGHT, dimension.height);

    // Check line heights.
    assertEquals("Target line heights don't match", buildExpectedLineHeights(expectedLines), lineHeights);

    // Check calculated text break offsets vs expected.
    TIntObjectHashMap<TIntArrayList> expectedBreakOffsets = buildExpectedBreakOffsets(fragments, expectedLines);
    assertEquals(expectedBreakOffsets, breakOffsets);
  }

  @NotNull
  private static TIntIntHashMap buildExpectedLineHeights(@NotNull List<String> expectedLines) {
    TIntIntHashMap result = new TIntIntHashMap();
    for (int i = 0; i < expectedLines.size(); i++) {
      result.put(i, SYMBOL_HEIGHT);
    }
    return result;
  }

  @NotNull
  private static TIntObjectHashMap<TIntArrayList> buildExpectedBreakOffsets(List<String> fragments, List<String> expectedLines) {
    TIntObjectHashMap<TIntArrayList> expectedBreakOffsets = new TIntObjectHashMap<TIntArrayList>();
    int currentLineOffset = 0;
    int fragmentOffset;
    expectedLines = new ArrayList<String>(expectedLines);
    for (int fragmentIndex = 0; fragmentIndex < fragments.size(); fragmentIndex++) {
      String fragmentText = fragments.get(fragmentIndex);
      fragmentOffset = 0;
      if (LINE_BREAK_MARKER.equals(fragmentText)) {
        currentLineOffset = 0;
        expectedLines.remove(0);
        continue;
      }
      while (true) {
        String s = expectedLines.get(0);
        if (s.length() - currentLineOffset < fragmentText.length() - fragmentOffset) {
          TIntArrayList list = expectedBreakOffsets.get(fragmentIndex);
          if (list == null) {
            expectedBreakOffsets.put(fragmentIndex, list = new TIntArrayList());
          }
          list.add(fragmentOffset += s.length() - currentLineOffset);
          expectedLines.remove(0);
          currentLineOffset = 0;
          continue;
        }
        currentLineOffset += fragmentText.length() - fragmentOffset;
        break;
      }
    }
    return expectedBreakOffsets;
  }

  private static void verifyTestData(@NotNull List<String> fragments, @NotNull List<String> expectedLines) {
    SymbolIterator initialIterator = new SymbolIterator(fragments);
    SymbolIterator expectedIterator = new SymbolIterator(expectedLines);
    StringBuilder buffer = new StringBuilder();
    int offset = 0;
    while (initialIterator.hasNext()) {
      char c = initialIterator.next();
      buffer.append(c);
      if (!expectedIterator.hasNext()) {
        throw new IllegalArgumentException(String.format(
          "Given input text has at least one more symbol than expected (at offset %d) %n Input:%n%s %n Expected:%n%s", offset, fragments,
          expectedLines));
      }
      char c1 = expectedIterator.next();
      if (c != c1) {
        throw new IllegalArgumentException(String.format(
          "Given input text mismatches given expected text: input text has symbol '%c' at offset %d but expected has '%c' (%s) %n "
          + "Input:%n%s %n Expected:%n%s",
          c, offset, c1, "..." + buffer.subSequence(Math.max(0, offset - 5), offset + 1), fragments, expectedLines
        ));
      }
      offset++;
    }
    if (expectedIterator.hasNext()) {
      throw new IllegalArgumentException(String.format(
        "Given expected text has at least one more symbol than initial (at offset %d) %n Input:%n%s %n Expected:%n%s",
        offset, fragments, expectedLines
      ));
    }
  }

  private void doMapTest(@NotNull List<String> fragments,
                         int availableWidthInSymbols,
                         int targetLine,
                         int targetColumn,
                         int expectedFragmentIndex)
  {
    // Prepare the data.
    List<SimpleTextAttributes> textAttributes = Collections.nCopies(fragments.size(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    Dimension dimension = new Dimension();
    TIntObjectHashMap<TIntArrayList> breakOffsets = new TIntObjectHashMap<TIntArrayList>();
    TIntIntHashMap lineHeights = new TIntIntHashMap();
    int widthLimit = availableWidthInSymbols * SYMBOL_WIDTH;
    myCalculator.wrap(fragments, textAttributes, DUMMY_FONT, myMinimumWidths, widthLimit, dimension, breakOffsets, lineHeights);

    // Do test.
    int actualFragmentIndex = myCalculator.mapFragment(fragments,
                                                       textAttributes,
                                                       myMinimumWidths,
                                                       breakOffsets,
                                                       lineHeights,
                                                       DUMMY_FONT,
                                                       targetColumn * SYMBOL_HEIGHT + SYMBOL_HEIGHT / 2,
                                                       targetLine * SYMBOL_HEIGHT + SYMBOL_HEIGHT / 2);

    if (expectedFragmentIndex < 0 ^ actualFragmentIndex < 0) {
      fail(String.format("Mapped fragment index mismatch for the input data: fragments=%s, available width=%d, target line=%d, "
                         + "target column=%d, expected index=%d, actual index=%d",
                         fragments, availableWidthInSymbols, targetLine, targetColumn, expectedFragmentIndex, actualFragmentIndex));
    }
    assertEquals("Target fragments index mismatch", expectedFragmentIndex, actualFragmentIndex);
  }

  private static class SymbolIterator {

    @NotNull private final Iterator<String> myDelegate;

    @Nullable private String myCurrentString;

    private int myCurrentStringOffset;

    private SymbolIterator(@NotNull Iterable<String> strings) {
      myDelegate = strings.iterator();
    }

    boolean hasNext() {
      if (myCurrentString != null) {
        return true;
      }
      if (!myDelegate.hasNext()) {
        return false;
      }
      myCurrentString = myDelegate.next();
      if (LINE_BREAK_MARKER.equals(myCurrentString)) {
        myCurrentString = null;
        return hasNext();
      }
      myCurrentStringOffset = 0;
      return true;
    }

    char next() {
      if (!hasNext()) {
        throw new IllegalStateException();
      }
      assert myCurrentString != null;
      char c = myCurrentString.charAt(myCurrentStringOffset++);
      if (myCurrentStringOffset >= myCurrentString.length()) {
        myCurrentString = null;
      }
      return c;
    }
  }
}
