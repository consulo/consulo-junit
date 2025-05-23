/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.junit;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.execution.impl.junit2.PsiMemberParameterizedLocation;
import com.intellij.java.execution.impl.junit2.info.MethodLocation;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.action.Location;
import consulo.execution.configuration.RunConfigurationModule;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.AbstractTestProxy;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.execution.test.sm.runner.SMTestProxy;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.ui.ex.action.ActionsBundle;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;

public class TestMethods extends TestMethod
{
	private final Collection<AbstractTestProxy> myFailedTests;

	public TestMethods(@Nonnull JUnitConfiguration configuration, @Nonnull ExecutionEnvironment environment, @Nonnull Collection<AbstractTestProxy> failedTests)
	{
		super(configuration, environment);

		myFailedTests = failedTests;
	}

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		final OwnJavaParameters javaParameters = super.createDefaultJavaParameters();
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
		final RunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
		final Project project = configurationModule.getProject();
		final Module module = configurationModule.getModule();
		final GlobalSearchScope searchScope = module != null ? GlobalSearchScope.moduleRuntimeScope(module, true) : GlobalSearchScope.allScope(project);
		addClassesListToJavaParameters(myFailedTests, testInfo -> testInfo != null ? getTestPresentation(testInfo, project, searchScope) : null, data.getPackageName(), true, javaParameters);

		return javaParameters;
	}

	@Override
	protected PsiElement retrievePsiElement(Object element)
	{
		if(element instanceof SMTestProxy)
		{
			JUnitConfiguration configuration = getConfiguration();
			Location location = ((SMTestProxy) element).getLocation(configuration.getProject(), configuration.getConfigurationModule().getSearchScope());
			if(location != null)
			{
				return location.getPsiElement();
			}
		}
		return super.retrievePsiElement(element);
	}

	@Nullable
	@Override
	public SourceScope getSourceScope()
	{
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
		return data.getScope().getSourceScope(getConfiguration());
	}

	@Override
	protected boolean configureByModule(Module module)
	{
		return super.configureByModule(module) && getConfiguration().getPersistentData().getScope() != TestSearchScope.WHOLE_PROJECT;
	}

	@Nullable
	public static String getTestPresentation(AbstractTestProxy testInfo, Project project, GlobalSearchScope searchScope)
	{
		final Location location = testInfo.getLocation(project, searchScope);
		final PsiElement element = location != null ? location.getPsiElement() : null;
		if(element instanceof PsiMethod)
		{
			String nodeId = TestUniqueId.getEffectiveNodeId(testInfo, project, searchScope);
			if(nodeId != null)
			{
				return TestUniqueId.getUniqueIdPresentation().apply(nodeId);
			}

			final PsiClass containingClass = location instanceof MethodLocation ? ((MethodLocation) location).getContainingClass() : location instanceof PsiMemberParameterizedLocation ? (
					(PsiMemberParameterizedLocation) location).getContainingClass() : ((PsiMethod) element).getContainingClass();
			if(containingClass != null)
			{
				final String proxyName = testInfo.getName();
				final String methodWithSignaturePresentation = JUnitConfiguration.Data.getMethodPresentation(((PsiMethod) element));
				return JavaExecutionUtil.getRuntimeQualifiedName(containingClass) + "," + (proxyName.contains(methodWithSignaturePresentation) ? proxyName.substring(proxyName.indexOf
						(methodWithSignaturePresentation)) : methodWithSignaturePresentation);
			}
		}
		return null;
	}

	@Override
	public String suggestActionName()
	{
		return ActionsBundle.message("action.RerunFailedTests.text");
	}
}
