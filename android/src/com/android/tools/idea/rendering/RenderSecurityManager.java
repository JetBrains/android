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
package com.android.tools.idea.rendering;

import com.android.annotations.Nullable;
import com.android.ide.common.rendering.RenderSecurityException;
import com.android.tools.idea.rendering.webp.NativeLibHelper;
import com.android.utils.ILogger;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.IOException;
import java.lang.reflect.Member;
import java.net.InetAddress;
import java.security.Permission;
import java.util.PropertyPermission;

import static com.android.SdkConstants.*;

/**
 * A {@link SecurityManager} which is used for layout lib rendering, to
 * prevent custom views from accidentally exiting the whole IDE if they call
 * {@code System.exit}, as well as unintentionally writing files etc.
 * <p>
 * The security manager only checks calls on the current thread for which it
 * was made active with a call to {@link #setActive(boolean, Object)}, as well as any
 * threads constructed from the render thread.
 */
public class RenderSecurityManager extends SecurityManager {
  /**
   * Property used to disable sandbox
   */
  public static final String ENABLED_PROPERTY = "android.render.sandbox";

  /**
   * Whether we should restrict reading to certain paths
   */
  public static final boolean RESTRICT_READS = false;

  /**
   * Whether the security manager is enabled for this session (it might still
   * be inactive, either because it's active for a different thread, or because
   * it has been disabled via {@link #setActive(boolean, Object)} (which sets the
   * per-instance mEnabled flag)
   */
  public static boolean sEnabled = !VALUE_FALSE.equals(System.getProperty(ENABLED_PROPERTY));

  /**
   * Thread local data which indicates whether the current thread is relevant for
   * this security manager. This is an inheritable thread local such that any threads
   * spawned from this thread will also apply the security manager; otherwise code
   * could just create new threads and execute code separate from the security manager
   * there.
   */
  private static ThreadLocal<Boolean> sIsRenderThread = new InheritableThreadLocal<Boolean>() {
    @Override
    protected synchronized Boolean initialValue() {
      return Boolean.FALSE;
    }

    @Override
    protected synchronized Boolean childValue(Boolean parentValue) {
      return parentValue;
    }
  };

  /**
   * Secret which must be provided by callers wishing to deactivate the security manager
   */
  private static Object sCredential;
  /**
   * For debugging purposes
   */
  private static String sLastFailedPath;

  private boolean mAllowSetSecurityManager;
  private boolean mDisabled;
  @SuppressWarnings("FieldCanBeLocal") private String mSdkPath;
  @SuppressWarnings("FieldCanBeLocal") private String mProjectPath;
  private final String mTempDir;
  private final String mNormalizedTempDir;
  private String mCanonicalTempDir;
  /** IDE's cache path. **/
  private final String mySystemDir;
  private final String myNormalizedSystemDir;
  private String myCanonicalSystemDir;
  private String mAppTempDir;
  private SecurityManager myPreviousSecurityManager;
  private ILogger mLogger;

  /**
   * Returns the current render security manager, if any. This will only return
   * non-null if there is an active {@linkplain RenderSecurityManager} as the
   * current global security manager.
   */
  @Nullable
  public static RenderSecurityManager getCurrent() {
    if (sIsRenderThread.get()) {
      SecurityManager securityManager = System.getSecurityManager();
      if (securityManager instanceof RenderSecurityManager) {
        RenderSecurityManager manager = (RenderSecurityManager)securityManager;
        return manager.isRelevant() ? manager : null;
      }
    }

    return null;
  }

