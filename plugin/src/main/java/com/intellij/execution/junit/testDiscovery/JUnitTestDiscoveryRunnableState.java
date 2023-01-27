/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.junit.testDiscovery;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestObject;
import com.intellij.java.execution.impl.testDiscovery.TestDiscoverySearchHelper;
import com.intellij.java.execution.impl.testframework.SearchForTestsTask;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.SourceScope;
import consulo.execution.test.TestSearchScope;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import java.util.Set;
import java.util.function.Function;

abstract class JUnitTestDiscoveryRunnableState extends TestObject
{
	public JUnitTestDiscoveryRunnableState(JUnitConfiguration configuration, ExecutionEnvironment environment)
	{
		super(configuration, environment);
	}

	protected abstract String getChangeList();

	protected abstract Pair<String, String> getPosition();


	@Override
	protected TestSearchScope getScope()
	{
		return getConfiguration().getConfigurationModule().getModule() != null ? TestSearchScope.MODULE_WITH_DEPENDENCIES : TestSearchScope.WHOLE_PROJECT;
	}

	@Override
	protected boolean forkPerModule()
	{
		return getConfiguration().getConfigurationModule().getModule() == null;
	}

	@Override
	protected PsiElement retrievePsiElement(Object pattern)
	{
		if(pattern instanceof String)
		{
			final String className = StringUtil.getPackageName((String) pattern, ',');
			if(!pattern.equals(className))
			{
				final Project project = getConfiguration().getProject();
				final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
				final SourceScope sourceScope = getSourceScope();
				final GlobalSearchScope globalSearchScope = sourceScope != null ? sourceScope.getGlobalSearchScope() : GlobalSearchScope.projectScope(project);
				return facade.findClass(className, globalSearchScope);
			}
		}
		return null;
	}

	@Override
	public SearchForTestsTask createSearchingForTestsTask()
	{
		return new SearchForTestsTask(getConfiguration().getProject(), myServerSocket)
		{
			private Set<String> myPatterns;

			@Override
			protected void search() throws ExecutionException
			{
				myPatterns = TestDiscoverySearchHelper.search((Project) getProject(), getPosition(), getChangeList(), getConfiguration().getFrameworkPrefix());
			}

			@Override
			protected void onFound()
			{
				if(myPatterns != null)
				{
					try
					{
						addClassesListToJavaParameters(myPatterns, Function.identity(), "", false, getJavaParameters());
					}
					catch(ExecutionException ignored)
					{
					}
				}
			}
		};
	}

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		final OwnJavaParameters javaParameters = super.createJavaParameters();
		createTempFiles(javaParameters);

		createServerSocket(javaParameters);
		return javaParameters;
	}

	@Override
	public String suggestActionName()
	{
		return "";
	}

	@Override
	public RefactoringElementListener getListener(PsiElement element, JUnitConfiguration configuration)
	{
		return null;
	}

	@Override
	public boolean isConfiguredByElement(JUnitConfiguration configuration, PsiClass testClass, PsiMethod testMethod, PsiPackage testPackage, PsiDirectory testDir)
	{
		return false;
	}
}
