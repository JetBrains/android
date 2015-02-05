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
package com.android.tools.idea.gradle.editor.ui;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.editor.entity.GradleEditorSourceBinding;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.*;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * There is a possible case that there is more than one {@link GradleEditorSourceBinding source binding} for particular value, e.g.:
 * <pre>
 *   ext.COMPILE_SDK_VERSION = 1
 *   if (System.getenv("my-custom-environment)) {
 *     COMPILE_SDK_VERSION = 2
 *   }
 * </pre>
 * Here there are two bindings - <code>'1'</code> and <code>'2'</code>, so, we should somehow indicate at UI level that there
 * is no single value and provide convenient access to the registered bindings.
 * <p/>
 * Current control solves that task.
 */
public class ReferencedValuesGradleEditorComponent extends JBPanel {

  private static final Function<GradleEditorSourceBinding, VirtualFile> GROUPER = new Function<GradleEditorSourceBinding, VirtualFile>() {
    @Override
    public VirtualFile apply(GradleEditorSourceBinding input) {
      return input.getFile();
    }
  };

  private static final Comparator<VirtualFile> FILES_COMPARATOR = new Comparator<VirtualFile>() {
    @Override
    public int compare(VirtualFile f1, VirtualFile f2) {
      if (f1.equals(f2)) {
        return 0;
      }

      VirtualFile d1 = f1.isDirectory() ? f1 : f1.getParent();
      VirtualFile d2 = f2.isDirectory() ? f2 : f2.getParent();
      if (d1.equals(d2)) {
        // Just use lexicographic order for files from the same directory.
        return f1.getName().compareTo(f2.getName());
      }

      // The general idea is to prefer files located more close to the file system root to files located lower.
      if (VfsUtilCore.isAncestor(d1, d2, false)) {
        return -1;
      }
      else if (VfsUtilCore.isAncestor(d2, d1, false)) {
        return 1;
      }
      for (VirtualFile p1 = d1.getParent(), p2 = d2.getParent(); ; p1 = p1.getParent(), p2 = p2.getParent()) {
        if (p1 == null && p2 == null) {
          return 0;
        }
        else if (p1 == null) {
          return -1;
        }
        else if (p2 == null) {
          return 1;
        }
      }
    }
  };

  private static final Comparator<RangeMarker> RANGE_COMPARATOR = new Comparator<RangeMarker>() {
    @Override
    public int compare(RangeMarker rm1, RangeMarker rm2) {
      if (rm1.getStartOffset() < rm2.getStartOffset()) {
        return -1;
      }
      else if (rm2.getStartOffset() < rm1.getStartOffset()) {
        return 1;
      }
      else if (rm1.getEndOffset() < rm2.getEndOffset()) {
        return -1;
      }
      else if (rm2.getEndOffset() < rm1.getEndOffset()) {
        return 1;
      }
      return 0;
    }
  };

  /** Holds source binding grouped by file */
  private final Map<String, List<RangeMarker>> mySourceBindings = Maps.newLinkedHashMap();
  private final Map<String, VirtualFile> myFilesByName = Maps.newHashMap();
  @Nullable private WeakReference<Project> myProjectRef;

  public ReferencedValuesGradleEditorComponent() {
    super(new GridBagLayout());
    final JBLabel label = new JBLabel("<~>");
    label.setCursor(new Cursor(Cursor.HAND_CURSOR));
    setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
    TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES);
    if (attributes != null) {
      Color color = attributes.getForegroundColor();
      if (color != null) {
        label.setForeground(color);
      }
    }
    add(label, new GridBag());
    label.addMouseListener(new MouseAdapter() {

      @Override
      public void mouseReleased(MouseEvent e) {
        WeakReference<Project> projectRef = myProjectRef;
        if (projectRef == null) {
          return;
        }
        Project project = projectRef.get();
        if (project == null) {
          return;
        }
        final Ref<Balloon> balloonRef = new Ref<Balloon>();
        Content content = new Content(project, new Runnable() {
          @Override
          public void run() {
            Balloon balloon = balloonRef.get();
            if (balloon != null && !balloon.isDisposed()) {
              Disposer.dispose(balloon);
              balloonRef.set(null);
            }
          }
        });
        BalloonBuilder builder = JBPopupFactory.getInstance().createBalloonBuilder(content).setDisposable(project)
          .setShowCallout(false).setAnimationCycle(GradleEditorUiConstants.ANIMATION_TIME_MILLIS).setFillColor(JBColor.border());
        Balloon balloon = builder.createBalloon();
        balloonRef.set(balloon);
        balloon.show(new RelativePoint(label, new Point(label.getWidth() / 2, label.getHeight())), Balloon.Position.atRight);
      }
    });
  }

  public void bind(@NotNull Project project, @NotNull List<GradleEditorSourceBinding> sourceBindings) {
    myProjectRef = new WeakReference<Project>(project);
    ImmutableListMultimap<VirtualFile, GradleEditorSourceBinding> byFile = Multimaps.index(sourceBindings, GROUPER);
    List<VirtualFile> orderedFiles = Lists.newArrayList(byFile.keySet());
    ContainerUtil.sort(orderedFiles, FILES_COMPARATOR);
    for (VirtualFile file : orderedFiles) {
      ImmutableList<GradleEditorSourceBinding> list = byFile.get(file);
      List<RangeMarker> rangeMarkers = Lists.newArrayList();
      for (GradleEditorSourceBinding descriptor : list) {
        rangeMarkers.add(descriptor.getRangeMarker());
      }
      if (!rangeMarkers.isEmpty()) {
        ContainerUtil.sort(rangeMarkers, RANGE_COMPARATOR);
        String name = getRepresentativeName(project, file);
        mySourceBindings.put(name, rangeMarkers);
        myFilesByName.put(name, file);
      }
    }
  }

  /**
   * @param project  current ide project
   * @param file     target file
   * @return         convenient user-readable name for the given file, e.g. there is a possible case that we have a multi-project
   *                 and multiple {@code build.gradle} files are located there. We want to show names like {@code build.gradle},
   *                 {@code :app:build.gradle} instead of full paths then
   */
  @NotNull
  private static String getRepresentativeName(@NotNull Project project, @NotNull VirtualFile file) {
    VirtualFile projectBaseDir = project.getBaseDir();
    if (!VfsUtilCore.isAncestor(projectBaseDir, file, false)) {
      return file.getPresentableName();
    }
    List<String> pathEntries = Lists.newArrayList();
    for (VirtualFile f = file.getParent(); !projectBaseDir.equals(f); f = f.getParent()) {
      pathEntries.add(f.getPresentableName());
    }
    if (pathEntries.isEmpty()) {
      return file.getPresentableName();
    }
    Collections.reverse(pathEntries);
    String sep = SdkConstants.GRADLE_PATH_SEPARATOR;
    return sep + Joiner.on(sep).join(pathEntries) + sep + file.getPresentableName();
  }

  /**
   * Creates an image which represents editor's text from the region identified by the given range marker.
   *
   * @param editor      an editor which serves as a renderer for the target text
   * @param marker      range marker that points to the target text region
   * @param minWidthPx  width in pixels to constraint resulting image from below
   * @return            an image which represents target text fragment
   */
  @NotNull
  private static BufferedImage getContentToShow(@NotNull Editor editor, @NotNull RangeMarker marker, int minWidthPx) {
    int maxWidth = Toolkit.getDefaultToolkit().getScreenSize().width * 4 / 5;
    Document document = editor.getDocument();
    int startLine = document.getLineNumber(marker.getStartOffset());
    int endLine = document.getLineNumber(marker.getEndOffset());
    int minStartX = Integer.MAX_VALUE;
    int maxEndX = 0;
    CharSequence text = document.getCharsSequence();

    // Calculate desired text dimensions.
    for (int line = startLine; line <= endLine; line++) {
      int startOffsetToUse = CharArrayUtil.shiftForward(text, document.getLineStartOffset(line), document.getLineEndOffset(line), " \t");
      int endOffsetToUse = CharArrayUtil.shiftBackward(text, document.getLineStartOffset(line), document.getLineEndOffset(line), " \t");
      minStartX = Math.min(minStartX, offsetToXY(editor, startOffsetToUse).x);
      maxEndX = Math.max(maxEndX, offsetToXY(editor, endOffsetToUse).x);
    }

    // Calculate text dimensions taking into consideration min/max/desired width
    int desiredWidth = maxEndX - minStartX;
    final int xStart;
    final int xEnd;
    if (desiredWidth > maxWidth) {
      int xShift = (desiredWidth - maxWidth) / 2;
      xStart = offsetToXY(editor, minStartX).x + xShift;
      xEnd = xStart + maxWidth;
    }
    else if (desiredWidth < minWidthPx) {
      int xShift = (minWidthPx - desiredWidth) / 2;
      xStart = offsetToXY(editor, minStartX).x - xShift;
      xEnd = xStart + minWidthPx;
    }
    else {
      xStart = minStartX;
      xEnd = maxEndX;
    }
    int lineHeight = editor.getLineHeight();
    int yStart = offsetToXY(editor, marker.getStartOffset()).y;
    int yEnd = yStart + lineHeight + lineHeight * (endLine - startLine);

    int width = xEnd - xStart;
    int height = yEnd - yStart;

    // Ask the editor to render target text.
    JScrollPane scrollPane = UIUtil.findComponentOfType(editor.getComponent(), JScrollPane.class);
    BufferedImage image = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    if (scrollPane != null) {
      Component editorComponent = scrollPane.getViewport().getView();
      editorComponent.setSize(Integer.MAX_VALUE, Integer.MAX_VALUE);
      Graphics2D graphics = image.createGraphics();
      UISettings.setupAntialiasing(graphics);
      graphics.translate(-xStart, -yStart);
      graphics.setClip(xStart, yStart, width, height);
      editorComponent.paint(graphics);
      graphics.dispose();
    }

    return image;
  }

  @NotNull
  private static Point offsetToXY(@NotNull Editor editor, int offset) {
    return editor.visualPositionToXY(editor.offsetToVisualPosition(offset));
  }

  private class Content extends JBPanel {

    private static final String FILE_KEY = "__FILE";
    private static final String MARKER_KEY = "__MARKER";

    private final List<JComponent> myTextFragmentPanels = Lists.newArrayList();
    @NotNull private final Runnable myCloseCallback;
    @Nullable private JComponent myTextFragmentPanelUnderMouse;

    /**
     * Constructs new <code>ReferencedValuesGradleEditorComponent</code> object.
     *
     * @param project        current ide project
     * @param closeCallback  callback to notify that current content should be closed
     */
    Content(@NotNull final Project project, @NotNull Runnable closeCallback) {
      super(new GridBagLayout());
      myCloseCallback = closeCallback;
      setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
      int gap = 8;
      GridBag constraints = new GridBag().fillCellHorizontally().weightx(1).coverLine().insets(gap, gap, gap, gap);
      EditorFactory editorFactory = EditorFactory.getInstance();
      int maxTitleWidthPx = 0;
      for (String s : mySourceBindings.keySet()) {
        maxTitleWidthPx = Math.max(maxTitleWidthPx, getFontMetrics(UIUtil.getTitledBorderFont()).stringWidth(s));
      }
      for (Map.Entry<String, List<RangeMarker>> entry : mySourceBindings.entrySet()) {
        JBPanel titledPanel = new JBPanel(new GridBagLayout());
        titledPanel.setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
        titledPanel.setBorder(IdeBorderFactory.createTitledBorder(entry.getKey()));
        boolean hasContent = false;
        Editor editor = null;
        for (RangeMarker marker : entry.getValue()) {
          if (!marker.isValid()) {
            continue;
          }
          if (editor == null) {
            editor = editorFactory.createEditor(marker.getDocument(), project);
          }
          JBPanel fragmentPanel = new JBPanel(new GridBagLayout());
          fragmentPanel.putClientProperty(FILE_KEY, entry.getKey());
          fragmentPanel.putClientProperty(MARKER_KEY, marker);
          fragmentPanel.setForeground(GradleEditorUiConstants.BACKGROUND_COLOR);
          fragmentPanel.setBackground(GradleEditorUiConstants.BACKGROUND_COLOR);
          fragmentPanel.setBorder(BorderFactory.createLoweredBevelBorder());
          final BufferedImage contentToShow = getContentToShow(editor, marker, maxTitleWidthPx);
          fragmentPanel.add(new JLabel(new ImageIcon(contentToShow)), constraints);
          hasContent = true;
          titledPanel.add(fragmentPanel, constraints);
          myTextFragmentPanels.add(fragmentPanel);
        }
        if (editor != null) {
          editorFactory.releaseEditor(editor);
        }
        if (hasContent) {
          add(titledPanel, constraints);
        }
      }

      addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseMoved(MouseEvent e) {
          if (myTextFragmentPanelUnderMouse != null && !isInside(e, myTextFragmentPanelUnderMouse)) {
            myTextFragmentPanelUnderMouse.setBorder(BorderFactory.createLoweredBevelBorder());
            myTextFragmentPanelUnderMouse = null;
          }

          //noinspection NullableProblems
          for (JComponent panel : myTextFragmentPanels) {
            if (isInside(e, panel)) {
              myTextFragmentPanelUnderMouse = panel;
              panel.setBorder(BorderFactory.createRaisedBevelBorder());
            }
          }
        }
      });

      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          if (myTextFragmentPanelUnderMouse != null && isInside(e, myTextFragmentPanelUnderMouse)) {
            myTextFragmentPanelUnderMouse.setBorder(BorderFactory.createLoweredBevelBorder());
          }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
          if (myTextFragmentPanelUnderMouse == null || !isInside(e, myTextFragmentPanelUnderMouse)) {
            return;
          }
          Object fileName = myTextFragmentPanelUnderMouse.getClientProperty(FILE_KEY);
          if (!(fileName instanceof String)) {
            return;
          }
          VirtualFile file = myFilesByName.get(fileName.toString());
          if (file == null) {
            return;
          }
          Object m = myTextFragmentPanelUnderMouse.getClientProperty(MARKER_KEY);
          if (!(m instanceof RangeMarker)) {
            return;
          }
          RangeMarker marker = (RangeMarker)m;
          if (!marker.isValid()) {
            return;
          }
          OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, marker.getStartOffset());
          if (descriptor.canNavigate()) {
            descriptor.navigate(true);
            myCloseCallback.run();
          }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
          if (myTextFragmentPanelUnderMouse != null && isInside(e, myTextFragmentPanelUnderMouse)) {
            myTextFragmentPanelUnderMouse.setBorder(BorderFactory.createRaisedBevelBorder());
          }
        }
      });
    }

    private boolean isInside(@NotNull MouseEvent e, @NotNull JComponent component) {
      return component.contains(SwingUtilities.convertPoint(this, e.getPoint(), component));
    }
  }
}
