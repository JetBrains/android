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


package com.android.tools.idea.sdk.legacy.remote.internal.archives;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.Revision;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArchFilter {

  private static final String PROP_HOST_OS = "Archive.HostOs";      //$NON-NLS-1$
  private static final String PROP_HOST_BITS = "Archive.HostBits";    //$NON-NLS-1$
  private static final String PROP_JVM_BITS = "Archive.JvmBits";     //$NON-NLS-1$
  private static final String PROP_MIN_JVM_VERSION = "Archive.MinJvmVers";  //$NON-NLS-1$

  /**
   * The legacy property used to serialize {@link LegacyOs} in source.properties files.
   * <p/>
   * Replaced by {@code ArchFilter.PROP_HOST_OS}.
   */
  public static final String LEGACY_PROP_OS = "Archive.Os";       //$NON-NLS-1$

  /**
   * The legacy property used to serialize {@link LegacyArch} in source.properties files.
   * <p/>
   * Replaced by {@code ArchFilter.PROP_HOST_BITS} and {@code ArchFilter.PROP_JVM_BITS}.
   */
  public static final String LEGACY_PROP_ARCH = "Archive.Arch";     //$NON-NLS-1$

  private final HostOs mHostOs;
  private final BitSize mHostBits;
  private final BitSize mJvmBits;
  private final Revision mMinJvmVersion;

  /**
   * Creates a new {@link ArchFilter} with the specified filter attributes.
   * <p/>
   * This filters represents the attributes requires for a package's {@link Archive} to
   * be installable on the current architecture. Not all fields are required -- those that
   * are not specified imply there is no limitation on that particular attribute.
   *
   * @param hostOs        The host OS or null if there's no limitation for this package.
   * @param hostBits      The host bit size or null if there's no limitation for this package.
   * @param jvmBits       The JVM bit size or null if there's no limitation for this package.
   * @param minJvmVersion The minimal JVM version required by this package
   *                      or null if there's no limitation for this package.
   */
  public ArchFilter(@Nullable HostOs hostOs,
                    @Nullable BitSize hostBits,
                    @Nullable BitSize jvmBits,
                    @Nullable Revision minJvmVersion) {
    mHostOs = hostOs;
    mHostBits = hostBits;
    mJvmBits = jvmBits;
    mMinJvmVersion = minJvmVersion;
  }

  /**
   * Creates an {@link ArchFilter} using properties previously saved in a {@link Properties}
   * object, typically by the {@link ArchFilter#saveProperties(Properties)} method.
   * <p/>
   * Missing properties are set to null and will not filter.
   *
   * @param props A properties object previously filled by {@link #saveProperties(Properties)}.
   *              If null, a default empty {@link ArchFilter} is created.
   */
  public ArchFilter(@Nullable Properties props) {
    HostOs hostOs = null;
    BitSize hostBits = null;
    BitSize jvmBits = null;
    Revision minJvmVers = null;

    if (props != null) {
      hostOs = HostOs.fromXmlName(props.getProperty(PROP_HOST_OS));
      hostBits = BitSize.fromXmlName(props.getProperty(PROP_HOST_BITS));
      jvmBits = BitSize.fromXmlName(props.getProperty(PROP_JVM_BITS));

      try {
        minJvmVers = Revision.parseRevision(props.getProperty(PROP_MIN_JVM_VERSION));
      }
      catch (NumberFormatException ignore) {
      }

      // Backward compatibility with older PROP_OS and PROP_ARCH values
      if (!props.containsKey(PROP_HOST_OS) && props.containsKey(LEGACY_PROP_OS)) {
        hostOs = HostOs.fromXmlName(props.getProperty(LEGACY_PROP_OS));
      }
      if (!props.containsKey(PROP_HOST_BITS) &&
          !props.containsKey(PROP_HOST_BITS) &&
          props.containsKey(LEGACY_PROP_ARCH)) {
        // We'll only handle the typical x86 and x86_64 values of the old PROP_ARCH
        // value and ignore the PPC value. "Any" is equivalent to keeping the new
        // attributes to null.
        String v = props.getProperty(LEGACY_PROP_ARCH).toLowerCase();

        if (v.indexOf("x86_64") > 0) {
          // JVM in 64-bit x86_64 mode so host-bits should be 64 too.
          hostBits = jvmBits = BitSize._64;
        }
        else if (v.indexOf("x86") > 0) {
          // JVM in 32-bit x86 mode, but host-bits could be either 32 or 64
          // so we don't set this one.
          jvmBits = BitSize._32;
        }
      }
    }

    mHostOs = hostOs;
    mHostBits = hostBits;
    mJvmBits = jvmBits;
    mMinJvmVersion = minJvmVers;
  }

  /**
   * @return the host OS or null if there's no limitation for this package.
   */
  @Nullable
  public HostOs getHostOS() {
    return mHostOs;
  }

  /**
   * @return the host bit size or null if there's no limitation for this package.
   */
  @Nullable
  public BitSize getHostBits() {
    return mHostBits;
  }

  /**
   * @return the JVM bit size or null if there's no limitation for this package.
   */
  @Nullable
  public BitSize getJvmBits() {
    return mJvmBits;
  }

  /**
   * @return the minimal JVM version required by this package
   * or null if there's no limitation for this package.
   */
  @Nullable
  public Revision getMinJvmVersion() {
    return mMinJvmVersion;
  }

  /**
   * Checks whether {@code this} {@link ArchFilter} is compatible with the right-hand side one.
   * <p/>
   * Typically this is used to check whether "this downloaded package is compatible with the
   * current architecture", which would be expressed as:
   * <pre>
   * DownloadedArchive.filter.isCompatibleWith(ArhFilter.getCurrent())
   * </pre>
   * For the host OS & bit size attribute, if the attributes are non-null they must be equal.
   * For the min-jvm-version, "this" version (the package we want to install) needs to be lower
   * or equal to the "required" (current host) version.
   *
   * @param required The requirements to meet.
   * @return True if this filter meets or exceeds the given requirements.
   */
  public boolean isCompatibleWith(@NonNull ArchFilter required) {
    if (mHostOs != null && required.mHostOs != null && !mHostOs.equals(required.mHostOs)) {
      return false;
    }

    if (mHostBits != null && required.mHostBits != null && !mHostBits.equals(required.mHostBits)) {
      return false;
    }

    if (mJvmBits != null && required.mJvmBits != null && !mJvmBits.equals(required.mJvmBits)) {
      return false;
    }

    if (mMinJvmVersion != null && required.mMinJvmVersion != null && mMinJvmVersion.compareTo(required.mMinJvmVersion) > 0) {
      return false;
    }

    return true;
  }

  /**
   * Returns an {@link ArchFilter} that represents the current host platform.
   *
   * @return an {@link ArchFilter} that represents the current host platform.
   */
  @NonNull
  public static ArchFilter getCurrent() {
    String os = System.getProperty("os.name");          //$NON-NLS-1$
    HostOs hostOS = null;
    if (os.startsWith("Mac")) {                         //$NON-NLS-1$
      hostOS = HostOs.MACOSX;
    }
    else if (os.startsWith("Windows")) {              //$NON-NLS-1$
      hostOS = HostOs.WINDOWS;
    }
    else if (os.startsWith("Linux")) {                //$NON-NLS-1$
      hostOS = HostOs.LINUX;
    }

    BitSize jvmBits;
    String arch = System.getProperty("os.arch");        //$NON-NLS-1$

    if (arch.equalsIgnoreCase("x86_64") ||            //$NON-NLS-1$
        arch.equalsIgnoreCase("ia64") ||            //$NON-NLS-1$
        arch.equalsIgnoreCase("amd64")) {           //$NON-NLS-1$
      jvmBits = BitSize._64;
    }
    else {
      jvmBits = BitSize._32;
    }

    // TODO figure out the host bit size.
    // When jvmBits is 64 we know it's surely 64
    // but that's not necessarily obvious when jvmBits is 32.
    BitSize hostBits = jvmBits;

    Revision minJvmVersion = null;
    String javav = System.getProperty("java.version");              //$NON-NLS-1$
    // java Version is typically in the form "1.2.3_45" and we just need to keep up to "1.2.3"
    // since our revision numbers are in 3-parts form (1.2.3).
    Pattern p = Pattern.compile("((\\d+)(\\.\\d+)?(\\.\\d+)?).*");  //$NON-NLS-1$
    Matcher m = p.matcher(javav);
    if (m.matches()) {
      minJvmVersion = Revision.parseRevision(m.group(1));
    }

    return new ArchFilter(hostOS, hostBits, jvmBits, minJvmVersion);
  }

  /**
   * Save this {@link ArchFilter} attributes into the the given {@link Properties} object.
   * These properties can later be given to the constructor that takes a {@link Properties} object.
   * <p/>
   * Null attributes are not saved in the properties.
   *
   * @param props A non-null properties object to fill with non-null attributes.
   */
  void saveProperties(@NonNull Properties props) {
    if (mHostOs != null) {
      props.setProperty(PROP_HOST_OS, mHostOs.getXmlName());
    }
    if (mHostBits != null) {
      props.setProperty(PROP_HOST_BITS, mHostBits.getXmlName());
    }
    if (mJvmBits != null) {
      props.setProperty(PROP_JVM_BITS, mJvmBits.getXmlName());
    }
    if (mMinJvmVersion != null) {
      props.setProperty(PROP_MIN_JVM_VERSION, mMinJvmVersion.toShortString());
    }
  }

  /**
   * String for debug purposes.
   */
  @Override
  public String toString() {
    return "<ArchFilter mHostOs=" + mHostOs +
           ", mHostBits=" + mHostBits + ", mJvmBits=" + mJvmBits +
           ", mMinJvmVersion=" + mMinJvmVersion + ">";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mHostOs == null) ? 0 : mHostOs.hashCode());
    result = prime * result + ((mHostBits == null) ? 0 : mHostBits.hashCode());
    result = prime * result + ((mJvmBits == null) ? 0 : mJvmBits.hashCode());
    result = prime * result + ((mMinJvmVersion == null) ? 0 : mMinJvmVersion.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    ArchFilter other =
      (ArchFilter)obj;
    if (mHostBits != other.mHostBits) {
      return false;
    }
    if (mHostOs != other.mHostOs) {
      return false;
    }
    if (mJvmBits != other.mJvmBits) {
      return false;
    }
    if (mMinJvmVersion == null) {
      if (other.mMinJvmVersion != null) {
        return false;
      }
    }
    else if (!mMinJvmVersion.equals(other.mMinJvmVersion)) {
      return false;
    }
    return true;
  }

}
