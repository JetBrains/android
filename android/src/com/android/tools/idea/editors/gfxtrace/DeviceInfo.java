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
package com.android.tools.idea.editors.gfxtrace;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.xml.AndroidManifest;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.dom.animator.ObjectAnimator;
import org.jetbrains.android.uipreview.VirtualFileWrapper;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * DeviceInfo holds installed package information for an Android device.
 */
public class DeviceInfo {
  public final Package[] myPackages;

  /**
   * Fetching device information will synchronize on this to avoid race conditions
   * if the user closes the dialog while fetching.
   */
  private final static Object myFetchLock = new Object();

  public interface Listener {
    void onDeviceInfoReceived(DeviceInfo info);
    void onFinished();
    void onException(Exception ex);
  }

  /**
   * Transform is the interface implemented by types that can transform an instance of type
   * {@link T} to another. Transforms can use used to transform parts of a {@link DeviceInfo}.
   */
  public interface Transform<T> {
    T transform(T obj);
  }

  public DeviceInfo(Package[] packages) {
    myPackages = packages;
  }

  @Nullable
  public Package getPackage(String name) {
    for (Package p : myPackages) {
      if (name.equals(p.myName)) {
        return p;
      }
    }
    return null;
  }

  public boolean equals(Object other) {
    return other instanceof DeviceInfo;
  }

  /**
   * All DeviceInfo instances are considered equal, for purposes of serving as paths in a tree.
   */
  public int hashCode() {
    return 1;
  }

  /**
   * transform returns a new {@link DeviceInfo} by transforming this {@link DeviceInfo}
   * with the provided transform.
   */
  public DeviceInfo transform(@NotNull Transform<Package> transform) {
    ArrayList<Package> pkgs = new ArrayList<Package>();
    pkgs.ensureCapacity(myPackages.length);
    for (Package pkg : myPackages) {
      pkg = transform.transform(pkg);
      if (pkg != null) {
        pkgs.add(pkg);
      }
    }
    Collections.sort(pkgs);
    Package[] p = new Package[pkgs.size()];
    pkgs.toArray(p);
    return new DeviceInfo(p);
  }

  private static File getPackageCacheFile(IDevice device) {
    File cacheDir = new File(FileUtil.join(PathManager.getSystemPath(), "android", "pkginfo"));
    cacheDir.mkdirs();
    return new File(cacheDir, device.getSerialNumber() + ".pkginfo");
  }

  /**
   * Package describes a package installed on the device.
   */
  public static class Package implements Comparable<Package> {
    /**
     * The package's name.
     */
    public final String myName;

    /**
     * The package's icon.
     * Null if the package does not have an icon.
     */
    public Icon myIcon;

    /**
     * The package's preferred CPU ABI.
     * Null if the package does not have a preferred CPU ABI.
     */
    public final String myABI;

    /**
     * The list of activities exposed by this package.
     */
    public final Activity[] myActivities;

    @Nullable
    public Activity getActivity(String name) {
      for (Activity a : myActivities) {
        if (name.equals(a.myName)) {
          return a;
        }
      }
      return null;
    }

    public Package(String name, Icon icon, String abi, Activity[] activities) {
      myName = name;
      myIcon = icon;
      myABI = abi;
      myActivities = activities;

      Arrays.sort(myActivities);
    }

    /**
     * transform returns a new {@link Package} by transforming this {@link Package}
     * with the provided transform.
     */
    public Package transform(@NotNull Transform<Activity> filter) {
      ArrayList<Activity> activities = new ArrayList<Activity>();
      activities.ensureCapacity(myActivities.length);
      for (Activity act : myActivities) {
        act = filter.transform(act);
        if (act != null) {
          activities.add(act);
        }
      }
      Collections.sort(activities);
      Activity[] a = new Activity[activities.size()];
      activities.toArray(a);
      return new Package(myName, myIcon, myABI, a);
    }

    /**
     * launchActivity returns the preferred activity used to launch this package.
     * Null if there's no preferred launch activity.
     */
    public Activity launchActivity() {
      for (Activity activity : myActivities) {
        if (activity.myIsLaunch) {
          return activity;
        }
      }
      return null;
    }

    public String getDisplayName() {
      int lastDot = myName.lastIndexOf('.');
      if (lastDot >= 0 && lastDot < myName.length() - 1) {
       return myName.substring(lastDot + 1);
      }
      return myName;
    }

    @Override
    public int compareTo(Package other) {
      return myName.compareTo(other.myName);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Package aPackage = (Package)o;
      if (!myName.equals(aPackage.myName)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return myName.hashCode();
    }
  }

  /**
   * Activity describes an activity exposed by a package installed on the device.
   */
  public static class Activity implements Comparable<Activity> {
    /**
     * The activity's name.
     */
    public final String myName;

    /**
     * True if the activity is the preferred activity used to launch the package.
     */
    public final boolean myIsLaunch;

