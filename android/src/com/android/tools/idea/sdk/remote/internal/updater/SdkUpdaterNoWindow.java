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

package com.android.tools.idea.sdk.remote.internal.updater;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.SdkManager;
import com.android.tools.idea.sdk.remote.internal.*;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.utils.ILogger;
import com.android.utils.IReaderLogger;
import com.android.utils.Pair;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Performs an update using only a non-interactive console output with no GUI.
 */
public class SdkUpdaterNoWindow {

  /**
   * The {@link UpdaterData} to use.
   */
  private final UpdaterData mUpdaterData;
  /**
   * The {@link ILogger} logger to use.
   */
  private final ILogger mSdkLog;
  /**
   * The reply to any question asked by the update process. Currently this will
   * be yes/no for ability to replace modified samples or restart ADB.
   */
  private final boolean mForce;

  /**
   * Creates an UpdateNoWindow object that will update using the given SDK root
   * and outputs to the given SDK logger.
   *
   * @param osSdkRoot  The OS path of the SDK folder to update.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @param sdkLog     A logger object, that should ideally output to a write-only console.
   * @param force      The reply to any question asked by the update process. Currently this will
   *                   be yes/no for ability to replace modified samples or restart ADB.
   * @param proxyPort  An optional HTTP/HTTPS proxy port. Can be null.
   * @param proxyHost  An optional HTTP/HTTPS proxy host. Can be null.
   */
  public SdkUpdaterNoWindow(String osSdkRoot,
                            SdkManager sdkManager,
                            ILogger sdkLog,
                            boolean force,
                            String proxyHost,
                            String proxyPort) {
    mSdkLog = sdkLog;
    mForce = force;
    mUpdaterData = new UpdaterData(osSdkRoot, sdkLog);

    // Use a factory that only outputs to the given ILogger.
    mUpdaterData.setTaskFactory(new ConsoleTaskFactory());

    // Setup the default sources including the getenv overrides.
    mUpdaterData.setupDefaultSources();
  }

  /**
   * Performs the actual update.
   *
   * @param pkgFilter           A list of {@link SdkRepoConstants#NODES} to limit the type of packages
   *                            we can update. A null or empty list means to update everything possible.
   * @param includeAll          True to list and install all packages, including obsolete ones.
   * @param dryMode             True to check what would be updated/installed but do not actually
   *                            download or install anything.
   * @param acceptLicense       SDK licenses to automatically accept.
   * @param includeDependencies If true, also include any required dependencies
   */
  public void updateAll(ArrayList<String> pkgFilter,
                        boolean includeAll,
                        boolean dryMode,
                        String acceptLicense,
                        boolean includeDependencies) {
    mUpdaterData.updateOrInstallAll_NoGUI(pkgFilter, includeAll, dryMode, acceptLicense, includeDependencies);
  }

  // -----

  /**
   * A custom implementation of {@link ITaskFactory} that
   * provides {@link ConsoleTaskMonitor} objects.
   */
  private class ConsoleTaskFactory implements ITaskFactory {
    @Override
    public void start(String title, ITask task) {
      start(title, null /*parentMonitor*/, task);
    }

    @Override
    public void start(String title, ITaskMonitor parentMonitor, ITask task) {
      if (parentMonitor == null) {
        task.run(new ConsoleTaskMonitor(title, task));
      }
      else {
        // Use all the reminder of the parent monitor.
        if (parentMonitor.getProgressMax() == 0) {
          parentMonitor.setProgressMax(1);
        }

        ITaskMonitor sub = parentMonitor.createSubMonitor(parentMonitor.getProgressMax() - parentMonitor.getProgress());
        try {
          task.run(sub);
        }
        finally {
          int delta = sub.getProgressMax() - sub.getProgress();
          if (delta > 0) {
            sub.incProgress(delta);
          }
        }
      }
    }
  }

  /**
   * A custom implementation of {@link ITaskMonitor} that defers all output to the
   * super {@link SdkUpdaterNoWindow#mSdkLog}.
   */
  private class ConsoleTaskMonitor implements ITaskMonitor {

    private static final double MAX_COUNT = 10000.0;
    private double mIncCoef = 0;
    private double mValue = 0;
    private String mLastDesc = null;
    private String mLastProgressBase = null;

