package org.jetbrains.android;

import com.android.annotations.VisibleForTesting;
import com.android.resources.ResourceType;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.xml.NanoXmlUtil;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.android.util.ResourceEntry;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidValueResourcesIndex
  extends FileBasedIndexExtension<ResourceEntry, ImmutableSet<AndroidValueResourcesIndex.MyResourceInfo>> {

  public static final ID<ResourceEntry, ImmutableSet<MyResourceInfo>> INDEX_ID = ID.create("android.value.resources.index");

  @NonNls private static final String RESOURCES_ROOT_TAG = "resources";
  @NonNls private static final String NAME_ATTRIBUTE_VALUE = "name";
  @NonNls private static final String TYPE_ATTRIBUTE_VALUE = "type";

  private final DataIndexer<ResourceEntry, ImmutableSet<MyResourceInfo>, FileContent> myIndexer =
    new DataIndexer<ResourceEntry, ImmutableSet<MyResourceInfo>, FileContent>() {
      @Override
      @NotNull
      public Map<ResourceEntry, ImmutableSet<MyResourceInfo>> map(@NotNull FileContent inputData) {
        if (!isSimilarFile(inputData)) {
          return Collections.emptyMap();
        }
        final PsiFile file = inputData.getPsiFile();

        if (!(file instanceof XmlFile)) {
          return Collections.emptyMap();
        }
        final Map<ResourceEntry, ImmutableSet.Builder<MyResourceInfo>> resultBuilder = Maps.newHashMap();

        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            final String resName = tag.getAttributeValue(NAME_ATTRIBUTE_VALUE);

            if (resName == null) {
              return;
            }
            final String tagName = tag.getName();
            final String resTypeStr;

            if ("item".equals(tagName)) {
              resTypeStr = tag.getAttributeValue(TYPE_ATTRIBUTE_VALUE);
            }
            else {
              resTypeStr = AndroidCommonUtils.getResourceTypeByTagName(tagName);
            }
            final ResourceType resType = resTypeStr != null ? ResourceType.getEnum(resTypeStr) : null;

            if (resType == null) {
              return;
            }
            final int offset = tag.getTextRange().getStartOffset();

            if (resType == ResourceType.ATTR) {
              final XmlTag parentTag = tag.getParentTag();
              final String contextName = parentTag != null ? parentTag.getAttributeValue(NAME_ATTRIBUTE_VALUE) : null;
              processResourceEntry(new ResourceEntry(resTypeStr, resName, contextName != null ? contextName : ""), resultBuilder, offset);
            }
            else {
              processResourceEntry(new ResourceEntry(resTypeStr, resName, ""), resultBuilder, offset);
            }
          }
        });

        Map<ResourceEntry, ImmutableSet<MyResourceInfo>> result = Maps.newHashMap();
        for (Map.Entry<ResourceEntry, ImmutableSet.Builder<MyResourceInfo>> entry : resultBuilder.entrySet()) {
          result.put(entry.getKey(), entry.getValue().build());
        }

        return result;
      }
    };

  private static boolean isSimilarFile(FileContent inputData) {
    if (CharArrayUtil.indexOf(inputData.getContentAsText(), "<" + RESOURCES_ROOT_TAG, 0) < 0) {
      return false;
    }
    final boolean[] ourRootTag = {false};

    NanoXmlUtil.parse(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()), new NanoXmlUtil.IXMLBuilderAdapter() {
      @Override
      public void startElement(String name, String nsPrefix, String nsURI, String systemID, int lineNr)
        throws Exception {
        ourRootTag[0] = RESOURCES_ROOT_TAG.equals(name) && nsPrefix == null;
        stop();
      }
    });
    return ourRootTag[0];
  }

  private static void processResourceEntry(@NotNull ResourceEntry entry,
                                           @NotNull Map<ResourceEntry, ImmutableSet.Builder<MyResourceInfo>> resultBuilder,
                                           int offset) {
    final MyResourceInfo info = new MyResourceInfo(entry, offset);
    resultBuilder.put(entry, ImmutableSet.<MyResourceInfo>builder().add(info));
    addEntryToMap(info, createTypeMarkerKey(entry.getType()), resultBuilder);
    addEntryToMap(info, createTypeNameMarkerKey(entry.getType(), entry.getName()), resultBuilder);
  }

  private static void addEntryToMap(MyResourceInfo info, ResourceEntry marker,
                                    Map<ResourceEntry, ImmutableSet.Builder<MyResourceInfo>> resultBuilder) {
    ImmutableSet.Builder<MyResourceInfo> setBuilder = resultBuilder.get(marker);

    if (setBuilder == null) {
      setBuilder = ImmutableSet.builder();
      resultBuilder.put(marker, setBuilder);
    }
    setBuilder.add(info);
  }

  @NotNull
  public static ResourceEntry createTypeMarkerKey(String type) {
    return createTypeNameMarkerKey(type, "TYPE_MARKER_RESOURCE");
  }

  @NotNull
  public static ResourceEntry createTypeNameMarkerKey(String type, String name) {
    return new ResourceEntry(type, normalizeDelimiters(name), "TYPE_MARKER_CONTEXT");
  }

  @VisibleForTesting
  static String normalizeDelimiters(String s) {
    int length = s.length();
    for (int j = 0, n = length; j < n; j++) {
      final char ch = s.charAt(j);
      if (!Character.isLetterOrDigit(ch) && ch != '_') {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < n; i++) {
          final char c = s.charAt(i);
          if (Character.isLetterOrDigit(c)) {
            result.append(c);
          }
          else {
            result.append('_');
          }
        }
        return result.toString();
      }
    }

    return s;
  }

  private final KeyDescriptor<ResourceEntry> myKeyDescriptor = new KeyDescriptor<ResourceEntry>() {
    @Override
    public void save(@NotNull DataOutput out, ResourceEntry value) throws IOException {
      IOUtil.writeUTF(out, value.getType());
      IOUtil.writeUTF(out, value.getName());
      IOUtil.writeUTF(out, value.getContext());
    }

    @Override
    public ResourceEntry read(@NotNull DataInput in) throws IOException {
      final String resType = IOUtil.readUTF(in);
      final String resName = IOUtil.readUTF(in);
      final String resContext = IOUtil.readUTF(in);
      return new ResourceEntry(resType, resName, resContext);
    }

    @Override
    public int getHashCode(ResourceEntry value) {
      return value.hashCode();
    }

    @Override
    public boolean isEqual(ResourceEntry val1, ResourceEntry val2) {
      return val1.equals(val2);
    }
  };

  private final DataExternalizer<ImmutableSet<MyResourceInfo>> myValueExternalizer = new DataExternalizer<ImmutableSet<MyResourceInfo>>() {
    @Override
    public void save(@NotNull DataOutput out, ImmutableSet<MyResourceInfo> value) throws IOException {
      DataInputOutputUtil.writeINT(out, value.size());

      for (MyResourceInfo entry : value) {
        IOUtil.writeUTF(out, entry.getResourceEntry().getType());
        IOUtil.writeUTF(out, entry.getResourceEntry().getName());
        IOUtil.writeUTF(out, entry.getResourceEntry().getContext());
        DataInputOutputUtil.writeINT(out, entry.getOffset());
      }
    }

    @Nullable
    @Override
    public ImmutableSet<MyResourceInfo> read(@NotNull DataInput in) throws IOException {
      final int size = DataInputOutputUtil.readINT(in);

      if (size < 0 || size > 65535) {
        // Something is very wrong; trigger an index rebuild
        throw new IOException("Corrupt Index: Size " + size);
      }

      if (size == 0) {
        return ImmutableSet.of();
      }
      final ImmutableSet.Builder<MyResourceInfo> result = ImmutableSet.builder();

      for (int i = 0; i < size; i++) {
        final String type = IOUtil.readUTF(in);
        final String name = IOUtil.readUTF(in);
        final String context = IOUtil.readUTF(in);
        final int offset = DataInputOutputUtil.readINT(in);
        result.add(new MyResourceInfo(new ResourceEntry(type, name, context), offset));
      }
      return result.build();
    }
  };

  @NotNull
  @Override
  public ID<ResourceEntry, ImmutableSet<MyResourceInfo>> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<ResourceEntry, ImmutableSet<MyResourceInfo>, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  @Override
  public KeyDescriptor<ResourceEntry> getKeyDescriptor() {
    return myKeyDescriptor;
  }

  @NotNull
  @Override
  public DataExternalizer<ImmutableSet<MyResourceInfo>> getValueExternalizer() {
    return myValueExternalizer;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return new DefaultFileTypeSpecificInputFilter(StdFileTypes.XML) {
      @Override
      public boolean acceptInput(@NotNull final VirtualFile file) {
        return file.isInLocalFileSystem();
      }
    };
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 6;
  }

  public static class MyResourceInfo {
    private final ResourceEntry myResourceEntry;
    private final int myOffset;

    private MyResourceInfo(@NotNull ResourceEntry resourceEntry, int offset) {
      myResourceEntry = resourceEntry;
      myOffset = offset;
    }

    @NotNull
    public ResourceEntry getResourceEntry() {
      return myResourceEntry;
    }

    public int getOffset() {
      return myOffset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      MyResourceInfo info = (MyResourceInfo)o;

      if (myOffset != info.myOffset) {
        return false;
      }
      if (!myResourceEntry.equals(info.myResourceEntry)) {
        return false;
      }

      return true;
    }

    @Override
    public int hashCode() {
      int result = myResourceEntry.hashCode();
      result = 31 * result + myOffset;
      return result;
    }

    @Override
    public String toString() {
      return this.getClass().getDeclaringClass().getSimpleName() +
             '.' +
             this.getClass().getSimpleName() +
             '@' +
             Integer.toHexString(System.identityHashCode(this)) +
             '(' +
             myResourceEntry +
             ',' +
             myOffset +
             ')';
    }
  }
}
