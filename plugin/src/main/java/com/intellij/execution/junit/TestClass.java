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
import com.intellij.java.execution.configurations.JavaRunConfigurationModule;
import com.intellij.java.execution.impl.junit.RefactoringListeners;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import consulo.execution.ExecutionBundle;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.process.ExecutionException;
import consulo.util.lang.Comparing;

import javax.annotation.Nonnull;

class TestClass extends TestObject
{
	public TestClass(JUnitConfiguration configuration, ExecutionEnvironment environment)
	{
		super(configuration, environment);
	}

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		final OwnJavaParameters javaParameters = super.createJavaParameters();
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
		javaParameters.getProgramParametersList().add(data.getMainClassName());
		return javaParameters;
	}

	@Nonnull
	@Override
	protected String getForkMode()
	{
		String forkMode = super.getForkMode();
		return JUnitConfiguration.FORK_KLASS.equals(forkMode) ? JUnitConfiguration.FORK_REPEAT : forkMode;
	}

	@Override
	public String suggestActionName()
	{
		String name = getConfiguration().getPersistentData().MAIN_CLASS_NAME;
		if(name != null && name.endsWith("."))
		{
			return name;
		}
		return JavaExecutionUtil.getShortClassName(name);
	}

	@Override
	public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration)
	{
		return RefactoringListeners.getClassOrPackageListener(element, configuration.myClass);
	}

	@Override
	public boolean isConfiguredByElement(final JUnitConfiguration configuration, PsiClass testClass, PsiMethod testMethod, PsiPackage testPackage, PsiDirectory testDir)
	{

		if(testClass == null)
		{
			return false;
		}
		if(testMethod != null)
		{
			// 'test class' configuration is not equal to the 'test method' configuration!
			return false;
		}
		return Comparing.equal(JavaExecutionUtil.getRuntimeQualifiedName(testClass), configuration.getPersistentData().getMainClassName());
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();
		final String testClassName = getConfiguration().getPersistentData().getMainClassName();
		final JavaRunConfigurationModule configurationModule = getConfiguration().getConfigurationModule();
		final PsiClass testClass = configurationModule.checkModuleAndClassName(testClassName, ExecutionBundle.message("no.test.class.specified.error.text"));
		if(!JUnitUtil.isTestClass(testClass))
		{
			throw new RuntimeConfigurationWarning(ExecutionBundle.message("class.isnt.test.class.error.message", testClassName));
		}
	}
}