    /**
     * Creates a new {@link ConsoleTaskMonitor} with the given title.
     */
    public ConsoleTaskMonitor(String title, ITask task) {
      mSdkLog.info("%s:\n", title);
    }

    /**
     * Sets the description in the current task dialog.
     */
    @Override
    public void setDescription(String format, Object... args) {

      String last = mLastDesc;
      String line = String.format("  " + format, args);                       //$NON-NLS-1$

      // If the description contains a %, it generally indicates a recurring
      // progress so we want a \r at the end.
      int pos = line.indexOf('%');
      if (pos > -1) {
        String base = line.trim();
        if (mLastProgressBase != null && base.startsWith(mLastProgressBase)) {
          line = "    " + base.substring(mLastProgressBase.length());     //$NON-NLS-1$
        }
        line += '\r';
      }
      else {
        mLastProgressBase = line.trim();
        line += '\n';
      }

      // Skip line if it's the same as the last one.
      if (last != null && last.equals(line.trim())) {
        return;
      }
      mLastDesc = line.trim();

      // If the last line terminated with a \r but the new one doesn't, we need to
      // insert a \n to avoid erasing the previous line.
      if (last != null &&
          last.endsWith("\r") &&                                          //$NON-NLS-1$
          !line.endsWith("\r")) {                                         //$NON-NLS-1$
        line = '\n' + line;
      }

      mSdkLog.info("%s", line);                                             //$NON-NLS-1$
    }

    @Override
    public void log(String format, Object... args) {
      setDescription("  " + format, args);                                    //$NON-NLS-1$
    }

    @Override
    public void logError(String format, Object... args) {
      setDescription(format, args);
    }

    @Override
    public void logVerbose(String format, Object... args) {
      // The ConsoleTask does not display verbose log messages.
    }

    // --- ILogger ---

    @Override
    public void error(@Nullable Throwable t, @Nullable String errorFormat, Object... args) {
      mSdkLog.error(t, errorFormat, args);
    }

    @Override
    public void warning(@NonNull String warningFormat, Object... args) {
      mSdkLog.warning(warningFormat, args);
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
      mSdkLog.info(msgFormat, args);
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
      mSdkLog.verbose(msgFormat, args);
    }

    /**
     * Sets the max value of the progress bar.
     * <p/>
     * Weird things will happen if setProgressMax is called multiple times
     * *after* {@link #incProgress(int)}: we don't try to adjust it on the
     * fly.
     */
    @Override
    public void setProgressMax(int max) {
      assert max > 0;
      // Always set the dialog's progress max to 10k since it only handles
      // integers and we want to have a better inner granularity. Instead
      // we use the max to compute a coefficient for inc deltas.
      mIncCoef = max > 0 ? MAX_COUNT / max : 0;
      assert mIncCoef > 0;
    }

    @Override
    public int getProgressMax() {
      return mIncCoef > 0 ? (int)(MAX_COUNT / mIncCoef) : 0;
    }

    /**
     * Increments the current value of the progress bar.
     */
    @Override
    public void incProgress(int delta) {
      if (delta > 0 && mIncCoef > 0) {
        internalIncProgress(delta * mIncCoef);
      }
    }

    private void internalIncProgress(double realDelta) {
      mValue += realDelta;
      // max value is 10k, so 10k/100 == 100%.
      // Experimentation shows that it is not really useful to display this
      // progression since during download the description line will change.
      // mSdkLog.printf("    [%3d%%]\r", ((int)mValue) / 100);
    }

    /**
     * Returns the current value of the progress bar,
     * between 0 and up to {@link #setProgressMax(int)} - 1.
     */
    @Override
    public int getProgress() {
      assert mIncCoef > 0;
      return mIncCoef > 0 ? (int)(mValue / mIncCoef) : 0;
    }

    /**
     * Returns true if the "Cancel" button was selected.
     */
    @Override
    public boolean isCancelRequested() {
      return false;
    }

