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

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configuration.ConfigurationFactoryEx;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import consulo.java.module.extension.JavaModuleExtension;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

public class JUnitConfigurationType implements ConfigurationType
{
	private final ConfigurationFactory myFactory;

	public JUnitConfigurationType()
	{
		myFactory = new ConfigurationFactoryEx(this)
		{
			@Override
			public RunConfiguration createTemplateConfiguration(Project project)
			{
				return new JUnitConfiguration("", project, this);
			}

			@Override
			public void onNewConfigurationCreated(@Nonnull RunConfiguration configuration)
			{
				((ModuleBasedConfiguration) configuration).onNewConfigurationCreated();
			}

			@Override
			public boolean isApplicable(@Nonnull Project project)
			{
				return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
			}
		};
	}

	@Override
	public String getDisplayName()
	{
		return ExecutionBundle.message("junit.configuration.display.name");
	}

	@Override
	public String getConfigurationTypeDescription()
	{
		return ExecutionBundle.message("junit.configuration.description");
	}

	@Override
	public Image getIcon()
	{
		return AllIcons.RunConfigurations.Junit;
	}

	@Override
	public ConfigurationFactory[] getConfigurationFactories()
	{
		return new ConfigurationFactory[]{myFactory};
	}

	@Override
	@Nonnull
	public String getId()
	{
		return "JUnit";
	}

	@Nonnull
	public static JUnitConfigurationType getInstance()
	{
		return CONFIGURATION_TYPE_EP.findExtensionOrFail(JUnitConfigurationType.class);
	}
}
