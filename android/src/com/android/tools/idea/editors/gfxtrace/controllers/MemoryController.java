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
import com.intellij.openapi.ui.ComboBox;
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
import java.awt.event.*;
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
  @NotNull private DataType myDataType = DataType.Bytes;
  private long myAddress;
  private byte[] myData;

  private MemoryController(@NotNull GfxTraceEditor editor) {
    super(editor);
    myLoading.add(myScrollPane, BorderLayout.CENTER);
    myPanel.add(new ComboBox(DataType.values()) {{
      addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          setDataType((DataType)e.getItem());
        }
      });
    }}, BorderLayout.NORTH);
    myPanel.add(myLoading, BorderLayout.CENTER);
  }

  private void setDataType(DataType dataType) {
    if (myDataType != dataType) {
      myDataType = dataType;

      Component component = myScrollPane.getViewport().getView();
      if (component instanceof MemoryPanel) {
        ((MemoryPanel)component).setModel(dataType.getMemoryModel(myAddress, myData));
      }
    }
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
    myAddress = address;
    myData = info.getData();

    final MemoryPanel contents = new MemoryPanel(myDataType.getMemoryModel(address, info.getData()));
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        myLoading.stopLoading();
        myScrollPane.setViewportView(contents);
      }
    });
  }

  private enum DataType {
    Bytes() {
      @Override
      public MemoryModel getMemoryModel(long address, byte[] data) {
        return new BytesMemoryModel(address, data);
      }
    }, Shorts() {
      @Override
      public MemoryModel getMemoryModel(long address, byte[] data) {
        return new ShortsMemoryModel(address, data);
      }
    }, Ints() {
      @Override
      public MemoryModel getMemoryModel(long address, byte[] data) {
        return new IntsMemoryModel(address, data);
      }
    }, Floats() {
      @Override
      public MemoryModel getMemoryModel(long address, byte[] data) {
        return new FloatsMemoryModel(address, data);
      }
    }, Doubles() {
      @Override
      public MemoryModel getMemoryModel(long address, byte[] data) {
        return new DoublesMemoryModel(address, data);
      }
    };

    public abstract MemoryModel getMemoryModel(long address, byte[] data);
  }

  private static class MemoryPanel extends JComponent implements Scrollable, DataProvider, CopyProvider {
    private MemoryModel myModel;
    private final EditorColorsScheme myTheme;
    private Range<Integer> mySelectionRange = null;
    private final Point mySelectionStart = new Point();
    private final Point mySelectionEnd = new Point();

    public MemoryPanel(MemoryModel model) {
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
          if (y < 0 || y >= myModel.getLineCount()) {
            mySelectionRange = null;
            return;
          }
          mySelectionInitiation.setLocation(getX(e), e.getY() / getLineHeight());
          mySelectionStart.setLocation(mySelectionInitiation);
          mySelectionEnd.setLocation(mySelectionStart);
          mySelectionRange = myModel.getSelectableRegion(mySelectionStart.x);
        }

        private void updateSelection(MouseEvent e) {
          int x = Math.max(mySelectionRange.getFrom(), Math.min(mySelectionRange.getTo(), getX(e)));
          int y = Math.max(0, e.getY() / getLineHeight());
          if (y >= myModel.getLineCount()) {
            y = myModel.getLineCount() - 1;
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

    public void setModel(MemoryModel model) {
      myModel = model;
      mySelectionRange = null;
      revalidate();
      repaint();
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

  private static abstract class MemoryModel {
    protected static final int BYTES_PER_ROW = 16; // If this is changed, Formatter.increment needs to be fixed.

    protected final byte[] myData;
    protected final int myRows;

    public MemoryModel(byte[] data, int align) {
      myData = aligned(data, align);
      myRows = (myData.length + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
    }

    private static byte[] aligned(byte[] data, int align) {
      int remainder = data.length % align;
      return (remainder == 0) ? data : Arrays.copyOf(data, data.length + align - remainder);
    }

    public int getLineCount() {
      return myRows;
    }

    public abstract int getLineLength();

    public Iterator<Segment> getLines(final int start, final int end) {
      if (start < 0 || end < start || end > getLineCount()) {
        throw new IndexOutOfBoundsException("[" + start + ", " + end + ") outside of [0, " + getLineCount() + ")");
      }
      return new Iterator<Segment>() {
        private int pos = start;
        private final Segment segment = new Segment(null, 0, 0);

        @Override
        public boolean hasNext() {
          return pos < end;
        }

        @Override
        public Segment next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          getLine(segment, pos++);
          return segment;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    protected abstract void getLine(Segment segment, int line);

    public abstract Range<Integer> getSelectableRegion(int column);

    public abstract Transferable getTransferable(Range<Integer> selectionRange, Point start, Point end);
  }

  private static abstract class CharBufferMemoryModel extends MemoryModel {
    protected static final int CHARS_PER_ADDRESS = 16; // 8 byte addresses
    protected static final int ADDRESS_SEPARATOR = 1;
    protected static final int ADDRESS_CHARS = CHARS_PER_ADDRESS + ADDRESS_SEPARATOR;
    protected static final Range<Integer> ADDRESS_RANGE = new Range<Integer>(0, CHARS_PER_ADDRESS);
    protected static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    protected final int myCharsPerRow;
    protected final char[] myCharData;
    protected final Range<Integer> myMemoryRange;

    public CharBufferMemoryModel(long address, byte[] data, int align, int charsPerRow, Range<Integer> memoryRange) {
      super(data, align);
      myCharsPerRow = charsPerRow;
      myCharData = new char[myRows * charsPerRow];
      myMemoryRange = memoryRange;

      Arrays.fill(myCharData, ' ');
      initAddrData(address);
      initMemoryData();
    }

    protected abstract void initMemoryData();

    @Override
    public int getLineLength() {
      return myCharsPerRow;
    }


    @Override
    protected void getLine(Segment segment, int line) {
      segment.array = myCharData;
      segment.offset = line * myCharsPerRow;
      segment.count = myCharsPerRow;
    }

    @Override
    public Range<Integer> getSelectableRegion(int column) {
      if (ADDRESS_RANGE.isWithin(column)) {
        return ADDRESS_RANGE;
      }
      else if (myMemoryRange.isWithin(column)) {
        return myMemoryRange;
      }
      return null;
    }

    @Override
    public Transferable getTransferable(Range<Integer> selectionRange, Point start, Point end) {
      StringBuilder buffer = new StringBuilder();
      if (start.y == end.y) {
        buffer.append(myCharData, start.y * myCharsPerRow + start.x, end.x - start.x);
      }
      else {
        buffer.append(myCharData, start.y * myCharsPerRow + start.x, selectionRange.getTo() - start.x).append('\n');
        int rangeWidth = selectionRange.getTo() - selectionRange.getFrom();
        for (int y = start.y + 1; y < end.y; y++) {
          buffer.append(myCharData, y * myCharsPerRow + selectionRange.getFrom(), rangeWidth).append('\n');
        }
        buffer.append(myCharData, end.y * myCharsPerRow + selectionRange.getFrom(), end.x - selectionRange.getFrom()).append('\n');
      }
      return new StringSelection(buffer.toString());
    }

    private void initAddrData(long address) {
      int[] digits = new int[CHARS_PER_ADDRESS];
      char[] chars = new char[CHARS_PER_ADDRESS];
      initDigitsAndChars(digits, chars, address);
      for (int i = 0, j = 0; i < myRows; i++, j += myCharsPerRow) {
        System.arraycopy(chars, 0, myCharData, j, CHARS_PER_ADDRESS);
        myCharData[j + CHARS_PER_ADDRESS] = ':';
        increment(digits, chars);
      }
    }

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

  private static class BytesMemoryModel extends CharBufferMemoryModel {
    private static final int CHARS_PER_BYTE = 2; // 2 hex chars per byte

    private static final int BYTE_SEPARATOR = 1;
    private static final int ASCII_SEPARATOR = 2;

    private static final int BYTES_CHARS = (CHARS_PER_BYTE + BYTE_SEPARATOR) * BYTES_PER_ROW;
    private static final int ASCII_CHARS = BYTES_PER_ROW + ASCII_SEPARATOR;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + BYTES_CHARS + ASCII_CHARS;

    private static final Range<Integer> BYTES_RANGE = new Range<Integer>(ADDRESS_CHARS + BYTE_SEPARATOR, ADDRESS_CHARS + BYTES_CHARS);
    private static final Range<Integer> ASCII_RANGE = new Range<Integer>(ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR, CHARS_PER_ROW);

    public BytesMemoryModel(long address, byte[] data) {
      super(address, data, 1, CHARS_PER_ROW, BYTES_RANGE);
    }

    @Override
    public Range<Integer> getSelectableRegion(int column) {
      if (ASCII_RANGE.isWithin(column)) {
        return ASCII_RANGE;
      }
      else {
        return super.getSelectableRegion(column);
      }
    }

    @Override
    public Transferable getTransferable(Range<Integer> selectionRange, Point start, Point end) {
      if (selectionRange == ASCII_RANGE) {
        // Copy the actual myData, rather than the display.
        int startPos = start.y * BYTES_PER_ROW + start.x - ASCII_RANGE.getFrom();
        int endPos = Math.min(myData.length, end.y * BYTES_PER_ROW + end.x - ASCII_RANGE.getFrom());
        return new StringSelection(new String(myData, startPos, endPos - startPos, Charset.forName("US-ASCII")));
      }
      else {
        return super.getTransferable(selectionRange, start, end);
      }
    }

    @Override
    protected void initMemoryData() {
      for (int i = 0, j = ADDRESS_CHARS; i < myData.length; i++, j += CHARS_PER_BYTE + BYTE_SEPARATOR) {
        myCharData[j + 1] = HEX_DIGITS[(myData[i] >> 4) & 0xF];
        myCharData[j + 2] = HEX_DIGITS[myData[i] & 0xF];
        if ((i % BYTES_PER_ROW) == BYTES_PER_ROW - 1) {
          j += ASCII_CHARS + ADDRESS_CHARS;
        }
      }

      for (int i = 0, j = ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR; i < myData.length; i++, j++) {
        myCharData[j] = (myData[i] >= 32 && myData[i] < 127) ? (char)myData[i] : '.';
        if ((i % BYTES_PER_ROW) == BYTES_PER_ROW - 1) {
          j += ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR;
        }
      }
    }
  }

  private static class ShortsMemoryModel extends CharBufferMemoryModel {
    private static final int SHORTS_PER_ROW = BYTES_PER_ROW / 2;
    private static final int CHARS_PER_SHORT = 4; // 4 hex chars per short

    private static final int SHORT_SEPARATOR = 1;

    private static final int SHORTS_CHARS = (CHARS_PER_SHORT + SHORT_SEPARATOR) * SHORTS_PER_ROW;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + SHORTS_CHARS;

    private static final Range<Integer> SHORTS_RANGE = new Range<Integer>(ADDRESS_CHARS + SHORT_SEPARATOR, ADDRESS_CHARS + SHORTS_CHARS);

    public ShortsMemoryModel(long address, byte[] data) {
      super(address, data, 2, CHARS_PER_ROW, SHORTS_RANGE);
    }

    @Override
    protected void initMemoryData() {
      for (int i = 0, j = 0; i < myData.length; i += 2, j += CHARS_PER_SHORT + SHORT_SEPARATOR) {
        if ((i % BYTES_PER_ROW) == 0) {
          j += ADDRESS_CHARS;
        }
        // TODO: figure out BigEndian vs LittleEndian.
        myCharData[j + 1] = HEX_DIGITS[(myData[i + 1] >> 4) & 0xF];
        myCharData[j + 2] = HEX_DIGITS[myData[i + 1] & 0xF];
        myCharData[j + 3] = HEX_DIGITS[(myData[i + 0] >> 4) & 0xF];
        myCharData[j + 4] = HEX_DIGITS[myData[i + 0] & 0xF];
      }
    }
  }

  private static class IntsMemoryModel extends CharBufferMemoryModel {
    private static final int INTS_PER_ROW = BYTES_PER_ROW / 4;
    private static final int CHARS_PER_INT = 8; // 8 hex chars per int

    private static final int INT_SEPARATOR = 1;

    private static final int INTS_CHARS = (CHARS_PER_INT + INT_SEPARATOR) * INTS_PER_ROW;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + INTS_CHARS;

    private static final Range<Integer> INTS_RANGE = new Range<Integer>(ADDRESS_CHARS + INT_SEPARATOR, ADDRESS_CHARS + INTS_CHARS);

    public IntsMemoryModel(long address, byte[] data) {
      super(address, data, 4, CHARS_PER_ROW, INTS_RANGE);
    }

    @Override
    protected void initMemoryData() {
      for (int i = 0, j = 0; i < myData.length; i += 4, j += CHARS_PER_INT + INT_SEPARATOR) {
        if ((i % BYTES_PER_ROW) == 0) {
          j += ADDRESS_CHARS;
        }
        // TODO: figure out BigEndian vs LittleEndian.
        myCharData[j + 1] = HEX_DIGITS[(myData[i + 3] >> 4) & 0xF];
        myCharData[j + 2] = HEX_DIGITS[myData[i + 3] & 0xF];
        myCharData[j + 3] = HEX_DIGITS[(myData[i + 2] >> 4) & 0xF];
        myCharData[j + 4] = HEX_DIGITS[myData[i + 2] & 0xF];
        myCharData[j + 5] = HEX_DIGITS[(myData[i + 1] >> 4) & 0xF];
        myCharData[j + 6] = HEX_DIGITS[myData[i + 1] & 0xF];
        myCharData[j + 7] = HEX_DIGITS[(myData[i + 0] >> 4) & 0xF];
        myCharData[j + 8] = HEX_DIGITS[myData[i + 0] & 0xF];
      }
    }
  }

  private static class FloatsMemoryModel extends CharBufferMemoryModel {
    private static final int FLOATS_PER_ROW = BYTES_PER_ROW / 4;
    private static final int CHARS_PER_FLOAT = 15;

    private static final int FLOAT_SEPARATOR = 1;

    private static final int FLOATS_CHARS = (CHARS_PER_FLOAT + FLOAT_SEPARATOR) * FLOATS_PER_ROW;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + FLOATS_CHARS;

    private static final Range<Integer> FLOATS_RANGE = new Range<Integer>(ADDRESS_CHARS + FLOAT_SEPARATOR, ADDRESS_CHARS + FLOATS_CHARS);

    public FloatsMemoryModel(long address, byte[] data) {
      super(address, data, 4, CHARS_PER_ROW, FLOATS_RANGE);
    }

    @Override
    protected void initMemoryData() {
      StringBuilder sb = new StringBuilder(50);
      for (int i = 0, j = 0; i < myData.length; i += 4, j += CHARS_PER_FLOAT + FLOAT_SEPARATOR) {
        if ((i % BYTES_PER_ROW) == 0) {
          j += ADDRESS_CHARS;
        }
        sb.setLength(0);
        sb.append(Float.intBitsToFloat(getInt(myData, i)));
        int count = Math.min(CHARS_PER_FLOAT, sb.length());
        sb.getChars(0, count, myCharData, j + CHARS_PER_FLOAT - count + 1);
      }
    }
  }

  private static class DoublesMemoryModel extends CharBufferMemoryModel {
    private static final int DOUBLES_PER_ROW = BYTES_PER_ROW / 8;
    private static final int CHARS_PER_DOUBLE = 24;

    private static final int DOUBLE_SEPARATOR = 1;

    private static final int DOUBLES_CHARS = (CHARS_PER_DOUBLE + DOUBLE_SEPARATOR) * DOUBLES_PER_ROW;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + DOUBLES_CHARS;

    private static final Range<Integer> DOUBLES_RANGE = new Range<Integer>(ADDRESS_CHARS + DOUBLE_SEPARATOR, ADDRESS_CHARS + DOUBLES_CHARS);

    public DoublesMemoryModel(long address, byte[] data) {
      super(address, data, 8, CHARS_PER_ROW, DOUBLES_RANGE);
    }

    @Override
    protected void initMemoryData() {
      StringBuilder sb = new StringBuilder(50);
      for (int i = 0, j = 0; i < myData.length; i += 8, j += CHARS_PER_DOUBLE + DOUBLE_SEPARATOR) {
        if ((i % BYTES_PER_ROW) == 0) {
          j += ADDRESS_CHARS;
        }
        sb.setLength(0);
        sb.append(Double.longBitsToDouble(getLong(myData, i)));
        int count = Math.min(CHARS_PER_DOUBLE, sb.length());
        sb.getChars(0, count, myCharData, j + CHARS_PER_DOUBLE - count + 1);
      }
    }
  }

  private static int getInt(byte[] data, int off) {
    // TODO: figure out BigEndian vs LittleEndian.
    return (data[off + 0] & 0xFF) | ((data[off + 1] & 0xFF) << 8) | ((data[off + 2] & 0xFF) << 16) | (data[off + 3] << 24);
  }

  private static long getLong(byte[] data, int off) {
    // TODO: figure out BigEndian vs LittleEndian.
    return (getInt(data, off) & 0xFFFFFFFFL) | ((long)getInt(data, off + 4) << 32);
  }
}
