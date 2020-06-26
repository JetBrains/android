// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.android;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.StorageProvider;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene.Kudelevsky
 */
public final class AndroidProGuardOptionsStorageProvider extends StorageProvider<AndroidProGuardStateStorage> {
  public static final AndroidProGuardOptionsStorageProvider INSTANCE = new AndroidProGuardOptionsStorageProvider();

  private AndroidProGuardOptionsStorageProvider() {
  }

  @NotNull
  @Override
  public AndroidProGuardStateStorage createStorage(File targetDataDir) throws IOException {
    return new AndroidProGuardStateStorage(new File(targetDataDir, "proguard_options" + File.separator + "data"));
  }
}
