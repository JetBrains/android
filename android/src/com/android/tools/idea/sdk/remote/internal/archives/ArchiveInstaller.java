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

package com.android.tools.idea.sdk.remote.internal.archives;

import com.android.SdkConstants;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.annotations.VisibleForTesting.Visibility;
import com.android.sdklib.SdkManager;
import com.android.sdklib.io.FileOp;
import com.android.sdklib.io.IFileOp;
import com.android.sdklib.repository.local.LocalPkgInfo;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.DownloadCache;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.sources.RepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import com.android.utils.GrabProcessOutput;
import com.android.utils.GrabProcessOutput.IProcessOutput;
import com.android.utils.GrabProcessOutput.Wait;
import com.android.utils.Pair;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;

import java.io.*;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Performs the work of installing a given {@link Archive}.
 */
public class ArchiveInstaller {

  private static final String PROP_STATUS_CODE = "StatusCode";                    //$NON-NLS-1$
  public static final String ENV_VAR_IGNORE_COMPAT = "ANDROID_SDK_IGNORE_COMPAT"; //$NON-NLS-1$

  public static final int NUM_MONITOR_INC = 100;

  /**
   * The current {@link FileOp} to use. Never null.
   */
  private final IFileOp mFileOp;

  /**
   * Generates an {@link ArchiveInstaller} that relies on the default {@link FileOp}.
   */
  public ArchiveInstaller() {
    mFileOp = new FileOp();
  }

  /**
   * Install this {@link ArchiveReplacement}s.
   * A "replacement" is composed of the actual new archive to install
   * (c.f. {@link ArchiveReplacement#getNewArchive()} and an <em>optional</em>
   * archive being replaced (c.f. {@link ArchiveReplacement#getReplaced()}.
   * In the case of a new install, the later should be null.
   * <p/>
   * The new archive to install will be skipped if it is incompatible.
   *
   * @return True if the archive was installed, false otherwise.
   */
  public boolean install(ArchiveReplacement archiveInfo,
                         String osSdkRoot,
                         boolean forceHttp,
                         SdkManager sdkManager,
                         DownloadCache cache,
                         ITaskMonitor monitor) {

    Archive newArchive = archiveInfo.getNewArchive();
    RemotePkgInfo pkg = newArchive.getParentPackage();

    String name = pkg.getShortDescription();

    // In detail mode, give us a way to force install of incompatible archives.
    boolean checkIsCompatible = System.getenv(ENV_VAR_IGNORE_COMPAT) == null;

    if (checkIsCompatible && !newArchive.isCompatible()) {
      monitor.log("Skipping incompatible archive: %1$s for %2$s", name, newArchive.getOsDescription());
      return false;
    }

    Pair<File, File> files = downloadFile(newArchive, osSdkRoot, cache, monitor, forceHttp);
    File tmpFile = files == null ? null : files.getFirst();
    File propsFile = files == null ? null : files.getSecond();
    if (tmpFile != null) {
      // Unarchive calls the pre/postInstallHook methods.
      if (unarchive(archiveInfo, osSdkRoot, tmpFile, sdkManager, monitor)) {
        monitor.log("Installed %1$s", name);
        // Delete the temp archive if it exists, only on success
        mFileOp.deleteFileOrFolder(tmpFile);
        mFileOp.deleteFileOrFolder(propsFile);
        return true;
      }
    }

    return false;
  }

