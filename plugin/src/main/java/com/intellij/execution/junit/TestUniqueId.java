// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.java.execution.JavaExecutionUtil;
import com.intellij.java.language.codeInsight.TestFrameworks;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.testIntegration.TestFramework;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.action.Location;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.AbstractTestProxy;
import consulo.execution.test.sm.runner.SMTestProxy;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPackage;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.process.ExecutionException;
import consulo.project.Project;

import jakarta.annotation.Nonnull;
import java.util.Arrays;
import java.util.function.Function;

public class TestUniqueId extends TestObject
{
	public TestUniqueId(JUnitConfiguration configuration, ExecutionEnvironment environment)
	{
		super(configuration, environment);
	}

	@Override
	protected OwnJavaParameters createJavaParameters() throws ExecutionException
	{
		final OwnJavaParameters javaParameters = super.createJavaParameters();
		final JUnitConfiguration.Data data = getConfiguration().getPersistentData();
		addClassesListToJavaParameters(Arrays.asList(data.getUniqueIds()), getUniqueIdPresentation(), "", true, javaParameters);
		return javaParameters;
	}

	public static Function<String, String> getUniqueIdPresentation()
	{
		return s -> "\u001B" + s;
	}

	/**
	 * Return nodeId for the cases where containing method or class do not represent tests (IDEA fails to detect them as tests),
	 * or if parent node provides the same location, the case of parameterized/dynamic tests
	 */
	public static String getEffectiveNodeId(AbstractTestProxy testInfo, Project project, GlobalSearchScope searchScope)
	{
		String nodeId = testInfo.getUserData(SMTestProxy.NODE_ID);
		if(nodeId != null)
		{
			Location location = testInfo.getLocation(project, searchScope);
			if(location == null)
			{
				return nodeId;
			}
			PsiElement psiElement = location.getPsiElement();
			PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
			if(method != null)
			{
				PsiClass containingClass = method.getContainingClass();
				TestFramework testFramework = containingClass != null ? TestFrameworks.detectFramework(containingClass) : null;
				if(testFramework == null || !testFramework.isTestMethod(psiElement))
				{
					return nodeId;
				}
			}
			else
			{
				PsiClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PsiClass.class);
				if(containingClass != null && TestFrameworks.detectFramework(containingClass) == null)
				{
					return nodeId;
				}
			}

			AbstractTestProxy parent = testInfo.getParent();
			if(parent != null)
			{
				Location parentLocation = parent.getLocation(project, searchScope);
				if(parentLocation != null && parentLocation.getPsiElement() == psiElement)
				{
					return nodeId;
				}
			}
		}
		return null;
	}

	@Nonnull
	@Override
	protected String getForkMode()
	{
		return super.getForkMode();
	}

	@Override
	public String suggestActionName()
	{
		String[] ids = getConfiguration().getPersistentData().getUniqueIds();
		return JavaExecutionUtil.getShortClassName(ids.length > 0 ? ids[0] : "<empty>");
	}

	@Override
	public RefactoringElementListener getListener(final PsiElement element, final JUnitConfiguration configuration)
	{
		return null;
	}

	@Override
	public boolean isConfiguredByElement(final JUnitConfiguration configuration, PsiClass testClass, PsiMethod testMethod, PsiPackage testPackage, PsiDirectory testDir)
	{

		return false;
	}

	@Override
	public void checkConfiguration() throws RuntimeConfigurationException
	{
		super.checkConfiguration();
		String[] ids = getConfiguration().getPersistentData().getUniqueIds();
		if(ids == null || ids.length == 0)
		{
			throw new RuntimeConfigurationException("No unique id specified");
		}
	}
}
