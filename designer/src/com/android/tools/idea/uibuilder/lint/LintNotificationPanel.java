/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.lint;

import com.android.tools.idea.rendering.HtmlBuilderHelper;
import com.android.tools.idea.rendering.HtmlLinkManager;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel.IssueData;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.lint.detector.api.Issue;
import com.android.utils.HtmlBuilder;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.DimensionService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiEditorUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;
import org.jetbrains.android.inspections.lint.AndroidLintQuickFix;
import org.jetbrains.android.inspections.lint.AndroidQuickfixContexts;
import org.jetbrains.android.inspections.lint.SuppressLintIntentionAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static com.android.tools.lint.detector.api.TextFormat.HTML;
import static java.awt.RenderingHints.*;

/**
 * Pane which lets you see the current lint warnings and apply fix/suppress
 */
public class LintNotificationPanel implements HyperlinkListener {
  private final ScreenView myScreenView;
  private JEditorPane myExplanationPane;
  private JBList myIssueList;
  private JPanel myPanel;
  private JBLabel myPreviewLabel;
  private JBLabel myTagLabel;

  private HtmlLinkManager myLinkManager = new HtmlLinkManager();

  public static final String DIMENSION_KEY = "lint.notification";
  private JBPopup myPopup;

  public LintNotificationPanel(@NotNull ScreenView screenView, @NotNull LintAnnotationsModel model) {
    myScreenView = screenView;

    List<IssueData> issues = getSortedIssues(screenView, model);
    if (issues == null) {
      return;
    }
    //noinspection unchecked
    myIssueList.setModel(new CollectionListModel<>(issues));
    configureCellRenderer();

    myPanel.setBorder(IdeBorderFactory.createEmptyBorder(JBUI.scale(8)));
    myExplanationPane.setMargin(JBUI.insets(3, 3, 3, 3));
    myExplanationPane.setContentType(UIUtil.HTML_MIME);
    myExplanationPane.addHyperlinkListener(this);

    myIssueList.setSelectedIndex(0);
    selectIssue(issues.get(0));
    myIssueList.addListSelectionListener(e -> {
      Object selectedValue = myIssueList.getSelectedValue();
      if (!(selectedValue instanceof IssueData)) {
        return;
      }
      IssueData selected = (IssueData)selectedValue;
      selectIssue(selected);
    });

    myPanel.setFocusable(false);
    ApplicationManager.getApplication().invokeLater(() -> myIssueList.requestFocus());
  }

  private void configureCellRenderer() {
    myIssueList.setCellRenderer(new ColoredListCellRenderer<IssueData>() {
      @Override
      protected void customizeCellRenderer(JList list, IssueData value, int index, boolean selected, boolean hasFocus) {
        if (value.level == HighlightDisplayLevel.ERROR) {
          append("Error: ", SimpleTextAttributes.ERROR_ATTRIBUTES);
        } else if (value.level == HighlightDisplayLevel.WARNING) {
          append("Warning: ");
        }
        append(value.message);
      }
    });
  }

  @Nullable
  private static List<IssueData> getSortedIssues(@NotNull ScreenView screenView, @NotNull LintAnnotationsModel model) {
    List<IssueData> issues = model.getIssues();
    if (issues.isEmpty()) {
      return null;
    }

    // Sort -- and prefer the selected components first
    List<NlComponent> selection = screenView.getSelectionModel().getSelection();
    Collections.sort(issues, (o1, o2) -> {
      boolean selected1 = selection.contains(o1.component);
      boolean selected2 = selection.contains(o2.component);
      if (selected1 != selected2) {
        return selected1 ? -1 : 1;
      }

      int compare = -o1.level.getSeverity().compareTo(o2.level.getSeverity());
      if (compare != 0) {
        return compare;
      }
      compare = o2.issue.getPriority() - o1.issue.getPriority();
      if (compare != 0) {
        return compare;
      }
      compare = o1.issue.compareTo(o2.issue);
      if (compare != 0) {
        return compare;
      }

      compare = o1.message.compareTo(o2.message);
      if (compare != 0) {
        return compare;
      }

      return o1.startElement.getTextOffset() - o2.startElement.getTextOffset();
    });
    return issues;
  }

