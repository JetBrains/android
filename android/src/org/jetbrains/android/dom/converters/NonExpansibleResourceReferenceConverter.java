package org.jetbrains.android.dom.converters;

/**
 * Useful in cases which the expanded completion suggestion list would be too long.
 * In those cases, don't list all the possible resource values, but the types of resources instead.
 */
public class NonExpansibleResourceReferenceConverter extends ResourceReferenceConverter {
  public NonExpansibleResourceReferenceConverter() {
    setExpandedCompletionSuggestion(false);
  }
}
