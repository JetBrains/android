package com.android.tools.idea.uibuilder.property.inspector;

import com.android.tools.idea.uibuilder.mockup.editor.FileChooserActionListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlPropertiesManager;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.NlComponentEditor;
import com.android.tools.idea.uibuilder.property.editors.NlReferenceEditor;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.rendering.RenderService.MOCKUP_EDITOR_ENABLED;
import static com.android.tools.idea.uibuilder.property.editors.NlEditingListener.DEFAULT_LISTENER;

/**
 * Inspector to quickly edit some mockup attributes as the path and the opacity.
 *
 * It will only be displayed if the user has already added a mockup to the selected component.
 */
public class MockupInspectorProvider implements InspectorProvider {

  private static final Set<String> MOCKUP_PROPERTIES = ImmutableSet.of(ATTR_MOCKUP);
  private MockupInspectorComponent myInspector;

  @Override
  public boolean isApplicable(@NotNull List<NlComponent> components,
                              @NotNull Map<String, NlProperty> properties,
                              @NotNull NlPropertiesManager propertiesManager) {
    //noinspection ConstantConditions
    return MOCKUP_EDITOR_ENABLED && properties.keySet().containsAll(MOCKUP_PROPERTIES)
           && !components.isEmpty()
           && components.get(0).getAttribute(TOOLS_URI, ATTR_MOCKUP) != null;
  }

  @NotNull
  @Override
  public InspectorComponent createCustomInspector(@NotNull List<NlComponent> components,
                                                  @NotNull Map<String, NlProperty> properties,
                                                  @NotNull NlPropertiesManager propertiesManager) {
    if (myInspector == null) {
      myInspector = new MockupInspectorComponent(propertiesManager.getProject());
    }
    myInspector.updateProperties(components, properties, propertiesManager);
    return myInspector;
  }

  @Override
  public void resetCache() {
    myInspector = null;
  }

  /**
   * Text font inspector component for setting font family, size, decorations, color.
   */
  private static class MockupInspectorComponent implements InspectorComponent {

    public static final String TITLE = "View Mockup";
    private final NlReferenceEditor myOpacityEditor;
    private final FileChooserActionListener myFileChooserListener;
    private NlProperty myMockupPath;
    private NlProperty myOpacityProperty;
    private TextFieldWithBrowseButton myFileChooser;

    public MockupInspectorComponent(@NotNull Project project) {
      myOpacityEditor = NlReferenceEditor.createForInspector(project, DEFAULT_LISTENER);
      myFileChooserListener = new FileChooserActionListener();
      myFileChooser = createFileChooser(myFileChooserListener);
    }

    @Override
    public void updateProperties(@NotNull List<NlComponent> components,
                                 @NotNull Map<String, NlProperty> properties,
                                 @NotNull NlPropertiesManager propertiesManager) {
      myMockupPath = properties.get(ATTR_MOCKUP);
      myOpacityProperty = properties.get(ATTR_MOCKUP_OPACITY);
    }


    @Override
    public int getMaxNumberOfRows() {
      return 3;
    }

    @Override
    public void attachToInspector(@NotNull InspectorPanel inspector) {
      refresh();
      inspector.addTitle(TITLE);
      inspector.addComponent(ATTR_MOCKUP, myMockupPath.getTooltipText(), myFileChooser);
      inspector.addComponent(ATTR_MOCKUP_OPACITY, null, myOpacityEditor.getComponent());
    }

    private static TextFieldWithBrowseButton createFileChooser(@NotNull FileChooserActionListener listener) {
      TextFieldWithBrowseButton fileChooser;
      fileChooser = new TextFieldWithBrowseButton();
      fileChooser.setEditable(false);
      fileChooser.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
      fileChooser.addActionListener(listener);
      return fileChooser;
    }

    @Override
    public void refresh() {
      if (myFileChooser != null && !myFileChooser.getText().equals(myMockupPath.getValue())) {
        myFileChooser.setText(myMockupPath.getValue());
      }
      myOpacityEditor.setProperty(myOpacityProperty);
      myFileChooserListener.setFilePathProperty(myMockupPath);
    }

    @Override
    @NotNull
    public List<NlComponentEditor> getEditors() {
      return ImmutableList.of(myOpacityEditor);
    }
  }
}
