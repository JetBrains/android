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
package com.android.tools.idea.welcome.wizard;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps track of attributes for ranges and has some special support for tracking process output.
 */
public final class ConsoleHighlighter implements EditorHighlighter, DocumentListener {
  private List<HighlightRange> myRanges = Lists.newArrayListWithCapacity(1024);
  private boolean myIsUpdatePending = false;
  private StringBuilder myPendingStrings = new StringBuilder(4096);
  private String myLastString = null;
  private HighlighterClient myEditor;
  private ModalityState myModalityState = ModalityState.defaultModalityState();

  public synchronized void print(String string, @Nullable TextAttributes attributes) {
    // Don't display the same string multiple times. This happens, for example,
    // when downloading a .zip file, as we get called multiple times with the same
    // zip file name.
    // Note the reason we need this de-duplication is because we add lines to
    // a log, whereas the progress indicator API (see ProgressIndicator.setText
    // and setText2) assumes the text is stored in some sort of JLabel, where it
    // does not matter if the same value is set multiple times.
    if (Objects.equals(myLastString, string)) {
      return;
    }
    myLastString = string;
    Application application = ApplicationManager.getApplication();
    myPendingStrings.append(string);
    if (!myIsUpdatePending && application != null && !application.isUnitTestMode()) {
      myIsUpdatePending = true;
      application.invokeLater(new Runnable() {
        @Override
        public void run() {
          appendToDocument();
        }
      }, myModalityState);
    }

    HighlightRange lastRange = Iterables.getLast(myRanges, HighlightRange.EMPTY);
    assert lastRange != null;
    int start = lastRange.end;
    myRanges.add(new HighlightRange(start, start + string.length(), attributes));
  }

  public void setModalityState(ModalityState state) {
    myModalityState = state;
  }

  /**
   * Code that requires locking and will be executed on UI thread. We should
   * not do long-running UI operations (e.g. document append) under the lock.
   */
  private synchronized String getPendingString() {
    String string = myPendingStrings.toString();
    myPendingStrings.delete(0, string.length());
    myIsUpdatePending = false;
    return string;
  }

  private void appendToDocument() {
    Document document = myEditor.getDocument();
    if (document != null) {
      String pendingString = StringUtil.convertLineSeparators(getPendingString());
      document.insertString(document.getTextLength(), pendingString);
      if (myEditor instanceof Editor) {
        Editor editor = (Editor)myEditor;
        int lineCount = document.getLineCount();
        editor.getScrollingModel().scrollTo(new LogicalPosition(lineCount - 1, 0), ScrollType.MAKE_VISIBLE);
      }
    }
  }

  @NotNull
  @Override
  public HighlighterIterator createIterator(int startOffset) {
    return new HighlightedRangesIterator(getOffsetRangeIndex(startOffset));
  }

  @Override
  public void setText(@NotNull CharSequence text) {

    EditorHighlighter.super.setText(text);
  }

  @Override
  public void setEditor(@NotNull HighlighterClient editor) {
    myEditor = editor;
  }

  @Override
  public void setColorScheme(@NotNull EditorColorsScheme scheme) {

    EditorHighlighter.super.setColorScheme(scheme);
  }

  private synchronized HighlightRange getRange(int index) {
    if (index < 0 || index >= myRanges.size()) {
      return HighlightRange.EMPTY;
    }
    else {
      return myRanges.get(index);
    }
  }

  @VisibleForTesting
  synchronized int getOffsetRangeIndex(int startOffset) {
    if (myRanges.isEmpty() || startOffset < 0 || startOffset >= Iterables.getLast(myRanges).end) {
      return -1;
    }
    int end = myRanges.size();
    int i = end / 2;
    while (true) {
      HighlightRange range = myRanges.get(i);
      if (range.end > startOffset) {
        if (range.start <= startOffset) {
          return i;
        }
        else {
          end = i;
          i /= 2;
        }
      }
      else {
        i = (i + end) / 2;
      }
    }
  }

  public void clear() {
    clearHighlightedState();
    myEditor.getDocument().setText("");
  }

  private synchronized void clearHighlightedState() {
    myRanges.clear();
    myPendingStrings.delete(0, myPendingStrings.length() - 1);
    myIsUpdatePending = false;
  }

  public void attachToProcess(ProcessHandler processHandler) {
    processHandler.addProcessListener(new ProcessOutputProcessor());
  }

  private static final class HighlightRange {
    public final static HighlightRange EMPTY = new HighlightRange(0, 0, null);

    @Nullable public final TextAttributes attributes;
    public final int start;
    public final int end;

    public HighlightRange(int start, int end, @Nullable TextAttributes attributes) {
      this.attributes = attributes;
      this.start = start;
      this.end = end;
    }
  }

  private class ProcessOutputProcessor extends ProcessAdapter {
    private AtomicBoolean mySkipped = new AtomicBoolean(false);

    @Override
    public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
      if (!mySkipped.compareAndSet(false, true)) {
        print(event.getText(), ConsoleViewContentType.getConsoleViewType(outputType).getAttributes());
      }
    }
  }

  private class HighlightedRangesIterator implements HighlighterIterator {
    private int myIndex;
    @NotNull private HighlightRange myRange = HighlightRange.EMPTY;

    public HighlightedRangesIterator(int index) {
      myIndex = index;
      myRange = getRange(index);
    }

    @Nullable
    @Override
    public TextAttributes getTextAttributes() {
      return myRange.attributes;
    }

    @Override
    public int getStart() {
      return myRange.start;
    }

    @Override
    public int getEnd() {
      return myRange.end;
    }

    @Nullable
    @Override
    public IElementType getTokenType() {
      return null;
    }

    @Override
    public void advance() {
      myRange = getRange(++myIndex);
    }

    @Override
    public void retreat() {
      myRange = getRange(--myIndex);
    }

    @Override
    public boolean atEnd() {
      return myRange == HighlightRange.EMPTY;
    }

    @Override
    public Document getDocument() {
      return myEditor.getDocument();
    }
  }
}