  /**
   * Downloads an archive and returns the temp file with it.
   * Caller is responsible with deleting the temp file when done.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected Pair<File, File> downloadFile(Archive archive, String osSdkRoot, DownloadCache cache, ITaskMonitor monitor, boolean forceHttp) {

    String pkgName = archive.getParentPackage().getShortDescription();
    monitor.setDescription("Downloading %1$s", pkgName);
    monitor.log("Downloading %1$s", pkgName);

    String link = archive.getUrl();
    if (!link.startsWith("http://")                          //$NON-NLS-1$
        && !link.startsWith("https://")                  //$NON-NLS-1$
        && !link.startsWith("ftp://")) {                 //$NON-NLS-1$
      // Make the URL absolute by prepending the source
      RemotePkgInfo pkg = archive.getParentPackage();
      SdkSource src = pkg.getParentSource();
      if (src == null) {
        monitor.logError("Internal error: no source for archive %1$s", pkgName);
        return null;
      }

      // take the URL to the repository.xml and remove the last component
      // to get the base
      String repoXml = src.getUrl();
      int pos = repoXml.lastIndexOf('/');
      String base = repoXml.substring(0, pos + 1);

      link = base + link;
    }

    if (forceHttp) {
      link = link.replaceAll("https://", "http://");  //$NON-NLS-1$ //$NON-NLS-2$
    }

    // Get the basename of the file we're downloading, i.e. the last component
    // of the URL
    int pos = link.lastIndexOf('/');
    String base = link.substring(pos + 1);

    // Rather than create a real temp file in the system, we simply use our
    // temp folder (in the SDK base folder) and use the archive name for the
    // download. This allows us to reuse or continue downloads.

    File tmpFolder = getTempFolder(osSdkRoot);
    if (!mFileOp.isDirectory(tmpFolder)) {
      if (mFileOp.isFile(tmpFolder)) {
        mFileOp.deleteFileOrFolder(tmpFolder);
      }
      if (!mFileOp.mkdirs(tmpFolder)) {
        monitor.logError("Failed to create directory %1$s", tmpFolder.getPath());
        return null;
      }
    }
    File tmpFile = new File(tmpFolder, base);

    // property file were we'll keep partial/resume information for reuse.
    File propsFile = new File(tmpFolder, base + ".inf"); //$NON-NLS-1$

    // if the file exists, check its checksum & size. Use it if complete
    if (mFileOp.exists(tmpFile)) {
      if (mFileOp.length(tmpFile) == archive.getSize()) {
        String chksum = "";                             //$NON-NLS-1$
        try {
          chksum = fileChecksum(archive.getChecksumType().getMessageDigest(), tmpFile, monitor);
        }
        catch (NoSuchAlgorithmException e) {
          // Ignore.
        }
        if (chksum.equalsIgnoreCase(archive.getChecksum())) {
          // File is good, let's use it.
          return Pair.of(tmpFile, propsFile);
        }
        else {
          // The file has the right size but the wrong content.
          // Just remove it and this will trigger a full download below.
          mFileOp.deleteFileOrFolder(tmpFile);
        }
      }
    }

    Header[] resumeHeaders = preparePartialDownload(archive, tmpFile, propsFile);

    if (fetchUrl(archive, resumeHeaders, tmpFile, propsFile, link, pkgName, cache, monitor)) {
      // Fetching was successful, let's use this file.
      return Pair.of(tmpFile, propsFile);
    }
    return null;
  }

  /**
   * Prepares to do a partial/resume download.
   *
   * @param archive   The archive we're trying to download.
   * @param tmpFile   The destination file to download (e.g. something.zip)
   * @param propsFile A properties file generated by the last partial download (e.g. .zip.inf)
   * @return Null in case we should perform a full download, or a set of headers
   * to resume a partial download.
   */
  private Header[] preparePartialDownload(Archive archive, File tmpFile, File propsFile) {
    // We need both the destination file and its properties to do a resume.
    if (mFileOp.isFile(tmpFile) && mFileOp.isFile(propsFile)) {
      // The caller already checked the case were the destination file has the
      // right size _and_ checksum, so we know at this point one of them is wrong
      // here.
      // We can obviously only resume a file if its size is smaller than expected.
      if (mFileOp.length(tmpFile) < archive.getSize()) {
        Properties props = mFileOp.loadProperties(propsFile);

        List<Header> headers = new ArrayList<Header>(2);
        headers.add(new BasicHeader(HttpHeaders.RANGE, String.format("bytes=%d-", mFileOp.length(tmpFile))));

        // Don't use the properties if there's not at least a 200 or 206 code from
        // the last download.
        int status = 0;
        try {
          status = Integer.parseInt(props.getProperty(PROP_STATUS_CODE));
        }
        catch (Exception ignore) {
        }

        if (status == HttpStatus.SC_OK || status == HttpStatus.SC_PARTIAL_CONTENT) {
          // Do we have an ETag and/or a Last-Modified?
          String etag = props.getProperty(HttpHeaders.ETAG);
          String lastMod = props.getProperty(HttpHeaders.LAST_MODIFIED);

          if (etag != null && etag.length() > 0) {
            headers.add(new BasicHeader(HttpHeaders.IF_MATCH, etag));
          }
          else if (lastMod != null && lastMod.length() > 0) {
            headers.add(new BasicHeader(HttpHeaders.IF_MATCH, lastMod));
          }

          return headers.toArray(new Header[headers.size()]);
        }
      }
    }

    // Existing file is either of different size or content.
    // Remove the existing file and request a full download.
    mFileOp.deleteFileOrFolder(tmpFile);
    mFileOp.deleteFileOrFolder(propsFile);

    return null;
  }

