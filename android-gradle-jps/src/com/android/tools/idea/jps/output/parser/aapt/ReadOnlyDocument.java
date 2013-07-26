/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.jps.output.parser.aapt;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.intellij.util.text.StringSearcher;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A read-only representation of the text of a file.
 */
class ReadOnlyDocument {
  @NotNull private final CharSequence myContents;
  @NotNull private final List<Integer> myOffsets;

  /**
   * Creates a new {@link ReadOnlyDocument} for the given file.
   *
   * @param file the file whose text will be stored in the document. UTF-8 charset is used to decode the contents of the file.
   * @throws java.io.IOException if an error occurs while reading the file.
   */
  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  ReadOnlyDocument(@NotNull File file) throws IOException {
    myContents = Files.toString(file, Charsets.UTF_8);
    myOffsets = Lists.newArrayListWithExpectedSize(myContents.length() / 30);
    myOffsets.add(0);
    for (int i = 0; i < myContents.length(); i++) {
      char c = myContents.charAt(i);
      if (c == '\n') {
        myOffsets.add(i + 1);
      }
    }
  }

  /**
   * Returns the offset of the given line number, relative to the beginning of the document.
   *
   * @param lineNumber the given line number.
   * @return the offset of the given line. -1 is returned if the document is empty, or if the given line number is negative or greater than
   *         the number of lines in the document.
   */
  int lineOffset(int lineNumber) {
    int index = lineNumber - 1;
    if (index < 0 || index >= myOffsets.size()) {
      return -1;
    }
    return myOffsets.get(index);
  }

  /**
   * Returns the line number of the given offset.
   *
   * @param offset the given offset.
   * @return the line number of the given offset. -1 is returned if the document is empty or if the offset is greater than the position of
   *         the last character in the document.
   */
  int lineNumber(int offset) {
    for (int i = 0; i < myOffsets.size(); i++) {
      int savedOffset = myOffsets.get(i);
      if (offset <= savedOffset) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Finds the given text in the document, starting from the given offset.
   *
   * @param text   the text to find.
   * @param offset the starting point of the search.
   * @return the offset of the found result, or -1 if no match was found.
   */
  int findText(String text, int offset) {
    StringSearcher searcher = new StringSearcher(text, true, true);
    return searcher.scan(myContents, offset, myContents.length());
  }

  int findTextBackwards(String text, int offset) {
    StringSearcher searcher = new StringSearcher(text, true, false);
    return searcher.scan(myContents, offset, myContents.length());
  }

  /**
   * Returns the character at the given offset.
   *
   * @param offset the position, relative to the beginning of the document, of the character to return.
   * @return the character at the given offset.
   * @throws IndexOutOfBoundsException if the {@code offset} argument is negative or not less than the document's size.
   */
  char charAt(int offset) {
    return myContents.charAt(offset);
  }

  /**
   * Returns the sub sequence for the given range.
   * @param start the starting offset.
   * @param end the ending offset, or -1 for the end of the file.
   * @return the sub sequence.
   */
  CharSequence subsequence(int start, int end) {
    return myContents.subSequence(start, end == -1 ? myContents.length() : end);
  }

  /**
   * @return the size (or length) of the document.
   */
  int length() {
    return myContents.length();
  }
}