  /**
   * Creates a security manager suitable for controlling access to custom views
   * being rendered by layoutlib, ensuring that they don't accidentally try to
   * write files etc (which could corrupt data if they for example assume device
   * paths that are not the same for the running IDE; for example, they could try
   * to clear out their own local app storage, which in the IDE could be the
   * user's home directory.)
   * <p>
   * Note: By default a security manager is not active. You need to call
   * {@link #setActive(boolean, Object)} with true to activate it, <b>instead</b> of just calling
   * {@link System#setSecurityManager(SecurityManager)}.
   *
   * @param sdkPath     an optional path to the SDK install being used by layoutlib;
   *                    this is used to white-list path prefixes for layoutlib resource
   *                    lookup
   * @param projectPath a path to the project directory, used for similar purposes
   */
  public RenderSecurityManager(@Nullable String sdkPath, @Nullable String projectPath) {
    mSdkPath = sdkPath;
    mProjectPath = projectPath;
    mTempDir = System.getProperty("java.io.tmpdir");
    mySystemDir = PathManager.getSystemPath();
    mNormalizedTempDir = new File(mTempDir).getPath(); // will call fs.normalize() on the path
    myNormalizedSystemDir = new File(mySystemDir).getPath();
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    sLastFailedPath = null;
  }

  /**
   * Sets an optional logger. Returns this for constructor chaining.
   */
  public RenderSecurityManager setLogger(@Nullable ILogger logger) {
    mLogger = logger;
    return this;
  }

  /**
   * Sets an optional application temp directory. Returns this for constructor chaining.
   */
  public RenderSecurityManager setAppTempDir(@Nullable String appTempDir) {
    mAppTempDir = appTempDir;
    return this;
  }

  /**
   * Sets whether the {@linkplain RenderSecurityManager} is active or not.
   * If it is being set as active, the passed in credential is remembered
   * and anyone wishing to turn off the security manager must provide the
   * same credential.
   *
   * @param active     whether to turn on or off the security manager
   * @param credential when turning off the security manager, the exact same
   *                   credential passed in to the earlier activation call
   */
  public void setActive(boolean active, @Nullable Object credential) {
    SecurityManager current = System.getSecurityManager();
    boolean isActive = current == this;
    if (active == isActive) {
      return;
    }

    if (active) {
      // Enable
      assert !(current instanceof RenderSecurityManager);
      myPreviousSecurityManager = current;
      sIsRenderThread.set(true);
      mDisabled = false;
      System.setSecurityManager(this);
      //noinspection AssignmentToStaticFieldFromInstanceMethod
      sCredential = credential;
    }
    else {
      if (credential != sCredential) {
        throw RenderSecurityException.create("Invalid credential");
      }

      // Disable
      mAllowSetSecurityManager = true;
      // Don't set mDisabled and clear sInRenderThread yet: the call
      // to revert to the previous security manager below will trigger
      // a check permission, and in that code we need to distinguish between
      // this call (isRelevant() should return true) and other threads calling
      // it outside the scope of the security manager
      try {
        // Only reset the security manager if it hasn't already been set to
        // something else. If other threads try to do the same thing we could have
        // a problem; if they sampled the render security manager while it was globally
        // active, replaced it with their own, and sometime in the future try to
        // set it back, it will be active when we didn't intend for it to be. That's
        // why there is also the {@code mDisabled} flag, used to ignore any requests
        // later on.
        if (current instanceof RenderSecurityManager) {
          System.setSecurityManager(myPreviousSecurityManager);
        }
        else if (mLogger != null) {
          sIsRenderThread.set(false);
          mLogger.warning("Security manager was changed behind the scenes: ", current);
        }
      }
      finally {
        mDisabled = true;
        mAllowSetSecurityManager = false;
        sIsRenderThread.set(false);
      }
    }
  }

  private boolean isRelevant() {
    return sEnabled && !mDisabled && sIsRenderThread.get();
  }

  /**
   * Disposes the security manager. An alias for calling {@link #setActive} with
   * false.
   *
   * @param credential the sandbox credential initially passed to
   *                   {@link #setActive(boolean, Object)}
   */
  public void dispose(@Nullable Object credential) {
    setActive(false, credential);
  }