  /**
   * Computes the SHA-1 checksum of the content of the given file.
   * Returns an empty string on error (rather than null).
   */
  private String fileChecksum(MessageDigest digester, File tmpFile, ITaskMonitor monitor) {
    InputStream is = null;
    try {
      is = new FileInputStream(tmpFile);

      byte[] buf = new byte[65536];
      int n;

      while ((n = is.read(buf)) >= 0) {
        if (n > 0) {
          digester.update(buf, 0, n);
        }
      }

      return getDigestChecksum(digester);

    }
    catch (FileNotFoundException e) {
      // The FNF message is just the URL. Make it a bit more useful.
      monitor.logError("File not found: %1$s", e.getMessage());

    }
    catch (Exception e) {
      monitor.logError("%1$s", e.getMessage());   //$NON-NLS-1$

    }
    finally {
      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          // pass
        }
      }
    }

    return "";  //$NON-NLS-1$
  }

  /**
   * Returns the SHA-1 from a {@link MessageDigest} as an hex string
   * that can be compared with {@link Archive#getChecksum()}.
   */
  private String getDigestChecksum(MessageDigest digester) {
    int n;
    // Create an hex string from the digest
    byte[] digest = digester.digest();
    n = digest.length;
    String hex = "0123456789abcdef";                     //$NON-NLS-1$
    char[] hexDigest = new char[n * 2];
    for (int i = 0; i < n; i++) {
      int b = digest[i] & 0x0FF;
      hexDigest[i * 2 + 0] = hex.charAt(b >>> 4);
      hexDigest[i * 2 + 1] = hex.charAt(b & 0x0f);
    }

    return new String(hexDigest);
  }

  /**
   * Actually performs the download.
   * Also computes the SHA1 of the file on the fly.
   * <p/>
   * Success is defined as downloading as many bytes as was expected and having the same
   * SHA1 as expected. Returns true on success or false if any of those checks fail.
   * <p/>
   * Increments the monitor by {@link #NUM_MONITOR_INC}.
   *
   * @param archive       The archive we're trying to download.
   * @param resumeHeaders The headers to use for a partial resume, or null when fetching
   *                      a whole new file.
   * @param tmpFile       The destination file to download (e.g. something.zip)
   * @param propsFile     A properties file generated by the last partial download (e.g. .zip.inf)
   * @param urlString     The URL as a string
   * @param pkgName       The archive's package name, used for progress output.
   * @param cache         The {@link DownloadCache} instance to use.
   * @param monitor       The monitor to output the progress and errors.
   * @return True if we fetched the file successfully.
   * False if the download failed or was aborted.
   */
  private boolean fetchUrl(Archive archive,
                           Header[] resumeHeaders,
                           File tmpFile,
                           File propsFile,
                           String urlString,
                           String pkgName,
                           DownloadCache cache,
                           ITaskMonitor monitor) {

    FileOutputStream os = null;
    InputStream is = null;
    int inc_remain = NUM_MONITOR_INC;
    try {
      Pair<InputStream, HttpURLConnection> result = cache.openDirectUrl(urlString, resumeHeaders, monitor);

      is = result.getFirst();
      HttpURLConnection connection = result.getSecond();
      int status = connection.getResponseCode();
      if (status == HttpStatus.SC_NOT_FOUND) {
        throw new Exception("URL not found.");
      }
      if (is == null) {
        throw new Exception("No content.");
      }


      Properties props = new Properties();
      props.setProperty(PROP_STATUS_CODE, Integer.toString(status));
      String etag = connection.getHeaderField(HttpHeaders.ETAG);
      if (etag != null) {
        props.setProperty(HttpHeaders.ETAG, etag);
      }
      String lastModified = connection.getHeaderField(HttpHeaders.LAST_MODIFIED);
      if (lastModified != null) {
        props.setProperty(HttpHeaders.LAST_MODIFIED, lastModified);
      }

      try {
        mFileOp.saveProperties(propsFile, props, "## Android SDK Download.");  //$NON-NLS-1$
      }
      catch (IOException ignore) {
      }

      // On success, status can be:
      // - 206 (Partial content), if resumeHeaders is not null (we asked for a partial
      //   download, and we get partial content for that download => we'll need to append
      //   to the existing file.)
      // - 200 (OK) meaning we're getting whole new content from scratch. This can happen
      //   even if resumeHeaders is not null (typically means the server has a new version
      //   of the file to serve.) In this case we reset the file and write from scratch.

      boolean append = status == HttpStatus.SC_PARTIAL_CONTENT;
      if (status != HttpStatus.SC_OK && !(append && resumeHeaders != null)) {
        throw new Exception(String.format("Unexpected HTTP Status %1$d", status));
      }
      MessageDigest digester = archive.getChecksumType().getMessageDigest();

      if (append) {
        // Seed the digest with the existing content.
        InputStream temp = null;
        try {
          temp = new FileInputStream(tmpFile);

          byte[] buf = new byte[65536];
          int n;

          while ((n = temp.read(buf)) >= 0) {
            if (n > 0) {
              digester.update(buf, 0, n);
            }
          }
        }
        catch (Exception ignore) {
        }
        finally {
          if (temp != null) {
            try {
              temp.close();
            }
            catch (IOException ignore) {
            }
          }
        }
      }

      // Open the output stream in append for a resume, or reset for a full download.
      os = new FileOutputStream(tmpFile, append);

      byte[] buf = new byte[65536];
      int n;

      long total = 0;
      long size = archive.getSize();
      if (append) {
        long len = mFileOp.length(tmpFile);
        int percent = (int)(len * 100 / size);
        size -= len;
        monitor.logVerbose("Resuming %1$s download at %2$d (%3$d%%)", pkgName, len, percent);
      }
      long inc = size / NUM_MONITOR_INC;
      long next_inc = inc;

      long startMs = System.currentTimeMillis();
      long nextMs = startMs + 2000;  // start update after 2 seconds

      while ((n = is.read(buf)) >= 0) {
        if (n > 0) {
          os.write(buf, 0, n);
          digester.update(buf, 0, n);
        }

        long timeMs = System.currentTimeMillis();

        total += n;
        if (total >= next_inc) {
          monitor.incProgress(1);
          inc_remain--;
          next_inc += inc;
        }

        if (timeMs > nextMs) {
          long delta = timeMs - startMs;
          if (total > 0 && delta > 0) {
            // percent left to download
            int percent = (int)(100 * total / size);
            // speed in KiB/s
            float speed = (float)total / (float)delta * (1000.f / 1024.f);
            // time left to download the rest at the current KiB/s rate
            int timeLeft = (speed > 1e-3) ? (int)(((size - total) / 1024.0f) / speed) : 0;
            String timeUnit = "seconds";
            if (timeLeft > 120) {
              timeUnit = "minutes";
              timeLeft /= 60;
            }

            monitor.setDescription("Downloading %1$s (%2$d%%, %3$.0f KiB/s, %4$d %5$s left)", pkgName, percent, speed, timeLeft, timeUnit);
          }
          nextMs = timeMs + 1000;  // update every second
        }

        if (monitor.isCancelRequested()) {
          monitor.log("Download aborted by user at %1$d bytes.", total);
          return false;
        }

      }

      if (total != size) {
        monitor.logError("Download finished with wrong size. Expected %1$d bytes, got %2$d bytes.", size, total);
        return false;
      }

      // Create an hex string from the digest
      String actual = getDigestChecksum(digester);
      String expected = archive.getChecksum();
      if (!actual.equalsIgnoreCase(expected)) {
        monitor.logError("Download finished with wrong checksum. Expected %1$s, got %2$s.", expected, actual);
        return false;
      }

      return true;

    }
    catch (ProcessCanceledException e) {
      // HTTP Basic Auth or NTLM login was canceled by user.
      // Don't output an error in the log.
      throw e;
    }
    catch (FileNotFoundException e) {
      // The FNF message is just the URL. Make it a bit more useful.
      monitor.logError("URL not found: %1$s", e.getMessage());

    }
    catch (Exception e) {
      monitor.logError("Download interrupted: %1$s", e.getMessage());   //$NON-NLS-1$

    }
    finally {
      if (os != null) {
        try {
          os.close();
        }
        catch (IOException e) {
          // pass
        }
      }

      if (is != null) {
        try {
          is.close();
        }
        catch (IOException e) {
          // pass
        }
      }
      if (inc_remain > 0) {
        monitor.incProgress(inc_remain);
      }
    }

    return false;
  }

  /**
   * Install the given archive in the given folder.
   */
  private boolean unarchive(ArchiveReplacement archiveInfo,
                            String osSdkRoot,
                            File archiveFile,
                            SdkManager sdkManager,
                            ITaskMonitor monitor) {
    boolean success = false;
    Archive newArchive = archiveInfo.getNewArchive();
    RemotePkgInfo pkg = newArchive.getParentPackage();
    String pkgName = pkg.getShortDescription();
    monitor.setDescription("Installing %1$s", pkgName);
    monitor.log("Installing %1$s", pkgName);

    // Ideally we want to always unzip in a temp folder which name depends on the package
    // type (e.g. addon, tools, etc.) and then move the folder to the destination folder.
    // If the destination folder exists, it will be renamed and deleted at the very
    // end if everything succeeded. This provides a nice atomic swap and should leave the
    // original folder untouched in case something wrong (e.g. program crash) in the
    // middle of the unzip operation.
    //
    // However that doesn't work on Windows, we always end up not being able to move the
    // new folder. There are actually 2 cases:
    // A- A process such as a the explorer is locking the *old* folder or a file inside
    //    (e.g. adb.exe)
    //    In this case we really shouldn't be tried to work around it and we need to let
    //    the user know and let it close apps that access that folder.
    // B- A process is locking the *new* folder. Very often this turns to be a file indexer
    //    or an anti-virus that is busy scanning the new folder that we just unzipped.
    //
    // So we're going to change the strategy:
    // 1- Try to move the old folder to a temp/old folder. This might fail in case of issue A.
    //    Note: for platform-tools, we can try killing adb first.
    //    If it still fails, we do nothing and ask the user to terminate apps that can be
    //    locking that folder.
    // 2- Once the old folder is out of the way, we unzip the archive directly into the
    //    optimal new location. We no longer unzip it in a temp folder and move it since we
    //    know that's what fails in most of the cases.
    // 3- If the unzip fails, remove everything and try to restore the old folder by doing
    //    a *copy* in place and not a folder move (which will likely fail too).

    String pkgKind = pkg.getClass().getSimpleName();

    File destFolder = null;
    File oldDestFolder = null;

    try {
      // -0- Compute destination directory and check install pre-conditions

      destFolder = pkg.getInstallFolder(osSdkRoot, sdkManager);

      if (destFolder == null) {
        // this should not seriously happen.
        monitor.log("Failed to compute installation directory for %1$s.", pkgName);
        return false;
      }

      if (!pkg.preInstallHook(newArchive, monitor, osSdkRoot, destFolder)) {
        monitor.log("Skipping archive: %1$s", pkgName);
        return false;
      }

      // -1- move old folder.

      if (mFileOp.exists(destFolder)) {
        // Create a new temp/old dir
        if (oldDestFolder == null) {
          oldDestFolder = getNewTempFolder(osSdkRoot, pkgKind, "old");  //$NON-NLS-1$
        }
        if (oldDestFolder == null) {
          // this should not seriously happen.
          monitor.logError("Failed to find a temp directory in %1$s.", osSdkRoot);
          return false;
        }

        // Try to move the current dest dir to the temp/old one. Tell the user if it failed.
        while (true) {
          if (!moveFolder(destFolder, oldDestFolder)) {
            monitor.logError("Failed to rename directory %1$s to %2$s.", destFolder.getPath(), oldDestFolder.getPath());

            if (SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS) {
              boolean tryAgain = true;

              tryAgain = windowsDestDirLocked(osSdkRoot, destFolder, monitor);

              if (tryAgain) {
                // loop, trying to rename the temp dir into the destination
                continue;
              }
              else {
                return false;
              }
            }
          }
          break;
        }
      }

      assert !mFileOp.exists(destFolder);

      // -2- Unzip new content directly in place.

      if (!mFileOp.mkdirs(destFolder)) {
        monitor.logError("Failed to create directory %1$s", destFolder.getPath());
        return false;
      }

      if (!unzipFolder(archiveInfo, archiveFile, destFolder, monitor)) {
        return false;
      }

      if (!generateSourceProperties(newArchive, destFolder)) {
        monitor.logError("Failed to generate source.properties in directory %1$s", destFolder.getPath());
        return false;
      }

      // In case of success, if we were replacing an archive
      // and the older one had a different path, remove it now.
      LocalPkgInfo oldArchive = archiveInfo.getReplaced();
      if (oldArchive != null) {
        File oldFolder = oldArchive.getLocalDir();
        if (mFileOp.exists(oldFolder) &&
            !oldFolder.equals(destFolder)) {
          monitor.logVerbose("Removing old archive at %1$s", oldFolder.getAbsolutePath());
          mFileOp.deleteFileOrFolder(oldFolder);
        }
      }

      success = true;
      pkg.postInstallHook(newArchive, monitor, destFolder);
      return true;

    }
    finally {
      if (!success) {
        // In case of failure, we try to restore the old folder content.
        if (oldDestFolder != null) {
          restoreFolder(oldDestFolder, destFolder);
        }

        // We also call the postInstallHool with a null directory to give a chance
        // to the archive to cleanup after preInstallHook.
        pkg.postInstallHook(newArchive, monitor, null /*installDir*/);
      }

      // Cleanup if the unzip folder is still set.
      mFileOp.deleteFileOrFolder(oldDestFolder);
    }
  }

  private boolean windowsDestDirLocked(String osSdkRoot, File destFolder, final ITaskMonitor monitor) {
    String msg = null;

    assert SdkConstants.CURRENT_PLATFORM == SdkConstants.PLATFORM_WINDOWS;

    File findLockExe = FileOp.append(osSdkRoot, SdkConstants.FD_TOOLS, SdkConstants.FD_LIB, SdkConstants.FN_FIND_LOCK);

    if (mFileOp.exists(findLockExe)) {
      try {
        final StringBuilder result = new StringBuilder();
        String command[] = new String[]{findLockExe.getAbsolutePath(), destFolder.getAbsolutePath()};
        Process process = Runtime.getRuntime().exec(command);
        int retCode = GrabProcessOutput.grabProcessOutput(process, Wait.WAIT_FOR_READERS, new IProcessOutput() {
          @Override
          public void out(@Nullable String line) {
            if (line != null) {
              result.append(line).append("\n");
            }
          }

          @Override
          public void err(@Nullable String line) {
            if (line != null) {
              monitor.logError("[find_lock] Error: %1$s", line);
            }
          }
        });

        if (retCode == 0 && result.length() > 0) {
          // TODO create a better dialog

          String found = result.toString().trim();
          monitor.logError("[find_lock] Directory locked by %1$s", found);

          TreeSet<String> apps = new TreeSet<String>(Arrays.asList(found.split(Pattern.quote(";"))));  //$NON-NLS-1$
          StringBuilder appStr = new StringBuilder();
          for (String app : apps) {
            appStr.append("\n  - ").append(app.trim());                //$NON-NLS-1$
          }

          msg = String.format("-= Warning ! =-\n" +
                              "The following processes: %1$s\n" +
                              "are locking the following directory: \n" +
                              "  %2$s\n" +
                              "Please close these applications so that the installation can continue.\n" +
                              "When ready, press YES to try again.", appStr.toString(), destFolder.getPath());
        }

      }
      catch (Exception e) {
        monitor.error(e, "[find_lock failed]");
      }


    }

    if (msg == null) {
      // Old way: simply display a generic text and let user figure it out.
      msg = String.format("-= Warning ! =-\n" +
                          "A folder failed to be moved. On Windows this " +
                          "typically means that a program is using that folder (for " +
                          "example Windows Explorer or your anti-virus software.)\n" +
                          "Please momentarily deactivate your anti-virus software or " +
                          "close any running programs that may be accessing the " +
                          "directory '%1$s'.\n" +
                          "When ready, press YES to try again.", destFolder.getPath());
    }

    boolean tryAgain = monitor.displayPrompt("SDK Manager: failed to install", msg);
    return tryAgain;
  }

  /**
   * Tries to rename/move a folder.
   * <p/>
   * Contract:
   * <ul>
   * <li> When we start, oldDir must exist and be a directory. newDir must not exist. </li>
   * <li> On successful completion, oldDir must not exists.
   * newDir must exist and have the same content. </li>
   * <li> On failure completion, oldDir must have the same content as before.
   * newDir must not exist. </li>
   * </ul>
   * <p/>
   * The simple "rename" operation on a folder can typically fail on Windows for a variety
   * of reason, in fact as soon as a single process holds a reference on a directory. The
   * most common case are the Explorer, the system's file indexer, Tortoise SVN cache or
   * an anti-virus that are busy indexing a new directory having been created.
   *
   * @param oldDir The old location to move. It must exist and be a directory.
   * @param newDir The new location where to move. It must not exist.
   * @return True if the move succeeded. On failure, we try hard to not have touched the old
   * directory in order not to loose its content.
   */
  private boolean moveFolder(File oldDir, File newDir) {
    // This is a simple folder rename that works on Linux/Mac all the time.
    //
    // On Windows this might fail if an indexer is busy looking at a new directory
    // (e.g. right after we unzip our archive), so it fails let's be nice and give
    // it a bit of time to succeed.
    for (int i = 0; i < 5; i++) {
      if (mFileOp.renameTo(oldDir, newDir)) {
        return true;
      }
      try {
        Thread.sleep(500 /*ms*/);
      }
      catch (InterruptedException e) {
        // ignore
      }
    }

    return false;
  }

  /**
   * Unzips a zip file into the given destination directory.
   * <p/>
   * The archive file MUST have a unique "root" folder.
   * This root folder is skipped when unarchiving.
   */
  @SuppressWarnings("unchecked")
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected boolean unzipFolder(ArchiveReplacement archiveInfo, File archiveFile, File unzipDestFolder, ITaskMonitor monitor) {

    Archive newArchive = archiveInfo.getNewArchive();
    RemotePkgInfo pkg = newArchive.getParentPackage();
    String pkgName = pkg.getShortDescription();
    long compressedSize = newArchive.getSize();

    ZipFile zipFile = null;
    try {
      zipFile = new ZipFile(archiveFile);

      // To advance the percent and the progress bar, we don't know the number of
      // items left to unzip. However we know the size of the archive and the size of
      // each uncompressed item. The zip file format overhead is negligible so that's
      // a good approximation.
      long incStep = compressedSize / NUM_MONITOR_INC;
      long incTotal = 0;
      long incCurr = 0;
      int lastPercent = 0;

      byte[] buf = new byte[65536];

      Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
      while (entries.hasMoreElements()) {
        ZipArchiveEntry entry = entries.nextElement();

        String name = entry.getName();

        // ZipFile entries should have forward slashes, but not all Zip
        // implementations can be expected to do that.
        name = name.replace('\\', '/');

        // Zip entries are always packages in a top-level directory (e.g. docs/index.html).
        int pos = name.indexOf('/');
        if (pos == -1) {
          // All zip entries should have a root folder.
          // This zip entry seems located at the root of the zip.
          // Rather than ignore the file, just place it at the root.
        }
        else if (pos == name.length() - 1) {
          // This is a zip *directory* entry in the form dir/, so essentially
          // it's the root directory of the SDK. It's safe to ignore that one
          // since we want to use our own root directory and we'll recreate
          // root directories as needed.
          // A direct consequence is that if a malformed archive has multiple
          // root directories, their content will all be merged together.
          continue;
        }
        else {
          // This is the expected behavior: the zip entry is in the form root/file
          // or root/dir/. We want to use our top-level directory so we drop the
          // first segment of the path name.
          name = name.substring(pos + 1);
        }

        File destFile = new File(unzipDestFolder, name);

        if (name.endsWith("/")) {  //$NON-NLS-1$
          // Create directory if it doesn't exist yet. This allows us to create
          // empty directories.
          if (!mFileOp.isDirectory(destFile) && !mFileOp.mkdirs(destFile)) {
            monitor.logError("Failed to create directory %1$s", destFile.getPath());
            return false;
          }
          continue;
        }
        else if (name.indexOf('/') != -1) {
          // Otherwise it's a file in a sub-directory.

          // Sanity check: since we're always unzipping in a fresh temp folder
          // the destination file shouldn't already exist.
          if (mFileOp.exists(destFile)) {
            monitor.logVerbose("Duplicate file found:  %1$s", name);
          }

          // Make sure the parent directory has been created.
          File parentDir = destFile.getParentFile();
          if (!mFileOp.isDirectory(parentDir)) {
            if (!mFileOp.mkdirs(parentDir)) {
              monitor.logError("Failed to create directory %1$s", parentDir.getPath());
              return false;
            }
          }
        }

        FileOutputStream fos = null;
        long remains = entry.getSize();
        try {
          fos = new FileOutputStream(destFile);

          // Java bug 4040920: do not rely on the input stream EOF and don't
          // try to read more than the entry's size.
          InputStream entryContent = zipFile.getInputStream(entry);
          int n;
          while (remains > 0 && (n = entryContent.read(buf, 0, (int)Math.min(remains, buf.length))) != -1) {
            remains -= n;
            if (n > 0) {
              fos.write(buf, 0, n);
            }
          }
        }
        catch (EOFException e) {
          monitor.logError("Error uncompressing file %s. Size: %d bytes, Unwritten: %d bytes.", entry.getName(), entry.getSize(), remains);
          throw e;
        }
        finally {
          if (fos != null) {
            fos.close();
          }
        }

        pkg.postUnzipFileHook(newArchive, monitor, mFileOp, destFile, entry);

        // Increment progress bar to match. We update only between files.
        for (incTotal += entry.getCompressedSize(); incCurr < incTotal; incCurr += incStep) {
          monitor.incProgress(1);
        }

        int percent = (int)(100 * incTotal / compressedSize);
        if (percent != lastPercent) {
          monitor.setDescription("Unzipping %1$s (%2$d%%)", pkgName, percent);
          lastPercent = percent;
        }

        if (monitor.isCancelRequested()) {
          return false;
        }
      }

      return true;

    }
    catch (IOException e) {
      monitor.logError("Unzip failed: %1$s", e.getMessage());

    }
    finally {
      if (zipFile != null) {
        try {
          zipFile.close();
        }
        catch (IOException e) {
          // pass
        }
      }
    }

    return false;
  }

  /**
   * Returns an unused temp folder path in the form of osBasePath/temp/prefix.suffixNNN.
   * <p/>
   * This does not actually <em>create</em> the folder. It just scan the base path for
   * a free folder name to use and returns the file to use to reference it.
   * <p/>
   * This operation is not atomic so there's no guarantee the folder can't get
   * created in between. This is however unlikely and the caller can assume the
   * returned folder does not exist yet.
   * <p/>
   * Returns null if no such folder can be found (e.g. if all candidates exist,
   * which is rather unlikely) or if the base temp folder cannot be created.
   */
  private File getNewTempFolder(String osBasePath, String prefix, String suffix) {
    File baseTempFolder = getTempFolder(osBasePath);

    if (!mFileOp.isDirectory(baseTempFolder)) {
      if (mFileOp.isFile(baseTempFolder)) {
        mFileOp.deleteFileOrFolder(baseTempFolder);
      }
      if (!mFileOp.mkdirs(baseTempFolder)) {
        return null;
      }
    }

    for (int i = 1; i < 100; i++) {
      File folder = new File(baseTempFolder, String.format("%1$s.%2$s%3$02d", prefix, suffix, i));  //$NON-NLS-1$
      if (!mFileOp.exists(folder)) {
        return folder;
      }
    }
    return null;
  }

  /**
   * Returns the single fixed "temp" folder used by the SDK Manager.
   * This folder is always at osBasePath/temp.
   * <p/>
   * This does not actually <em>create</em> the folder.
   */
  private File getTempFolder(String osBasePath) {
    File baseTempFolder = new File(osBasePath, RepoConstants.FD_TEMP);
    return baseTempFolder;
  }

  /**
   * Generates a source.properties in the destination folder that contains all the infos
   * relevant to this archive, this package and the source so that we can reload them
   * locally later.
   */
  @VisibleForTesting(visibility = Visibility.PRIVATE)
  protected boolean generateSourceProperties(Archive archive, File unzipDestFolder) {

    // Create a version of Properties that returns a sorted key set.
    // This is used by Properties#saveProperties and should ensure the
    // properties are in a stable order. Unit tests rely on this fact.
    @SuppressWarnings("serial") Properties props = new Properties() {
      @Override
      public synchronized Enumeration<Object> keys() {
        Set<Object> sortedSet = new TreeSet<Object>(keySet());
        final Iterator<Object> it = sortedSet.iterator();
        return new Enumeration<Object>() {
          @Override
          public boolean hasMoreElements() {
            return it.hasNext();
          }

          @Override
          public Object nextElement() {
            return it.next();
          }

        };
      }
    };

    archive.saveProperties(props);

    RemotePkgInfo pkg = archive.getParentPackage();
    if (pkg != null) {
      pkg.saveProperties(props);
    }

    try {
      mFileOp.saveProperties(new File(unzipDestFolder, SdkConstants.FN_SOURCE_PROP), props,
                             "## Android Tool: Source of this archive.");  //$NON-NLS-1$
      return true;
    }
    catch (IOException ignore) {
      return false;
    }
  }

  /**
   * Recursively restore srcFolder into destFolder by performing a copy of the file
   * content rather than rename/moves.
   *
   * @param srcFolder  The source folder to restore.
   * @param destFolder The destination folder where to restore.
   * @return True if the folder was successfully restored, false if it was not at all or
   * only partially restored.
   */
  private boolean restoreFolder(File srcFolder, File destFolder) {
    boolean result = true;

    // Process sub-folders first
    File[] srcFiles = mFileOp.listFiles(srcFolder);
    if (srcFiles == null) {
      // Source does not exist. That is quite odd.
      return false;
    }

    if (mFileOp.isFile(destFolder)) {
      if (!mFileOp.delete(destFolder)) {
        // There's already a file in there where we want a directory and
        // we can't delete it. This is rather unexpected. Just give up on
        // that folder.
        return false;
      }
    }
    else if (!mFileOp.isDirectory(destFolder)) {
      mFileOp.mkdirs(destFolder);
    }

    // Get all the files and dirs of the current destination.
    // We are not going to clean up the destination first.
    // Instead we'll copy over and just remove any remaining files or directories.
    Set<File> destDirs = new HashSet<File>();
    Set<File> destFiles = new HashSet<File>();
    File[] files = mFileOp.listFiles(destFolder);
    if (files != null) {
      for (File f : files) {
        if (mFileOp.isDirectory(f)) {
          destDirs.add(f);
        }
        else {
          destFiles.add(f);
        }
      }
    }

    // First restore all source directories.
    for (File dir : srcFiles) {
      if (mFileOp.isDirectory(dir)) {
        File d = new File(destFolder, dir.getName());
        destDirs.remove(d);
        if (!restoreFolder(dir, d)) {
          result = false;
        }
      }
    }

    // Remove any remaining directories not processed above.
    for (File dir : destDirs) {
      mFileOp.deleteFileOrFolder(dir);
    }

    // Copy any source files over to the destination.
    for (File file : srcFiles) {
      if (mFileOp.isFile(file)) {
        File f = new File(destFolder, file.getName());
        destFiles.remove(f);
        try {
          mFileOp.copyFile(file, f);
        }
        catch (IOException e) {
          result = false;
        }
      }
    }

    // Remove any remaining files not processed above.
    for (File file : destFiles) {
      mFileOp.deleteFileOrFolder(file);
    }

    return result;
  }
}