  private void selectIssue(@Nullable IssueData selected) {
    NlComponent component = selected != null ? selected.component : null;
    updateIdLabel(component);
    updateExplanation(selected);
    updatePreviewImage(component);
  }

  private void updateIdLabel(@Nullable NlComponent component) {
    String text = "";
    if (component != null) {
      String id = component.getId();
      if (id != null) {
        text = id;
      } else {
        String tagName = component.getTagName();
        // custom views: strip off package:
        tagName = tagName.substring(tagName.lastIndexOf(".")+1);
        text = "<" + tagName + ">";
      }

      // Include position too to help disambiguate
      ViewEditorImpl viewEditor = new ViewEditorImpl(myScreenView);
      text += " at (" + viewEditor.pxToDp(component.x) + "," + viewEditor.pxToDp(component.y) + ") dp";
    }
    myTagLabel.setText(text);
  }

  private void updateExplanation(@Nullable IssueData selected) {
    // We have the capability to show markup text here, e.g.
    // myExplanationPane.setContentType(UIUtil.HTML_MIME)
    // and then use an HtmlBuilder to populate it with for
    // example issue.getExplanation(HTML). However, the builtin
    // HTML formatter ends up using a bunch of weird fonts etc
    // so the dialog just ends up looking tacky.

    String headerFontColor = HtmlBuilderHelper.getHeaderFontColor();
    HtmlBuilder builder = new HtmlBuilder();
    builder.openHtmlBody();

    if (selected != null) {
      builder.addHeading("Message: ", headerFontColor);
      builder.add(selected.message).newline();


      // Look for quick fixes
      AndroidLintInspectionBase inspection = selected.inspection;
      AndroidLintQuickFix[] quickFixes = inspection.getQuickFixes(selected.startElement, selected.endElement, selected.message);
      IntentionAction[] intentions = inspection.getIntentions(selected.startElement, selected.endElement);
      builder.addHeading("Suggested Fixes:", headerFontColor).newline();
      builder.beginList();
      for (final AndroidLintQuickFix fix : quickFixes) {
        builder.listItem();
        builder.addLink(fix.getName(), myLinkManager.createRunnableLink(() -> {
          myPopup.cancel();
          // TODO: Pull in editor context?
          WriteCommandAction.runWriteCommandAction(selected.startElement.getProject(), () -> {
            fix.apply(selected.startElement, selected.endElement, AndroidQuickfixContexts.BatchContext.getInstance());
          });
        }));
      }

      for (final IntentionAction fix : intentions) {
        builder.listItem();
        builder.addLink(fix.getText(), myLinkManager.createRunnableLink(() -> {
          NlModel model = myScreenView.getModel();
          Editor editor = PsiEditorUtil.Service.getInstance().findEditorByPsiElement(selected.startElement);
          if (editor != null) {
            editor.getCaretModel().getCurrentCaret().moveToOffset(selected.startElement.getTextOffset());
            myPopup.cancel();
            WriteCommandAction.runWriteCommandAction(model.getProject(), () -> {
              fix.invoke(model.getProject(), editor, model.getFile());
            });
          }
        }));
      }

      final SuppressLintIntentionAction suppress = new SuppressLintIntentionAction(selected.issue.getId(), selected.startElement);
      builder.listItem();
      builder.addLink(suppress.getText(), myLinkManager.createRunnableLink(() -> {
        myPopup.cancel();
        WriteCommandAction.runWriteCommandAction(selected.startElement.getProject(), () -> {
          suppress.invoke(selected.startElement.getProject(), null, myScreenView.getModel().getFile());
        });
      }));

      builder.endList();

      Issue issue = selected.issue;

      builder.addHeading("Priority: ", headerFontColor);
      builder.addHtml(String.format("%1$d / 10", issue.getPriority()));
      builder.newline();
      builder.addHeading("Category: ", headerFontColor);
      builder.add(issue.getCategory().getFullName());
      builder.newline();

      builder.addHeading("Severity: ", headerFontColor);
      builder.beginSpan();

      // Use converted level instead of *default* severity such that we match any user configured overrides
      HighlightDisplayLevel level = selected.level;
      builder.add(StringUtil.capitalize(level.getName().toLowerCase(Locale.US)));
      builder.endSpan();
      builder.newline();

      builder.addHeading("Explanation: ", headerFontColor);
      String description = issue.getBriefDescription(HTML);
      builder.addHtml(description);
      if (!description.isEmpty()
          && Character.isLetter(description.charAt(description.length() - 1))) {
        builder.addHtml(".");
      }
      builder.newline();
      String explanationHtml = issue.getExplanation(HTML);
      builder.addHtml(explanationHtml);
      List<String> moreInfo = issue.getMoreInfo();
      builder.newline();
      int count = moreInfo.size();
      if (count > 1) {
        builder.addHeading("More Info: ", headerFontColor);
        builder.beginList();
      }
      for (String uri : moreInfo) {
        if (count > 1) {
          builder.listItem();
        }
        builder.addLink(uri, uri);
      }
      if (count > 1) {
        builder.endList();
      }
      builder.newline();
    }

    builder.closeHtmlBody();

    try {
      myExplanationPane.read(new StringReader(builder.getHtml()), null);
      HtmlBuilderHelper.fixFontStyles(myExplanationPane);
      myExplanationPane.setCaretPosition(0);
    }
    catch (IOException ignore) { // can't happen for internal string reading
    }
  }

