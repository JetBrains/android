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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.AndroidVersion;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repository.IdDisplay;
import com.android.sdklib.repository.targets.SystemImage;
import com.android.tools.idea.npw.ChooseApiLevelDialog;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Displays information about a {@link SystemImage}, including its
 * launch graphic, platform and API level, and target CPU architecture.
 */
public class SystemImagePreview {
  private JBLabel myReleaseName;
  private JBLabel myReleaseIcon;
  private JBLabel myApiLevel;
  private JBLabel myAndroidVersion;
  private JBLabel myAbi;
  private HyperlinkLabel myDocumentationLink;
  private JBLabel myVendor;
  private JPanel myRootPanel;
  private JPanel myMainPanel;
  private JSeparator mySeparator;
  private HaxmAlert myHaxmAlert;
  private JBLabel myRecommendedExplanation;
  private SystemImageDescription myImageDescription;
  private Disposable myDisposable;
  ApiLevelHyperlinkListener myApiLevelListener = new ApiLevelHyperlinkListener();

  private static final String NO_SYSTEM_IMAGE_SELECTED = "No System Image Selected";
  private static final String MAIN_CONTENT = "main";
  private static final String NO_IMAGE_CONTENT = "none";

  public SystemImagePreview(@Nullable Disposable disposable) {
    myDisposable = disposable;
    myRootPanel.setLayout(new CardLayout());
    myReleaseName.setFont(AvdWizardUtils.TITLE_FONT);
    myApiLevel.setFont(AvdWizardUtils.TITLE_FONT);
    myAndroidVersion.setFont(AvdWizardUtils.TITLE_FONT);
    myVendor.setFont(AvdWizardUtils.TITLE_FONT);
    myDocumentationLink.setOpaque(false);
    myAbi.setFont(AvdWizardUtils.TITLE_FONT);
    myRootPanel.add(myMainPanel, MAIN_CONTENT);
    JPanel nonePanel = new JPanel(new BorderLayout());
    JBLabel noneLabel = new JBLabel(NO_SYSTEM_IMAGE_SELECTED);
    noneLabel.setHorizontalAlignment(SwingConstants.CENTER);
    nonePanel.add(noneLabel, BorderLayout.CENTER);
    nonePanel.setBackground(JBColor.WHITE);
    myRootPanel.add(nonePanel, NO_IMAGE_CONTENT);
    myMainPanel.setBackground(JBColor.WHITE);
    mySeparator.setForeground(JBColor.BLACK);

    myDocumentationLink.addHyperlinkListener(myApiLevelListener);
    myDocumentationLink.setHtmlText("See the <a>API level distribution chart</a>");
  }

  /**
   * Set the image to display.
   */
  public void setImage(@Nullable SystemImageDescription image) {
    myImageDescription = image;
    myHaxmAlert.setSystemImageDescription(image);
    ((CardLayout)myRootPanel.getLayout()).show(myRootPanel, NO_IMAGE_CONTENT);

    if (image != null) {
      ((CardLayout)myRootPanel.getLayout()).show(myRootPanel, MAIN_CONTENT);
      AndroidVersion version = image.getVersion();
      if (version == null) {
        return;
      }
      int apiLevel = version.getApiLevel();
      myApiLevelListener.setApiLevel(apiLevel);
      String codeName = SdkVersionInfo.getCodeName(myImageDescription.getVersion().getApiLevel());
      if (codeName != null) {
        myReleaseName.setText(codeName);
      }
      Icon icon = getIcon(codeName);
      if (icon != null) {
        myReleaseIcon.setIcon(icon);
      }
      myApiLevel.setText(image.getVersion().getApiString());
      myAndroidVersion.setVisible(!image.getVersion().isPreview());
      myAndroidVersion.setText(SdkVersionInfo.getVersionString(apiLevel));
      String vendorName;
      IdDisplay tag = myImageDescription.getTag();
      if (tag.getId().equals("android-wear") || tag.getId().equals("android-tv")) {
        vendorName = "Android";
      } else {
        vendorName = myImageDescription.getVendor();
      }
      myVendor.setText("<html>" + vendorName + "</html>");
      myAbi.setText(myImageDescription.getAbiType());
    }
  }

  public void showExplanationForRecommended(boolean show) {
    myRecommendedExplanation.setVisible(show);
  }

  /**
   * Get the launch graphic which corresponds with the given codename, or a question mark
   * if we don't have an icon for that codename.
   */
  @Nullable
  public static Icon getIcon(@Nullable String codename) {
    if (codename == null) {
      return null;
    }
    Icon icon = null;
    try {
      icon = IconLoader.findIcon(String.format("/icons/versions/%1$s.png", codename), AndroidIcons.class);
    } catch (RuntimeException ignored) {
    }
    if (icon != null) {
      return icon;
    }
    try {
      icon = IconLoader.findIcon("/icons/versions/Default.png", AndroidIcons.class);
    } catch (RuntimeException ignored) {
    }
    if (icon != null) {
      return icon;
    }
    int size = JBUI.scale(128);
    Image image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics g = image.getGraphics();
    GraphicsUtil.setupAntialiasing(g);
    GraphicsUtil.setupAAPainting(g);
    Font f = UIUtil.getLabelFont();
    Font font = new Font(f.getName(), f.getStyle() | Font.BOLD, JBUI.scale(100));
    g.setColor(JBColor.background());
    g.fillRect(0, 0, size, size);
    g.setColor(JBColor.foreground());
    g.setFont(font);
    int height = g.getFontMetrics().getHeight();
    int width = g.getFontMetrics().stringWidth("?");
    g.drawString("?", (size - width) / 2, height + (size - height) / 2);
    return new ImageIcon(image);
  }

  private void createUIComponents() {
    myHaxmAlert = new HaxmAlert();
    myHaxmAlert.setSystemImageDescription(myImageDescription);
  }

  private class ApiLevelHyperlinkListener extends HyperlinkAdapter {
    private int myApiLevel = -1;

    @Override
    protected void hyperlinkActivated(HyperlinkEvent e) {
      ChooseApiLevelDialog dialog = new ChooseApiLevelDialog(null, myApiLevel) {
        @NotNull
        @Override
        protected Action[] createActions() {
          Action close = getCancelAction();
          close.putValue(Action.NAME, "Close");
          return new Action[] {close};
        }
      };
      Disposer.register(myDisposable, dialog.getDisposable());
      dialog.show();
    }

    public void setApiLevel(int apiLevel) {
      myApiLevel = apiLevel;
    }
  }
}
