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
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.tools.idea.sdk.wizard.LicenseAgreementStep;
import com.android.tools.idea.welcome.install.*;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.*;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.View;
import java.awt.*;
import java.io.*;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
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
    if (isIntel()) {
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
          else if (Haxm.canRun()) {
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

    Window window = SwingUtilities.getWindowAncestor(this);
    if (window != null) {
      window.pack();
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

  private boolean isIntel() {
    if (SystemInfo.isMac) {
      return true;
    } else if (SystemInfo.isLinux) {
      BufferedReader br = null;
      try {
        br = new BufferedReader(new FileReader("/proc/cpuinfo"));
        String line = br.readLine();
        while (br.ready()) {
          if (line.startsWith("vendor_id") && line.endsWith("GenuineIntel")) {
            return true;
          }
          line = br.readLine();
        }
      } catch (FileNotFoundException e) {
        Logger.getInstance(getClass()).warn("/proc/cpuinfo not found, assuming non-intel CPU");
        return false;
      } catch (IOException e) {
        Logger.getInstance(getClass()).warn("Error reading /proc/cpuinfo, assuming non-intel CPU");
        return false;
      } finally {
        Closeables.closeQuietly(br);
      }
      return false;
    } else if (SystemInfo.isWindows) {
      String id = System.getenv().get("PROCESSOR_IDENTIFIER");
      return id != null && id.contains("GenuineIntel");
    }
    return false;
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
        FullRevision current = new FullRevision(1, 1, 1);
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

  private class HaxmPath extends DynamicWizardPath {
    DynamicWizardHost myHost;

    public HaxmPath(DynamicWizardHost host) {
      myHost = host;
    }

    @Override
    protected void init() {
      ScopedStateStore.Key<Boolean> canShow = ScopedStateStore.createKey("ShowHaxmSteps",
                                                                         ScopedStateStore.Scope.PATH, Boolean.class);
      myState.put(canShow, true);
      Haxm haxm = new Haxm(getState(), canShow);
      for (IPkgDesc desc : haxm.getRequiredSdkPackages(null)) {
        myState.listPush(WizardConstants.INSTALL_REQUESTS_KEY, desc);
      }

      for (DynamicWizardStep step : haxm.createSteps()) {
        addStep(step);
      }
      addStep(new LicenseAgreementStep(getWizard().getDisposable()));
      ProgressStep progressStep = new SetupProgressStep(getWizard().getDisposable(), haxm, myHost);
      addStep(progressStep);
      haxm.init(progressStep);
    }

    @NotNull
    @Override
    public String getPathName() {
      return "Haxm Path";
    }

    @Override
    public boolean performFinishingActions() {
      return false;
    }
  }


  private class HaxmWizard extends DynamicWizard {

    public HaxmWizard() {
      super(null, null, "HAXM");
      HaxmPath path = new HaxmPath(myHost);
      addPath(path);
    }

    @Override
    public void performFinishingActions() {
      // Nothing. Handled by SetupProgressStep.
    }

    @NotNull
    @Override
    protected String getProgressTitle() {
      return "Finishing install...";
    }

    @Override
    protected String getWizardActionDescription() {
      return "HAXM Installation";
    }
  }

  private static class SetupProgressStep extends ProgressStep {
    private Haxm myHaxm;
    private final AtomicBoolean myIsBusy = new AtomicBoolean(false);
    private DynamicWizardHost myHost;

    public SetupProgressStep(Disposable parentDisposable, Haxm haxm, DynamicWizardHost host) {
      super(parentDisposable);
      myHaxm = haxm;
      myHost = host;
    }

    @Override
    public boolean canGoNext() {
      return false;
    }

    @Override
    protected void execute() {
      myIsBusy.set(true);
      myHost.runSensitiveOperation(getProgressIndicator(), true, new Runnable() {
        @Override
        public void run() {
          try {
            setupHaxm();
          }
          catch (Exception e) {
            Logger.getInstance(getClass()).error(e);
            showConsole();
            print(e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
          }
          finally {
            myIsBusy.set(false);
          }
        }
      });
    }

    @Override
    public boolean canGoPrevious() {
      return false;
    }

    private void setupHaxm() throws IOException {
      final InstallContext installContext = new InstallContext(
        FileUtil.createTempDirectory("AndroidStudio", "Haxm", true), this);
      final File destination = AndroidSdkUtils.tryToChooseAndroidSdk().getLocation();

      final Collection<? extends InstallableComponent> selectedComponents = Lists.newArrayList(myHaxm);
      installContext.print("Looking for SDK updates...\n", ConsoleViewContentType.NORMAL_OUTPUT);

      // Assume install and configure take approximately the same time; assign 0.5 progressRatio to each
      InstallComponentsOperation install =
        new InstallComponentsOperation(installContext, selectedComponents, new ComponentInstaller(null), 0.5);

      try {
        install.then(InstallOperation.wrap(installContext, new Function<File, File>() {
          @Override
          public File apply(@Nullable File input) {
            myHaxm.configure(installContext, input);
            return input;
          }
        }, 0.5)).execute(destination);
      }
      catch (InstallationCancelledException e) {
        installContext.print("Android Studio setup was canceled", ConsoleViewContentType.ERROR_OUTPUT);
      }
      catch (WizardException e) {
        throw new RuntimeException(e);
      }
      installContext.print("Done", ConsoleViewContentType.NORMAL_OUTPUT);
    }
  }
}
