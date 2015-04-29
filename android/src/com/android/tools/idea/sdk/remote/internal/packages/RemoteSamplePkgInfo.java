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

package com.android.tools.idea.sdk.remote.internal.packages;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkManager;
import com.android.sdklib.io.IFileOp;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.MajorRevision;
import com.android.sdklib.repository.PkgProps;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.sdk.remote.internal.ITaskMonitor;
import com.android.tools.idea.sdk.remote.internal.archives.Archive;
import com.android.tools.idea.sdk.remote.internal.sources.SdkRepoConstants;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.w3c.dom.Node;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Properties;

/**
 * Represents a sample XML node in an SDK repository.
 */
public class RemoteSamplePkgInfo extends RemoteMinToolsPkgInfo implements IAndroidVersionProvider, IMinApiLevelDependency {

  /**
   * The minimal API level required by this extra package, if > 0,
   * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
   */
  private final int mMinApiLevel;

  /**
   * Creates a new sample package from the attributes and elements of the given XML node.
   * This constructor should throw an exception if the package cannot be created.
   *
   * @param source      The {@link SdkSource} where this is loaded from.
   * @param packageNode The XML element being parsed.
   * @param nsUri       The namespace URI of the originating XML document, to be able to deal with
   *                    parameters that vary according to the originating XML schema.
   * @param licenses    The licenses loaded from the XML originating document.
   */
  public RemoteSamplePkgInfo(SdkSource source, Node packageNode, String nsUri, Map<String, String> licenses) {
    super(source, packageNode, nsUri, licenses);

    int apiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_API_LEVEL, 0);
    String codeName = RemotePackageParserUtils.getXmlString(packageNode, SdkRepoConstants.NODE_CODENAME);
    if (codeName.length() == 0) {
      codeName = null;
    }
    AndroidVersion version = new AndroidVersion(apiLevel, codeName);

    mMinApiLevel = RemotePackageParserUtils.getXmlInt(packageNode, SdkRepoConstants.NODE_MIN_API_LEVEL, MIN_API_LEVEL_NOT_SPECIFIED);

