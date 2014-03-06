/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.templates;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateMethodModel;
import freemarker.template.TemplateModelException;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.List;

/**
 * Freemarker method to read a keystore file from a given path, and
 * return a SHA1 hash of its first certificate, encoded as base16 (each byte separated by ':').
 */
public class FmKeystoreSha1Method implements TemplateMethodModel {
  @Override
  public Object exec(List args) throws TemplateModelException {
    if (args.size() != 1) {
      throw new TemplateModelException("Wrong arguments");
    }

    File debugKeystore = new File(args.get(0).toString());

    Certificate signingCert;

    try {
      KeyStore keyStore = KeyStore.getInstance("JKS");
      keyStore.load(new FileInputStream(debugKeystore), "android".toCharArray());
      String keyAlias = keyStore.aliases().nextElement();
      signingCert = keyStore.getCertificate(keyAlias);
    }
    catch (Exception e) {
      throw new TemplateModelException(e);
    }

    // Produce SHA1 fingerprint.

    try {
      byte[] certBytes = MessageDigest.getInstance("SHA1").digest(signingCert.getEncoded());
      return new SimpleScalar(base16(certBytes));
    }
    catch (NoSuchAlgorithmException e) {
      throw new TemplateModelException(e);
    }
    catch (CertificateEncodingException e) {
      throw new TemplateModelException(e);
    }
  }

  private static String base16(byte[] bytes) {
    char[] alphabet = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    StringBuilder buf = new StringBuilder();
    for (byte b : bytes) {
      buf.append(alphabet[(b >> 4) & 0xf]);
      buf.append(alphabet[b & 0xf]);
      buf.append(':');
    }
    // Remove last colon.
    buf.deleteCharAt(buf.length() - 1);
    return buf.toString();
  }
}
