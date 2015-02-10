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

import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.FullRevision;
import com.android.tools.idea.stats.Distribution;
import com.android.tools.idea.stats.DistributionService;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Displays information about a {@link com.android.sdklib.SystemImage}, including its
 * launch graphic, platform and API level, and target CPU architecture.
 */
public class SystemImagePreview {
  private JBLabel myReleaseName;
  private JBLabel myReleaseIcon;
  private JBLabel myApiLevel;
  private JBLabel myAndroidVersion;
  private JBLabel myAbi;
  private JBLabel myWarningMessage;
  private HyperlinkLabel myErrorInstructionsLink;
  private HyperlinkLabel myDocumentationLink;
  private JBLabel myVendor;
  private JPanel myRootPanel;
  private JPanel myMainPanel;
  private JSeparator mySeparator;
  private AvdWizardConstants.SystemImageDescription myImageDescription;

  private static final String NO_SYSTEM_IMAGE_SELECTED = "No System Image Selected";
  private static final String MAIN_CONTENT = "main";
  private static final String NO_IMAGE_CONTENT = "none";

  public SystemImagePreview() {
    myRootPanel.setLayout(new CardLayout());
    myReleaseName.setFont(AvdWizardConstants.TITLE_FONT);
    myApiLevel.setFont(AvdWizardConstants.TITLE_FONT);
    myAndroidVersion.setFont(AvdWizardConstants.TITLE_FONT);
    myVendor.setFont(AvdWizardConstants.TITLE_FONT);
    myErrorInstructionsLink.setOpaque(false);
    myDocumentationLink.setOpaque(false);
    myWarningMessage.setFont(AvdWizardConstants.TITLE_FONT);
    myWarningMessage.setForeground(JBColor.RED);
    myAbi.setFont(AvdWizardConstants.TITLE_FONT);
    myRootPanel.add(myMainPanel, MAIN_CONTENT);
    JPanel nonePanel = new JPanel(new BorderLayout());
    JBLabel noneLabel = new JBLabel(NO_SYSTEM_IMAGE_SELECTED);
    noneLabel.setHorizontalAlignment(JBLabel.CENTER);
    nonePanel.add(noneLabel, BorderLayout.CENTER);
    nonePanel.setBackground(JBColor.WHITE);
    myRootPanel.add(nonePanel, NO_IMAGE_CONTENT);
    myMainPanel.setBackground(JBColor.WHITE);
    mySeparator.setForeground(JBColor.BLACK);
  }

  /**
   * Set the image to display.
   */
  public void setImage(@Nullable AvdWizardConstants.SystemImageDescription image) {
    if (image == null || !image.isRemote()) {
      myImageDescription = image;
      ((CardLayout)myRootPanel.getLayout()).show(myRootPanel, NO_IMAGE_CONTENT);
    }
    if (image != null && !image.isRemote()) {
      ((CardLayout)myRootPanel.getLayout()).show(myRootPanel, MAIN_CONTENT);
      Distribution distribution = DistributionService.getInstance().getDistributionForApiLevel(image.getVersion().getApiLevel());
      String codeName = getCodeName(myImageDescription);
      if (codeName != null) {
        myReleaseName.setText(codeName);
      }
      Icon icon = getIcon(codeName);
      if (icon != null) {
        myReleaseIcon.setIcon(icon);
      }
      if (distribution != null) {
        myDocumentationLink.setHtmlText("<a>? - See documentation for Android " + distribution.getVersion().toShortString() + " APIs</a>");
        myDocumentationLink.setHyperlinkTarget(distribution.getUrl());
      }
      myApiLevel.setText(myImageDescription.getVersion().getApiString());
      myAndroidVersion.setText(myImageDescription.getVersionName());
      String vendorName;
      String tag = myImageDescription.getTag().getId();
      if (tag.equals("android-wear") || tag.equals("android-tv")) {
        vendorName = "Android";
      } else {
        vendorName = myImageDescription.getVendor();
      }
      myVendor.setText("<html>" + vendorName + "</html>");
      myAbi.setText(myImageDescription.getAbiType());
      StringBuilder myWarningText = new StringBuilder("<html>");
      if (myImageDescription.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
        myWarningText.append("This API Level is Deprecated<br>");
      }
      HaxmState haxmState = getHaxmState(false);
      if (haxmState == HaxmState.NOT_INSTALLED) {
        if (!myImageDescription.getAbiType().startsWith(Abi.X86.toString())) {
          myWarningText.append("Consider installing HAXM<br>");
          myWarningText.append("for better emulation speed");
        } else {
          myWarningText.append("HAXM is required for running<br>");
          myWarningText.append("x86 System Images<br>");
        }
        myErrorInstructionsLink.setHtmlText("<a>HAXM installation instructions</a>");
        myErrorInstructionsLink.setHyperlinkTarget("http://developer.android.com/tools/devices/emulator.html#acceleration");
        myErrorInstructionsLink.setVisible(true);
      } else {
        if (haxmState == HaxmState.NOT_LATEST) {
          myWarningText.append("Newer HAXM Version Available");
          myErrorInstructionsLink.setVisible(true);
          myErrorInstructionsLink.setHtmlText("<a>HAXM installation instructions</a>");
          myErrorInstructionsLink.setHyperlinkTarget("http://developer.android.com/tools/devices/emulator.html#acceleration");
        } else {
          myErrorInstructionsLink.setVisible(false);
        }
      }
      myWarningMessage.setText(myWarningText.toString());
    }
  }