    /**
     * Display a yes/no question dialog box.
     * <p/>
     * This implementation allow this to be called from any thread, it
     * makes sure the dialog is opened synchronously in the ui thread.
     *
     * @param title   The title of the dialog box
     * @param message The error message
     * @return true if YES was clicked.
     */
    @Override
    public boolean displayPrompt(final String title, final String message) {
      // TODO Make it interactive if mForce==false
      mSdkLog.info("\n%1$s\n%2$s\n%3$s",        //$NON-NLS-1$
                   title, message, mForce ? "--force used, will reply yes\n" : "Note: you  can use --force to override to yes.\n");
      if (mForce) {
        return true;
      }

      while (true) {
        mSdkLog.info("%1$s", "[y/n] =>");     //$NON-NLS-1$
        try {
          byte[] readBuffer = new byte[2048];
          String reply = readLine(readBuffer).trim();
          mSdkLog.info("\n");               //$NON-NLS-1$
          if (reply.length() > 0 && reply.length() <= 3) {
            char c = reply.charAt(0);
            if (c == 'y' || c == 'Y') {
              return true;
            }
            else if (c == 'n' || c == 'N') {
              return false;
            }
          }
          mSdkLog.info("Unknown reply '%s'. Please use y[es]/n[o].\n");  //$NON-NLS-1$

        }
        catch (IOException e) {
          // Exception. Be conservative and say no.
          mSdkLog.info("\n");               //$NON-NLS-1$
          return false;
        }
      }
    }

    /**
     * Displays a prompt message to the user and read two values,
     * login/password.
     * <p/>
     * <i>Asks user for login/password information.</i>
     * <p/>
     * This method shows a question in the standard output, asking for login
     * and password.</br>
     * <b>Method Output:</b></br>
     * Title</br>
     * Message</br>
     * Login: (Wait for user input)</br>
     * Password: (Wait for user input)</br>
     * <p/>
     *
     * @param title   The title of the iteration.
     * @param message The message to be displayed.
     * @return A {@link Pair} holding the entered login and password. The
     * <b>first element</b> is always the <b>Login</b>, and the
     * <b>second element</b> is always the <b>Password</b>. This
     * method will never return null, in case of error the pair will
     * be filled with empty strings.
     * @see ITaskMonitor#displayLoginCredentialsPrompt(String, String)
     */
    @Override
    public UserCredentials displayLoginCredentialsPrompt(String title, String message) {
      String login = "";    //$NON-NLS-1$
      String password = ""; //$NON-NLS-1$
      String workstation = ""; //$NON-NLS-1$
      String domain = ""; //$NON-NLS-1$

      mSdkLog.info("\n%1$s\n%2$s", title, message);
      byte[] readBuffer = new byte[2048];
      try {
        mSdkLog.info("\nLogin: ");
        login = readLine(readBuffer);
        mSdkLog.info("\nPassword: ");
        password = readLine(readBuffer);
        mSdkLog.info("\nIf your proxy uses NTLM authentication, provide the following information. Leave blank otherwise.");
        mSdkLog.info("\nWorkstation: ");
        workstation = readLine(readBuffer);
        mSdkLog.info("\nDomain: ");
        domain = readLine(readBuffer);

                /*
                 * TODO: Implement a way to don't echo the typed password On
                 * Java 5 there's no simple way to do this. There's just a
                 * workaround which is output backspaces on each keystroke.
                 * A good alternative is to use Java 6 java.io.Console
                 */
      }
      catch (IOException e) {
        // Reset login/pass to empty Strings.
        login = "";    //$NON-NLS-1$
        password = ""; //$NON-NLS-1$
        workstation = ""; //$NON-NLS-1$
        domain = ""; //$NON-NLS-1$
        //Just print the error to console.
        mSdkLog.info("\nError occurred during login/pass query: %s\n", e.getMessage());
      }

      return new UserCredentials(login, password, workstation, domain);
    }

    /**
     * Reads current console input in the given buffer.
     *
     * @param buffer Buffer to hold the user input. Must be larger than the largest
     *               expected input. Cannot be null.
     * @return A new string. May be empty but not null.
     * @throws IOException in case the buffer isn't long enough.
     */
    private String readLine(byte[] buffer) throws IOException {

      int count;
      if (mSdkLog instanceof IReaderLogger) {
        count = ((IReaderLogger)mSdkLog).readLine(buffer);
      }
      else {
        count = System.in.read(buffer);
      }

      // is the input longer than the buffer?
      if (count == buffer.length && buffer[count - 1] != 10) {
        throw new IOException(String.format("Input is longer than the buffer size, (%1$s) bytes", buffer.length));
      }

      // ignore end whitespace
      while (count > 0 && (buffer[count - 1] == '\r' || buffer[count - 1] == '\n')) {
        count--;
      }

      return new String(buffer, 0, count);
    }