  /**
   * Enters a code region where the sandbox is not needed
   *
   * @param credential a credential which proves that the caller has the right to do this
   * @return a token which should be passed back to {@link #exitSafeRegion(boolean)}
   */
  public static boolean enterSafeRegion(@Nullable Object credential) {
    boolean token = sEnabled;
    if (credential == sCredential) {
      sEnabled = false;
    }
    return token;
  }

  /**
   * Exits a code region where the sandbox was not needed
   *
   * @param token the token which was returned back from the paired
   *              {@link #enterSafeRegion(Object)} call
   */
  public static void exitSafeRegion(boolean token) {
    sEnabled = token;
  }

  /**
   * Returns the most recently denied path.
   *
   * @return the most recently denied path
   */
  @Nullable
  public static String getLastFailedPath() {
    return sLastFailedPath;
  }

  // Permitted by custom views: access any package or member, read properties

  @Override
  public void checkPackageAccess(String pkg) {
  }

  @Override
  public void checkMemberAccess(Class<?> clazz, int which) {
    if (which == Member.DECLARED && isRelevant() &&
        RenderSecurityException.class.getName().equals(clazz.getName())) {
      throw RenderSecurityException.create("Reflection", clazz.getName());
    }
  }

  @Override
  public void checkPropertyAccess(String property) {
  }

  @Override
  public void checkLink(String lib) {
    // Allow linking with relative paths
    // Needed to for example load the "fontmanager" library from layout lib (from the
    // BiDiRenderer's layoutGlyphVector call
    if (isRelevant() && (lib.indexOf('/') != -1 || lib.indexOf('\\') != -1)) {
      if (lib.startsWith(System.getProperty("java.home"))) {
        return; // Allow loading JRE libraries
      }
      // Allow loading webp library
      if (lib.equals(new File(NativeLibHelper.getLibLocation(), NativeLibHelper.getLibName()).getAbsolutePath())) {
        return;
      }
      throw RenderSecurityException.create("Link", lib);
    }
  }

  @Override
  public void checkCreateClassLoader() {
    // TODO: Layoutlib makes heavy use of this, so we can't block it yet.
    // To fix this we should make a local class loader, passed to layoutlib, which
    // knows how to reset the security manager
  }

  //------------------------------------------------------------------------------------------
  // Reading is permitted for certain files only
  //------------------------------------------------------------------------------------------

  @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
  @Override
  public void checkRead(String file) {
    if (RESTRICT_READS && isRelevant() && !isReadingAllowed(file)) {
      throw RenderSecurityException.create("Read", file);
    }
  }

  @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
  @Override
  public void checkRead(String file, Object context) {
    if (RESTRICT_READS && isRelevant() && !isReadingAllowed(file)) {
      throw RenderSecurityException.create("Read", file);
    }
  }

  private boolean isReadingAllowed(String path) {
    if (RESTRICT_READS) {
      // Allow reading files in the SDK install (fonts etc)
      if (mSdkPath != null && path.startsWith(mSdkPath)) {
        return true;
      }

      // Allowing reading resources in the project, such as icons
      if (mProjectPath != null && path.startsWith(mProjectPath)) {
        return true;
      }

      if (path.startsWith("#") && path.indexOf(File.separatorChar) == -1) {
        // It's really layoutlib's ResourceHelper.getColorStateList which calls isFile()
        // on values to see if it's a file or a color.
        return true;
      }

      // Needed by layoutlib's class loader. Note that we've locked down the ability to
      // create new class loaders.
      if (path.endsWith(DOT_CLASS) || path.endsWith(DOT_JAR)) {
        return true;
      }

      // Allow reading files in temp
      if (isTempDirPath(path)) {
        return true;
      }

      String javaHome = System.getProperty("java.home");
      if (path.startsWith(javaHome)) { // Allow JDK to load its own classes
        return true;
      }
      else if (javaHome.endsWith("/Contents/Home")) {
        // On Mac, Home lives two directory levels down from the real home, and we
        // sometimes need to read from sibling directories (e.g. ../Libraries/ etc)
        if (path.regionMatches(0, javaHome, 0, javaHome.length() - "Contents/Home".length())) {
          return true;
        }
      }

      return false;
    }
    else {
      return true;
    }
  }

