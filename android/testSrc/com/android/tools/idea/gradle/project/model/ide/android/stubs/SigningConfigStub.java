/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.annotations.Nullable;
import com.android.builder.model.SigningConfig;
import com.android.tools.idea.gradle.project.model.ide.android.UnusedModelMethodException;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public final class SigningConfigStub implements SigningConfig {
  @NotNull private final String myName;
  @Nullable private final File myStoreFile;
  @Nullable private final String myStorePassword;
  @Nullable private final String myKeyAlias;
  private final boolean myV1SigningEnabled;

  public SigningConfigStub(@NotNull String name,
                           @Nullable File storeFile,
                           @Nullable String storePassword,
                           @Nullable String keyAlias,
                           boolean v1SigningEnabled) {
    myName = name;
    myStoreFile = storeFile;
    myStorePassword = storePassword;
    myKeyAlias = keyAlias;
    myV1SigningEnabled = v1SigningEnabled;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @Nullable
  public File getStoreFile() {
    return myStoreFile;
  }

  @Override
  @Nullable
  public String getStorePassword() {
    return myStorePassword;
  }

  @Override
  @Nullable
  public String getKeyAlias() {
    return myKeyAlias;
  }

  @Override
  @Nullable
  public String getKeyPassword() {
    throw new UnusedModelMethodException("getKeyPassword");
  }

  @Override
  @Nullable
  public String getStoreType() {
    throw new UnusedModelMethodException("getStoreType");
  }

  @Override
  public boolean isV1SigningEnabled() {
    return myV1SigningEnabled;
  }

  @Override
  public boolean isV2SigningEnabled() {
    throw new UnusedModelMethodException("isV2SigningEnabled");
  }

  @Override
  public boolean isSigningReady() {
    throw new UnusedModelMethodException("isSigningReady");
  }
}