    /**
     * Creates a sub-monitor that will use up to tickCount on the progress bar.
     * tickCount must be 1 or more.
     */
    @Override
    public ITaskMonitor createSubMonitor(int tickCount) {
      assert mIncCoef > 0;
      assert tickCount > 0;
      return new ConsoleSubTaskMonitor(this, null, mValue, tickCount * mIncCoef);
    }
  }

  private interface IConsoleSubTaskMonitor extends ITaskMonitor {
    void subIncProgress(double realDelta);
  }

  private static class ConsoleSubTaskMonitor implements IConsoleSubTaskMonitor {

    private final ConsoleTaskMonitor mRoot;
    private final IConsoleSubTaskMonitor mParent;
    private final double mStart;
    private final double mSpan;
    private double mSubValue;
    private double mSubCoef;

    /**
     * Creates a new sub task monitor which will work for the given range [start, start+span]
     * in its parent.
     *
     * @param root   The ProgressTask root
     * @param parent The immediate parent. Can be the null or another sub task monitor.
     * @param start  The start value in the root's coordinates
     * @param span   The span value in the root's coordinates
     */
    public ConsoleSubTaskMonitor(ConsoleTaskMonitor root, IConsoleSubTaskMonitor parent, double start, double span) {
      mRoot = root;
      mParent = parent;
      mStart = start;
      mSpan = span;
      mSubValue = start;
    }

    @Override
    public boolean isCancelRequested() {
      return mRoot.isCancelRequested();
    }

    @Override
    public void setDescription(String format, Object... args) {
      mRoot.setDescription(format, args);
    }

    @Override
    public void log(String format, Object... args) {
      mRoot.log(format, args);
    }

    @Override
    public void logError(String format, Object... args) {
      mRoot.logError(format, args);
    }

    @Override
    public void logVerbose(String format, Object... args) {
      mRoot.logVerbose(format, args);
    }

    @Override
    public void setProgressMax(int max) {
      assert max > 0;
      mSubCoef = max > 0 ? mSpan / max : 0;
      assert mSubCoef > 0;
    }

    @Override
    public int getProgressMax() {
      return mSubCoef > 0 ? (int)(mSpan / mSubCoef) : 0;
    }

    @Override
    public int getProgress() {
      assert mSubCoef > 0;
      return mSubCoef > 0 ? (int)((mSubValue - mStart) / mSubCoef) : 0;
    }

    @Override
    public void incProgress(int delta) {
      if (delta > 0 && mSubCoef > 0) {
        subIncProgress(delta * mSubCoef);
      }
    }

    @Override
    public void subIncProgress(double realDelta) {
      mSubValue += realDelta;
      if (mParent != null) {
        mParent.subIncProgress(realDelta);
      }
      else {
        mRoot.internalIncProgress(realDelta);
      }
    }

    @Override
    public boolean displayPrompt(String title, String message) {
      return mRoot.displayPrompt(title, message);
    }

    @Override
    public UserCredentials displayLoginCredentialsPrompt(String title, String message) {
      return mRoot.displayLoginCredentialsPrompt(title, message);
    }

    @Override
    public ITaskMonitor createSubMonitor(int tickCount) {
      assert mSubCoef > 0;
      assert tickCount > 0;
      return new ConsoleSubTaskMonitor(mRoot, this, mSubValue, tickCount * mSubCoef);
    }

    // --- ILogger ---

    @Override
    public void error(@Nullable Throwable t, @Nullable String errorFormat, Object... args) {
      mRoot.error(t, errorFormat, args);
    }

    @Override
    public void warning(@NonNull String warningFormat, Object... args) {
      mRoot.warning(warningFormat, args);
    }

    @Override
    public void info(@NonNull String msgFormat, Object... args) {
      mRoot.info(msgFormat, args);
    }

    @Override
    public void verbose(@NonNull String msgFormat, Object... args) {
      mRoot.verbose(msgFormat, args);
    }
  }
}
