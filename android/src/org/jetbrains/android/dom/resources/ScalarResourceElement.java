package org.jetbrains.android.dom.resources;

import com.intellij.spellchecker.xml.NoSpellchecking;
import com.intellij.util.xml.Convert;
import org.jetbrains.android.dom.converters.QuietResourceReferenceConverter;

@NoSpellchecking
@Convert(QuietResourceReferenceConverter.class)
public interface ScalarResourceElement extends ResourceElement {
}
