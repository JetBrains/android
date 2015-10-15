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
package com.android.tools.idea.gradle.util;

import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

import static com.google.common.base.Strings.nullToEmpty;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class ProxySettings {
  @NonNls public static final String HTTP_PROXY_TYPE = "http";
  @NonNls public static final String HTTPS_PROXY_TYPE = "https";

  @NonNls private static final String PROXY_HOST_PROPERTY_SUFFIX = "proxyHost";
  @NonNls private static final String PROXY_PORT_PROPERTY_SUFFIX = "proxyPort";
  @NonNls private static final String PROXY_USER_PROPERTY_SUFFIX = "proxyUser";
  @NonNls private static final String PROXY_PASSWORD_PROPERTY_SUFFIX = "proxyPassword";
  @NonNls private static final String PROXY_EXCEPTIONS_PROPERTY_SUFFIX = "nonProxyHosts";

  @NotNull private String myProxyType;
  @Nullable private String myHost;
  @Nullable private String myExceptions;
  @Nullable private String myUser;
  @Nullable private String myPassword;

  private int myPort = 80;

  public ProxySettings(@NotNull String proxyType) {
    myProxyType = proxyType;
  }

  public ProxySettings(@NotNull Properties properties, @NotNull String proxyType) {
    myProxyType = proxyType;
    myHost = properties.getProperty(getProxyPropertyName(PROXY_HOST_PROPERTY_SUFFIX));
    String portValue = properties.getProperty(getProxyPropertyName(PROXY_PORT_PROPERTY_SUFFIX));
    if (isNotEmpty(portValue)) {
      try {
        myPort = Integer.parseInt(portValue);
      }
      catch (NumberFormatException ignored) {
      }
    }
    myExceptions = properties.getProperty(getProxyPropertyName(PROXY_EXCEPTIONS_PROPERTY_SUFFIX));
    myUser = properties.getProperty(getProxyPropertyName(PROXY_USER_PROPERTY_SUFFIX));
    myPassword = properties.getProperty(getProxyPropertyName(PROXY_PASSWORD_PROPERTY_SUFFIX));
  }

  public ProxySettings(@NotNull HttpConfigurable ideProxySettings) {
    myProxyType = HTTP_PROXY_TYPE;
    myHost = ideProxySettings.PROXY_HOST;
    myPort = ideProxySettings.PROXY_PORT;
    if (ideProxySettings.PROXY_AUTHENTICATION) {
      myUser = ideProxySettings.PROXY_LOGIN;
      myPassword = ideProxySettings.getPlainProxyPassword();
    }
    myExceptions = ideProxySettings.PROXY_EXCEPTIONS;
  }

  public void applyProxySettings(@NotNull Properties properties) {
    properties.setProperty(getProxyPropertyName(PROXY_HOST_PROPERTY_SUFFIX), myHost);
    properties.setProperty(getProxyPropertyName(PROXY_PORT_PROPERTY_SUFFIX), String.valueOf(myPort));
    if (isEmpty(myUser)) {
      properties.remove(getProxyPropertyName(PROXY_USER_PROPERTY_SUFFIX));
      properties.remove(getProxyPropertyName(PROXY_PASSWORD_PROPERTY_SUFFIX));
    }
    else {
      properties.setProperty(getProxyPropertyName(PROXY_USER_PROPERTY_SUFFIX), myUser);
      // password can be blank.
      properties.setProperty(getProxyPropertyName(PROXY_PASSWORD_PROPERTY_SUFFIX), nullToEmpty(myPassword));
    }
    if (isEmpty(myExceptions)) {
      properties.remove(getProxyPropertyName(PROXY_EXCEPTIONS_PROPERTY_SUFFIX));
    }
    else {
      properties.setProperty(getProxyPropertyName(PROXY_EXCEPTIONS_PROPERTY_SUFFIX), myExceptions);
    }
  }

  @NotNull
  private String getProxyPropertyName(@NotNull String propertySuffix) {
    return "systemProp." + myProxyType + "." + propertySuffix;
  }

  @Nullable
  public String getHost() {
    return myHost;
  }

  public void setHost(@Nullable String host) {
    myHost = host;
  }

  @Nullable
  public String getExceptions() {
    return myExceptions;
  }

  public void setExceptions(@Nullable String exceptions) {
    this.myExceptions = exceptions;
  }

  @Nullable
  public String getUser() {
    return myUser;
  }

  public void setUser(@Nullable String user) {
    this.myUser = user;
  }

  @Nullable
  public String getPassword() {
    return myPassword;
  }

  public void setPassword(@Nullable String password) {
    this.myPassword = password;
  }

  public int getPort() {
    return myPort;
  }

  public void setPort(int port) {
    this.myPort = port;
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ProxySettings) {
      ProxySettings other = (ProxySettings)o;
      return myProxyType.equals(other.myProxyType) && nullableEquals(myHost, other.myHost) && myPort == other.myPort &&
             nullableEquals(myExceptions, other.myExceptions) && nullableEquals(myUser, other.myUser) &&
             nullableEquals(myPassword, other.myPassword);
    }
    return false;
  }

  private static boolean nullableEquals(@Nullable Object a, @Nullable Object b) {
    if (a != null) {
      return a.equals(b);
    }
    return b == null;
  }

  public void setProxyType(@NotNull String proxyType) {
    myProxyType = proxyType;
  }
}

