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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.tools.idea.welcome.install.*;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Closeables;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import java.awt.*;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Component for displaying an alert on the installation state of HAXM/KVM.
 */
public class HaxmAlert extends JPanel {
  private JBLabel myWarningMessage;
  private HyperlinkLabel myErrorInstructionsLink;
  private HyperlinkListener myErrorLinkListener;
  SystemImageDescription myImageDescription;

  private static final IdDisplay GOOGLE_APIS_TAG = new IdDisplay("google_apis", "");

  public HaxmAlert() {
    myErrorInstructionsLink = new HyperlinkLabel();
    myWarningMessage = new JBLabel() {
      @Override
      public Dimension getPreferredSize() {
        // Since this contains auto-wrapped text, the preferred height will not be set until repaint(). The below will set it as soon
        // as the actual width is known. This allows the wizard dialog to be set to the correct size even before this step is shown.
        final View view = (View)getClientProperty("html");
        Component parent = getParent();
        if (view != null && parent != null && parent.getWidth() > 0) {
          view.setSize(parent.getWidth(), 0);
          return new Dimension((int)view.getPreferredSpan(View.X_AXIS), (int)view.getPreferredSpan(View.Y_AXIS));
        }
        return super.getPreferredSize();
      }
    };
    this.setLayout(new GridLayoutManager(2, 1));
    GridConstraints constraints = new GridConstraints();
    constraints.setAnchor(GridConstraints.ANCHOR_WEST);
    add(myWarningMessage, constraints);
    constraints.setRow(1);
    add(myErrorInstructionsLink, constraints);
    myErrorInstructionsLink.setOpaque(false);
    myWarningMessage.setForeground(JBColor.RED);
    myWarningMessage.setHorizontalAlignment(SwingConstants.LEFT);
    setOpaque(false);
    this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Recommendation"),
                                                      BorderFactory.createEmptyBorder(0, 5, 3, 5)));
  }

  public void setSystemImageDescription(SystemImageDescription description) {
    myImageDescription = description;
    refresh();
  }

  private void refresh() {
    if (myImageDescription == null) {
      setVisible(false);
      return;
    }

    boolean hasLink = false;
    StringBuilder warningTextBuilder = new StringBuilder();

    if (Haxm.canRun() || SystemInfo.isUnix) {
      if (!myImageDescription.getAbiType().startsWith(Abi.X86.toString())) {
        warningTextBuilder.append("Consider using an x86 system image for better emulation performance.<br>");
      } else {
        HaxmState haxmState = getHaxmState(false);
        if (haxmState == HaxmState.NOT_INSTALLED) {
          if (SystemInfo.isLinux) {
            warningTextBuilder.append("Enable Linux KVM for better emulation performance.<br>");
            myErrorInstructionsLink.setHyperlinkTarget(FirstRunWizardDefaults.KVM_LINUX_INSTALL_URL);
            myErrorInstructionsLink.setHtmlText("<a>KVM Instructions</a>");
            if (myErrorLinkListener != null) {
              myErrorInstructionsLink.removeHyperlinkListener(myErrorLinkListener);
            }
            hasLink = true;
          }
          else {
            warningTextBuilder.append("Install Intel HAXM for better emulation performance.<br>");
            setupDownloadLink();
            hasLink = true;
          }
        }
        else if (haxmState == HaxmState.NOT_LATEST) {
          warningTextBuilder.append("Newer HAXM Version Available<br>");
          setupDownloadLink();
          hasLink = true;
        }
      }
    }

    if (myImageDescription.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
      warningTextBuilder.append("This API Level is Deprecated<br>");
    }

    if (!GOOGLE_APIS_TAG.equals(myImageDescription.getTag())) {
      warningTextBuilder.append("Consider using a system image with Google APIs to enable testing with Google Play Services.");
    }

    String warningText = warningTextBuilder.toString();
    if (!warningText.isEmpty()) {
      warningTextBuilder.insert(0, "<html>");
      warningTextBuilder.append("</html>");
      myWarningMessage.setText(warningTextBuilder.toString());
      setVisible(true);
      myErrorInstructionsLink.setVisible(hasLink);
    } else {
      setVisible(false);
    }
  }

  private void setupDownloadLink() {
    myErrorInstructionsLink.setHyperlinkTarget(null);
    myErrorInstructionsLink.setHtmlText("<a>Download and install HAXM<a>");
    if (myErrorLinkListener != null) {
      myErrorInstructionsLink.removeHyperlinkListener(myErrorLinkListener);
    }
    myErrorLinkListener =
      new HyperlinkAdapter() {
        @Override
        protected void hyperlinkActivated(HyperlinkEvent e) {
          HaxmWizard wizard = new HaxmWizard();
          wizard.init();
          wizard.show();
          getHaxmState(true);
          refresh();
        }
      };
    myErrorInstructionsLink.addHyperlinkListener(myErrorLinkListener);
  }

  public enum HaxmState { NOT_INITIALIZED, INSTALLED, NOT_INSTALLED, NOT_LATEST }
  private static HaxmState ourHaxmState = HaxmState.NOT_INITIALIZED;

  public static HaxmState getHaxmState(boolean forceRefresh) {
    if (ourHaxmState == HaxmState.NOT_INITIALIZED || forceRefresh) {
      ourHaxmState = computeHaxmState();
    }
    return ourHaxmState;
  }

  private static HaxmState computeHaxmState() {
    boolean found = false;
    try {
      if (SystemInfo.isMac) {
        @SuppressWarnings("SpellCheckingInspection")
        String output = ExecUtil.execAndReadLine("/usr/sbin/kextstat", "-l", "-b", "com.intel.kext.intelhaxm");
        if (output != null && !output.isEmpty()) {
          Pattern pattern = Pattern.compile("com\\.intel\\.kext\\.intelhaxm( \\((.+)\\))?");
          Matcher matcher = pattern.matcher(output);
          if (matcher.find()) {
            found = true;
          }
        }
      } else if (SystemInfo.isWindows) {
        @SuppressWarnings("SpellCheckingInspection") ProcessOutput
          processOutput = ExecUtil.execAndGetOutput(ImmutableList.of("sc", "query", "intelhaxm"), null);
        found = Iterables.all(processOutput.getStdoutLines(), new Predicate<String>() {
          @Override
          public boolean apply(String input) {
            return input == null || !input.contains("does not exist");
          }
        });
      } else if (SystemInfo.isUnix) {
        File kvm = new File("/dev/kvm");
        return kvm.exists() ? HaxmState.INSTALLED : HaxmState.NOT_INSTALLED;
      } else {
        assert !SystemInfo.isLinux; // should be covered by SystemInfo.isUnix
        return HaxmState.NOT_INSTALLED;
      }
    } catch (ExecutionException e) {
      return HaxmState.NOT_INSTALLED;
    }

    if (found) {
      try {
        FullRevision revision = Haxm.getInstalledVersion(AndroidSdkUtils.tryToChooseAndroidSdk().getLocation());
        // TODO: Use value from remote repository instead
        FullRevision current = new FullRevision(1, 1, 5);
        if (revision.compareTo(current) < 0) {
          // We have the new version number, as well as the currently installed
          // version number here, which we could use to make a better error message.
          // However, these versions do not correspond to the version number we show
          // in the SDK manager (e.g. in the SDK version manager we show "5"
          // and the corresponding kernel stat version number is 1.1.1.
          return HaxmState.NOT_LATEST;
        }
      } catch (WizardException e) {
        return HaxmState.NOT_INSTALLED;
      }
      return HaxmState.INSTALLED;
    }
    return HaxmState.NOT_INSTALLED;
  }

}
