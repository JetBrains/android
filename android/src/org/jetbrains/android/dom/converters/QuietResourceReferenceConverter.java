package org.jetbrains.android.dom.converters;

public class QuietResourceReferenceConverter extends ResourceReferenceConverter {
  public QuietResourceReferenceConverter() {
    setQuiet(true);
  }
}
