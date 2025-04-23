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
package com.android.tools.rendering.security;

import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.VALUE_FALSE;

import com.android.annotations.Nullable;
import com.android.tools.rendering.RenderService;
import com.android.utils.ILogger;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Permission;
import java.util.Arrays;
import java.util.PropertyPermission;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;

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
   * Whether the security manager is enabled for this session (it might still
   * be inactive, either because it's active for a different thread, or because
   * it has been disabled via {@link #setActive(boolean, Object)} (which sets the
   * per-instance mEnabled flag)
   */
  public static boolean sEnabled = !VALUE_FALSE.equals(System.getProperty(ENABLED_PROPERTY));

  /**
   * Secret which must be provided by callers wishing to deactivate the security manager
   */
  private static Object sCredential;
  /**
   * For debugging purposes
   */
  private static String sLastFailedPath;
  private final String[] mAllowedPaths;

  private boolean mAllowSetSecurityManager;
  private boolean mDisabled;
  private final String mSdkPath;
  private final String mProjectPath;
  private final String mTempDir;
  private final String mNormalizedTempDir;
  private String mCanonicalTempDir;
  private String mAppTempDir;
  private SecurityManager myPreviousSecurityManager;
  private ILogger mLogger;

  private boolean isRestrictReads;

  private final Supplier<Boolean> isRenderThread;

  /**
   * Returns the current render security manager, if any. This will only return
   * non-null if there is an active {@linkplain RenderSecurityManager} as the
   * current global security manager.
   */
  @Nullable
  public static RenderSecurityManager getCurrent() {
    SecurityManager securityManager = System.getSecurityManager();
    if (securityManager instanceof RenderSecurityManager) {
      RenderSecurityManager manager = (RenderSecurityManager)securityManager;
      return manager.isRelevant() ? manager : null;
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
   *                    this is used to allow specific path prefixes for layoutlib resource
   *                    lookup
   * @param projectPath a path to the project directory, used for similar purposes
   * @param restrictReads when true, reads will be restricted to only a set of directories including temp directory and the given sdkPath
   *                      and projectPath directories.
   */
  protected RenderSecurityManager(
    @Nullable String sdkPath,
    @Nullable String projectPath,
    boolean restrictReads,
    @NotNull String[] allowedPaths,
    @NotNull Supplier<Boolean> isRenderThread) {
    mSdkPath = sdkPath;
    mProjectPath = projectPath;
    mTempDir = System.getProperty("java.io.tmpdir");
    mNormalizedTempDir = new File(mTempDir).getPath(); // will call fs.normalize() on the path
    mAllowedPaths = allowedPaths;
    //noinspection AssignmentToStaticFieldFromInstanceMethod
    sLastFailedPath = null;
    isRestrictReads = restrictReads;
    this.isRenderThread = isRenderThread;
  }

  @NotNull
  static RenderSecurityManager createForTests(
    @Nullable String sdkPath,
    @Nullable String projectPath,
    boolean restrictReads,
    @NotNull Supplier<Boolean> isRenderThread) {
    return new RenderSecurityManager(sdkPath, projectPath, restrictReads, RenderSecurityManagerDefaults.getDefaultAllowedPaths(), isRenderThread);
  }

  @NotNull
  public static RenderSecurityManager create(
    @Nullable String sdkPath,
    @Nullable String projectPath,
    boolean restrictReads,
    @NotNull String[] allowedPaths) {
    return new RenderSecurityManager(sdkPath, projectPath, restrictReads, allowedPaths, RenderService::isRenderThread);
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
          mLogger.warning("Security manager was changed behind the scenes: ", current);
        }
      }
      finally {
        mDisabled = true;
        mAllowSetSecurityManager = false;
      }
    }
  }

  protected boolean isRelevant() {
    return sEnabled && !mDisabled && isRenderThread.get();
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
   * Executes the given {@link Runnable} without a sandbox. See {@link #enterSafeRegion(Object)}.
   * This is equivalent to running the {@link Runnable} between a {@link #enterSafeRegion(Object)} and a
   * {@link #exitSafeRegion(boolean)} calls.
   *
   * @param credential a credential which proves that the caller has the right to do this
   * @param runnable the runnable to execute
   */
  public static void runInSafeRegion(@Nullable Object credential, @NotNull Runnable runnable) {
    boolean token = enterSafeRegion(credential);
    try {
      runnable.run();
    } finally {
      exitSafeRegion(token);
    }
  }

  /**
   * Executes the given {@link Callable} without a sandbox and returns the result. See {@link #enterSafeRegion(Object)}.
   * This is equivalent to running the {@link Runnable} between a {@link #enterSafeRegion(Object)} and a
   * {@link #exitSafeRegion(boolean)} calls.
   *
   * @param credential a credential which proves that the caller has the right to do this
   * @param callable the runnable to execute
   */
  public static <T> T runInSafeRegion(@Nullable Object credential, @NotNull Callable<T> callable) throws Exception {
    boolean token = enterSafeRegion(credential);
    try {
      return callable.call();
    } finally {
      exitSafeRegion(token);
    }
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
  public void checkPropertyAccess(String property) {
  }

  @Override
  public void checkPropertiesAccess() {
    if (isRelevant() && !RenderPropertiesAccessUtil.isPropertyAccessAllowed()) {
      boolean isWithinLogger = Arrays.stream(this.getClassContext())
        .anyMatch(
          (clazz) -> "Logger".equals(clazz.getSimpleName()) && "com.intellij.openapi.diagnostic.Logger".equals(clazz.getCanonicalName()));

      if (!isWithinLogger) {
        throw RenderSecurityException.create("Property", null);
      }
    }
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
      if (RenderPropertiesAccessUtil.isLibraryLinkingAllowed(lib)) {
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
    if (isRestrictReads && isRelevant() && !isReadingAllowed(file)) {
      throw RenderSecurityException.create("Read", file);
    }
  }

  @SuppressWarnings({"PointlessBooleanExpression", "ConstantConditions"})
  @Override
  public void checkRead(String file, Object context) {
    if (isRestrictReads && isRelevant() && !isReadingAllowed(file)) {
      throw RenderSecurityException.create("Read", file);
    }
  }

  private boolean isReadingAllowed(String path) {
    if (isRestrictReads) {
      try {
        path = canonicalize(path);
      }
      catch (IOException e) {
        return false;
      }

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

  private static String canonicalize(@NotNull String path) throws IOException {
    return Paths.get(path).normalize().toFile().getCanonicalPath();
  }

  private boolean isInAllowedPath(@NotNull String path) {
    for (int i = 0; i < mAllowedPaths.length; ++i) {
      if (path.startsWith(mAllowedPaths[i])) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("RedundantIfStatement")
  private boolean isWritingAllowed(String path) {
    try {
      path = canonicalize(path);
      // We do not allow writing to links of paths that do not exist. If the path had existed, it would have
      // been resolved by the canonicalize call.
      if (Files.isSymbolicLink(Paths.get(path))) return false;
    }
    catch (IOException e) {
      return false;
    }
    return isTempDirPath(path) || isInAllowedPath(path);
  }

  private boolean isTempDirPath(String path) {
    if (path.startsWith(mTempDir) || path.startsWith(mNormalizedTempDir)) {
      return true;
    }

    if (mAppTempDir != null && path.startsWith(mAppTempDir)) {
      return true;
    }

    // Work around weird temp directories
    try {
      if (mCanonicalTempDir == null) {
        mCanonicalTempDir = canonicalize(mNormalizedTempDir);
      }

      if (path.startsWith(mCanonicalTempDir) || canonicalize(path).startsWith(mCanonicalTempDir)) {
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
    else if ("accessEventQueue".equals(name)) {
      if (isRelevant()) {
        throw RenderSecurityException.create("Event", null);
      }
    }
    else if ("accessClipboard".equals(name)) {
      if (isRelevant()) {
        throw RenderSecurityException.create("Clipboard", null);
      }
    }
    else if ("showWindowWithoutWarningBanner".equals(name)) {
      if (isRelevant()) {
        throw RenderSecurityException.create("Window", null);
      }
    }
    else if ("symbolic".equals(name)) {
      if (isRelevant()) {
        throw RenderSecurityException.create("SymbolicLinks", null);
      }
    } else if (isRelevant()) {
      String actions = permission.getActions();
      //noinspection PointlessBooleanExpression,ConstantConditions
      if (isRestrictReads && "read".equals(actions)) {
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
