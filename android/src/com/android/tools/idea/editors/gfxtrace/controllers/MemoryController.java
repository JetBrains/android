/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.MemoryInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.MemoryRangePath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Range;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Segment;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class MemoryController extends Controller {
  @NotNull private static final Logger LOG = Logger.getInstance(MemoryController.class);

  public static JComponent createUI(GfxTraceEditor editor) {
    return new MemoryController(editor).myPanel;
  }

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final JBLoadingPanel myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject(), 50);
  @NotNull private final JScrollPane myScrollPane = new JBScrollPane();

  private MemoryController(@NotNull GfxTraceEditor editor) {
    super(editor);
    myLoading.add(myScrollPane, BorderLayout.CENTER);
    myPanel.add(myLoading, BorderLayout.CENTER);
  }

  @Override
  public void notifyPath(final Path path) {
    myScrollPane.setViewportView(null);
    if (path instanceof MemoryRangePath) {
      myLoading.startLoading();
      ListenableFuture<MemoryInfo> memoryFuture = myEditor.getClient().get((MemoryRangePath)path);
      Futures.addCallback(memoryFuture, new FutureCallback<MemoryInfo>() {
        @Override
        public void onSuccess(MemoryInfo result) {
          update(((MemoryRangePath)path).getAddress(), result);
        }

        @Override
        public void onFailure(Throwable t) {
          LOG.error("Failed to load memory " + path, t);
        }
      });
    }
  }

  private void update(long address, MemoryInfo info) {
    final MemoryPanel contents = new MemoryPanel(new MemoryModel(address, info.getData()));
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myLoading.stopLoading();
        myScrollPane.setViewportView(contents);
      }
    });
  }

  private static class MemoryPanel extends JComponent implements Scrollable, DataProvider, CopyProvider {
    private final MemoryModel myModel;
    private final EditorColorsScheme myTheme;
    private Range<Integer> mySelectionRange = null;
    private final Point mySelectionStart = new Point();
    private final Point mySelectionEnd = new Point();

    public MemoryPanel(final MemoryModel model) {
      myModel = model;
      myTheme = EditorColorsManager.getInstance().getGlobalScheme();

      setCursor(new Cursor(Cursor.TEXT_CURSOR));
      setFocusable(true);

      MouseAdapter mouseHandler = new MouseAdapter() {
        private final Point mySelectionInitiation = new Point();

        @Override
        public void mousePressed(MouseEvent e) {
          requestFocus();
          if (isSelectionButton(e)) {
            startSelecting(e);
            repaint();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          if (mySelectionRange != null && mySelectionStart.equals(mySelectionEnd)) {
            mySelectionRange = null;
            repaint();
          }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          if (isSelectionButton(e)) {
            if (mySelectionRange == null) {
              startSelecting(e);
            }
            else {
              updateSelection(e);
            }
          }
        }

        private void startSelecting(MouseEvent e) {
          int y = e.getY() / getLineHeight();
          if (y < 0 || y >= model.getLineCount()) {
            mySelectionRange = null;
            return;
          }
          mySelectionInitiation.setLocation(getX(e), e.getY() / getLineHeight());
          mySelectionStart.setLocation(mySelectionInitiation);
          mySelectionEnd.setLocation(mySelectionStart);
          mySelectionRange = model.getSelectableRegion(mySelectionStart.x);
        }

        private void updateSelection(MouseEvent e) {
          int x = Math.max(mySelectionRange.getFrom(), Math.min(mySelectionRange.getTo(), getX(e)));
          int y = Math.max(0, e.getY() / getLineHeight());
          if (y >= model.getLineCount()) {
            y = model.getLineCount() - 1;
            x = mySelectionRange.getTo();
          }

          if (y < mySelectionInitiation.y || (y == mySelectionInitiation.y && x < mySelectionInitiation.x)) {
            mySelectionStart.setLocation(x, y);
            mySelectionEnd.setLocation(mySelectionInitiation);
          }
          else {
            mySelectionStart.setLocation(mySelectionInitiation);
            mySelectionEnd.setLocation(x, y);
          }

          repaint();
        }

        private int getX(MouseEvent e) {
          int charWidth = getCharWidth();
          return (e.getX() + charWidth / 2) / charWidth;
        }

        private boolean isSelectionButton(MouseEvent e) {
          return (e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0;
        }
      };
      addMouseListener(mouseHandler);
      addMouseMotionListener(mouseHandler);
    }

    @Nullable
    @Override
    public Object getData(@NonNls String dataId) {
      if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
        return this;
      }
      return null;
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return mySelectionRange != null && !mySelectionStart.equals(mySelectionEnd);
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      if (isCopyEnabled(dataContext)) {
        CopyPasteManager.getInstance().setContents(myModel.getTransferable(mySelectionRange, mySelectionStart, mySelectionEnd));
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension result = new Dimension(myModel.getLineLength() * getCharWidth(), myModel.getLineCount() * getLineHeight());
      if (getParent() instanceof JViewport) {
        Dimension parent = ((JViewport)getParent()).getExtentSize();
        result.width = Math.max(parent.width, result.width);
        result.height = Math.max(parent.height, result.height);
      }
      return result;
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      setFont(myTheme.getFont(EditorFontType.PLAIN));
      initMeasurements();
      setBackground(myTheme.getDefaultBackground());
      setForeground(myTheme.getDefaultForeground());

      Rectangle clip = g.getClipBounds();

      g.setColor(getBackground());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
      g.setColor(getForeground());

      int lineHeight = getLineHeight();
      int charWidth = getCharWidth();
      int startRow = Math.max(0, clip.y / lineHeight);
      int endRow = Math.min(myModel.getLineCount(), (clip.y + clip.height + lineHeight - 1) / lineHeight);
      boolean selectionVisible = false;

      if (mySelectionRange != null && startRow <= mySelectionEnd.y && mySelectionStart.y <= endRow) {
        selectionVisible = true;
        g.setColor(myTheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
        if (mySelectionStart.y == mySelectionEnd.y) {
          g.fillRect(mySelectionStart.x * charWidth, mySelectionStart.y * lineHeight, (mySelectionEnd.x - mySelectionStart.x) * charWidth,
                     lineHeight);
        }
        else {
          g.fillRect(mySelectionStart.x * charWidth, mySelectionStart.y * lineHeight,
                     (mySelectionRange.getTo() - mySelectionStart.x) * charWidth, lineHeight);
          g.fillRect(mySelectionRange.getFrom() * charWidth, mySelectionEnd.y * lineHeight,
                     (mySelectionEnd.x - mySelectionRange.getFrom()) * charWidth, lineHeight);
          g.fillRect(mySelectionRange.getFrom() * charWidth, (mySelectionStart.y + 1) * lineHeight,
                     (mySelectionRange.getTo() - mySelectionRange.getFrom()) * charWidth,
                     (mySelectionEnd.y - mySelectionStart.y - 1) * lineHeight);
        }
        g.setColor(getForeground());
      }

      int y = getAscent() + startRow * lineHeight;
      if (!selectionVisible) {
        for (Iterator<Segment> it = myModel.getLines(startRow, endRow); it.hasNext(); y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, segment.count, 0, y);
        }
      }
      else {
        int row = startRow;
        int rangeWidth = mySelectionRange.getTo() - mySelectionRange.getFrom();
        int fromWidth = mySelectionRange.getFrom() * charWidth, toWidth = mySelectionRange.getTo() * charWidth;
        Iterator<Segment> it = myModel.getLines(startRow, endRow);
        // Lines before selection.
        for (; it.hasNext() && row < mySelectionStart.y; row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, segment.count, 0, y);
        }
        // First selected line, possibly partially selected.
        for (; it.hasNext() && row == mySelectionStart.y; row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, mySelectionStart.x, 0, y);
          g.setColor(myTheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
          if (mySelectionStart.y == mySelectionEnd.y) {
            g.drawChars(segment.array, segment.offset + mySelectionStart.x, mySelectionEnd.x - mySelectionStart.x,
                        mySelectionStart.x * charWidth, y);
            g.setColor(getForeground());
            g.drawChars(
              segment.array, segment.offset + mySelectionEnd.x, segment.count - mySelectionEnd.x, mySelectionEnd.x * charWidth, y);
          }
          else {
            g.drawChars(segment.array, segment.offset + mySelectionStart.x, mySelectionRange.getTo() - mySelectionStart.x,
                        mySelectionStart.x * charWidth, y);
            g.setColor(getForeground());
            g.drawChars(segment.array, segment.offset + mySelectionRange.getTo(), segment.count - mySelectionRange.getTo(), toWidth, y);
          }
        }
        // Fully selected lines.
        for (; it.hasNext() && row < mySelectionEnd.y; row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, mySelectionRange.getFrom(), 0, y);
          g.setColor(myTheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
          g.drawChars(segment.array, segment.offset + mySelectionRange.getFrom(), rangeWidth, fromWidth, y);
          g.setColor(getForeground());
          g.drawChars(segment.array, segment.offset + mySelectionRange.getTo(), segment.count - mySelectionRange.getTo(), toWidth, y);
        }
        // Last selected line, possibly partially selected.
        for (; it.hasNext() && row == mySelectionEnd.y; row++, y += lineHeight) { // Note: mySelectionStart.y != mySelectionEnd.y here.
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, mySelectionRange.getFrom(), 0, y);
          g.setColor(myTheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
          g.drawChars(segment.array, segment.offset + mySelectionRange.getFrom(), mySelectionEnd.x - mySelectionRange.getFrom(), fromWidth,
                      y);
          g.setColor(getForeground());
          g.drawChars(segment.array, segment.offset + mySelectionEnd.x, segment.count - mySelectionEnd.x, mySelectionEnd.x * charWidth, y);
        }
        // Lines after selection.
        for (; it.hasNext(); row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, segment.count, 0, y);
        }
      }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return (orientation == SwingConstants.VERTICAL) ? getLineHeight() : getCharWidth();
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      return (orientation == SwingConstants.VERTICAL)
             ? (visibleRect.height - (visibleRect.height % getLineHeight()))
             : (visibleRect.width - (visibleRect.width % getCharWidth()));
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      return false;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return false;
    }

    private int myLineHeight;
    private int myAscent;
    private int myCharWidth;

    private int getLineHeight() {
      if (myLineHeight <= 0) {
        initMeasurements();
      }
      return myLineHeight;
    }

    public int getAscent() {
      if (myAscent <= 0) {
        initMeasurements();
      }
      return myAscent;
    }

    private int getCharWidth() {
      if (myCharWidth <= 0) {
        initMeasurements();
      }
      return myCharWidth;
    }

    private void initMeasurements() {
      FontMetrics metrics = getFontMetrics(getFont());
      myLineHeight = metrics.getHeight();
      myAscent = metrics.getAscent();
      myCharWidth = metrics.charWidth(' ');
    }
  }

  private static class MemoryModel {
    private static final int BYTES_PER_ROW = 16; // If this is changed, Formatter.increment needs to be fixed.
    private static final int CHARS_PER_ADDRESS = 16; // 8 byte addresses
    private static final int CHARS_PER_BYTE = 2; //* 2 hex chars per byte

    private static final int ADDRESS_SEPARATOR = 1;
    private static final int BYTE_SEPARATOR = 1;
    private static final int ASCII_SEPARATOR = 2;

    private static final int ADDRESS_CHARS = CHARS_PER_ADDRESS + ADDRESS_SEPARATOR;
    private static final int BYTES_CHARS = (CHARS_PER_BYTE + BYTE_SEPARATOR) * BYTES_PER_ROW;
    private static final int ASCII_CHARS = BYTES_PER_ROW + ASCII_SEPARATOR;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + BYTES_CHARS + ASCII_CHARS;

    private static final Range<Integer> ADDRESS_RANGE = new Range<Integer>(0, CHARS_PER_ADDRESS);
    private static final Range<Integer> BYTES_RANGE = new Range<Integer>(ADDRESS_CHARS + BYTE_SEPARATOR, ADDRESS_CHARS + BYTES_CHARS);
    private static final Range<Integer> ASCII_RANGE = new Range<Integer>(ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR, CHARS_PER_ROW);

    private final byte[] myData;
    private final char[] myCharData;
    private final int myRows;

    public MemoryModel(long address, byte[] data) {
      myData = data;
      myRows = (data.length + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
      myCharData = new char[myRows * CHARS_PER_ROW];

      Arrays.fill(myCharData, ' ');
      Formatter.initAddrData(address, myCharData, myRows);
      Formatter.initMemoryData(data, myCharData);
      Formatter.initAsciiData(data, myCharData);
    }

    public int getLineCount() {
      return myRows;
    }

    public int getLineLength() {
      return CHARS_PER_ROW;
    }

    public Iterator<Segment> getLines(final int start, final int end) {
      if (start < 0 || end < start || end > getLineCount()) {
        throw new IndexOutOfBoundsException("[" + start + ", " + end + ") outside of [0, " + getLineCount() + ")");
      }
      return new Iterator<Segment>() {
        private int pos = start;
        private final Segment segment = new Segment(myCharData, 0, CHARS_PER_ROW);

        @Override
        public boolean hasNext() {
          return pos < end;
        }

        @Override
        public Segment next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          segment.offset = pos++ * CHARS_PER_ROW;
          return segment;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    public Range<Integer> getSelectableRegion(int column) {
      if (ADDRESS_RANGE.isWithin(column)) {
        return ADDRESS_RANGE;
      }
      else if (BYTES_RANGE.isWithin(column)) {
        return BYTES_RANGE;
      }
      else if (ASCII_RANGE.isWithin(column)) {
        return ASCII_RANGE;
      }
      else {
        return null;
      }
    }

    public Transferable getTransferable(final Range<Integer> selectionRange, final Point start, final Point end) {
      String result;
      if (selectionRange == ASCII_RANGE) {
        // Copy the actual myData, rather than the display.
        int startPos = start.y * BYTES_PER_ROW + start.x - ASCII_RANGE.getFrom();
        int endPos = Math.min(myData.length, end.y * BYTES_PER_ROW + end.x - ASCII_RANGE.getFrom());
        result = new String(myData, startPos, endPos - startPos, Charset.forName("US-ASCII"));
      }
      else {
        StringBuilder buffer = new StringBuilder();
        if (start.y == end.y) {
          buffer.append(myCharData, start.y * CHARS_PER_ROW + start.x, end.x - start.x);
        }
        else {
          buffer.append(myCharData, start.y * CHARS_PER_ROW + start.x, selectionRange.getTo() - start.x).append('\n');
          int rangeWidth = selectionRange.getTo() - selectionRange.getFrom();
          for (int y = start.y + 1; y < end.y; y++) {
            buffer.append(myCharData, y * CHARS_PER_ROW + selectionRange.getFrom(), rangeWidth).append('\n');
          }
          buffer.append(myCharData, end.y * CHARS_PER_ROW + selectionRange.getFrom(), end.x - selectionRange.getFrom()).append('\n');
        }
        result = buffer.toString();
      }
      return new StringSelection(result);
    }

    private static class Formatter {
      private static void initAddrData(long address, char[] buffer, int rows) {
        int[] digits = new int[CHARS_PER_ADDRESS];
        char[] chars = new char[CHARS_PER_ADDRESS];
        initDigitsAndChars(digits, chars, address);
        for (int i = 0, j = 0; i < rows; i++, j += CHARS_PER_ROW) {
          System.arraycopy(chars, 0, buffer, j, CHARS_PER_ADDRESS);
          buffer[j + CHARS_PER_ADDRESS] = ':';
          increment(digits, chars);
        }
      }

      private static void initMemoryData(byte[] data, char[] result) {
        for (int i = 0, j = ADDRESS_CHARS; i < data.length; i++, j += CHARS_PER_BYTE + BYTE_SEPARATOR) {
          // result[j + 0] = ' ';
          result[j + 1] = HEX_DIGITS[(data[i] >> 4) & 0xF];
          result[j + 2] = HEX_DIGITS[data[i] & 0xF];
          if ((i % BYTES_PER_ROW) == BYTES_PER_ROW - 1) {
            j += ASCII_CHARS + ADDRESS_CHARS;
          }
        }
      }

      private static void initAsciiData(byte[] data, char[] result) {
        for (int i = 0, j = ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR; i < data.length; i++, j++) {
          result[j] = (data[i] >= 32 && data[i] < 127) ? (char)data[i] : '.';
          if ((i % BYTES_PER_ROW) == BYTES_PER_ROW - 1) {
            j += ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR;
          }
        }
      }

      private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

      private static void initDigitsAndChars(int[] digits, char[] chars, long address) {
        Arrays.fill(chars, '0');
        for (int i = digits.length - 1; i >= 0 && address != 0; i--) {
          int digit = (int)address & 0xF;
          digits[i] = digit;
          chars[i] = HEX_DIGITS[digit];
          address >>>= 4;
        }
      }

      private static void increment(int[] digits, char[] chars) {
        int pos = digits.length - 2; // We increment by values of 16.
        while (pos >= 0) {
          int digit = digits[pos] + 1;
          if (digit < 16) {
            digits[pos] = digit;
            chars[pos] = HEX_DIGITS[digit];
            return;
          }
          digits[pos] = 0;
          chars[pos] = '0';
          pos--;
        }
      }
    }
  }
}
