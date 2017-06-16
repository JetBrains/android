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

import com.android.builder.model.SigningConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Objects;

public final class SigningConfigStub extends BaseStub implements SigningConfig {
  @NotNull private final String myName;
  @Nullable private final File myStoreFile;
  @Nullable private final String myStorePassword;
  @Nullable private final String myKeyAlias;
  @Nullable private final String myKeyPassword;
  @Nullable private final String myStoreType;
  private final boolean myV1SigningEnabled;
  private final boolean myV2SigningEnabled;
  private final boolean mySigningReady;

  public SigningConfigStub() {
    this("name", new File("fake"), "psw", "alias", "kePsw", "storeType", true, true, true);
  }

  public SigningConfigStub(@NotNull String name,
                           @Nullable File storeFile,
                           @Nullable String storePassword,
                           @Nullable String keyAlias,
                           @Nullable String keyPassword,
                           @Nullable String storeType,
                           boolean v1SigningEnabled,
                           boolean v2SigningEnabled,
                           boolean signingReady) {
    myName = name;
    myStoreFile = storeFile;
    myStorePassword = storePassword;
    myKeyAlias = keyAlias;
    myKeyPassword = keyPassword;
    myStoreType = storeType;
    myV1SigningEnabled = v1SigningEnabled;
    myV2SigningEnabled = v2SigningEnabled;
    mySigningReady = signingReady;
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
    return myKeyPassword;
  }

  @Override
  @Nullable
  public String getStoreType() {
    return myStoreType;
  }

  @Override
  public boolean isV1SigningEnabled() {
    return myV1SigningEnabled;
  }

  @Override
  public boolean isV2SigningEnabled() {
    return myV2SigningEnabled;
  }

  @Override
  public boolean isSigningReady() {
    return mySigningReady;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof SigningConfig)) {
      return false;
    }

    SigningConfig config = (SigningConfig)o;

    return isV1SigningEnabled() == config.isV1SigningEnabled() &&
           equals(config, SigningConfig::isV2SigningEnabled) &&
           equals(config, SigningConfig::isSigningReady) &&
           Objects.equals(getName(), config.getName()) &&
           Objects.equals(getStoreFile(), config.getStoreFile()) &&
           Objects.equals(getStorePassword(), config.getStorePassword()) &&
           Objects.equals(getKeyAlias(), config.getKeyAlias()) &&
           equals(config, SigningConfig::getKeyPassword) &&
           equals(config, SigningConfig::getStoreType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getStoreFile(), getStorePassword(), getKeyAlias(), getKeyPassword(), getStoreType(),
                        isV1SigningEnabled(), isV2SigningEnabled(), isSigningReady());
  }

  @Override
  public String toString() {
    return "SigningConfigStub{" +
           "myName='" + myName + '\'' +
           ", myStoreFile=" + myStoreFile +
           ", myStorePassword='" + myStorePassword + '\'' +
           ", myKeyAlias='" + myKeyAlias + '\'' +
           ", myKeyPassword='" + myKeyPassword + '\'' +
           ", myStoreType='" + myStoreType + '\'' +
           ", myV1SigningEnabled=" + myV1SigningEnabled +
           ", myV2SigningEnabled=" + myV2SigningEnabled +
           ", mySigningReady=" + mySigningReady +
           "}";
  }
}