  /**
   * @return the codename for the given System Image's API level
   */
  @Nullable
  public static String getCodeName(@NotNull AvdWizardConstants.SystemImageDescription description) {
    return SdkVersionInfo.getCodeName(description.getVersion().getApiLevel());
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
    int size = 128;
    Image image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics g = image.getGraphics();
    GraphicsUtil.setupAntialiasing(g);
    GraphicsUtil.setupAAPainting(g);
    Font f = UIUtil.getLabelFont();
    Font font = new Font(f.getName(), f.getStyle() | Font.BOLD, 100);
    g.setColor(JBColor.background());
    g.fillRect(0, 0, size, size);
    g.setColor(JBColor.foreground());
    g.setFont(font);
    int height = g.getFontMetrics().getHeight();
    int width = g.getFontMetrics().stringWidth("?");
    g.drawString("?", (size - width) / 2, height + (size - height) / 2);
    return new ImageIcon(image);
  }

  enum HaxmState { NOT_INITIALIZED, INSTALLED, NOT_INSTALLED, NOT_LATEST }
  private static HaxmState ourHaxmState = HaxmState.NOT_INITIALIZED;

  private static HaxmState getHaxmState(boolean forceRefresh) {
    if (ourHaxmState == HaxmState.NOT_INITIALIZED || forceRefresh) {
      ourHaxmState = computeHaxmState();
    }
    return ourHaxmState;
  }

  private static HaxmState computeHaxmState() {
    try {
      if (SystemInfo.isMac) {
        @SuppressWarnings("SpellCheckingInspection")
        String output = ExecUtil.execAndReadLine("/usr/sbin/kextstat", "-l", "-b", "com.intel.kext.intelhaxm");
        if (output != null && !output.isEmpty()) {
          Pattern pattern = Pattern.compile("com\\.intel\\.kext\\.intelhaxm( \\((.+)\\))?");
          Matcher matcher = pattern.matcher(output);
          if (matcher.find()) {
            if (matcher.groupCount() >= 2) {
              String version = matcher.group(2);
              try {
                FullRevision revision = FullRevision.parseRevision(version);
                FullRevision current = new FullRevision(1, 1, 1);
                if (revision.compareTo(current) < 0) {
                  // We have the new version number, as well as the currently installed
                  // version number here, which we could use to make a better error message.
                  // However, these versions do not correspond to the version number we show
                  // in the SDK manager (e.g. in the SDK version manager we show "5"
                  // and the corresponding kernel stat version number is 1.1.1.
                  return HaxmState.NOT_LATEST;
                }
              }
              catch (NumberFormatException e) {
                // Some unexpected new (or old?) format for HAXM versions; ignore since we
                // can't check whether it is up to date.
              }
            }
            return HaxmState.INSTALLED;
          }
        }
        return HaxmState.NOT_INSTALLED;
      } else if (SystemInfo.isWindows) {
        @SuppressWarnings("SpellCheckingInspection") ProcessOutput
          processOutput = ExecUtil.execAndGetOutput(ImmutableList.of("sc", "query", "intelhaxm"), null);
        return Iterables.all(processOutput.getStdoutLines(), new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return input == null || !input.contains("does not exist");
          }
        }) ? HaxmState.INSTALLED : HaxmState.NOT_INSTALLED;
      } else if (SystemInfo.isUnix) {
        ProcessOutput processOutput = ExecUtil.execAndGetOutput(ImmutableList.of("kvm-ok"), null);
        return Iterables.any(processOutput.getStdoutLines(), new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return input != null && input.contains("KVM acceleration can be used");
          }
        }) ? HaxmState.INSTALLED : HaxmState.NOT_INSTALLED;
      } else {
        assert !SystemInfo.isLinux; // should be covered by SystemInfo.isUnix
        return HaxmState.NOT_INSTALLED;
      }
    } catch (ExecutionException e) {
      return HaxmState.NOT_INSTALLED;
    }
  }
}
