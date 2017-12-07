/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.eclipse.dataflow.ui.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineConfigurationAttr;
import com.google.cloud.tools.eclipse.dataflow.core.launcher.PipelineRunner;
import com.google.cloud.tools.eclipse.dataflow.core.project.DataflowDependencyManager;
import com.google.cloud.tools.eclipse.dataflow.core.project.MajorVersion;
import com.google.cloud.tools.eclipse.test.util.project.ProjectUtils;
import com.google.cloud.tools.eclipse.test.util.ui.CompositeUtil;
import com.google.cloud.tools.eclipse.test.util.ui.ShellTestResource;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.ILaunchConfigurationDialog;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Suite;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(Suite.class)
@Suite.SuiteClasses({
  PipelineArgumentsTabTest.TabTest.class,
  PipelineArgumentsTabTest.RunButtonCheckedTest.class
})
public class PipelineArgumentsTabTest {

  public static class TabTest {

    @Rule public ShellTestResource shellResource = new ShellTestResource();

    private IWorkspaceRoot workspaceRoot;
    private DataflowDependencyManager dependencyManager;
    private PipelineArgumentsTab pipelineArgumentsTab;

    private IProject project1;
    private ILaunchConfigurationWorkingCopy configuration1;
    private IProject project2;
    private ILaunchConfigurationWorkingCopy configuration2;

    @Before
    public void setUp() throws CoreException {
      workspaceRoot = mock(IWorkspaceRoot.class);
      dependencyManager = mock(DataflowDependencyManager.class);

      project1 = mockProject("project1");
      when(workspaceRoot.getProject(eq("project1"))).thenReturn(project1);
      configuration1 = mockLaunchConfiguration();
      when(configuration1.getAttribute(eq(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME),
          anyString())).thenReturn("project1");
      doReturn(MajorVersion.ONE).when(dependencyManager).getProjectMajorVersion(project1);

      project2 = mockProject("project2");
      when(workspaceRoot.getProject(eq("project2"))).thenReturn(project2);
      configuration2 = mockLaunchConfiguration();
      when(configuration2.getAttribute(eq(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME),
          anyString())).thenReturn("project2");
      doReturn(MajorVersion.TWO).when(dependencyManager).getProjectMajorVersion(project2);

      ILaunchConfigurationDialog dialog = mock(ILaunchConfigurationDialog.class);
      pipelineArgumentsTab = new PipelineArgumentsTab(workspaceRoot, dependencyManager);
      pipelineArgumentsTab.setLaunchConfigurationDialog(dialog);
      pipelineArgumentsTab.createControl(shellResource.getShell());
    }

    @Test
    public void testGetName() {
      Assert.assertEquals("Pipeline Arguments", pipelineArgumentsTab.getName());
    }

    @Test
    public void testSetDefaults() {
      ILaunchConfigurationWorkingCopy configuration = mock(ILaunchConfigurationWorkingCopy.class);
      pipelineArgumentsTab.setDefaults(configuration);
      // we don't set any defaults
      Mockito.verifyZeroInteractions(configuration);
    }

    // https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/2165
    @Test
    public void testInitializeFrom_noExceptionForNonAccessibleProject() throws CoreException {
      when(project1.isAccessible()).thenReturn(false);

      pipelineArgumentsTab.initializeFrom(configuration1); // Should not throw NPE.
      ProjectUtils.waitForProjects();  // Suppress some non-terminated-job error logs
    }

    @Test
    public void testReload()
        throws CoreException, InvocationTargetException, InterruptedException {
      assertTrue(pipelineArgumentsTab.reload(configuration1));
      assertFalse("cache should be ok", pipelineArgumentsTab.reload(configuration1)); //$NON-NLS-1$
      configuration1.setAttribute(PipelineConfigurationAttr.USER_OPTIONS_NAME.toString(), "bar"); //$NON-NLS-1$
      assertTrue("config changed, cache should be discarded", //$NON-NLS-1$
          pipelineArgumentsTab.reload(configuration1));
      assertTrue("different config; cache should be discarded", //$NON-NLS-1$
          pipelineArgumentsTab.reload(configuration2));
      assertTrue("config changed, cache should be discarded", //$NON-NLS-1$
          pipelineArgumentsTab.reload(configuration1));
    }

    @Test
    public void testIsValid_errorOnNonDataflowProject()
        throws CoreException, InvocationTargetException, InterruptedException {
      doReturn(null).when(dependencyManager).getProjectMajorVersion(project1);

      pipelineArgumentsTab.isValid(configuration1);
      assertEquals("Project is not configured for Dataflow",
          pipelineArgumentsTab.getErrorMessage());
    }

