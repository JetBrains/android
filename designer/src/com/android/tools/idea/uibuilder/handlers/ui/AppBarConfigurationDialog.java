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
package com.android.tools.idea.uibuilder.handlers.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.facet.AndroidFacet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import static com.android.SdkConstants.*;

public class AppBarConfigurationDialog extends JDialog {
  // TODO: Remove the hardcoded AppBar height (192dp) and ID (appbar).
  private static final String TAG_COORDINATOR_LAYOUT =              // 1 = Prefix for android namespace
    "<android.support.design.widget.CoordinatorLayout\n" +          // 2 = Prefix for auto namespace
    "%3$s" +                                                        // 3 = Namespace declarations
    "%4$s" +                                                        // 4 = FitsSystemWindows
    "    %1$s:layout_width=\"match_parent\"\n" +
    "    %1$s:layout_height=\"match_parent\">\n" +
    "  <android.support.design.widget.AppBarLayout\n" +
    "      %1$s:id=\"@+id/appbar\"\n" +
    "%4$s" +                                                        // 4 = FitsSystemWindows
    "      %1$s:layout_height=\"192dp\"\n" +
    "      %1$s:layout_width=\"match_parent\">\n" +
    "    <android.support.design.widget.CollapsingToolbarLayout\n" +
    "        %1$s:layout_width=\"match_parent\"\n" +
    "        %1$s:layout_height=\"match_parent\"\n" +
    "        %2$s:toolbarId=\"@+id/toolbar\"\n" +
    "        %2$s:layout_scrollFlags=\"%5$s\"\n" +                  // 5 = ScrollFlags in CollapsingToolbarLayout
    "%6$s" +                                                        // 6 = ScrollInterpolator
    "        %2$s:contentScrim=\"?attr/colorPrimary\">\n" +
    "%7$s" +                                                        // 7 = Optional background image
    "      <android.support.v7.widget.Toolbar\n" +
    "          %1$s:id=\"@+id/toolbar\"\n" +
    "          %1$s:layout_height=\"?attr/actionBarSize\"\n" +
    "          %1$s:layout_width=\"match_parent\">\n" +
    "      </android.support.v7.widget.Toolbar>\n" +
    "    </android.support.design.widget.CollapsingToolbarLayout>\n" +
    "  </android.support.design.widget.AppBarLayout>\n" +
    "  <android.support.v4.widget.NestedScrollView\n" +
    "      %1$s:layout_width=\"match_parent\"\n" +
    "      %1$s:layout_height=\"match_parent\"\n" +
    "%8$s" +                                                        // 8 = behavior_overlapTop
    "%9$s" +                                                        // 9 = scrollY position
    "      %2$s:layout_behavior=\"android.support.design.widget.AppBarLayout$ScrollingViewBehavior\">\n" +
    "%10$s" +                                                       //10 = Page content as xml
    "  </android.support.v4.widget.NestedScrollView>\n" +
    "%11$s" +                                                       //11 = Optional FAB
    "</android.support.design.widget.CoordinatorLayout>\n";

  private static final String TAG_COORDINATOR_WITH_TABS_LAYOUT =    // 1 = Prefix for android namespace
    "<android.support.design.widget.CoordinatorLayout\n" +          // 2 = Prefix for auto namespace
    "%3$s" +                                                        // 3 = Namespace declarations
    "    %1$s:layout_width=\"match_parent\"\n" +
    "    %1$s:layout_height=\"match_parent\">\n" +
    "  <android.support.design.widget.AppBarLayout\n" +
    "      %1$s:id=\"@+id/appbar\"\n" +
    "      %1$s:layout_height=\"wrap_content\"\n" +
    "      %1$s:layout_width=\"match_parent\">\n" +
    "    <android.support.v7.widget.Toolbar\n" +
    "        %1$s:layout_height=\"?attr/actionBarSize\"\n" +
    "        %1$s:layout_width=\"match_parent\"\n" +
    "        %2$s:layout_scrollFlags=\"scroll|enterAlways\">\n" +
    "    </android.support.v7.widget.Toolbar>\n" +
    "    <android.support.design.widget.TabLayout\n" +
    "        %1$s:id=\"@+id/tabs\"\n" +
    "        %1$s:layout_width=\"match_parent\"\n" +
    "        %1$s:layout_height=\"wrap_content\"\n" +
    "%4$s" +                                                        // 4 = ScrollFlags for TabLayout
    "        %2$s:tabMode=\"scrollable\">\n" +
    "    </android.support.design.widget.TabLayout>\n" +
    "  </android.support.design.widget.AppBarLayout>\n" +
    "  <android.support.v4.widget.NestedScrollView\n" +
    "      %1$s:layout_width=\"match_parent\"\n" +
    "      %1$s:layout_height=\"match_parent\"\n" +
    "%5$s" +                                                        // 5 = scrollY position
    "      %2$s:layout_behavior=\"android.support.design.widget.AppBarLayout$ScrollingViewBehavior\">\n" +
    "    %6$s\n" +                                                  // 6 = Page content as xml
    "  </android.support.v4.widget.NestedScrollView>\n" +
    "%7$s" +                                                        // 7 = Optional FAB
    "</android.support.design.widget.CoordinatorLayout>\n";

