// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.util;

import com.android.ide.common.signing.KeystoreHelper;
import com.android.ide.common.signing.KeytoolException;
import com.android.prefs.AndroidLocation.AndroidLocationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import org.jetbrains.annotations.Nullable;

public class DebugKeyProvider {
  private static final String PASSWORD_STRING = "android";
  private static final char[] PASSWORD_CHAR = "android".toCharArray();
  private static final String DEBUG_ALIAS = "AndroidDebugKey";
  private static final String CERTIFICATE_DESC = "CN=Android Debug,O=Android,C=US";
  private PrivateKeyEntry mEntry;

  public DebugKeyProvider(@Nullable String osKeyStorePath, String storeType) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException, IOException, KeytoolException, AndroidLocationException {
    if (osKeyStorePath == null) {
      osKeyStorePath = getDefaultKeyStoreOsPath();
    }

    if (!this.loadKeyEntry(osKeyStorePath, storeType)) {
      this.createNewStore(osKeyStorePath, storeType);
    }
  }

  public static String getDefaultKeyStoreOsPath() throws AndroidLocationException {
    return KeystoreHelper.defaultDebugKeystoreLocation();
  }

  public PrivateKey getDebugKey() {
    return this.mEntry != null ? this.mEntry.getPrivateKey() : null;
  }

  public Certificate getCertificate() {
    return this.mEntry != null ? this.mEntry.getCertificate() : null;
  }

  private boolean loadKeyEntry(String osKeyStorePath, String storeType) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException, UnrecoverableEntryException {
    try {
      KeyStore keyStore = KeyStore.getInstance(storeType != null ? storeType : KeyStore.getDefaultType());
      FileInputStream fis = new FileInputStream(osKeyStorePath);
      keyStore.load(fis, PASSWORD_CHAR);
      fis.close();
      this.mEntry = (PrivateKeyEntry)keyStore.getEntry(DEBUG_ALIAS, new PasswordProtection(PASSWORD_CHAR));
      return true;
    } catch (FileNotFoundException var5) {
      return false;
    }
  }

  private void createNewStore(String osKeyStorePath, String storeType) throws KeyStoreException, NoSuchAlgorithmException, CertificateException, UnrecoverableEntryException, IOException, KeytoolException {
    if (KeystoreHelper.createNewStore(storeType, new File(osKeyStorePath), PASSWORD_STRING, PASSWORD_STRING, DEBUG_ALIAS, CERTIFICATE_DESC, 30)) {
      this.loadKeyEntry(osKeyStorePath, storeType);
    }
  }
}