    @Test
    public void testIsValid_errorOnNonProject()
        throws CoreException, InvocationTargetException, InterruptedException {
      ILaunchConfiguration configuration = mock(ILaunchConfiguration.class);

      pipelineArgumentsTab.isValid(configuration);
      assertEquals("Project is not configured for Dataflow",
          pipelineArgumentsTab.getErrorMessage());
    }
  }

  private static ILaunchConfigurationWorkingCopy mockLaunchConfiguration() throws CoreException {
    final Map<String, Object> attributes = new HashMap<>();
    Answer<Object> getAttributes = new Answer<Object>() {
      @Override
      public Object answer(InvocationOnMock invocation) throws Throwable {
        String key = invocation.getArgumentAt(0, String.class);
        return attributes.containsKey(key) ? attributes.get(key) : invocation.getArgumentAt(1, Object.class);
      }};
    Answer<Void> setAttributes = new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) throws Throwable {
        attributes.put(invocation.getArgumentAt(0, String.class),
            invocation.getArgumentAt(1, Object.class));
        return null;
      }};
    ILaunchConfigurationWorkingCopy launch = mock(ILaunchConfigurationWorkingCopy.class);
    doReturn(attributes).when(launch).getAttributes();
    doAnswer(getAttributes).when(launch).getAttribute(anyString(), anyString());
    doAnswer(getAttributes).when(launch).getAttribute(anyString(), anyInt());
    doAnswer(getAttributes).when(launch).getAttribute(anyString(), anyBoolean());
    doAnswer(getAttributes).when(launch).getAttribute(anyString(), anyMap());
    doAnswer(setAttributes).when(launch).setAttribute(anyString(), anyString());
    doAnswer(setAttributes).when(launch).setAttribute(anyString(), anyInt());
    doAnswer(setAttributes).when(launch).setAttribute(anyString(), anyBoolean());
    doAnswer(setAttributes).when(launch).setAttribute(anyString(), anyMap());
    return launch;
  }

  private static IProject mockProject(String projectName) {
    IProject project = mock(IProject.class, projectName);
    when(project.getName()).thenReturn(projectName);
    when(project.isAccessible()).thenReturn(true);
    when(project.isOpen()).thenReturn(true);
    return project;

  }

  @RunWith(Parameterized.class)
  public static class RunButtonCheckedTest {

    private static class Parameter {
      private final MajorVersion majorVersion;
      private final String expectedButtonText;

      private Parameter(MajorVersion majorVersion, String expectedButtonText) {
        this.majorVersion = majorVersion;
        this.expectedButtonText = expectedButtonText;
      }
    }

    @Parameters
    public static Iterable<? extends Object> parameters() {
      return Lists.newArrayList(
          new Parameter(MajorVersion.ONE, "DirectPipelineRunner"),
          new Parameter(MajorVersion.TWO, "DirectRunner"),
          new Parameter(MajorVersion.QUALIFIED_TWO, "DirectRunner"),
          new Parameter(MajorVersion.THREE_PLUS, "DirectRunner"),
          new Parameter(MajorVersion.ALL, "DirectRunner"));
    }

    @Rule public ShellTestResource shellResource = new ShellTestResource();

    private final Parameter testParameter;
    public RunButtonCheckedTest(Parameter parameter) {
      testParameter = parameter;
    }

    @Test
    public void assertRunnerButtonChecked() {
      ILaunchConfigurationDialog dialog = mock(ILaunchConfigurationDialog.class);
      Shell shell = shellResource.getShell();
      PipelineArgumentsTab tab = new PipelineArgumentsTab();
      tab.setLaunchConfigurationDialog(dialog);
      tab.createControl(shell);

      tab.updateRunnerButtons(testParameter.majorVersion, PipelineRunner.DIRECT_RUNNER);
      Button runnerButton = getCheckedRunnerButton(shell);
      assertNotNull(runnerButton);
      assertEquals(testParameter.expectedButtonText, runnerButton.getText());
      assertTrue(runnerButton.getSelection());

      // Should not throw IllegalStateException:
      // https://github.com/GoogleCloudPlatform/google-cloud-eclipse/issues/2136
      tab.getSelectedRunner();
    }

    private static Button getCheckedRunnerButton(Composite composite) {
      Group runnerGroup = CompositeUtil.findControl(composite, Group.class);
      assertEquals("Runner:", runnerGroup.getText());

      return (Button) CompositeUtil.findControl(runnerGroup, new Predicate<Control>() {
        @Override
        public boolean apply(Control control) {
          return control instanceof Button && ((Button) control).getSelection();
        }
      });
    }
  }
}