  @SuppressWarnings("RedundantIfStatement")
  private boolean isWritingAllowed(String path) {
    return isTempDirPath(path);
  }

  private boolean isTempDirPath(String path) {
    if (path.startsWith(mTempDir) ||
        path.startsWith(mNormalizedTempDir) ||
        path.startsWith(mySystemDir) ||
        path.startsWith(myNormalizedSystemDir)) {
      return true;
    }

    if (mAppTempDir != null && path.startsWith(mAppTempDir)) {
      return true;
    }

    // Work around weird temp directories
    try {
      String canonicalDir = getCanonicalTempDir();
      String canonicalFile;
      if (path.startsWith(canonicalDir) || (canonicalFile = new File(path).getCanonicalPath()).startsWith(canonicalDir)) {
        return true;
      }
      canonicalDir = getCanonicalSystemDir();
      if (path.startsWith(canonicalDir) || canonicalFile.startsWith(canonicalDir)) {
        return true;
      }
    }
    catch (IOException e) {
      // ignore
    }

    //noinspection AssignmentToStaticFieldFromInstanceMethod
    sLastFailedPath = path;

    return false;
  }

  @NotNull
  private String getCanonicalTempDir() {
    if (mCanonicalTempDir == null) {
      try {
        mCanonicalTempDir = new File(mNormalizedTempDir).getCanonicalPath();
      }
      catch (IOException e) {
        // ignore the error and use normalized temp dir
        mCanonicalTempDir = new File(mNormalizedTempDir).getAbsolutePath();
      }
    }
    return mCanonicalTempDir;
  }

  @NotNull
  private String getCanonicalSystemDir() {
    if (myCanonicalSystemDir == null) {
      try {
        myCanonicalSystemDir = new File(myNormalizedSystemDir).getCanonicalPath();
      }
      catch (IOException e) {
        // ignore the error and use normalized system dir
        myCanonicalSystemDir = new File(myNormalizedSystemDir).getAbsolutePath();
      }
    }
    return myCanonicalSystemDir;
  }

  @SuppressWarnings({"SpellCheckingInspection", "RedundantIfStatement"})
  private static boolean isPropertyWriteAllowed(String name) {
    // Linux sets this on fontmanager load; allow it since even if code points
    // to their own classes they don't get additional privileges, it's just like
    // using reflection
    if (name.equals("sun.font.fontmanager")) {
      return true;
    }

    // Toolkit initializations
    if (name.startsWith("sun.awt.") || name.startsWith("apple.awt.")) {
      return true;
    }

    if (name.equals("user.timezone")) {
      return true;
    }

    return false;
  }

  //------------------------------------------------------------------------------------------
  // Not permitted:
  //------------------------------------------------------------------------------------------

  @Override
  public void checkExit(int status) {
    // Probably not intentional in a custom view; would take down the whole IDE!
    if (isRelevant()) {
      throw RenderSecurityException.create("Exit", String.valueOf(status));
    }

    super.checkExit(status);
  }

  @Override
  public void checkPropertiesAccess() {
    if (isRelevant()) {
      throw RenderSecurityException.create("Property", null);
    }
  }

  // Prevent code execution/linking/loading