    /**
     * The activity's icon.
     * Null if the activity does not have an icon.
     */
    public Icon myIcon;

    public Activity(String name, boolean isLaunch, Icon icon) {
      myName = name;
      myIsLaunch = isLaunch;
      myIcon = icon;
    }

    @Override
    public int compareTo(Activity other) {
      return myName.compareTo(other.myName);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Activity activity = (Activity)o;
      if (!myName.equals(activity.myName)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return myName.hashCode();
    }
  }

  /**
   * PkgInfoProvider provides device package information using the PkgInfo helper APK.
   */
  public static class PkgInfoProvider {
    private static final int LATCH_TIMEOUT_MS = 3000;
    private static final int LOCAL_PORT = 3333;
    private static final String REMOTE_SOCKET = "pkginfo";
    private static final String PKGINFO_PACKAGE = "com.google.android.pkginfo";
    private static final String PKGINFO_SERVICE = "PkgInfoService";
    private static final String PKGINFO_ACTION = "com.google.android.pkginfo.action.SEND_PKG_INFO";
    private static final String EXTRA_ONLY_DEBUG = "com.google.android.pkginfo.extra.ONLY_DEBUG";
    private static final String EXTRA_PROTOCOL_VERSION = "com.google.android.pkginfo.extra.PROTOCOL_VERSION";
    private static final int READ_SOCKET_MAX_RETRIES = 40;
    private static final int READ_SOCKET_DELAY_MS = 1000;

    @NotNull private static final Logger LOG = Logger.getInstance(PkgInfoProvider.class);

    private final Project myProject;
    public final IDevice myDevice;

    public PkgInfoProvider(Project project, IDevice device) {
      myProject = project;
      myDevice = device;
    }

    public void getDeviceInfo(int iconWidth, int iconHeight, DeviceInfo.Listener acceptor) {
      ApplicationManager.getApplication().executeOnPooledThread(new FetchDeviceInfo(acceptor, iconWidth, iconHeight));
    }

    private class FetchDeviceInfo implements Runnable {
      private final DeviceInfo.Listener myAcceptor;
      private final int myIconWidth;
      private final int myIconHeight;

      public FetchDeviceInfo(DeviceInfo.Listener acceptor, int iconWidth, int iconHeight) {
        myAcceptor = acceptor;
        myIconWidth = iconWidth;
        myIconHeight = iconHeight;
      }

