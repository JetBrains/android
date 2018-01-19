package com.android.tools.idea.gradle.project.sync.setup.post.module;

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.testartifacts.scopes.TestArtifactSearchScopes;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link TestArtifactSearchScopeSetupStep}.
 */
public class TestArtifactSearchScopeSetupStepTest extends IdeaTestCase {
  private TestArtifactSearchScopeSetupStep mySetupStep;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySetupStep = new TestArtifactSearchScopeSetupStep();
  }

  public void testSetUpModuleWithAndroidModule() {
    AndroidFacet facet = createAndAddAndroidFacet(myModule);
    facet.getConfiguration().setModel(mock(AndroidModuleModel.class));

    mySetupStep.setUpModule(myModule, null);
    assertNotNull(TestArtifactSearchScopes.get(myModule));
  }

  public void testSetUpModuleWithNonAndroidModule() {
    mySetupStep.setUpModule(myModule, null);
    assertNull(TestArtifactSearchScopes.get(myModule));
  }
}
