/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.java.execution.impl.actions.AbstractAddToTestsPatternAction;
import consulo.execution.action.RunConfigurationProducer;
import consulo.execution.configuration.ConfigurationType;

import jakarta.annotation.Nonnull;
import java.util.Set;

public class JUnitAddToTestsPatternAction extends AbstractAddToTestsPatternAction<JUnitConfiguration>
{
	@Override
	@Nonnull
	protected PatternConfigurationProducer getPatternBasedProducer()
	{
		return RunConfigurationProducer.getInstance(PatternConfigurationProducer.class);
	}

	@Override
	@Nonnull
	protected ConfigurationType getConfigurationType()
	{
		return JUnitConfigurationType.getInstance();
	}

	@Override
	protected boolean isPatternBasedConfiguration(JUnitConfiguration configuration)
	{
		return configuration.getPersistentData().TEST_OBJECT == JUnitConfiguration.TEST_PATTERN;
	}

	@Override
	protected Set<String> getPatterns(JUnitConfiguration configuration)
	{
		return configuration.getPersistentData().getPatterns();
	}
}