  private void updatePreviewImage(@Nullable NlComponent component) {
    // Show the icon in the image view
    if (component != null) {
      // Try to get the image
      int iw = myPreviewLabel.getSize().width;
      int ih = myPreviewLabel.getSize().height;
      if (iw == 0 || ih == 0) {
        iw = 200;
        ih = 200;
      }

      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);

      RenderResult renderResult = myScreenView.getModel().getRenderResult();
      if (renderResult != null && renderResult.getImage() != null) {
        BufferedImage fullImage = renderResult.getImage().getOriginalImage();
        // Draw the component into the preview image
        Graphics2D g2d = (Graphics2D)image.getGraphics();

        int sx1 = component.x;
        int sy1 = component.y;
        int sx2 = sx1 + component.w;
        int sy2 = sy1 + component.h;

        int dx1 = 0;
        int dy1 = 0;
        int dx2 = image.getWidth();
        int dy2 = image.getHeight();

        int ex1 = 0;
        int ey1 = 0;
        int ew = image.getWidth();
        int eh = image.getHeight();

        if (component.isRoot()) {
          int w = image.getWidth();
          int h = image.getHeight();

          double aspectRatio = (sx2 - sx1) / (double) (sy2 - sy1);
          if (aspectRatio >= 1) {
            int newH = (int)(h / aspectRatio);
            dy1 += (h - newH) / 2;
            h = newH;

            if (w >= (sx2 - sx1)) {
              // No need to scale: just paint 1-1
              dx1 = (w - (sx2 - sx1)) / 2;
              w = sx2 - sx1;
              dy1 = (h - (sy2 - sy1)) / 2;
              h = sy2 - sy1;
            }
          } else {
            int newW = (int)(w * aspectRatio);
            dx1 += (w - newW) / 2;
            w = newW;

            if (h >= (sy2 - sy1)) {
              // No need to scale: just paint 1-1
              dx1 = (w - (sx2 - sx1)) / 2;
              w = sx2 - sx1;
              dy1 = (h - (sy2 - sy1)) / 2;
              h = sy2 - sy1;
            }
          }
          dx2 = dx1 + w;
          dy2 = dy1 + h;
        } else {
          double aspectRatio = (sx2 - sx1) / (double)(sy2 - sy1);
          if (aspectRatio >= 1) {
            // Include enough context
            int verticalPadding = ((sx2 - sx1) - (sy2 - sy1)) / 2;
            sy1 -= verticalPadding;
            sy2 += verticalPadding;
            double scale = (sx2 - sx1) / (double)(dx2 - dx1);
            ey1 = (int)(verticalPadding / scale);
            eh = (int)(component.h / scale);
          }
          else {
            int horizontalPadding = ((sy2 - sy1) - (sx2 - sx1)) / 2;
            sx1 -= horizontalPadding;
            sx2 += horizontalPadding;
            double scale = (sy2 - sy1) / (double)(dy2 - dy1);
            ex1 = (int)(horizontalPadding / scale);
            ew = (int)(component.w / scale);
          }
        }

        // Use a gradient paint here with alpha?

        //graphics.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(KEY_ALPHA_INTERPOLATION, VALUE_ALPHA_INTERPOLATION_QUALITY);
        g2d.drawImage(fullImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);

        if (!component.isRoot()) {
          Area outside = new Area(new Rectangle2D.Double(0, 0, iw, ih));
          int padding = 10;
          Area area = new Area(new Ellipse2D.Double(ex1 - padding, ey1 - padding, ew + 2 * padding, eh + 2 * padding));
          outside.subtract(area);

          // To get anti=aliased shape clipping (e.g. soft shape clipping) we need to use an intermediate image:
          GraphicsConfiguration gc = g2d.getDeviceConfiguration();
          BufferedImage img = gc.createCompatibleImage(iw, ih, Transparency.TRANSLUCENT);
          Graphics2D g2 = img.createGraphics();
          g2.setComposite(AlphaComposite.Clear);
          g2.fillRect(0, 0, iw, ih);
          g2.setComposite(AlphaComposite.Src);
          g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
          // This color is relative to the Android image being painted, not dark/light IDE theme
          //noinspection UseJBColor
          g2.setColor(Color.WHITE);
          g2.fill(outside);
          g2.setComposite(AlphaComposite.SrcAtop);
          Color background = myPanel.getBackground();
          if (background == null) {
            background = Gray._230;
          }
          g2.setPaint(background);
          g2.fillRect(0, 0, iw, ih);
          g2.dispose();
          g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
          g2d.drawImage(img, 0, 0, null);
        }

        g2d.dispose();
      }

      myPreviewLabel.setIcon(new ImageIcon(image));
    } else {
      myPreviewLabel.setIcon(null);
    }
  }

  public void show(AnActionEvent e) {
    Project project = e.getProject();
    Dimension minSize = new Dimension(600, 300);
    JBPopup builder = JBPopupFactory.getInstance().createComponentPopupBuilder(myPanel, myPanel)
      .setProject(project)
      .setDimensionServiceKey(project, DIMENSION_KEY, false)
      .setResizable(true)
      .setMovable(true)
      .setMinSize(minSize)
      .setRequestFocus(true)
      .setTitle("Lint Warnings in Layout")
      .setCancelOnClickOutside(true)
      .setLocateWithinScreenBounds(true)
      .setShowShadow(true)
      .setCancelOnWindowDeactivation(true)
      .createPopup();

    Dimension preferredSize = DimensionService.getInstance().getSize(DIMENSION_KEY, project);
    if (preferredSize == null) {
      preferredSize = myPanel.getPreferredSize();
    }

    Object source = e.getInputEvent().getSource();
    if (source instanceof JComponent) {
      JComponent component = (JComponent)source;
      RelativePoint point = new RelativePoint(component, new Point(component.getWidth() - preferredSize.width, component.getHeight()));
      builder.show(point);
    }
    else {
      builder.showInBestPositionFor(e.getDataContext());
    }
    myPopup = builder;
  }

  // ---- Implements HyperlinkListener ----

  @Override
  public void hyperlinkUpdate(HyperlinkEvent e) {
    if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
      JEditorPane pane = (JEditorPane)e.getSource();
      if (e instanceof HTMLFrameHyperlinkEvent) {
        HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
        HTMLDocument doc = (HTMLDocument)pane.getDocument();
        doc.processHTMLFrameHyperlinkEvent(evt);
        return;
      }

      String url = e.getDescription();
      NlModel model = myScreenView.getModel();
      Module module = model.getModule();
      PsiFile file = model.getFile();
      DataContext dataContext = DataManager.getInstance().getDataContext(myScreenView.getSurface());
      assert dataContext != null;

      myLinkManager.handleUrl(url, module, file, dataContext, null);
    }
  }
}
