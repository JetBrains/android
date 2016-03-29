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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.MemoryInfo;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryRange;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AsyncFunction;
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
import com.intellij.reference.SoftReference;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Range;
import com.intellij.util.containers.EmptyIterator;
import com.intellij.util.ui.StatusText;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.Segment;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MemoryController extends Controller {
  private static final int DEFAULT_MEMORY_SIZE = 0x10000;
  private static final char UNKNOWN_CHAR = '?';

  @NotNull private static final Logger LOG = Logger.getInstance(MemoryController.class);

  public static JComponent createUI(GfxTraceEditor editor) {
    return new MemoryController(editor).myPanel;
  }

  @NotNull private final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull private final JBLoadingPanel myLoading = new JBLoadingPanel(new BorderLayout(), myEditor.getProject(), 50);
  @NotNull private final EmptyPanel myEmptyPanel = new EmptyPanel();
  @NotNull private final JScrollPane myScrollPane = new JBScrollPane(myEmptyPanel);
  @NotNull private DataType myDataType = DataType.Bytes;
  @NotNull private ComboBox myCombo;
  private AtomPath myAtomPath;
  private MemoryDataModel myMemoryData;

  private MemoryController(@NotNull GfxTraceEditor editor) {
    super(editor);
    myLoading.add(myScrollPane, BorderLayout.CENTER);
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

    myPanel.setBorder(BorderFactory.createTitledBorder(myScrollPane.getBorder(), "Memory"));
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
    if (kind.equals(MemoryKind.Integer)) {
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
    else if (size == 1 && kind.equals(MemoryKind.Char)) {
      return DataType.Text;
    }
    else if (kind.equals(MemoryKind.Address)) {
      if (size == 4) {
        return DataType.Ints;
      } else if (size == 8) {
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
    final DataType dataType = typedMemoryPath != null ? dataTypeFromMemoryType(typedMemoryPath.getType()) : myDataType;
    if (memoryPath != null) {
      if (memoryPath.getSize() == 0) {
        // Fetch a default amount of memory for memory pointers, as they point to a region of unknown size.
        memoryPath.setSize(DEFAULT_MEMORY_SIZE);
      }
      myLoading.startLoading();
      PagedMemoryDataModel.MemoryFetcher fetcher = new PagedMemoryDataModel.MemoryFetcher() {
        @Override
        public ListenableFuture<MemoryInfo> get(long address, long count) {
          return myEditor.getClient().get(
            new MemoryRangePath().setAfter(memoryPath.getAfter()).setPool(memoryPath.getPool()).setAddress(address).setSize(count));
        }
      };
      if (PagedMemoryDataModel.shouldUsePagedModel(memoryPath.getSize())) {
        myMemoryData = new PagedMemoryDataModel(fetcher, memoryPath.getAddress(), memoryPath.getSize());
        myDataType = dataType;
        myCombo.setSelectedItem(dataType);
        update();
      }
      else {
        Rpc.listen(fetcher.get(memoryPath.getAddress(), memoryPath.getSize()), LOG, new UiErrorCallback<MemoryInfo, MemoryDataModel, String>() {
          @Override
          protected ResultOrError<MemoryDataModel, String> onRpcThread(Rpc.Result<MemoryInfo> result) throws RpcException, ExecutionException {
            final MemoryInfo info;
            try {
              info = result.get();
            } catch (ErrDataUnavailable e) {
              return error(e.getMessage());
            }
            if (info == null) {
              return error("No memory data");
            }
            return success(new ImmediateMemoryDataModel(memoryPath.getAddress(), info));
          }

          @Override
          protected void onUiThreadSuccess(MemoryDataModel result) {
            myMemoryData = result;
            myDataType = dataType;
            myCombo.setSelectedItem(dataType);
            update();
          }

          @Override
          protected void onUiThreadError(String message) {
            myEmptyPanel.setText(message);
            myScrollPane.setViewportView(myEmptyPanel);
          }
        });
      }
      myAtomPath = memoryPath.getAfter();
    }
    else {
      // As it is hard to select a memory path in the atom view without also selecting an atom we ignore any
      // atom event for the current atom, otherwise you can get flicked back to the empty panel unexpectedly.
      AtomPath atomPath = event.findAtomPath();
      if (myAtomPath == null || atomPath == null || !myAtomPath.equals(atomPath)) {
        myAtomPath = atomPath;
        myEmptyPanel.resetText();
        myScrollPane.setViewportView(myEmptyPanel);
      }
    }
  }

  private void update() {
    myLoading.stopLoading();
    myScrollPane.setViewportView(new MemoryPanel(myDataType.getMemoryModel(myMemoryData)));
  }

  private enum DataType {
    Text() {
      @Override
      public MemoryModel getMemoryModel(MemoryDataModel memory) {
        return new TextMemoryModel(memory);
      }
    },
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

  private static class MemoryPanel extends JComponent implements Scrollable, DataProvider, CopyProvider {
    private MemoryModel myModel;
    private final EditorColorsScheme myTheme;
    private Range<Integer> mySelectionRange = null;
    private final Point mySelectionStart = new Point();
    private final Point mySelectionEnd = new Point();
    private final Runnable myRepainter = new Runnable() {
      @Override
      public void run() {
        revalidate();
        repaint();
      }
    };

    public MemoryPanel(MemoryModel model) {
      myModel = model;
      myTheme = EditorColorsManager.getInstance().getGlobalScheme();

      setCursor(new Cursor(Cursor.TEXT_CURSOR));
      setFocusable(true);

      MouseAdapter mouseHandler = new MouseAdapter() {
        private final Point mySelectionInitiation = new Point();
        private boolean mySelecting;

        @Override
        public void mousePressed(MouseEvent e) {
          requestFocus();
          if (isSelectionButton(e)) {
            if ((e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0 && mySelectionRange != null) {
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
            if (mySelectionRange != null && mySelectionStart.equals(mySelectionEnd)) {
              mySelectionRange = null;
              repaint();
            }
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

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
          if (mySelecting) {
            if (mySelectionRange == null) {
              startSelecting(e);
            }
            else {
              updateSelection(e);
            }
          }

          // Bubble the event.
          JScrollPane ancestor = (JBScrollPane)SwingUtilities.getAncestorOfClass(JBScrollPane.class, MemoryPanel.this);
          if (ancestor != null) {
            MouseWheelEvent converted = (MouseWheelEvent)SwingUtilities.convertMouseEvent(MemoryPanel.this, e, ancestor);
            for (MouseWheelListener listener : ancestor.getMouseWheelListeners()) {
              listener.mouseWheelMoved(converted);
            }
          }
        }

        private void startSelecting(MouseEvent e) {
          mySelecting = true;
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
      addMouseWheelListener(mouseHandler);
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
        Futures.addCallback(
          myModel.getTransferable(mySelectionRange, mySelectionStart, mySelectionEnd), new FutureCallback<Transferable>() {
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
      int startRow = Math.max(0, Math.min(myModel.getLineCount() - 1, clip.y / lineHeight));
      int endRow = Math.max(0, Math.min(myModel.getLineCount(), (clip.y + clip.height + lineHeight - 1) / lineHeight));
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

      // Drawing fonts in swing appears to use floating point math. Thus, y-cordinates greater than 16,777,217 cause issues.
      g.translate(0, startRow * lineHeight);

      int y = getAscent();
      if (!selectionVisible) {
        for (Iterator<Segment> it = myModel.getLines(startRow, endRow, myRepainter); it.hasNext(); y += lineHeight) {
          Segment segment = it.next();
          g.drawChars(segment.array, segment.offset, segment.count, 0, y);
        }
      }
      else {
        int row = startRow;
        int rangeWidth = mySelectionRange.getTo() - mySelectionRange.getFrom();
        int fromWidth = mySelectionRange.getFrom() * charWidth, toWidth = mySelectionRange.getTo() * charWidth;
        Iterator<Segment> it = myModel.getLines(startRow, endRow, myRepainter);
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

  private interface MemoryDataModel {
    long getAddress();

    int getByteCount();

    ListenableFuture<MemorySegment> get(int offset, int length);

    MemoryDataModel align(int byteAlign);
  }

  private static BitSet computeKnown(MemoryInfo data) {
    BitSet known = new BitSet(data.getData().length);
    for (MemoryRange rng : data.getObserved()) {
      known.set((int)rng.getBase(), (int)rng.getBase() + (int)rng.getSize());
    }
    return known;
  }

  private static class ImmediateMemoryDataModel implements MemoryDataModel {
    private final long address;
    private final MemorySegment memory;

    public ImmediateMemoryDataModel(long address, MemoryInfo info) {
      this.address = address;
      this.memory = new MemorySegment(info.getData(), computeKnown(info), 0, info.getData().length);
    }

    private ImmediateMemoryDataModel(long address, MemorySegment data) {
      this.address = address;
      this.memory = data;
    }

    @Override
    public long getAddress() {
      return address;
    }

    @Override
    public int getByteCount() {
      return memory.myLength;
    }

    @Override
    public ListenableFuture<MemorySegment> get(int offset, int length) {
      return Futures.immediateFuture(memory.subSegment(offset, length));
    }

    @Override
    public MemoryDataModel align(int align) {
      int remainder = getByteCount() % align;
      return (remainder == 0) ? this : new ImmediateMemoryDataModel(address, memory.subSegment(0, getByteCount() + align - remainder));
    }
  }

  private static class PagedMemoryDataModel implements MemoryDataModel {
    private static final int PAGE_SIZE = 0x10000;
    private static final int PAGE_SHIFT = 16;

    private final MemoryFetcher fetcher;
    private final long address;
    private final int size;
    private final Map<Integer, SoftReference<MemoryInfo>> pageCache = Maps.newHashMap();

    public PagedMemoryDataModel(MemoryFetcher fetcher, long address, long size) {
      this.fetcher = fetcher;
      this.address = address;
      this.size = (int)size; // TODO: handle larger memory areas?
    }

    public static boolean shouldUsePagedModel(long size) {
      return size >= 2 * PAGE_SIZE;
    }

    @Override
    public long getAddress() {
      return address;
    }

    @Override
    public int getByteCount() {
      return size;
    }

    @Override
    public ListenableFuture<MemorySegment> get(int offset, int length) {
      offset = Math.min(size - 1, offset);
      length = Math.min(size - offset, length);

      int firstPage = getPageForOffset(offset);
      int lastPage = getPageForOffset(offset + length);
      if (firstPage == lastPage) {
        return getPage(firstPage, getOffsetInPage(offset), length);
      }
      List<ListenableFuture<MemorySegment>> futures = Lists.newArrayList();
      futures.add(getPage(firstPage, getOffsetInPage(offset), PAGE_SIZE - getOffsetInPage(offset)));
      for (int page = firstPage + 1, left = length - PAGE_SIZE + getOffsetInPage(offset); page <= lastPage; page++, left -= PAGE_SIZE) {
        futures.add(getPage(page, 0, Math.min(left, PAGE_SIZE)));
      }

      final int totalLength = length;
      return Futures.transform(Futures.allAsList(futures), new Function<List<MemorySegment>, MemorySegment>() {
        @Override
        public MemoryController.MemorySegment apply(List<MemorySegment> segments) {
          return new MemorySegment(segments, totalLength);
        }
      });
    }

    private static int getPageForOffset(int offset) {
      return offset >>> PAGE_SHIFT;
    }

    private static int getOffsetForPage(int page) {
      return page << PAGE_SHIFT;
    }

    private static int getOffsetInPage(int offset) {
      return offset & (PAGE_SIZE - 1);
    }

    private ListenableFuture<MemorySegment> getPage(final int page, final int offset, final int length) {
      MemoryInfo mem = getFromCache(page);
      if (mem != null) {
        return Futures.immediateFuture(new MemorySegment(mem).subSegment(offset, length));
      }

      long base = address + getOffsetForPage(page);
      return Futures
        .transform(fetcher.get(base, (int)Math.min(address + size - base, PAGE_SIZE)), new Function<MemoryInfo, MemorySegment>() {
          @Override
          public MemorySegment apply(MemoryInfo mem) {
            addToCache(page, mem);
            return new MemorySegment(mem);
          }
        });
    }

    private MemoryInfo getFromCache(int page) {
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

    private void addToCache(int page, MemoryInfo data) {
      synchronized (pageCache) {
        pageCache.put(page, new SoftReference<MemoryInfo>(data));
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

    private MemorySegment(byte[] data, BitSet known, int offset, int length) {
      myData = data;
      myOffset = offset;
      myLength = length;
      myKnown = known;
    }

    public MemorySegment(List<MemorySegment> segments, int length) {
      byte[] data = new byte[length];
      BitSet known = new BitSet(length);
      int done = 0;
      for (Iterator<MemorySegment> it = segments.iterator(); it.hasNext() && done < length; ) {
        MemorySegment segment = it.next();
        int count = Math.min(length - done, segment.myLength);
        System.arraycopy(segment.myData, segment.myOffset, data, done, count);
        for (int i = 0; i < count; ++i) {
          known.set(done + i, segment.myKnown.get(segment.myOffset + i));
        }
        done += count;
      }
      myData = data;
      myKnown = known;
      myOffset = 0;
      myLength = done;
    }

    public MemorySegment(MemoryInfo info) {
      myData = info.getData();
      myOffset = 0;
      myKnown = computeKnown(info);
      myLength = info.getData().length;
    }

    public MemorySegment subSegment(int start, int count) {
      return new MemorySegment(myData, myKnown, myOffset + start, Math.min(count, myLength - start));
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
    int getLineCount();

    int getLineLength();

    Iterator<Segment> getLines(int start, int end, Runnable onChange);

    Range<Integer> getSelectableRegion(int column);

    ListenableFuture<Transferable> getTransferable(Range<Integer> selectionRange, Point start, Point end);
  }

  private static abstract class FixedMemoryModel implements MemoryModel {
    protected static final int BYTES_PER_ROW = 16;

    protected final MemoryDataModel myData;
    protected final int myRows;

    public FixedMemoryModel(MemoryDataModel data) {
      myData = data;
      myRows = (data.getByteCount() + BYTES_PER_ROW - 1) / BYTES_PER_ROW;
    }

    @Override
    public int getLineCount() {
      return myRows;
    }

    @Override
    public Iterator<Segment> getLines(int start, int end, Runnable onChange) {
      if (start < 0 || end < start || end > getLineCount()) {
        throw new IndexOutOfBoundsException("[" + start + ", " + end + ") outside of [0, " + getLineCount() + ")");
      }
      ListenableFuture<MemorySegment> future = myData.get(start * BYTES_PER_ROW, (end - start) * BYTES_PER_ROW);
      if (future.isDone()) {
        return getLines(start, end, Futures.getUnchecked(future));
      }
      else {
        future.addListener(onChange, EdtExecutor.INSTANCE);
        return EmptyIterator.getInstance();
      }
    }

    protected Iterator<Segment> getLines(final int start, final int end, final MemorySegment memory) {
      return new Iterator<Segment>() {
        private int pos = start;
        private int offset = 0;
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

    protected abstract void getLine(Segment segment, MemorySegment memory, int line);
  }

  private static abstract class CharBufferMemoryModel extends FixedMemoryModel {
    protected static final int CHARS_PER_ADDRESS = 16; // 8 byte addresses
    protected static final int ADDRESS_SEPARATOR = 1;
    protected static final int ADDRESS_CHARS = CHARS_PER_ADDRESS + ADDRESS_SEPARATOR;
    protected static final Range<Integer> ADDRESS_RANGE = new Range<Integer>(0, CHARS_PER_ADDRESS);
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
    protected void getLine(Segment segment, MemorySegment memory, int line) {
      segment.array = new char[myCharsPerRow];
      segment.offset = 0;
      segment.count = myCharsPerRow;
      formatLine(segment.array, memory, line);
    }

    private void formatLine(char[] array, MemorySegment memory, int line) {
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
    public ListenableFuture<Transferable> getTransferable(final Range<Integer> selectionRange, final Point start, final Point end) {
      return Futures.transform(
        myData.get(start.y * BYTES_PER_ROW, (end.y - start.y + 1) * BYTES_PER_ROW), new Function<MemorySegment, Transferable>() {
          @Override
          public Transferable apply(MemorySegment memory) {
            StringBuilder buffer = new StringBuilder();
            Iterator<Segment> lines = getLines(start.y, end.y + 1, memory);
            if (lines.hasNext()) {
              Segment segment = lines.next();
              if (start.y == end.y) {
                buffer.append(segment.array, segment.offset + start.x, end.x - start.x);
              }
              else {
                buffer.append(segment.array, segment.offset + start.x, selectionRange.getTo() - start.x)
                  .append('\n');
              }
            }
            int rangeWidth = selectionRange.getTo() - selectionRange.getFrom();
            for (int line = start.y + 1; lines.hasNext() && line < end.y; line++) {
              Segment segment = lines.next();
              buffer.append(segment.array, segment.offset + selectionRange.getFrom(), rangeWidth).append('\n');
            }
            if (lines.hasNext()) {
              Segment segment = lines.next();
              buffer
                .append(segment.array, segment.offset + selectionRange.getFrom(), end.x - selectionRange.getFrom())
                .append('\n');
            }
            return new StringSelection(buffer.toString());
          }
        });
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
    public ListenableFuture<Transferable> getTransferable(Range<Integer> selectionRange, final Point start, final Point end) {
      if (selectionRange == ASCII_RANGE) {
        // Copy the actual myData, rather than the display.
        return Futures.transform(
          myData.get(start.y * BYTES_PER_ROW, (end.y - start.y + 1) * BYTES_PER_ROW), new Function<MemorySegment, Transferable>() {
            @Override
            public Transferable apply(MemorySegment s) {
              return new StringSelection(
                s.asString(start.x - ASCII_RANGE.getFrom(), s.myLength - start.x + ASCII_RANGE.getFrom() - ASCII_RANGE.getTo() + end.x));
            }
          });
      }
      else {
        return super.getTransferable(selectionRange, start, end);
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

  /**
   * A {@link MemoryModel} that renders the memory data as ASCII text. Uses a place holder for
   * non-ASCII bytes. Unlike the other models, this one will load all memory data when
   * instantiated. This is because the number of lines and their lengths is very much dynamic,
   * depending on the data and thus impossible to predict.
   */
  private static class TextMemoryModel implements MemoryModel {
    private static final int UNKNOWN_LINE_LENGTH = 100;
    private static final int SCAN_SIZE = 64 * 1024;
    private static final char PLACE_HOLDER = '\u25AF'; // Unicode 'white vertical rectangle'

    private final MemoryDataModel myMemory;
    private final List<String> myLines = Lists.newArrayList();
    private int myLineLength;
    private Runnable myListener;

    public TextMemoryModel(MemoryDataModel memory) {
      myMemory = memory;
      scan();
    }

    /**
     * Scans the memory range for new line characters to break the memory regions into lines of
     * text. While we are scanning, the model will be empty, but we'll notify the renderer once the
     * scan is complete.
     */
    private void scan() {
      ListenableFuture<List<String>> lines =
        Futures.transform(myMemory.get(0, Math.min(myMemory.getByteCount(), SCAN_SIZE)), new AsyncFunction<MemorySegment, List<String>>() {
          private List<String> result = Lists.newArrayList();
          private int done = 0;
          private boolean prevWasCarriageRet = false;
          private StringBuilder current = new StringBuilder();
          private StringBuilder unknown = new StringBuilder();

          /**
           * Note, we'll re-use this same function instance to process each chunk of memory.
           */
          @Override
          public ListenableFuture<List<String>> apply(MemorySegment data) throws Exception {
            // Process all the bytes in this memory chunk.
            for (int i = 0, j = data.myOffset; i < data.myLength; i++, j++) {
              // Skip any '\n' following an '\r', otherwise, '\r' is treated as a newline.
              if (!data.getByteKnown(j)) {
                if (current.length() != 0) {
                  result.add(current.toString());
                  current.delete(0, current.length());
                }
                if (unknown.length() == UNKNOWN_LINE_LENGTH) {
                  result.add(unknown.toString());
                  unknown.delete(0, unknown.length());
                }
                else {
                  unknown.append(UNKNOWN_CHAR);
                }
                prevWasCarriageRet = false;
              }
              else {
                if (unknown.length() != 0) {
                  result.add(unknown.toString());
                  unknown.delete(0, unknown.length());
                }
                if (data.myData[j] == '\r') {
                  result.add(current.toString());
                  current.delete(0, current.length());
                  prevWasCarriageRet = true;
                }
                else {
                  if (data.myData[j] == '\n') {
                    if (!prevWasCarriageRet) {
                      result.add(current.toString());
                      current.delete(0, current.length());
                    }
                  }
                  else {
                    int value = data.myData[j] & 0xFF;
                    // Use a place holder for non-printable ASCII characters.
                    current.append((value >= 0x7f || value < 0x20) ? PLACE_HOLDER : (char)value);
                  }
                  prevWasCarriageRet = false;
                }
              }
            }

            done += data.myLength;
            if (done < myMemory.getByteCount()) {
              // Fetch the next chunk of memory and process with this function.
              return Futures.transform(myMemory.get(done, Math.min(myMemory.getByteCount() - done, SCAN_SIZE)), this);
            }

            // All memory has been scanned, add any remaining characters as a line and finish up.
            if (current.length() > 0) {
              result.add(current.toString());
            }
            if (unknown.length() > 0) {
              result.add(unknown.toString());
            }
            return Futures.immediateFuture(result);
          }
        });
      Futures.addCallback(lines, new FutureCallback<List<String>>() {
        @Override
        public void onSuccess(List<String> result) {
          int maxLength = 0;
          for (String line : result) {
            maxLength = Math.max(maxLength, line.length());
          }

          // Update our state and notify the renderer.
          Runnable listener;
          synchronized (this) {
            myLines.addAll(result);
            myLineLength = maxLength;
            listener = myListener;
            myListener = null;
          }
          if (listener != null) {
            ApplicationManager.getApplication().invokeLater(listener);
          }
        }

        @Override
        public void onFailure(Throwable t) {
          LOG.error("Failed to read memory", t);
        }
      });
    }

    @Override
    public synchronized int getLineCount() {
      return myLines.size();
    }

    @Override
    public synchronized int getLineLength() {
      return myLineLength;
    }

    @Override
    public synchronized Iterator<Segment> getLines(int start, int end, Runnable onChange) {
      if (start < 0 || end < start || end > getLineCount()) {
        throw new IndexOutOfBoundsException("[" + start + ", " + end + ") outside of [0, " + getLineCount() + ")");
      }

      // If we've not yet finished scanning, keep track of who's interested. Note, we currently
      // just keep the last listener, since there's only one, but this could eaisly be changed to
      // chain multiple listeners.
      if (myLines.isEmpty()) {
        myListener = onChange;
        return Iterators.emptyIterator();
      }

      return Iterators.transform(myLines.subList(start, end).iterator(), new Function<String, Segment>() {
        private final Segment segment = new Segment(new char[getLineLength()], 0, getLineLength());

        @Override
        public Segment apply(String input) {
          // To not complicate the selection/rendering logic, every line returned by this iterator
          // will have the same length, which corresponds to the max length and .getLineLength().
          // This is the same as for the other models.
          input.getChars(0, input.length(), segment.array, 0);
          Arrays.fill(segment.array, input.length(), segment.array.length, ' ');
          return segment;
        }
      });
    }

    @Override
    public Range<Integer> getSelectableRegion(int column) {
      if (column >= 0 && column < getLineLength()) {
        return new Range<Integer>(0, getLineLength());
      }
      return null;
    }

    @Override
    public synchronized ListenableFuture<Transferable> getTransferable(Range<Integer> selectionRange, Point start, Point end) {
      // Note, we need to handle selection of whitespace correctly. Because most lines have a lot
      // of appended whitespace to make them the same length, the user selection may be out of
      // range of the lines of text. We handles this here, rather than in the renderer's
      // selection/rendering logic.
      if (start.y >= myLines.size() || end.y >= myLines.size() || start.y > end.y ||
          (start.y == end.y && (start.x >= end.x || start.x >= myLines.get(start.y).length()))) {
        return Futures.immediateFuture((Transferable)new StringSelection(""));
      }

      StringBuilder result = new StringBuilder();
      // Only process the first line, if the selection start is in range of it.
      if (start.x < myLines.get(start.y).length()) {
        String first = myLines.get(start.y);
        if (start.y == end.y) {
          result.append(first.substring(start.x, Math.min(first.length(), end.x)));
        }
        else {
          result.append(first.substring(start.x)).append('\n');
        }
      }
      // All intermediate lines are easy to process: simply append them.
      for (int i = start.y + 1; i < end.y; i++) {
        result.append(myLines.get(i)).append('\n');
      }
      // Only process the last line, if we haven't already done so.
      if (end.y != start.y) {
        String last = myLines.get(end.y);
        if (end.x > last.length()) {
          // If the selection was past the end, add an additional newline.
          result.append(last).append('\n');
        }
        else {
          result.append(last.substring(0, end.x));
        }
      }
      return Futures.immediateFuture((Transferable)new StringSelection(result.toString()));
    }
  }
}
