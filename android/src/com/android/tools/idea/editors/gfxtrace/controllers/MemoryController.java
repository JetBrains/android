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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.service.MemoryInfo;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryProtos;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryRange;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.service.path.PathProtos.MemoryKind;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.swing.util.BigSpinnerNumberModel;
import com.android.tools.swing.util.HexFormatter;
import com.android.tools.swing.ui.InfiniteScrollPane;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.UnsignedLong;
import com.google.common.primitives.UnsignedLongs;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.reference.SoftReference;
import com.intellij.ui.JBColor;
import com.intellij.util.Range;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultFormatterFactory;
import javax.swing.text.Segment;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;

public class MemoryController extends Controller {
  private static final char UNKNOWN_CHAR = '?';

  @NotNull private static final Logger LOG = Logger.getInstance(MemoryController.class);

  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new MemoryController(editor).myPanel;
  }

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final LoadablePanel myLoading = new LoadablePanel(new BorderLayout());
  @NotNull private final EmptyPanel myEmptyPanel = new EmptyPanel();
  @NotNull private final InfiniteScrollPane myScrollPane = new InfiniteScrollPane(myEmptyPanel);
  @NotNull private DataType myDataType = DataType.Bytes;
  @NotNull private ComboBox myCombo;
  private AtomPath myAtomPath;
  private MemoryDataModel myMemoryData;

  private MemoryController(@NotNull GfxTraceEditor editor) {
    super(editor);
    myLoading.getContentLayer().add(myScrollPane, BorderLayout.CENTER);
    myCombo = new ComboBox(DataType.values()) {{
      setSelectedIndex(1);
      addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          setDataType((DataType)e.getItem());
        }
      });
    }};
    myPanel.add(myCombo, BorderLayout.NORTH);
    myPanel.add(myLoading, BorderLayout.CENTER);

    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
  }

  private void setDataType(DataType dataType) {
    if (myDataType != dataType) {
      myDataType = dataType;

      Component component = myScrollPane.getViewport().getView();
      if (component instanceof MemoryPanel) {
        ((MemoryPanel)component).setModel(dataType.getMemoryModel(myMemoryData));
      }
    }
  }

  private DataType dataTypeFromMemoryType(MemoryType type) {
    final long size = type.getByteSize();
    // Note this happens before we have sent a request to a server, so we
    // can only handle fixed size types at the moment.
    if (size == 0) {
      return myDataType;
    }
    final MemoryKind kind = type.getKind();
    if (kind.equals(MemoryKind.Integer) || kind.equals(MemoryKind.Char) || kind.equals(MemoryKind.Address)) {
      if (size == 1) {
        return DataType.Bytes;
      }
      else if (size == 2) {
        return DataType.Shorts;
      }
      else if (size == 4) {
        return DataType.Ints;
      }
      else if (size == 8) {
        return DataType.Longs;
      }
    }
    else if (kind.equals(MemoryKind.Float)) {
      if (size == 4) {
        return DataType.Floats;
      } else if (size == 8) {
        return DataType.Doubles;
      }
    }
    return myDataType;
  }

  @Override
  public void notifyPath(PathEvent event) {
    final TypedMemoryPath typedMemoryPath = event.findTypedMemoryPath();
    final MemoryRangePath memoryPath = typedMemoryPath != null ? typedMemoryPath.getRange() : event.findMemoryPath();
    if (memoryPath != null) {

      UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(AndroidStudioEvent.EventCategory.GPU_PROFILER)
                                       .setKind(EventKind.GFX_TRACE_MEMORY_VIEWED));

      final DataType dataType = typedMemoryPath != null ? dataTypeFromMemoryType(typedMemoryPath.getType()) : myDataType;

      myLoading.startLoading();
      PagedMemoryDataModel.MemoryFetcher fetcher = (address, count) -> myEditor.getClient().get(
        new MemoryRangePath().setAfter(memoryPath.getAfter()).setPool(memoryPath.getPool()).setAddress(address).setSize(count));

      long offset = Long.remainderUnsigned(memoryPath.getAddress(), FixedMemoryModel.BYTES_PER_ROW);
      myMemoryData = new PagedMemoryDataModel(fetcher, offset, UnsignedLong.MAX_VALUE.longValue());
      myDataType = dataType;
      myCombo.setSelectedItem(dataType);
      update();
      goToAddress(memoryPath.getAddress());

      myAtomPath = memoryPath.getAfter();
    }
    else {
      // As it is hard to select a memory path in the atom view without also selecting an atom we ignore any
      // atom event for the current atom, otherwise you can get flicked back to the empty panel unexpectedly.
      AtomRangePath atomPath = event.findAtomPath();
      if (myAtomPath == null || atomPath == null || !myAtomPath.equals(atomPath.getPathToLast())) {
        myAtomPath = (atomPath == null) ? null : atomPath.getPathToLast();
        myEmptyPanel.resetText();
        myScrollPane.setViewportView(myEmptyPanel);
      }
    }
  }

  private void goToAddress(long address) {
    MemoryPanel memPanel = (MemoryPanel)myScrollPane.getViewport().getView();
    myScrollPane.setViewPosition(0, UnsignedLong.fromLongBits(address).bigIntegerValue().divide(BigInteger.valueOf(FixedMemoryModel.BYTES_PER_ROW)).multiply(BigInteger.valueOf(memPanel.getLineHeight())));
  }

  private long getCurrentAddress() {
    MemoryPanel memPanel = (MemoryPanel)myScrollPane.getViewport().getView();
    return memPanel.myYOffset.add(BigInteger.valueOf(myScrollPane.getViewport().getViewPosition().y)).divide(BigInteger.valueOf(memPanel.getLineHeight())).multiply(BigInteger.valueOf(FixedMemoryModel.BYTES_PER_ROW)).add(UnsignedLong.fromLongBits(myMemoryData.getAddress()).bigIntegerValue()).longValue();
  }

  private void update() {
    myLoading.stopLoading();
    MemoryPanel memoryPanel = new MemoryPanel(myDataType.getMemoryModel(myMemoryData));

    myScrollPane.setViewportView(memoryPanel);

    setNavigableComponentAction(memoryPanel, new AbstractAction("Jump to address") {
      @Override
      public void actionPerformed(ActionEvent e) {
        // we can't use Long as then comparison with other Longs will fail, and we NEED to compare to numbers, not just Comparable
        JSpinner spinner = new JSpinner(
          new BigSpinnerNumberModel(UnsignedLong.fromLongBits(getCurrentAddress()).bigIntegerValue(), BigInteger.ZERO,
                                    UnsignedLong.MAX_VALUE.bigIntegerValue(), 1));
        ((JSpinner.DefaultEditor)spinner.getEditor()).getTextField().setFormatterFactory(new DefaultFormatterFactory(){
          @Override
          public JFormattedTextField.AbstractFormatter getDefaultFormatter() {
            return new HexFormatter();
          }
        });

        int result = JOptionPane.showOptionDialog(myEditor.getComponent(), spinner, (String)getValue(Action.NAME),
                                                  JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE,
                                                  null, null, null);
        if (result == JOptionPane.OK_OPTION) {
          myEditor.activatePath(
            myAtomPath.memoryAfter(MemoryProtos.PoolNames.Application_VALUE, new MemoryRange().setBase(((Number)spinner.getValue()).longValue())), this);
        }
      }

      @Override
      public boolean isEnabled() {
        return myEditor.getAtomStream().isLoaded();
      }
    });
  }

  private enum DataType {
    Bytes() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new BytesMemoryModel(memory);
      }
    }, Shorts() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new ShortsMemoryModel(memory);
      }
    }, Ints() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new IntsMemoryModel(memory);
      }
    }, Longs() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new LongsMemoryModel(memory);
      }
    }, Floats() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new FloatsMemoryModel(memory);
      }
    }, Doubles() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new DoublesMemoryModel(memory);
      }
    };

    public abstract MemoryModel getMemoryModel(MemoryDataModel memory);
  }

  private static class EmptyPanel extends JComponent {
    private final StatusText myEmptyText = new StatusText() {
      @Override
      protected boolean isStatusVisible() {
        return true;
      }
    };

    public void resetText() {
      myEmptyText.setText(GfxTraceEditor.SELECT_MEMORY);
    }

    public void setText(String message) {
      myEmptyText.setText(message);
    }

    public EmptyPanel() {
      myEmptyText.setText(GfxTraceEditor.SELECT_MEMORY);
      myEmptyText.attachTo(this);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      myEmptyText.paint(this, graphics);
    }
  }

  private static class MemoryPanel extends JComponent implements Scrollable, DataProvider, CopyProvider, InfiniteScrollPane.InfinitePanel {
    /**
     * @see com.intellij.execution.testframework.LvcsHelper#RED
     */
    private static final Color RED = new JBColor(new Color(250, 220, 220), new Color(104, 67, 67));
    /**
     * @see com.intellij.execution.testframework.LvcsHelper#GREEN
     */
    private static final Color GREEN = new JBColor(new Color(220, 250, 220), new Color(44, 66, 60));

    private MemoryModel myModel;
    private EditorColorsScheme myTheme;

    @NotNull private BigInteger myYOffset = BigInteger.ZERO;
    @Nullable private Selection mySelection;

    private final Runnable myRepainter = new Runnable() {
      @Override
      public void run() {
        revalidate();
        repaint();
      }
    };

    public MemoryPanel(MemoryModel model) {
      myModel = model;
      updateUI();
      setCursor(new Cursor(Cursor.TEXT_CURSOR));
      setFocusable(true);

      MouseAdapter mouseHandler = new MouseAdapter() {
        private int mySelectionInitiationCol;
        private long mySelectionInitiationRow;
        private boolean mySelecting;

        @Override
        public void mousePressed(MouseEvent e) {
          requestFocus();
          if (isSelectionButton(e)) {
            if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0 && mySelection != null) {
              mySelecting = true;
              updateSelection(e);
            }
            else {
              startSelecting(e);
            }
            repaint();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          if (!isSelectionButton(e)) {
            mySelecting = false;
            if (mySelection != null && mySelection.isEmpty()) {
              mySelection = null;
              repaint();
            }
          }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
          if (isSelectionButton(e)) {
            if (mySelection == null) {
              startSelecting(e);
            }
            else {
              updateSelection(e);
            }
          }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          if (mySelecting) {
            if (mySelection == null) {
              startSelecting(e);
            }
            else {
              updateSelection(e);
            }
          }

          // Bubble the event.
          JScrollPane ancestor = (JScrollPane)SwingUtilities.getAncestorOfClass(JScrollPane.class, MemoryPanel.this);
          if (ancestor != null) {
            MouseWheelEvent converted = (MouseWheelEvent)SwingUtilities.convertMouseEvent(MemoryPanel.this, e, ancestor);
            for (MouseWheelListener listener : ancestor.getMouseWheelListeners()) {
              listener.mouseWheelMoved(converted);
            }
          }
        }

        private void startSelecting(MouseEvent e) {
          mySelecting = true;
          long row = getRow(e);
          if (row < 0 || row >= myModel.getLineCount()) {
            mySelection = null;
            return;
          }
          mySelectionInitiationCol = getCol(e);
          mySelectionInitiationRow = row;

          Range<Integer> selectableRegion = myModel.getSelectableRegion(mySelectionInitiationCol);
          if (selectableRegion != null) {
            mySelection = new Selection(selectableRegion, mySelectionInitiationCol, mySelectionInitiationRow, mySelectionInitiationCol, mySelectionInitiationRow);
          }
        }

        private void updateSelection(MouseEvent e) {
          int col = Math.max(mySelection.myRange.getFrom(), Math.min(mySelection.myRange.getTo(), getCol(e)));
          long row = Math.max(0, getRow(e));
          if (row >= myModel.getLineCount()) {
            row = myModel.getLineCount() - 1;
            col = mySelection.myRange.getTo();
          }

          if (row < mySelectionInitiationRow || (row == mySelectionInitiationRow && col < mySelectionInitiationCol)) {
            mySelection = new Selection(mySelection.myRange, col, row, mySelectionInitiationCol, mySelectionInitiationRow);
          }
          else {
            mySelection = new Selection(mySelection.myRange, mySelectionInitiationCol, mySelectionInitiationRow, col, row);
          }

          repaint();
        }

        private int getCol(MouseEvent e) {
          int charWidth = getCharWidth();
          return (e.getX() + charWidth / 2) / charWidth;
        }

        private long getRow(MouseEvent e) {
          return myYOffset.add(BigInteger.valueOf(e.getY())).divide(BigInteger.valueOf(getLineHeight())).longValueExact();
        }

        private boolean isSelectionButton(MouseEvent e) {
          return (e.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0;
        }
      };
      addMouseListener(mouseHandler);
      addMouseMotionListener(mouseHandler);
      addMouseWheelListener(mouseHandler);
    }

    @Override
    public void updateUI() {
      super.updateUI();
      myTheme = EditorColorsManager.getInstance().getGlobalScheme();
      // we must set the font in the constructor as it will be used to measure the panel before we paint it
      setFont(myTheme.getFont(EditorFontType.PLAIN));
    }

    public void setModel(MemoryModel model) {
      myModel = model;
      mySelection = null;
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
      return mySelection != null && !mySelection.isEmpty();
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      if (isCopyEnabled(dataContext)) {
        Futures.addCallback(
          myModel.getTransferable(mySelection), new FutureCallback<Transferable>() {
            @Override
            public void onFailure(Throwable t) {
              LOG.error("Failed to load memory", t);
            }

            @Override
            public void onSuccess(Transferable result) {
              CopyPasteManager.getInstance().setContents(result);
            }
          });
      }
    }

    @Override
    public void setYOffset(BigInteger start) {
      myYOffset = start;
    }

    @Override
    public BigInteger getYOffset() {
      return myYOffset;
    }

    @Override
    public int getFullWidth() {
      return myModel.getLineLength() * getCharWidth();
    }

    @Override
    public BigInteger getFullHeight() {
      return BigInteger.valueOf(myModel.getLineCount()).multiply(BigInteger.valueOf(getLineHeight()));
    }

    private void highlight(@NotNull Graphics g, @NotNull Selection selection) {
      int lineHeight = getLineHeight();
      int charWidth = getCharWidth();

      if (selection.myStartRow == selection.myEndRow) {
        g.fillRect(selection.myStartCol * charWidth, getY(selection.myStartRow),
                                      (selection.myEndCol - selection.myStartCol) * charWidth, lineHeight);
      }
      else {
        g.fillRect(selection.myStartCol * charWidth, getY(selection.myStartRow),
                                      (selection.myRange.getTo() - selection.myStartCol) * charWidth, lineHeight);
        g.fillRect(selection.myRange.getFrom() * charWidth, getY(selection.myEndRow),
                                        (selection.myEndCol - selection.myRange.getFrom()) * charWidth, lineHeight);
        g.fillRect(selection.myRange.getFrom() * charWidth, getY(selection.myStartRow + 1),
                                        (selection.myRange.getTo() - selection.myRange.getFrom()) * charWidth,
                                        (int)(selection.myEndRow - selection.myStartRow - 1) * lineHeight);
      }
    }

    private int getY(long line) {
      return BigInteger.valueOf(line).multiply(BigInteger.valueOf(getLineHeight())).subtract(myYOffset).intValueExact();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      g.setFont(myTheme.getFont(EditorFontType.PLAIN));
      initMeasurements();
      setBackground(myTheme.getDefaultBackground());
      setForeground(myTheme.getDefaultForeground());
      Rectangle clip = g.getClipBounds();

      g.setColor(getBackground());
      g.fillRect(clip.x, clip.y, clip.width, clip.height);
      g.setColor(getForeground());

      int lineHeight = getLineHeight();
      int charWidth = getCharWidth();

      BigInteger startY = myYOffset.add(BigInteger.valueOf(clip.y));

      long startRow = startY.divide(BigInteger.valueOf(lineHeight))
        .max(BigInteger.ZERO).min(BigInteger.valueOf(myModel.getLineCount() - 1)).longValueExact();
      long endRow = startY.add(BigInteger.valueOf(clip.height + lineHeight - 1)).divide(BigInteger.valueOf(lineHeight))
        .max(BigInteger.ZERO).min(BigInteger.valueOf(myModel.getLineCount())).longValueExact();

      g.setColor(GREEN);
      Selection[] reads = myModel.getReads(startRow, endRow, myRepainter);
      for (Selection read : reads) {
        highlight(g, read);
      }

      g.setColor(RED);
      Selection[] writes = myModel.getWrites(startRow, endRow, myRepainter);
      for (Selection write : writes) {
        highlight(g, write);
      }

      boolean selectionVisible = mySelection != null && mySelection.isSelectionVisible(startRow, endRow);
      if (selectionVisible) {
        g.setColor(myTheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR));
        highlight(g, mySelection);
      }

      g.setColor(getForeground());
      // TODO maybe remove this as it may not be needed any more
      // Drawing fonts in swing appears to use floating point math. Thus, y-cordinates greater than 16,777,217 cause issues.
      g.translate(0, BigInteger.valueOf(startRow).multiply(BigInteger.valueOf(lineHeight)).subtract(myYOffset).intValueExact());

      int y = getAscent();
      Iterator<Segment> it = myModel.getLines(startRow, endRow, myRepainter);
      if (!selectionVisible) {
        for (; it.hasNext(); y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, segment.count, 0, y);
        }
      }
      else {
        long row = startRow;
        int rangeWidth = mySelection.myRange.getTo() - mySelection.myRange.getFrom();
        int fromWidth = mySelection.myRange.getFrom() * charWidth, toWidth = mySelection.myRange.getTo() * charWidth;

        // Lines before selection.
        for (; it.hasNext() && row < mySelection.myStartRow; row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, segment.count, 0, y);
        }
        // First selected line, possibly partially selected.
        for (; it.hasNext() && row == mySelection.myStartRow; row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, mySelection.myStartCol, 0, y);
          g.setColor(myTheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
          if (mySelection.myStartRow == mySelection.myEndRow) {
            g.drawChars(segment.array, segment.offset + mySelection.myStartCol, mySelection.myEndCol - mySelection.myStartCol,
                        mySelection.myStartCol * charWidth, y);
            g.setColor(getForeground());
            g.drawChars(
              segment.array, segment.offset + mySelection.myEndCol, segment.count - mySelection.myEndCol, mySelection.myEndCol * charWidth, y);
          }
          else {
            g.drawChars(segment.array, segment.offset + mySelection.myStartCol, mySelection.myRange.getTo() - mySelection.myStartCol,
                        mySelection.myStartCol * charWidth, y);
            g.setColor(getForeground());
            g.drawChars(segment.array, segment.offset + mySelection.myRange.getTo(), segment.count - mySelection.myRange.getTo(), toWidth, y);
          }
        }
        // Fully selected lines.
        for (; it.hasNext() && row < mySelection.myEndRow; row++, y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, mySelection.myRange.getFrom(), 0, y);
          g.setColor(myTheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
          g.drawChars(segment.array, segment.offset + mySelection.myRange.getFrom(), rangeWidth, fromWidth, y);
          g.setColor(getForeground());
          g.drawChars(segment.array, segment.offset + mySelection.myRange.getTo(), segment.count - mySelection.myRange.getTo(), toWidth, y);
        }
        // Last selected line, possibly partially selected.
        for (; it.hasNext() && row == mySelection.myEndRow; row++, y += lineHeight) { // Note: mySelectionStart.y != mySelectionEnd.y here.
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, mySelection.myRange.getFrom(), 0, y);
          g.setColor(myTheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR));
          g.drawChars(segment.array, segment.offset + mySelection.myRange.getFrom(), mySelection.myEndCol - mySelection.myRange.getFrom(), fromWidth,
                      y);
          g.setColor(getForeground());
          g.drawChars(segment.array, segment.offset + mySelection.myEndCol, segment.count - mySelection.myEndCol, mySelection.myEndCol * charWidth, y);
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

  private interface MemoryDataModel {
    long getAddress();

    /**
     * we use first and last address instead of size, as there is no unsigned long big enough to express the full size of the entire address range.
     */
    long getEndAddress();

    ListenableFuture<MemorySegment> get(long offset, int length);

    MemoryDataModel align(int byteAlign);
  }

  private static BitSet computeKnown(MemoryInfo data) {
    BitSet known = new BitSet(data.getData().length);
    for (MemoryRange rng : data.getObserved()) {
      known.set((int)rng.getBase(), (int)rng.getBase() + (int)rng.getSize());
    }
    return known;
  }

  private static class PagedMemoryDataModel implements MemoryDataModel {
    private static final int PAGE_SIZE = 0x10000;

    private final MemoryFetcher fetcher;
    private final long address;
    private final long lastAddress;

    // TODO maybe remove this as it may not be needed any more
    private final Map<Long, SoftReference<MemoryInfo>> pageCache = Maps.newHashMap();

    public PagedMemoryDataModel(MemoryFetcher fetcher, long address, long lastAddress) {
      this.fetcher = fetcher;
      this.address = address;
      this.lastAddress = lastAddress;
    }

    @Override
    public long getAddress() {
      return address;
    }

    @Override
    public long getEndAddress() {
      return lastAddress;
    }

    @Override
    public ListenableFuture<MemorySegment> get(long offset, int length) {
      offset = UnsignedLongs.min(lastAddress - address, offset);
      length = (int)UnsignedLongs.min(lastAddress - address - offset, length - 1) + 1;

      long firstPage = getPageForOffset(offset);
      long lastPage = getPageForOffset(offset + length - 1);
      if (firstPage == lastPage) {
        return getPage(firstPage, getOffsetInPage(offset), length);
      }
      List<ListenableFuture<MemorySegment>> futures = Lists.newArrayList();
      futures.add(getPage(firstPage, getOffsetInPage(offset), PAGE_SIZE - getOffsetInPage(offset)));
      for (long page = firstPage + 1, left = length - PAGE_SIZE + getOffsetInPage(offset); page <= lastPage; page++, left -= PAGE_SIZE) {
        futures.add(getPage(page, 0, (int)Math.min(left, PAGE_SIZE)));
      }

      final int totalLength = length;
      return Futures.transform(Futures.allAsList(futures), new Function<List<MemorySegment>, MemorySegment>() {
        @Override
        public MemoryController.MemorySegment apply(List<MemorySegment> segments) {
          return new MemorySegment(segments, totalLength);
        }
      });
    }

    private static long getPageForOffset(long offset) {
      return Long.divideUnsigned(offset, PAGE_SIZE);
    }

    private static long getOffsetForPage(long page) {
      return page * PAGE_SIZE;
    }

    private static int getOffsetInPage(long offset) {
      return (int)Long.remainderUnsigned(offset, PAGE_SIZE);
    }

    private ListenableFuture<MemorySegment> getPage(final long page, final int offset, final int length) {
      MemoryInfo mem = getFromCache(page);
      if (mem != null) {
        return Futures.immediateFuture(new MemorySegment(mem).subSegment(offset, length));
      }

      long base = address + getOffsetForPage(page);
      return Futures
        .transform(fetcher.get(base, (int)UnsignedLongs.min(lastAddress - base, PAGE_SIZE - 1) + 1), new Function<MemoryInfo, MemorySegment>() {
          @Override
          public MemorySegment apply(MemoryInfo mem) {
            addToCache(page, mem);
            return new MemorySegment(mem);
          }
        });
    }

    private MemoryInfo getFromCache(long page) {
      MemoryInfo result = null;
      synchronized (pageCache) {
        SoftReference<MemoryInfo> reference = pageCache.get(page);
        if (reference != null) {
          result = reference.get();
          if (result == null) {
            pageCache.remove(page);
          }
        }
      }
      return result;
    }

    private void addToCache(long page, MemoryInfo data) {
      synchronized (pageCache) {
        pageCache.put(page, new SoftReference<>(data));
      }
    }

    @Override
    public MemoryDataModel align(int byteAlign) {
      return this;
    }

    public interface MemoryFetcher {
      ListenableFuture<MemoryInfo> get(long address, long count);
    }
  }

  private static class MemorySegment {
    private final byte[] myData;
    private final BitSet myKnown;
    private final int myOffset;
    private final int myLength;

    private final MemoryRange[] myReads;
    private final MemoryRange[] myWrites;

    private MemorySegment(byte[] data, BitSet known, int offset, int length, MemoryRange[] reads, MemoryRange[] writes) {
      myData = data;
      myOffset = offset;
      myLength = length;
      myKnown = known;
      myReads = reads;
      myWrites = writes;
    }

    public MemorySegment(List<MemorySegment> segments, int length) {
      byte[] data = new byte[length];
      BitSet known = new BitSet(length);
      int done = 0;

      List<MemoryRange> newReads = new ArrayList<>();
      List<MemoryRange> newWrites = new ArrayList<>();

      for (Iterator<MemorySegment> it = segments.iterator(); it.hasNext() && done < length; ) {
        MemorySegment segment = it.next();
        int count = Math.min(length - done, segment.myLength);
        System.arraycopy(segment.myData, segment.myOffset, data, done, count);
        for (int i = 0; i < count; ++i) {
          known.set(done + i, segment.myKnown.get(segment.myOffset + i));
        }

        for (MemoryRange range : segment.myReads) {
          newReads.add(done == 0 && segment.myOffset == 0 ? range : new MemoryRange().setBase(done - segment.myOffset + range.getBase()).setSize(range.getSize()));
        }
        for (MemoryRange range : segment.myWrites) {
          newWrites.add(done == 0 && segment.myOffset == 0 ? range : new MemoryRange().setBase(done - segment.myOffset + range.getBase()).setSize(range.getSize()));
        }

        done += count;
      }
      myData = data;
      myKnown = known;
      myOffset = 0;
      myLength = done;

      myReads = newReads.toArray(new MemoryRange[newReads.size()]);
      myWrites = newWrites.toArray(new MemoryRange[newWrites.size()]);
    }

    public MemorySegment(MemoryInfo info) {
      myData = info.getData();
      myOffset = 0;
      myKnown = computeKnown(info);
      myLength = info.getData().length;
      myReads = info.getReads();
      myWrites = info.getWrites();
    }

    public MemorySegment subSegment(int start, int count) {
      return new MemorySegment(myData, myKnown, myOffset + start, Math.min(count, myLength - start), myReads, myWrites);
    }

    public String asString(int start, int count) {
      return new String(myData, myOffset + start, Math.min(count, myLength - start), Charset.forName("US-ASCII"));
    }

    private boolean getByteKnown(int offset, int size) {
      if (offset < 0 || size < 0 || myOffset + offset + size > myData.length) {
        return false;
      }
      if (myKnown == null) {
        return true;
      }
      for (int o = offset; o < offset + size; o++) {
        if (!myKnown.get(myOffset + o)) {
          return false;
        }
      }
      return true;
    }

    public boolean getByteKnown(int off) {
      return getByteKnown(off, 1);
    }

    public int getByte(int off) {
      return myData[myOffset + off] & 0xFF;
    }

    public boolean getIntKnown(int off) {
      return getByteKnown(off, 4);
    }

    public int getInt(int off) {
      off += myOffset;
      // TODO: figure out BigEndian vs LittleEndian.
      return (myData[off + 0] & 0xFF) | ((myData[off + 1] & 0xFF) << 8) | ((myData[off + 2] & 0xFF) << 16) | (myData[off + 3] << 24);
    }

    public boolean getLongKnown(int off) {
      return getByteKnown(off, 8);
    }

    public long getLong(int off) {
      // TODO: figure out BigEndian vs LittleEndian.
      return (getInt(off) & 0xFFFFFFFFL) | ((long)getInt(off + 4) << 32);
    }
  }

  private interface MemoryModel {
    /**
     * this will always be a signed Long
     */
    long getLineCount();

    int getLineLength();

    Iterator<Segment> getLines(long start, long end, Runnable onChange);

    Range<Integer> getSelectableRegion(int column);

    ListenableFuture<Transferable> getTransferable(Selection selection);

    @NotNull
    Selection[] getReads(long startRow, long endRow, Runnable repainter);

    @NotNull
    Selection[] getWrites(long startRow, long endRow, Runnable repainter);

    @NotNull
    Selection[] getSelectionForMemoryRange(@NotNull MemoryRange selectedRange);
  }

  private static class Selection {
    @NotNull public final Range<Integer> myRange;

    public final int myStartCol;
    public final long myStartRow;

    public final int myEndCol;
    public final long myEndRow;

    public Selection(@NotNull Range<Integer> range, int startCol, long startRow, int endCol, long endRow) {
      myRange = range;
      myStartCol = startCol;
      myStartRow = startRow;
      myEndCol = endCol;
      myEndRow = endRow;
    }

    public boolean isEmpty() {
      return myStartCol == myEndCol && myStartRow == myEndRow;
    }

    public boolean isSelectionVisible(long startRow, long endRow) {
      return startRow <= myEndRow && myStartRow <= endRow;
    }

    @Override
    public String toString() {
      return myRange + " (" + myStartCol + "," + myStartRow + ") -> (" + myEndCol + "," + myEndRow + ")";
    }
  }

  private static abstract class FixedMemoryModel implements MemoryModel {
    protected static final int BYTES_PER_ROW = 16;
    private final static Selection[] NO_SELECTIONS = new Selection[0];

    protected final MemoryDataModel myData;
    protected final long myRows;

    public FixedMemoryModel(MemoryDataModel data) {
      myData = data;
      myRows = UnsignedLong.fromLongBits(data.getEndAddress() - data.getAddress()).bigIntegerValue().add(BigInteger.valueOf(BYTES_PER_ROW)).divide(BigInteger.valueOf(BYTES_PER_ROW)).longValueExact();
    }

    @Override
    public long getLineCount() {
      return myRows;
    }

    @Nullable
    private MemorySegment getMemorySegment(long startRow, long endRow, Runnable onChange) {
      if (startRow < 0 || endRow < startRow || endRow > getLineCount()) {
        throw new IndexOutOfBoundsException("[" + startRow + ", " + endRow + ") outside of [0, " + getLineCount() + ")");
      }
      ListenableFuture<MemorySegment> future = myData.get(startRow * BYTES_PER_ROW, (int)(endRow - startRow) * BYTES_PER_ROW);
      if (future.isDone()) {
        return Futures.getUnchecked(future);
      }
      else {
        future.addListener(onChange, EdtExecutor.INSTANCE);
        return null;
      }
    }

    @Override
    public Iterator<Segment> getLines(long startRow, long endRow, Runnable onChange) {
      MemorySegment segment = getMemorySegment(startRow, endRow, onChange);
      if (segment != null) {
        return getLines(startRow, endRow, segment);
      }
      else {
        return EmptyIterator.getInstance();
      }
    }

    protected Iterator<Segment> getLines(final long startRow, final long endRow, final MemorySegment memory) {
      return new Iterator<Segment>() {
        private long pos = startRow;
        private int offset = 0;
        private final Segment segment = new Segment(null, 0, 0);

        @Override
        public boolean hasNext() {
          return pos < endRow;
        }

        @Override
        public Segment next() {
          if (!hasNext()) {
            throw new NoSuchElementException();
          }
          getLine(segment, memory.subSegment(offset, BYTES_PER_ROW), pos);
          pos++;
          offset += BYTES_PER_ROW;
          return segment;
        }

        @Override
        public void remove() {
          throw new UnsupportedOperationException();
        }
      };
    }

    protected abstract void getLine(Segment segment, MemorySegment memory, long line);

    protected abstract Range<Integer>[] getDataRanges();

    @Override
    @NotNull
    public Selection[] getReads(long startRow, long endRow, Runnable repainter) {
      MemorySegment memory = getMemorySegment(startRow, endRow, repainter);
      return memory == null || memory.myReads == null || memory.myReads.length == 0 ? NO_SELECTIONS
                                                                                    : getSelections(memory.myReads, ((long)startRow * BYTES_PER_ROW) - memory.myOffset);
    }

    @Override
    @NotNull
    public Selection[] getWrites(long startRow, long endRow, Runnable repainter) {
      MemorySegment memory = getMemorySegment(startRow, endRow, repainter);
      return memory == null || memory.myWrites == null || memory.myWrites.length == 0 ? NO_SELECTIONS
                                                                                      : getSelections(memory.myWrites, ((long)startRow * BYTES_PER_ROW) - memory.myOffset);
    }

    @Override
    @NotNull
    public Selection[] getSelectionForMemoryRange(@NotNull MemoryRange selectedRange) {
      return getSelections(new MemoryRange[] { selectedRange }, - myData.getAddress());
    }

    @NotNull
    private Selection[] getSelections(@NotNull MemoryRange[] operation, long offset) {
      assert operation.length > 0;
      Range<Integer>[] ranges = getDataRanges();
      Selection[] shapes = new Selection[operation.length * ranges.length];
      for (int ri = 0; ri < ranges.length; ri++) {
        for (int oi = 0; oi < operation.length; oi++) {
          MemoryRange memoryRange = operation[oi];
          long startOffset = offset + memoryRange.getBase();
          int startCol = getColForOffset(ranges[ri], startOffset, true);
          long endOffset = offset + memoryRange.getBase() + memoryRange.getSize();
          int endCol = getColForOffset(ranges[ri], endOffset, false);
          shapes[ri * operation.length + oi] = new Selection(ranges[ri], startCol, Long.divideUnsigned(startOffset, BYTES_PER_ROW), endCol, Long.divideUnsigned(endOffset, BYTES_PER_ROW));
        }
      }
      return shapes;
    }

    private int getColForOffset(@NotNull Range<Integer> range, long offset, boolean start) {
      double positionOffset = (range.getTo() - range.getFrom()) * ((double)Long.remainderUnsigned(offset, BYTES_PER_ROW)) / BYTES_PER_ROW;
      return range.getFrom() + (int)(start ? Math.ceil(positionOffset) : positionOffset);
    }
  }

  private static abstract class CharBufferMemoryModel extends FixedMemoryModel {
    protected static final int CHARS_PER_ADDRESS = 16; // 8 byte addresses
    protected static final int ADDRESS_SEPARATOR = 1;
    protected static final int ADDRESS_CHARS = CHARS_PER_ADDRESS + ADDRESS_SEPARATOR;
    protected static final Range<Integer> ADDRESS_RANGE = new Range<>(0, CHARS_PER_ADDRESS);
    protected static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    protected final int myCharsPerRow;
    protected final Range<Integer> myMemoryRange;

    public CharBufferMemoryModel(MemoryDataModel data, int charsPerRow, Range<Integer> memoryRange) {
      super(data);
      myCharsPerRow = charsPerRow;
      myMemoryRange = memoryRange;
    }

    @Override
    public int getLineLength() {
      return myCharsPerRow;
    }

    protected static void appendUnknown(StringBuilder str, int n) {
      for (int i = 0; i < n; i++) {
        str.append(UNKNOWN_CHAR);
      }
    }

    @Override
    protected void getLine(Segment segment, MemorySegment memory, long line) {
      segment.array = new char[myCharsPerRow];
      segment.offset = 0;
      segment.count = myCharsPerRow;
      formatLine(segment.array, memory, line);
    }

    private void formatLine(char[] array, MemorySegment memory, long line) {
      Arrays.fill(array, ' ');
      long address = myData.getAddress() + line * BYTES_PER_ROW;
      for (int i = CHARS_PER_ADDRESS - 1; i >= 0; i--, address >>>= 4) {
        array[i] = HEX_DIGITS[(int)address & 0xF];
      }
      array[CHARS_PER_ADDRESS] = ':';
      formatMemory(array, memory);
    }

    protected abstract void formatMemory(char[] buffer, MemorySegment memory);

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
    public ListenableFuture<Transferable> getTransferable(final Selection selection) {
      return Futures.transform(
        myData.get(selection.myStartRow * BYTES_PER_ROW, (int)(selection.myEndRow - selection.myStartRow + 1) * BYTES_PER_ROW), new Function<MemorySegment, Transferable>() {
          @Override
          public Transferable apply(MemorySegment memory) {
            StringBuilder buffer = new StringBuilder();
            Iterator<Segment> lines = getLines(selection.myStartRow, selection.myEndRow + 1, memory);
            if (lines.hasNext()) {
              Segment segment = lines.next();
              if (selection.myStartRow == selection.myEndRow) {
                buffer.append(segment.array, segment.offset + selection.myStartCol, selection.myEndCol - selection.myStartCol);
              }
              else {
                buffer.append(segment.array, segment.offset + selection.myStartCol, selection.myRange.getTo() - selection.myStartCol)
                  .append('\n');
              }
            }
            int rangeWidth = selection.myRange.getTo() - selection.myRange.getFrom();
            for (long line = selection.myStartRow + 1; lines.hasNext() && line < selection.myEndRow; line++) {
              Segment segment = lines.next();
              buffer.append(segment.array, segment.offset + selection.myRange.getFrom(), rangeWidth).append('\n');
            }
            if (lines.hasNext()) {
              Segment segment = lines.next();
              buffer
                .append(segment.array, segment.offset + selection.myRange.getFrom(), selection.myEndCol - selection.myRange.getFrom())
                .append('\n');
            }
            return new StringSelection(buffer.toString());
          }
        });
    }

    @Override
    public Range<Integer>[] getDataRanges() {
      //noinspection unchecked
      return new Range[] { myMemoryRange };
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

    public BytesMemoryModel(MemoryDataModel data) {
      super(data, CHARS_PER_ROW, BYTES_RANGE);
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
    public ListenableFuture<Transferable> getTransferable(Selection selection) {
      if (selection.myRange == ASCII_RANGE) {
        // Copy the actual myData, rather than the display.
        return Futures.transform(
          myData.get(selection.myStartRow * BYTES_PER_ROW, (int)(selection.myEndRow - selection.myStartRow + 1) * BYTES_PER_ROW), new Function<MemorySegment, Transferable>() {
            @Override
            public Transferable apply(MemorySegment s) {
              return new StringSelection(
                s.asString(selection.myStartCol - ASCII_RANGE.getFrom(), s.myLength - selection.myStartCol + ASCII_RANGE.getFrom() - ASCII_RANGE.getTo() + selection.myEndCol));
            }
          });
      }
      else {
        return super.getTransferable(selection);
      }
    }

    @Override
    protected void formatMemory(char[] buffer, MemorySegment memory) {
      for (int i = 0, j = ADDRESS_CHARS; i < memory.myLength; i++, j += CHARS_PER_BYTE + BYTE_SEPARATOR) {
        int b = memory.getByte(i);
        if (memory.getByteKnown(i)) {
          buffer[j + 1] = HEX_DIGITS[(b >> 4) & 0xF];
          buffer[j + 2] = HEX_DIGITS[(b >> 0) & 0xF];
        }
        else {
          buffer[j + 1] = UNKNOWN_CHAR;
          buffer[j + 2] = UNKNOWN_CHAR;
        }
      }

      for (int i = 0, j = ADDRESS_CHARS + BYTES_CHARS + ASCII_SEPARATOR; i < memory.myLength; i++, j++) {
        int b = memory.getByte(i);
        buffer[j] = memory.getByteKnown(i) && (b >= 32 && b < 127) ? (char)b : '.';
      }
    }

    @Override
    public Range<Integer>[] getDataRanges() {
      //noinspection unchecked
      return new Range[] { myMemoryRange, ASCII_RANGE };
    }
  }

  private static class IntegersMemoryModel extends CharBufferMemoryModel {
    // Little endian assumption
    private static final ByteOrder ENDIAN = ByteOrder.LITTLE_ENDIAN;
    private static final int ITEM_SEPARATOR = 1;
    private final int mySize;

    /**
     * @return the number of characters used to represent a single item
     */
    private static int charsPerItem(int size) {
      return size * 2;
    }

    /**
     * @return the number of characters per row needed for items including separators.
     */
    private static int itemChars(int size) {
      int itemsPerRow = BYTES_PER_ROW / size;
      int charsPerItem = charsPerItem(size); // hex chars per item
      return (charsPerItem + ITEM_SEPARATOR) * itemsPerRow;
    }

    /**
     * @return the number of characters per row including the address and items.
     */
    private static int charsPerRow(int size) {
      return ADDRESS_CHARS + itemChars(size);
    }

    /**
     * @return the character column range where the items are present
     */
    private static Range<Integer> itemsRange(int size) {
      return new Range<Integer>(ADDRESS_CHARS + ITEM_SEPARATOR, ADDRESS_CHARS + itemChars(size));
    }

    protected IntegersMemoryModel(MemoryDataModel data, int size) {
      super(data.align(size), charsPerRow(size), itemsRange(size));
      mySize = size;
    }

    @Override
    protected void formatMemory(char[] buffer, MemorySegment memory) {
      final int charsPerItem = charsPerItem(mySize);
      for (int i = 0, j = ADDRESS_CHARS; i + mySize <= memory.myLength; i += mySize, j += charsPerItem + ITEM_SEPARATOR) {
        if (memory.getByteKnown(i, mySize)) {
          for (int k = 0; k < mySize; ++k) {
            int val = memory.getByte(i + k);
            int chOff = ENDIAN.equals(ByteOrder.LITTLE_ENDIAN) ? charsPerItem(mySize - 1 - k) : charsPerItem(k);
            buffer[j + chOff + 1] = HEX_DIGITS[(val >> 4) & 0xF];
            buffer[j + chOff + 2] = HEX_DIGITS[val & 0xF];
          }
        }
        else {
          for (int k = 0; k < charsPerItem; ++k) {
            buffer[j + k + 1] = UNKNOWN_CHAR;
          }
        }
      }
    }
  }

  private static class ShortsMemoryModel extends IntegersMemoryModel {
    public ShortsMemoryModel(MemoryDataModel data) {
      super(data, 2);
    }
  }

  private static class IntsMemoryModel extends IntegersMemoryModel {
    public IntsMemoryModel(MemoryDataModel data) {
      super(data, 4);
    }
  }

  private static class LongsMemoryModel extends IntegersMemoryModel {
    public LongsMemoryModel(MemoryDataModel data) {
      super(data, 8);
    }
  }

  private static class FloatsMemoryModel extends CharBufferMemoryModel {
    private static final int FLOATS_PER_ROW = BYTES_PER_ROW / 4;
    private static final int CHARS_PER_FLOAT = 15;

    private static final int FLOAT_SEPARATOR = 1;

    private static final int FLOATS_CHARS = (CHARS_PER_FLOAT + FLOAT_SEPARATOR) * FLOATS_PER_ROW;
    private static final int CHARS_PER_ROW = ADDRESS_CHARS + FLOATS_CHARS;

    private static final Range<Integer> FLOATS_RANGE = new Range<Integer>(ADDRESS_CHARS + FLOAT_SEPARATOR, ADDRESS_CHARS + FLOATS_CHARS);

    public FloatsMemoryModel(MemoryDataModel data) {
      super(data.align(4), CHARS_PER_ROW, FLOATS_RANGE);
    }

    @Override
    protected void formatMemory(char[] buffer, MemorySegment memory) {
      StringBuilder sb = new StringBuilder(50);
      for (int i = 0, j = ADDRESS_CHARS; i + 3 < memory.myLength; i += 4, j += CHARS_PER_FLOAT + FLOAT_SEPARATOR) {
        sb.setLength(0);
        if (memory.getIntKnown(i)) {
          sb.append(Float.intBitsToFloat(memory.getInt(i)));
        }
        else {
          appendUnknown(sb, CHARS_PER_FLOAT);
        }
        int count = Math.min(CHARS_PER_FLOAT, sb.length());
        sb.getChars(0, count, buffer, j + CHARS_PER_FLOAT - count + 1);
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

    public DoublesMemoryModel(MemoryDataModel data) {
      super(data.align(8), CHARS_PER_ROW, DOUBLES_RANGE);
    }

    @Override
    protected void formatMemory(char[] buffer, MemorySegment memory) {
      StringBuilder sb = new StringBuilder(50);
      for (int i = 0, j = ADDRESS_CHARS; i + 7 < memory.myLength; i += 8, j += CHARS_PER_DOUBLE + DOUBLE_SEPARATOR) {
        sb.setLength(0);
        if (memory.getLongKnown(i)) {
          sb.append(Double.longBitsToDouble(memory.getLong(i)));
        }
        else {
          appendUnknown(sb, CHARS_PER_DOUBLE);
        }
        int count = Math.min(CHARS_PER_DOUBLE, sb.length());
        sb.getChars(0, count, buffer, j + CHARS_PER_DOUBLE - count + 1);
      }
    }
  }
}
