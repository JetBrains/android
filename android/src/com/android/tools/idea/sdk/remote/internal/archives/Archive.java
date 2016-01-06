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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.sdk.remote.internal.sources.SdkSource;

import java.util.Properties;


/**
 * A {@link Archive} is the base class for "something" that can be downloaded from
 * the SDK repository.
 * <p/>
 * A package has some attributes (revision, description) and a list of archives
 * which represent the downloadable bits.
 * <p/>
 * Packages are offered by a {@link SdkSource} (a download site).
 */
public class Archive implements Comparable<Archive> {

  private final String mUrl;
  private final long mSize;
  private final String mChecksum;
  private final ChecksumType mChecksumType = ChecksumType.SHA1;
  private final RemotePkgInfo mPackage;
  private final ArchFilter mArchFilter;

  /**
   * Creates a new remote archive.
   * This is typically called when inflating a remote-package info from XML meta-data.
   *
   * @param pkg        The package that contains this archive. Typically not null.
   * @param archFilter The {@link ArchFilter} for the archive. Typically not null.
   * @param url        The URL where the archive is available.
   *                   Typically not null but code should be able to handles both.
   * @param size       The expected size in bytes of the archive to download.
   * @param checksum   The expected checksum string of the archive. Currently only the
   *                   {@link ChecksumType#SHA1} format is supported.
   */
  public Archive(@NonNull RemotePkgInfo pkg, @Nullable ArchFilter archFilter, @Nullable String url, long size, @NonNull String checksum) {
    mPackage = pkg;
    mArchFilter = archFilter != null ? archFilter : new ArchFilter(null);
    mUrl = url == null ? null : url.trim();
    mSize = size;
    mChecksum = checksum;
  }

  /**
   * Save the properties of the current archive in the give {@link Properties} object.
   * These properties will later be give the constructor that takes a {@link Properties} object.
   */
  void saveProperties(@NonNull Properties props) {
    mArchFilter.saveProperties(props);
  }

  /**
   * Returns the package that created and owns this archive.
   * It should generally not be null.
   */
  @NonNull
  public RemotePkgInfo getParentPackage() {
    return mPackage;
  }

  /**
   * Returns the archive size, an int > 0.
   * Size will be 0 if this a local installed folder of unknown size.
   */
  public long getSize() {
    return mSize;
  }

  /**
   * Returns the SHA1 archive checksum, as a 40-char hex.
   * Can be empty but not null for local installed folders.
   */
  @NonNull
  public String getChecksum() {
    return mChecksum;
  }

  /**
   * Returns the checksum type, always {@link ChecksumType#SHA1} right now.
   */
  @NonNull
  public ChecksumType getChecksumType() {
    return mChecksumType;
  }

  /**
   * Returns the download archive URL, either absolute or relative to the repository xml.
   * Always return null for a local installed folder.
   *
   * @see #getLocalOsPath()
   */
  @Nullable
  public String getUrl() {
    return mUrl;
  }

  /**
   * Returns the architecture filter.
   * This non-null filter indicates which host/jvm this archive is compatible with.
   */
  @NonNull
  public ArchFilter getArchFilter() {
    return mArchFilter;
  }

  /**
   * Generates a description of the {@link ArchFilter} supported by this archive.
   */
  public String getOsDescription() {
    StringBuilder sb = new StringBuilder();

    HostOs hos = mArchFilter.getHostOS();
    sb.append(hos == null ? "any OS" : hos.getUiName());

    BitSize jvmBits = mArchFilter.getJvmBits();
    if (jvmBits != null) {
      sb.append(", JVM ").append(jvmBits.getSize()).append("-bits");
    }

    BitSize hostBits = mArchFilter.getJvmBits();
    if (hostBits != null) {
      sb.append(", Host ").append(hostBits.getSize()).append("-bits");
    }

    return sb.toString();
  }

  /**
   * Returns the short description of the source, if not null.
   * Otherwise returns the default Object toString result.
   * <p/>
   * This is mostly helpful for debugging.
   * For UI display, use the {@link IDescription} interface.
   */
  @Override
  public String toString() {
    String s = getShortDescription();
    if (s != null) {
      return s;
    }
    return super.toString();
  }

  /**
   * Generates a short description for this archive.
   */
  public String getShortDescription() {
    return String.format("Archive for %1$s", getOsDescription());
  }

  /**
   * Returns true if this archive can be installed on the current platform.
   */
  public boolean isCompatible() {
    ArchFilter current = ArchFilter.getCurrent();
    return mArchFilter.isCompatibleWith(current);
  }

  /**
   * Archives are compared using their {@link Package} ordering.
   *
   * @see Package#compareTo(Package)
   */
  @Override
  public int compareTo(Archive rhs) {
    if (mPackage != null && rhs != null) {
      return mPackage.compareTo(rhs.getParentPackage());
    }
    return 0;
  }

  /**
   * Note: An {@link Archive}'s hash code does NOT depend on the parent {@link Package} hash code.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((mArchFilter == null) ? 0 : mArchFilter.hashCode());
    result = prime * result + ((mChecksum == null) ? 0 : mChecksum.hashCode());
    result = prime * result + ((mChecksumType == null) ? 0 : mChecksumType.hashCode());
    result = prime * result + (int)(mSize ^ (mSize >>> 32));
    result = prime * result + ((mUrl == null) ? 0 : mUrl.hashCode());
    return result;
  }

  /**
   * Note: An {@link Archive}'s equality does NOT depend on the parent {@link Package} equality.
   * <p/>
   * {@inheritDoc}
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof Archive)) {
      return false;
    }
    Archive other = (Archive)obj;
    if (!mArchFilter.equals(other.mArchFilter)) {
      return false;
    }
    if (!mChecksum.equals(other.mChecksum)) {
      return false;
    }
    if (mChecksumType == null) {
      if (other.mChecksumType != null) {
        return false;
      }
    }
    else if (!mChecksumType.equals(other.mChecksumType)) {
      return false;
    }
    if (mSize != other.mSize) {
      return false;
    }
    if (mUrl == null) {
      if (other.mUrl != null) {
        return false;
      }
    }
    else if (!mUrl.equals(other.mUrl)) {
      return false;
    }
    return true;
  }
}