    PkgDesc.Builder pkgDescBuilder = PkgDesc.Builder.newSample(version, new MajorRevision(getRevision()), getMinToolsRevision());
    pkgDescBuilder.setDescriptionShort(createShortDescription(mListDisplay, getRevision(), version, isObsolete()));
    pkgDescBuilder.setDescriptionUrl(getDescUrl());
    pkgDescBuilder.setListDisplay(createListDescription(mListDisplay, version, isObsolete()));
    pkgDescBuilder.setIsObsolete(isObsolete());
    pkgDescBuilder.setLicense(getLicense());
    mPkgDesc = pkgDescBuilder.create();
  }

  /**
   * Save the properties of the current packages in the given {@link Properties} object.
   * These properties will later be given to a constructor that takes a {@link Properties} object.
   */
  @Override
  public void saveProperties(Properties props) {
    super.saveProperties(props);

    getAndroidVersion().saveProperties(props);

    if (getMinApiLevel() != MIN_API_LEVEL_NOT_SPECIFIED) {
      props.setProperty(PkgProps.SAMPLE_MIN_API_LEVEL, Integer.toString(getMinApiLevel()));
    }
  }

  /**
   * Returns the minimal API level required by this extra package, if > 0,
   * or {@link #MIN_API_LEVEL_NOT_SPECIFIED} if there is no such requirement.
   */
  @Override
  public int getMinApiLevel() {
    return mMinApiLevel;
  }

  /**
   * Returns the matching platform version.
   */
  @Override
  @NonNull
  public AndroidVersion getAndroidVersion() {
    return getPkgDesc().getAndroidVersion();
  }

  /**
   * Returns a string identifier to install this package from the command line.
   * For samples, we use "sample-N" where N is the API or the preview codename.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public String installId() {
    return "sample-" + getAndroidVersion().getApiString();    //$NON-NLS-1$
  }

  /**
   * Returns a description of this package that is suitable for a list display.
   * <p/>
   */
  private static String createListDescription(String listDisplay, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s%2$s", listDisplay, obsolete ? " (Obsolete)" : "");
    }

    String s = String
      .format("Samples for SDK API %1$s%2$s%3$s", version.getApiString(), version.isPreview() ? " Preview" : "",
              obsolete ? " (Obsolete)" : "");
    return s;
  }

  /**
   * Returns a short description for an {@link IDescription}.
   */
  private static String createShortDescription(String listDisplay, FullRevision revision, AndroidVersion version, boolean obsolete) {
    if (!listDisplay.isEmpty()) {
      return String.format("%1$s, revision %2$s%3$s", listDisplay, revision.toShortString(), obsolete ? " (Obsolete)" : "");
    }

    String s = String
      .format("Samples for SDK API %1$s%2$s, revision %3$s%4$s", version.getApiString(), version.isPreview() ? " Preview" : "",
              revision.toShortString(), obsolete ? " (Obsolete)" : "");
    return s;
  }

  /**
   * Computes a potential installation folder if an archive of this package were
   * to be installed right away in the given SDK root.
   * <p/>
   * A sample package is typically installed in SDK/samples/android-"version".
   * However if we can find a different directory that already has this sample
   * version installed, we'll use that one.
   *
   * @param osSdkRoot  The OS path of the SDK root folder.
   * @param sdkManager An existing SDK manager to list current platforms and addons.
   * @return A new {@link File} corresponding to the directory to use to install this package.
   */
  @Override
  public File getInstallFolder(String osSdkRoot, SdkManager sdkManager) {

    // The /samples dir at the root of the SDK
    File samplesRoot = new File(osSdkRoot, SdkConstants.FD_SAMPLES);

    // First find if this sample is already installed. If so, reuse the same directory.
    for (IAndroidTarget target : sdkManager.getTargets()) {
      if (target.isPlatform() && target.getVersion().equals(getAndroidVersion())) {
        String p = target.getPath(IAndroidTarget.SAMPLES);
        File f = new File(p);
        if (f.isDirectory()) {
          // We *only* use this directory if it's using the "new" location
          // under SDK/samples. We explicitly do not reuse the "old" location
          // under SDK/platform/android-N/samples.
          if (f.getParentFile().equals(samplesRoot)) {
            return f;
          }
        }
      }
    }

    // Otherwise, get a suitable default
    File folder = new File(samplesRoot, String.format("android-%s", getAndroidVersion().getApiString())); //$NON-NLS-1$

    for (int n = 1; folder.exists(); n++) {
      // Keep trying till we find an unused directory.
      folder = new File(samplesRoot, String.format("android-%s_%d", getAndroidVersion().getApiString(), n)); //$NON-NLS-1$
    }

    return folder;
  }

  /**
   * Makes sure the base /samples folder exists before installing.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public boolean preInstallHook(Archive archive, ITaskMonitor monitor, String osSdkRoot, File installFolder) {

    if (installFolder != null && installFolder.isDirectory()) {
      // Get the hash computed during the last installation
      String storedHash = readContentHash(installFolder);
      if (storedHash != null && storedHash.length() > 0) {

        // Get the hash of the folder now
        String currentHash = computeContentHash(installFolder);

        if (!storedHash.equals(currentHash)) {
          // The hashes differ. The content was modified.
          // Ask the user if we should still wipe the old samples.

          String pkgName = archive.getParentPackage().getShortDescription();

          String msg = String.format("-= Warning ! =-\n" +
                                     "You are about to replace the content of the folder:\n " +
                                     "  %1$s\n" +
                                     "by the new package:\n" +
                                     "  %2$s.\n" +
                                     "\n" +
                                     "However it seems that the content of the existing samples " +
                                     "has been modified since it was last installed. Are you sure " +
                                     "you want to DELETE the existing samples? This cannot be undone.\n" +
                                     "Please select YES to delete the existing sample and replace them " +
                                     "by the new ones.\n" +
                                     "Please select NO to skip this package. You can always install it later.",
                                     installFolder.getAbsolutePath(), pkgName);

          // Returns true if we can wipe & replace.
          return monitor.displayPrompt("SDK Manager: overwrite samples?", msg);
        }
      }
    }

    // The default is to allow installation
    return super.preInstallHook(archive, monitor, osSdkRoot, installFolder);
  }

  /**
   * Computes a hash of the installed content (in case of successful install.)
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public void postInstallHook(Archive archive, ITaskMonitor monitor, File installFolder) {
    super.postInstallHook(archive, monitor, installFolder);

    if (installFolder != null) {
      String h = computeContentHash(installFolder);
      saveContentHash(installFolder, h);
    }
  }

  /**
   * Set all the files from a sample package as read-only so that
   * users don't end up modifying sources by mistake in Eclipse
   * (samples are copied if using the NPW > Create from sample.)
   */
  @Override
  public void postUnzipFileHook(Archive archive, ITaskMonitor monitor, IFileOp fileOp, File unzippedFile, ZipArchiveEntry zipEntry) {
    super.postUnzipFileHook(archive, monitor, fileOp, unzippedFile, zipEntry);

    if (fileOp.isFile(unzippedFile) && !SdkConstants.FN_SOURCE_PROP.equals(unzippedFile.getName())) {
      fileOp.setReadOnly(unzippedFile);
    }
  }

  /**
   * Reads the hash from the properties file, if it exists.
   * Returns null if something goes wrong, e.g. there's no property file or
   * it doesn't contain our hash. Returns an empty string if the hash wasn't
   * correctly computed last time by {@link #saveContentHash(File, String)}.
   */
  private String readContentHash(File folder) {
    Properties props = new Properties();

    FileInputStream fis = null;
    try {
      File f = new File(folder, SdkConstants.FN_CONTENT_HASH_PROP);
      if (f.isFile()) {
        fis = new FileInputStream(f);
        props.load(fis);
        return props.getProperty("content-hash", null);  //$NON-NLS-1$
      }
    }
    catch (Exception e) {
      // ignore
    }
    finally {
      if (fis != null) {
        try {
          fis.close();
        }
        catch (IOException e) {
        }
      }
    }

    return null;
  }

  /**
   * Saves the hash using a properties file
   */
  private void saveContentHash(File folder, String hash) {
    Properties props = new Properties();

    props.setProperty("content-hash", hash == null ? "" : hash);  //$NON-NLS-1$ //$NON-NLS-2$

    FileOutputStream fos = null;
    try {
      File f = new File(folder, SdkConstants.FN_CONTENT_HASH_PROP);
      fos = new FileOutputStream(f);
      props.store(fos, "## Android - hash of this archive.");  //$NON-NLS-1$
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    finally {
      if (fos != null) {
        try {
          fos.close();
        }
        catch (IOException e) {
        }
      }
    }
  }

  /**
   * Computes a hash of the files names and sizes installed in the folder
   * using the SHA-1 digest.
   * Returns null if the digest algorithm is not available.
   */
  private String computeContentHash(File installFolder) {
    MessageDigest md = null;
    try {
      // SHA-1 is a standard algorithm.
      // http://java.sun.com/j2se/1.4.2/docs/guide/security/CryptoSpec.html#AppB
      md = MessageDigest.getInstance("SHA-1");    //$NON-NLS-1$
    }
    catch (NoSuchAlgorithmException e) {
      // We're unlikely to get there unless this JVM is not spec conforming
      // in which case there won't be any hash available.
    }

    if (md != null) {
      hashDirectoryContent(installFolder, md);
      return getDigestHexString(md);
    }

    return null;
  }

  /**
   * Computes a hash of the *content* of this directory. The hash only uses
   * the files names and the file sizes.
   */
  private void hashDirectoryContent(File folder, MessageDigest md) {
    if (folder == null || md == null || !folder.isDirectory()) {
      return;
    }

    for (File f : folder.listFiles()) {
      if (f.isDirectory()) {
        hashDirectoryContent(f, md);

      }
      else {
        String name = f.getName();

        // Skip the file we use to store the content hash
        if (name == null || SdkConstants.FN_CONTENT_HASH_PROP.equals(name)) {
          continue;
        }

        try {
          md.update(name.getBytes(SdkConstants.UTF_8));
        }
        catch (UnsupportedEncodingException e) {
          // There is no valid reason for UTF-8 to be unsupported. Ignore.
        }
        try {
          long len = f.length();
          md.update((byte)(len & 0x0FF));
          md.update((byte)((len >> 8) & 0x0FF));
          md.update((byte)((len >> 16) & 0x0FF));
          md.update((byte)((len >> 24) & 0x0FF));

        }
        catch (SecurityException e) {
          // Might happen if file is not readable. Ignore.
        }
      }
    }
  }

  /**
   * Returns a digest as an hex string.
   */
  private String getDigestHexString(MessageDigest digester) {
    // Create an hex string from the digest
    byte[] digest = digester.digest();
    int n = digest.length;
    String hex = "0123456789abcdef";                     //$NON-NLS-1$
    char[] hexDigest = new char[n * 2];
    for (int i = 0; i < n; i++) {
      int b = digest[i] & 0x0FF;
      hexDigest[i * 2 + 0] = hex.charAt(b >>> 4);
      hexDigest[i * 2 + 1] = hex.charAt(b & 0x0f);
    }

    return new String(hexDigest);
  }
}