      @Override
      public void run() {
        File cacheFile = getPackageCacheFile(myDevice);
        if (cacheFile.exists()) {
          try (BufferedReader r = Files.newReader(cacheFile, StandardCharsets.UTF_8)) {
            myAcceptor.onDeviceInfoReceived(new Gson().fromJson(r, Info.class).get(myIconWidth, myIconHeight));
          }
          catch (Exception ex) {
            LOG.info("Error reading pkginfo cache file " + cacheFile, ex);
          }
        }

        synchronized (myFetchLock) {
          try {
            installApk();

            try {
              myDevice.createForward(LOCAL_PORT, REMOTE_SOCKET, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
              try {
                requestAndReadResponseAndUpdateListener();
              }
              finally {
                myDevice.removeForward(LOCAL_PORT, REMOTE_SOCKET, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
              }
            }
            finally {
              uninstallApk();
            }
          }
          catch (Exception ex) {
            myAcceptor.onException(ex);
          }
        }
      }

      private int getProtocolVersionFromApk() {
        try {
          File apk = GapiPaths.pkgInfoApk();
          if (!apk.exists()) {
            return 1;
          }
          VirtualFile apkFile = LocalFileSystem.getInstance().findFileByIoFile(apk);
          VirtualFile apkRoot = ApkFileSystem.getInstance().getRootByLocal(apkFile);
          if (apkRoot == null) {
            return 1;
          }
          VirtualFile manifest = apkRoot.findChild(SdkConstants.FN_ANDROID_MANIFEST_XML);
          return AndroidManifest.getVersionCode(new VirtualFileWrapper(myProject, manifest));
        } catch (Exception ex) {
          LOG.warn("Could not determine version of pkginfo.apk", ex);
          return 1;
        }
      }

      private void installApk() throws Exception {
        File apk = GapiPaths.pkgInfoApk();
        if (!apk.exists()) {
          throw new RuntimeException("pkginfo.apk not found at " + apk.getAbsolutePath());
        }

        try {
          myDevice.installPackage(apk.getAbsolutePath(), true);
        }
        catch (InstallException e) {
          throw new RuntimeException("Failed to install pkginfo.apk: " + e.getMessage());
        }
      }

      private void uninstallApk() {
        try {
          myDevice.uninstallPackage(PKGINFO_PACKAGE);
        }
        catch (InstallException e) {
          // There's nothing we can do about this apart from log.
          LOG.warn("Failed to uninstall pkginfo: " + e.getMessage());
        }
      }

      private String readStringFromStream(DataInputStream dis) throws IOException {
        byte[] buffer = new byte[dis.readInt()];
        dis.readFully(buffer);
        return new String(buffer, StandardCharsets.UTF_8);
      }

      private void requestAndReadResponseAndUpdateListener() throws Exception {
        int protocolVersion = Math.min(2 /* protocol version this method can handle */, getProtocolVersionFromApk());
        Exception lastException = null;
        for (int i = 0; i < READ_SOCKET_MAX_RETRIES; i++) {
          Thread.sleep(READ_SOCKET_DELAY_MS);
          sendIntent(protocolVersion);
          Socket socket = new Socket("localhost", LOCAL_PORT);
          try {
            InputStream in = socket.getInputStream();
            String response;
            switch (protocolVersion) {
              case 1:
                response = StreamUtil.readText(in, StandardCharsets.UTF_8);
                if (response.length() > 0) {
                  Files.write(response, getPackageCacheFile(myDevice), StandardCharsets.UTF_8);
                  myAcceptor.onDeviceInfoReceived(new Gson().fromJson(response, Info.class).get(myIconWidth, myIconHeight));
                  myAcceptor.onFinished();
                  return;
                }
              default:
                DataInputStream dis = new DataInputStream(in);
                myAcceptor
                  .onDeviceInfoReceived(new Gson().fromJson(readStringFromStream(dis), Info.class).get(myIconWidth, myIconHeight));

                // Final version with icons.
                response = readStringFromStream(dis);
                Files.write(response, getPackageCacheFile(myDevice), Charset.defaultCharset());
                myAcceptor.onDeviceInfoReceived(new Gson().fromJson(response, Info.class).get(myIconWidth, myIconHeight));

                myAcceptor.onFinished();
                return;
            }
          } catch (Exception ex) {
            lastException = ex;
          } finally {
            socket.close();
          }
        }
        throw new Exception("Error getting package information", lastException);
      }

      private String getAdbCommand(int protocolVersion) {
        String extraArg = " ";
        try {
          extraArg = " --ez " + EXTRA_ONLY_DEBUG + " " + !myDevice.isRoot();
        } catch (Exception ex) {
          // ignore.
        }
        if (protocolVersion != 1) {
          extraArg += " --ei " + EXTRA_PROTOCOL_VERSION + " " + protocolVersion;
        }
        return "am startservice -n " + PKGINFO_PACKAGE + "/." + PKGINFO_SERVICE + extraArg + " -a " + PKGINFO_ACTION;
      }

      private void sendIntent(int protocolVersion) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        String adbCommand = getAdbCommand(protocolVersion);
        myDevice.executeShellCommand(adbCommand, receiver);
        latch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      }
    }

    /**
     * Info matches the JSON encoded representation of the {@link DeviceInfo} structure.
     */
    public static class Info {
      public PackageInfo[] Packages;
      public String[] Icons;

      public DeviceInfo get(int iconWidth, int iconHeight) {
        Icon[] icons = new Icon[Icons.length];
        for (int i = 0; i < Icons.length; i++) {
          byte[] bytes = DatatypeConverter.parseBase64Binary(Icons[i]);
          ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
          try {
            Image img = ImageIO.read(bin);
            if (UIUtil.isRetina()) {
              img = img.getScaledInstance(iconWidth * 2, iconHeight * 2, Image.SCALE_SMOOTH);
              img = new JBHiDPIScaledImage(img, iconWidth, iconHeight, BufferedImage.TYPE_INT_ARGB);
            }
            else {
              img = img.getScaledInstance(iconWidth, iconHeight, Image.SCALE_DEFAULT);
            }
            icons[i] = new JBImageIcon(img);
          }
          catch (IOException e) {
          }
        }

        Package[] pkgs = new Package[Packages.length];
        for (int i = 0; i < Packages.length; i++) {
          pkgs[i] = Packages[i].get(icons);
        }
        return new DeviceInfo(pkgs);
      }
    }

    /**
     * PackageInfo matches the JSON encoded representation of the {@link Package} structure.
     */
    public static class PackageInfo {
      public String Name;
      public int Icon;
      public String ABI;
      public ActivityInfo[] Activities;
      public boolean Debuggable;

      public Package get(Icon icons[]) {
        Icon icon = null;
        if (Icon >= 0) {
          icon = icons[Icon];
        }
        Activity activities[] = new Activity[Activities.length];
        for (int i = 0; i < Activities.length; i++) {
          activities[i] = Activities[i].get(icons);
        }
        return new Package(Name, icon, ABI, activities);
      }
    }

    /**
     * ActivityInfo matches the JSON encoded representation of the {@link Activity} structure.
     */
    public static class ActivityInfo {
      public String Name;
      public boolean IsLaunch;
      public int Icon;

      public Activity get(Icon icons[]) {
        Icon icon = null;
        if (Icon >= 0) {
          icon = icons[Icon];
        }
        return new Activity(Name, IsLaunch, icon);
      }
    }
  }
}