  private static final String TAG_FLOATING_ACTION_BUTTON =          // 1 = Prefix for android namespace
    "<android.support.design.widget.FloatingActionButton\n" +       // 2 = Prefix for auto namespace
    "    %1$s:layout_height=\"wrap_content\"\n" +
    "    %1$s:layout_width=\"wrap_content\"\n" +
    "    %2$s:layout_anchor=\"@id/appbar\"\n" +
    "    %2$s:layout_anchorGravity=\"bottom|right|end\"\n" +
    "    %1$s:src=\"%3$s\"\n" +                                     // 3 = Image location
    "    %1$s:layout_marginRight=\"16dp\"\n" +
    "    %1$s:clickable=\"true\"\n" +
    "    %2$s:fabSize=\"mini\"/>\n";

  private static final String TAG_IMAGE_VIEW =                      // 1 = Prefix for android namespace
    "<ImageView\n" +
    "    %1$s:id=\"@+id/app_bar_image\"\n" +
    "    %1$s:layout_width=\"match_parent\"\n" +
    "    %1$s:layout_height=\"match_parent\"\n" +
    "%2$s" +                                                        // 2 = Collapse mode
    "    %1$s:src=\"%3$s\"\n" +                                     // 3 = Image src
    "    %1$s:scaleType=\"centerCrop\"/>\n";

  private static final String TAG_TEXTVIEW =
    "<TextView\n" +
    "    android:layout_width=\"match_parent\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:text=\"%1$s\"\n" +                                 // 1 = Text in the TextView
    "    android:padding=\"16dp\"/>";

  private static final String DIALOG_TITLE = "Configure App Bar";
  private static final String DEFAULT_BACKGROUND_IMAGE = "@android:drawable/sym_def_app_icon";
  private static final String DEFAULT_FAB_IMAGE = "@android:drawable/ic_input_add";
  private static final String PREVIEW_PLACEHOLDER_FILE = "preview.xml";
  private static final String DUMMY_TEXT = "This text is present to test the Application Bar. ";
  private static final int DUMMY_REPETITION = 200;
  private static final String PREVIEW_HEADER = "Preview:";
  private static final String RENDER_ERROR = "An error happened during rendering...";
  private static final String OVERLAP_TOP_FORMAT = "%1$s:behavior_overlapTop=\"%2$s\"";
  private static final double FUDGE_FACTOR = 0.95;
  private static final int MIN_WIDTH = 40;
  private static final int MIN_HEIGHT = 60;
  private static final int START_WIDTH = 225;
  private static final int START_HEIGHT = 400;

  private ViewEditor myEditor;
  private JPanel myContentPane;
  private JButton myButtonOK;
  private JButton myButtonCancel;
  private JBLabel myPreview;
  private JCheckBox myCollapsing;
  private JCheckBox myShowBackgroundImage;
  private JCheckBox myFloatingActionButton;
  private JCheckBox myFitStatusBar;
  private JCheckBox myContentOverlap;
  private JCheckBox myWithTabs;
  private JButton myBackgroundImageSelector;
  private JButton myFloatingActionButtonImageSelector;
  private JPanel myPreviewPanel;
  private JBLabel myCollapsedPreview;
  private JBLabel myExpandedPreview;
  private JBLabel myExpandedLabel;
  private JBLabel myCollapsedLabel;
  private JCheckBox myParallax;
  private JTextField myContentOverlapAmount;
  private boolean myWasAccepted;
  private BufferedImage myExpandedImage;
  private BufferedImage myCollapsedImage;
  private String myBackgroundImage;
  private String myFloatingActionButtonImage;

