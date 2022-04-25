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

import com.android.annotations.NonNull;
import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.devices.Abi;
import com.android.tools.analytics.CommonMetricsData;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.ProductDetails;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import java.awt.*;

import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.NONE;

/**
 * Component for displaying an alert on the installation state of HAXM/KVM.
 */
public class HaxmAlert extends JPanel {
  private JBLabel myWarningMessage;
  private HyperlinkLabel myErrorInstructionsLink;
  private HyperlinkListener myErrorLinkListener;
  private SystemImageDescription myImageDescription;
  private AccelerationErrorCode myAccelerationErrorCode;
  private Logger myLogger;

  public HaxmAlert() {
    myErrorInstructionsLink = new HyperlinkLabel();
    myWarningMessage = new JBLabel() {
      @Override
      public Dimension getPreferredSize() {
        // Since this contains auto-wrapped text, the preferred height will not be set until repaint(). The below will set it as soon
        // as the actual width is known. This allows the wizard dialog to be set to the correct size even before this step is shown.
        final View view = (View)getClientProperty("html");
        Component parent = getParent();
        if (view != null && parent != null && parent.getWidth() > 0 && parent.getWidth() != (int)view.getPreferredSpan(View.X_AXIS)) {
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

  @VisibleForTesting
  static String getWarningTextForX86HostsUsingNonX86Image(@NonNull SystemImageDescription description,
                                                          ProductDetails.CpuArchitecture arch) {
    Abi abi = Abi.getEnum(description.getAbiType());
    boolean isX86Host = arch == ProductDetails.CpuArchitecture.X86 || arch == ProductDetails.CpuArchitecture.X86_64;
    if (isX86Host && abi != Abi.X86 && abi != Abi.X86_64) {
      return "Consider using an x86 system image on an x86 host for better emulation performance.";
    }
    return null;
  }

  private void refresh() {
    if (myImageDescription == null) {
      setVisible(false);
      return;
    }

    ListenableFuture<AccelerationErrorCode> accelerationError = getAccelerationState(false);
    Futures.addCallback(accelerationError, new FutureCallback<AccelerationErrorCode>() {
      @Override
      public void onSuccess(AccelerationErrorCode result) {
        myAccelerationErrorCode = result;

        boolean hasLink = false;
        StringBuilder warningTextBuilder = new StringBuilder();

        if (result != AccelerationErrorCode.ALREADY_INSTALLED) {
          hasLink = true;
          warningTextBuilder.append(result.getProblem());
          warningTextBuilder.append("<br>");
          myErrorInstructionsLink.setHyperlinkText(result.getSolution().getDescription());
          if (myErrorLinkListener != null) {
            myErrorInstructionsLink.removeHyperlinkListener(myErrorLinkListener);
          }
          final Runnable action = AccelerationErrorSolution.getActionForFix(result, null, () -> refresh(), null);
          myErrorLinkListener = new HyperlinkAdapter() {
              @Override
              protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
                action.run();
              }
            };
          myErrorInstructionsLink.addHyperlinkListener(myErrorLinkListener);
          myErrorInstructionsLink.setToolTipText(result.getSolution() != NONE ? result.getSolutionMessage() : null);
        }

        if (myImageDescription != null) {
          if (myImageDescription.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
            if (warningTextBuilder.length() > 0) {
              warningTextBuilder.append("<br>");
            }
            warningTextBuilder.append("This API Level is Deprecated<br>");
          }

          String nonX86ImageWarning = getWarningTextForX86HostsUsingNonX86Image(myImageDescription, CommonMetricsData.getOsArchitecture());
          if (nonX86ImageWarning != null) {
            if (warningTextBuilder.length() > 0) {
              warningTextBuilder.append("<br>");
            }
            warningTextBuilder.append(nonX86ImageWarning + "<br>");
          }

          if (!SystemImageDescription.TAGS_WITH_GOOGLE_API.contains(myImageDescription.getTag())) {
            if (warningTextBuilder.length() > 0) {
              warningTextBuilder.append("<br>");
            }
            warningTextBuilder.append("Consider using a system image with Google APIs to enable testing with Google Play Services.");
          }
        }

        String warningText = warningTextBuilder.toString();
        if (!warningText.isEmpty()) {
          warningTextBuilder.insert(0, "<html>");
          warningTextBuilder.append("</html>");
          myWarningMessage.setText(warningTextBuilder.toString().replaceAll("\n", "<br>"));
          setVisible(true);
          myErrorInstructionsLink.setVisible(hasLink);
        } else {
          setVisible(false);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        if (myLogger == null) {
          myLogger = Logger.getInstance(HaxmAlert.class);
        }
        myLogger.warn("Check for emulation acceleration failed", t);
      }
    }, EdtExecutorService.getInstance());
  }

  @NotNull
  public ListenableFuture<AccelerationErrorCode> getAccelerationState(boolean forceRefresh) {
    if (myAccelerationErrorCode == null || forceRefresh) {
      return computeAccelerationState();
    }
    return Futures.immediateFuture(myAccelerationErrorCode);
  }

  @NotNull
  private static ListenableFuture<AccelerationErrorCode> computeAccelerationState() {
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    return manager.checkAccelerationAsync();
  }
}