  @Override
  public void checkPackageDefinition(String pkg) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Package", pkg);
    }
  }

  @Override
  public void checkExec(String cmd) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Exec", cmd);
    }
  }

  // Prevent network access

  @Override
  public void checkConnect(String host, int port) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", host + ":" + port);
    }
  }

  @Override
  public void checkConnect(String host, int port, Object context) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", host + ":" + port);
    }
  }

  @Override
  public void checkListen(int port) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", "port " + port);
    }
  }

  @Override
  public void checkAccept(String host, int port) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", host + ":" + port);
    }
  }

  @Override
  public void checkSetFactory() {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", null);
    }
  }

  @Override
  public void checkMulticast(InetAddress inetAddress) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", inetAddress.getCanonicalHostName());
    }
  }

  @SuppressWarnings("deprecation")
  @Override
  public void checkMulticast(InetAddress inetAddress, byte ttl) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Socket", inetAddress.getCanonicalHostName());
    }
  }

  // Prevent file access

  @Override
  public void checkDelete(String file) {
    if (isRelevant()) {
      // Allow writing to temp
      if (isWritingAllowed(file)) {
        return;
      }

      throw RenderSecurityException.create("Delete", file);
    }
  }

  @Override
  public void checkAwtEventQueueAccess() {
    if (isRelevant()) {
      throw RenderSecurityException.create("Event", null);
    }
  }

  // Prevent writes

  @Override
  public void checkWrite(FileDescriptor fileDescriptor) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Write", fileDescriptor.toString());
    }
  }

  @Override
  public void checkWrite(String file) {
    if (isRelevant()) {
      if (isWritingAllowed(file)) {
        return;
      }

      throw RenderSecurityException.create("Write", file);
    }
  }

  // Misc

  @Override
  public void checkPrintJobAccess() {
    if (isRelevant()) {
      throw RenderSecurityException.create("Print", null);
    }
  }

  @Override
  public void checkSystemClipboardAccess() {
    if (isRelevant()) {
      throw RenderSecurityException.create("Clipboard", null);
    }
  }

  @Override
  public boolean checkTopLevelWindow(Object context) {
    if (isRelevant()) {
      throw RenderSecurityException.create("Window", null);
    }
    return false;
  }

  @Override
  public void checkAccess(Thread thread) {
    // Turns out layoutlib sometimes creates asynchronous calls, for example
    //       java.lang.Thread.<init>(Thread.java:521)
    //       at android.os.AsyncTask$1.newThread(AsyncTask.java:189)
    //       at java.util.concurrent.ThreadPoolExecutor.addThread(ThreadPoolExecutor.java:670)
    //       at java.util.concurrent.ThreadPoolExecutor.addIfUnderCorePoolSize(ThreadPoolExecutor.java:706)
    //       at java.util.concurrent.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:650)
    //       at android.os.AsyncTask$SerialExecutor.scheduleNext(AsyncTask.java:244)
    //       at android.os.AsyncTask$SerialExecutor.execute(AsyncTask.java:238)
    //       at android.os.AsyncTask.execute(AsyncTask.java:604)
    //       at android.widget.TextView.updateTextServicesLocaleAsync(TextView.java:8078)

    // This may not work correctly for render sessions, which are treated as synchronous
    // by callers. We should re-enable these checks to chase down these calls and
    // eliminate them from layoutlib, but until we do, it's necessary to allow thread
    // creation.
  }

  @Override
  public void checkAccess(ThreadGroup threadGroup) {
    // See checkAccess(Thread)
  }

  @Override
  public void checkPermission(Permission permission) {
    String name = permission.getName();
    if ("setSecurityManager".equals(name)) {
      if (isRelevant()) {
        if (!mAllowSetSecurityManager) {
          throw RenderSecurityException.create("Security", null);
        }
      }
      else if (mLogger != null) {
        mLogger.warning("RenderSecurityManager being replaced by another thread");
      }
    }
    else if (isRelevant()) {
      String actions = permission.getActions();
      //noinspection PointlessBooleanExpression,ConstantConditions
      if (RESTRICT_READS && "read".equals(actions)) {
        if (!isReadingAllowed(name)) {
          throw RenderSecurityException.create("Read", name);
        }
      }
      else if (!actions.isEmpty() && !actions.equals("read")) {
        // write, execute, delete, readlink
        if (!(permission instanceof FilePermission) || !isWritingAllowed(name)) {
          if (permission instanceof PropertyPermission && isPropertyWriteAllowed(name)) {
            return;
          }
          throw RenderSecurityException.create("Write", name);
        }
      }
    }
  }
}
