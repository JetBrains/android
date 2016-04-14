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

import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.JBHiDPIScaledImage;
import com.intellij.util.ui.JBImageIcon;
import com.intellij.util.ui.UIUtil;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.xml.bind.DatatypeConverter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
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
   * Provider is the interface implemented by types that can provide a {@link DeviceInfo}.
   */
  public interface Provider {
    ListenableFuture<DeviceInfo> getDeviceInfo(int iconWidth, int iconHeight);
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
    public final Icon myIcon;

    /**
     * The package's preferred CPU ABI.
     * Null if the package does not have a preferred CPU ABI.
     */
    public final String myABI;

    /**
     * The list of activities exposed by this package.
     */
    public final Activity[] myActivities;

    public Package(String name, Icon icon, String abi, Activity[] activities) {
      myName = name;
      myIcon = icon;
      myABI = abi;
      myActivities = activities;
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
    public final Icon myIcon;

    public Activity(String name, boolean isLaunch, Icon icon) {
      myName = name;
      myIsLaunch = isLaunch;
      myIcon = icon;
    }

    @Override
    public int compareTo(Activity other) {
      return myName.compareTo(other.myName);
    }
  }

  /**
   * PkgInfoProvider implements the Provider interface using the PkgInfo helper APK.
   */
  public static class PkgInfoProvider implements Provider {
    private static final int LATCH_TIMEOUT_MS = 3000;
    private static final int LOCAL_PORT = 3333;
    private static final String REMOTE_SOCKET = "pkginfo";
    private static final String PKGINFO_PACKAGE = "com.google.android.pkginfo";
    private static final String PKGINFO_SERVICE = "PkgInfoService";
    private static final String PKGINFO_ACTION = "com.google.android.pkginfo.action.SEND_PKG_INFO";
    private static final String ADB_COMMAND = "am startservice -n " +
                                              PKGINFO_PACKAGE + "/." + PKGINFO_SERVICE +
                                              " -a " + PKGINFO_ACTION;
    private static final int READ_SOCKET_MAX_RETRIES = 30;
    private static final int READ_SOCKET_DELAY_MS = 1000;

    @NotNull private static final Logger LOG = Logger.getInstance(PkgInfoProvider.class);


    public final IDevice myDevice;

    public PkgInfoProvider(IDevice device) {
      myDevice = device;
    }

    @Override
    public ListenableFuture<DeviceInfo> getDeviceInfo(int iconWidth, int iconHeight) {
      SettableFuture<DeviceInfo> future = SettableFuture.create();
      ApplicationManager.getApplication().executeOnPooledThread(new FetchDeviceInfo(myDevice, future, iconWidth, iconHeight));
      return future;
    }

    private static class FetchDeviceInfo implements Runnable {
      private final IDevice myDevice;
      private final SettableFuture<DeviceInfo> myFuture;
      private final int myIconWidth;
      private final int myIconHeight;

      public FetchDeviceInfo(IDevice device, SettableFuture<DeviceInfo> future, int iconWidth, int iconHeight) {
        myDevice = device;
        myFuture = future;
        myIconWidth = iconWidth;
        myIconHeight = iconHeight;
      }

      @Override
      public void run() {
        try {
          installApk();

          try {
            myDevice.createForward(LOCAL_PORT, REMOTE_SOCKET, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            try {
              myFuture.set(requestAndReadResponse().get(myIconWidth, myIconHeight));
            }
            finally {
              myDevice.removeForward(LOCAL_PORT, REMOTE_SOCKET, IDevice.DeviceUnixSocketNamespace.ABSTRACT);
            }
          }
          finally {
            uninstallApk();
          }
        }
        catch (Exception e) {
          myFuture.setException(e);
        }
      }

      private void installApk() throws Exception {
        File apk = GapiPaths.findPkgInfoApk();
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


      private Info requestAndReadResponse() throws Exception {
        for (int i = 0; i < READ_SOCKET_MAX_RETRIES; i++) {
          Thread.sleep(READ_SOCKET_DELAY_MS);
          sendIntent();
          Socket socket = new Socket("localhost", LOCAL_PORT);
          try {
            InputStream in = socket.getInputStream();
            StringWriter out = new StringWriter();
            IOUtils.copy(in, out);
            String response = out.toString();
            if (response.length() > 0) {
              return new Gson().fromJson(response, Info.class);
            }
          }
          finally {
            socket.close();
          }
        }
        throw new RuntimeException("Timeout waiting for package info");
      }

      private void sendIntent() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CollectingOutputReceiver receiver = new CollectingOutputReceiver(latch);
        myDevice.executeShellCommand(ADB_COMMAND, receiver);
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
