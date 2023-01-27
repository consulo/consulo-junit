/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.junit.JUnitConfiguration;
import com.intellij.execution.junit.TestMethods;
import com.intellij.java.execution.impl.actions.JavaRerunFailedTestsAction;
import consulo.execution.configuration.RunProfileState;
import consulo.execution.executor.Executor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.test.TestConsoleProperties;
import consulo.module.Module;
import consulo.ui.ex.ComponentContainer;

import javax.annotation.Nonnull;

public class RerunFailedTestsAction extends JavaRerunFailedTestsAction
{
	public RerunFailedTestsAction(@Nonnull ComponentContainer componentContainer, @Nonnull TestConsoleProperties consoleProperties)
	{
		super(componentContainer, consoleProperties);
	}

	@Override
	protected MyRunProfile getRunProfile(@Nonnull ExecutionEnvironment environment)
	{
		//noinspection ConstantConditions
		final JUnitConfiguration configuration = (JUnitConfiguration) myConsoleProperties.getConfiguration();
		final TestMethods testMethods = new TestMethods(configuration, environment, getFailedTests(configuration.getProject()));
		return new MyRunProfile(configuration)
		{
			@Override
			@Nonnull
			public Module[] getModules()
			{
				return testMethods.getModulesToCompile();
			}

			@Override
			public RunProfileState getState(@Nonnull Executor executor, @Nonnull ExecutionEnvironment env)
			{
				testMethods.clear();
				return testMethods;
			}
		};
	}
}
