package org.jetbrains.android.spellchecker;

import com.intellij.spellchecker.BundledDictionaryProvider;

public class AndroidBundledDictionaryProvider implements BundledDictionaryProvider {
  @Override
  public String[] getBundledDictionaries() {
    return new String[] {"android.dic"};
  }
}