  public AppBarConfigurationDialog(@NotNull ViewEditor editor) {
    myEditor = editor;
    setTitle(DIALOG_TITLE);
    setContentPane(myContentPane);
    setModal(true);
    getRootPane().setDefaultButton(myButtonOK);
    myBackgroundImage = DEFAULT_BACKGROUND_IMAGE;
    myFloatingActionButtonImage = DEFAULT_FAB_IMAGE;
    final ActionListener updatePreviewListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent actionEvent) {
        updateControls();
        generatePreviews();
      }
    };
    myWithTabs.addActionListener(updatePreviewListener);
    myCollapsing.addActionListener(updatePreviewListener);
    myShowBackgroundImage.addActionListener(updatePreviewListener);
    myFloatingActionButton.addActionListener(updatePreviewListener);
    myFitStatusBar.addActionListener(updatePreviewListener);
    myParallax.addActionListener(updatePreviewListener);
    myContentOverlap.addActionListener(updatePreviewListener);
    myContentOverlapAmount.addActionListener(updatePreviewListener);
    ((GridLayoutManager)myPreviewPanel.getLayout()).setRowStretch(0, 2);

    final ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (e.getSource() == myBackgroundImageSelector) {
          String src = myEditor.displayResourceInput(EnumSet.of(ResourceType.DRAWABLE), null);
          if (src != null) {
            myBackgroundImage = src;
            generatePreviews();
          }
        } else if (e.getSource() == myFloatingActionButtonImageSelector) {
          String src = myEditor.displayResourceInput(EnumSet.of(ResourceType.DRAWABLE), null);
          if (src != null) {
            myFloatingActionButtonImage = src;
            generatePreviews();
          }
        } else if (e.getSource() == myButtonOK) {
          onOK();
        } else if (e.getSource() == myButtonCancel ||
                   e.getSource() == myContentPane) {
          onCancel();
        }
      }
    };
    myBackgroundImageSelector.addActionListener(actionListener);
    myFloatingActionButtonImageSelector.addActionListener(actionListener);
    myButtonOK.addActionListener(actionListener);
    myButtonCancel.addActionListener(actionListener);
    myContentPane.registerKeyboardAction(actionListener, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                                         JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myPreviewPanel.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(final ComponentEvent e) {
        updatePreviewImages();
      }
    });

    setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent e) {
        onCancel();
      }
    });
  }

  public boolean open(@NotNull final XmlFile file) {
    myCollapsedPreview.setMinimumSize(new Dimension(START_WIDTH, START_HEIGHT));
    myExpandedPreview.setMinimumSize(new Dimension(START_WIDTH, START_HEIGHT));
    pack();
    myCollapsedPreview.setMinimumSize(null);
    myExpandedPreview.setMinimumSize(null);
    Dimension size = getSize();
    Rectangle screen = getGraphicsConfiguration().getBounds();
    setLocation(screen.x + (screen.width - size.width)/2, screen.y + (screen.height - size.height)/2);
    updateControls();
    generatePreviews();
    setVisible(true);
    if (myWasAccepted) {
      Project project = file.getProject();
      WriteCommandAction action = new WriteCommandAction(project, "Configure App Bar", file) {
        @Override
        protected void run(@NotNull Result result) throws Throwable {
          applyChanges(file);
        }
      };
      action.execute();
    }
    return myWasAccepted;
  }

  private void onOK() {
    myWasAccepted = true;
    dispose();
  }

  private void onCancel() {
    dispose();
  }

  private void updateControls() {
    myShowBackgroundImage.setEnabled(!myWithTabs.isSelected());
    myBackgroundImageSelector.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myFitStatusBar.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myParallax.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myFitStatusBar.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myContentOverlap.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected());
    myContentOverlapAmount.setEnabled(!myWithTabs.isSelected() && myShowBackgroundImage.isSelected() && myContentOverlap.isSelected());
    myFloatingActionButtonImageSelector.setEnabled(myFloatingActionButton.isSelected());
  }

  private void generatePreviews() {
    myCollapsedImage = generatePreview(true);
    myExpandedImage = generatePreview(false);
    updatePreviewImages();
  }

  private BufferedImage generatePreview(boolean collapsed) {
    StringBuilder text = new StringBuilder(DUMMY_REPETITION * DUMMY_TEXT.length());
    for (int i=0; i<DUMMY_REPETITION; i++) {
      text.append(DUMMY_TEXT);
    }
    String content = String.format(TAG_TEXTVIEW, text.toString());

    Map<String, String> namespaces = getNameSpaces(null, collapsed);
    String xml = getXml(content, collapsed, namespaces);
    BufferedImage image = renderImage(xml, myEditor.getConfiguration());
    if (image != null && (image.getHeight() < MIN_HEIGHT || image.getWidth() < MIN_WIDTH)) {
      image = null;
    }
    return image;
  }

  private void updatePreviewImages() {
    if (myExpandedImage == null || myCollapsedImage == null) {
      myPreview.setText(RENDER_ERROR);
    } else {
      myPreview.setText(PREVIEW_HEADER);
    }
    updatePreviewImage(myCollapsedImage, myCollapsedPreview);
    updatePreviewImage(myExpandedImage, myExpandedPreview);
  }

  private void updatePreviewImage(BufferedImage image, JBLabel view) {
    if (image == null) {
      view.setIcon(null);
      return;
    }
    double width = myPreviewPanel.getWidth() / 2;
    double height = myPreviewPanel.getHeight() - myPreview.getHeight() - Math.max(myExpandedLabel.getHeight(), myCollapsedLabel.getHeight());
    if (width < MIN_WIDTH || height < MIN_HEIGHT) {
      view.setIcon(null);
    }
    double scale = Math.min(width / image.getWidth(), height / image.getHeight()) * FUDGE_FACTOR;
    image = ImageUtils.scale(image, scale, scale);
    view.setIcon(new ImageIcon(image));
  }

  private void applyChanges(@NotNull XmlFile file) {
    Map<String, String> namespaces = getNameSpaces(file.getRootTag(), false);
    String xml = getXml(getDesignContent(file), false, namespaces);
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(file.getProject());
    XmlTag tag = elementFactory.createTagFromText(xml);
    if (file.getRootTag() == null) {
      file.add(tag);
    } else {
      file.getRootTag().replace(tag);
    }
  }

  @NotNull
  private String getXml(@NotNull String content, boolean collapsed, @NotNull Map<String, String> namespaces) {
    return myWithTabs.isSelected() ? getXmlWithTabs(content, collapsed, namespaces) : getXmlWithoutTabs(content, collapsed, namespaces);
  }

  @NotNull
  private String getXmlWithoutTabs(@NotNull String content, boolean collapsed, @NotNull Map<String, String> namespaces) {
    return String.format(
      TAG_COORDINATOR_LAYOUT,
      namespaces.get(ANDROID_URI),
      namespaces.get(AUTO_URI),
      formatNamespaces(namespaces),
      getFitsSystemWindows(namespaces),
      getToolbarScrollBehavior(),
      getInterpolator(namespaces),
      getBackgroundImage(namespaces),
      getBehaviorOverlapTop(namespaces),
      getScrollPos(collapsed, namespaces),
      content,
      getFloatingActionButton(namespaces));
  }

  @NotNull
  private String getXmlWithTabs(@NotNull String content, boolean collapsed, @NotNull Map<String, String> namespaces) {
    return String.format(
      TAG_COORDINATOR_WITH_TABS_LAYOUT,
      namespaces.get(ANDROID_URI),
      namespaces.get(AUTO_URI),
      formatNamespaces(namespaces),
      getTabLayoutScroll(namespaces),
      getScrollPos(collapsed, namespaces),
      content,
      getFloatingActionButton(namespaces));
  }

  @NotNull
  private String getFitsSystemWindows(@NotNull Map<String, String> namespaces) {
    if (!myShowBackgroundImage.isSelected() || !myFitStatusBar.isSelected()) {
      return "";
    }
    return String.format("    %1$s:fitsSystemWindows=\"true\"\n",
                         namespaces.get(ANDROID_URI));
  }

  @NotNull
  private String getToolbarScrollBehavior() {
    return myCollapsing.isSelected() ? "scroll|enterAlways|enterAlwaysCollapsed" : "scroll|exitUntilCollapsed";
  }

  @NotNull
  private String getTabLayoutScroll(@NotNull Map<String, String> namespaces) {
    if (!myCollapsing.isSelected()) {
      return "";
    }
    return String.format("        %1$s:layout_scrollFlags=\"scroll|enterAlways\"\n",
                         namespaces.get(AUTO_URI));
  }

  @NotNull
  private String getInterpolator(@NotNull Map<String, String> namespaces) {
    if (!myShowBackgroundImage.isSelected()) {
      return "";
    }
    return String.format("        %2$s:layout_scrollInterpolator=\"@%1$s:anim/decelerate_interpolator\"\n",
                         namespaces.get(ANDROID_URI),
                         namespaces.get(AUTO_URI));
  }

  @NotNull
  private String getBackgroundImage(@NotNull Map<String, String> namespaces) {
    if (!myShowBackgroundImage.isSelected()) {
      return "";
    }
    return String.format(TAG_IMAGE_VIEW,
                         namespaces.get(ANDROID_URI),
                         getBackgroundImageCollapseMode(namespaces),
                         myBackgroundImage);
  }

  @NotNull
  private String getBehaviorOverlapTop(@NotNull Map<String, String> namespaces) {
    if (!myContentOverlap.isSelected()) {
      return "";
    }
    return String.format(OVERLAP_TOP_FORMAT,
                         namespaces.get(AUTO_URI),
                         myContentOverlapAmount.getText());
  }

  @NotNull
  private static String getScrollPos(boolean collapsed, @NotNull Map<String, String> namespaces) {
    if (!collapsed) {
      return "";
    }
    return String.format("        %1$s:scrollY=\"830px\"\n",
                         namespaces.get(TOOLS_URI));
  }

  @NotNull
  private String getBackgroundImageCollapseMode(@NotNull Map<String, String> namespaces) {
    if (myParallax.isSelected()) {
      return "";
    }
    return String.format("    %1$s:layout_collapseMode=\"parallax\"\n",
                         namespaces.get(AUTO_URI));
  }

  @NotNull
  private String getFloatingActionButton(@NotNull Map<String, String> namespaces) {
    if (!myFloatingActionButton.isSelected()) {
      return "";
    }
    return String.format(TAG_FLOATING_ACTION_BUTTON, namespaces.get(ANDROID_URI),
                         namespaces.get(AUTO_URI),
                         myFloatingActionButtonImage);
  }

  @NotNull
  private static Map<String, String> getNameSpaces(@Nullable XmlTag root, boolean includeToolsNamespace) {
    Map<String, String> reverse = new HashMap<String, String>();
    if (root != null) {
      Map<String, String> namespaces = root.getLocalNamespaceDeclarations();
      for (String prefix : namespaces.keySet()) {
        reverse.put(namespaces.get(prefix), prefix);
      }
    }
    if (!reverse.containsKey(ANDROID_URI)) {
      reverse.put(ANDROID_URI, ANDROID_NS_NAME);
    }
    if (!reverse.containsKey(AUTO_URI)) {
      reverse.put(AUTO_URI, APP_PREFIX);
    }
    if (includeToolsNamespace && !reverse.containsKey(TOOLS_URI)) {
      reverse.put(TOOLS_URI, TOOLS_PREFIX);
    }
    return reverse;
  }

  @NotNull
  private static String formatNamespaces(@NotNull Map<String, String> namespaces) {
    StringBuilder result = new StringBuilder();
    for (String ns : namespaces.keySet()) {
      String prefix = namespaces.get(ns);
      result.append(String.format("    xmlns:%1$s=\"%2$s\"\n", prefix, ns));
    }
    return result.toString();
  }

  // If AppBarLayout is applied a second time it should replace the current AppBarLayout:
  @NotNull
  private static String getDesignContent(@NotNull XmlFile file) {
    XmlTag content = file.getRootTag();
    if (content != null && content.getName().equals(COORDINATOR_LAYOUT)) {
      XmlTag root = content;
      content = null;
      for (XmlTag tag : root.getSubTags()) {
        if (!tag.getName().equals(APP_BAR_LAYOUT) &&
            !tag.getName().equals(FLOATING_ACTION_BUTTON)) {
          if (tag.getName().equals(CLASS_NESTED_SCROLL_VIEW)) {
            content = tag.getSubTags().length > 0 ? tag.getSubTags()[0] : null;
          } else {
            content = tag;
          }
          break;
        }
      }
    }
    if (content == null) {
      return "";
    }
    // Remove any xmlns: attributes since this element will be added into the document
    for (XmlAttribute attribute : content.getAttributes()) {
      if (attribute != null && attribute.getName().startsWith(XMLNS_PREFIX)) {
        attribute.delete();
      }
    }
    return content.getText();
  }

  private static BufferedImage renderImage(@NotNull String xml, @NotNull Configuration configuration) {
    AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
    if (facet == null) {
      return null;
    }
    Project project = configuration.getModule().getProject();
    PsiFile file = PsiFileFactory.getInstance(project).createFileFromText(PREVIEW_PLACEHOLDER_FILE, XmlFileType.INSTANCE, xml);
    RenderService renderService = RenderService.get(facet);
    RenderLogger logger = renderService.createLogger();
    final RenderTask task = renderService.createTask(file, configuration, logger, null);
    RenderResult result = null;
    if (task != null) {
      task.setRenderingMode(SessionParams.RenderingMode.NORMAL);
      task.setFolderType(ResourceFolderType.LAYOUT);
      result = task.render();
      task.dispose();
    }
    if (result == null) {
      return null;
    }
    return result.getRenderedImage();
  }
}
