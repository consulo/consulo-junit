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

import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.configuration.SimpleConfigurationType;
import consulo.java.language.module.extension.JavaModuleExtension;
import consulo.junit.localize.JUnitLocalize;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JUnitConfigurationType extends SimpleConfigurationType {
    public JUnitConfigurationType() {
        super(
            "JUnit",
            JUnitLocalize.junitConfigurationName(),
            JUnitLocalize.junitConfigurationDescription(),
            PlatformIconGroup.runconfigurationsJunit()
        );
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
        return new JUnitConfiguration("", project, this);
    }

    @Override
    public void onNewConfigurationCreated(@Nonnull RunConfiguration configuration) {
        ((ModuleBasedConfiguration)configuration).onNewConfigurationCreated();
    }

    @Override
    public boolean isApplicable(@Nonnull Project project) {
        return ModuleExtensionHelper.getInstance(project).hasModuleExtension(JavaModuleExtension.class);
    }

    @Nonnull
    public static JUnitConfigurationType getInstance() {
        return EP_NAME.findExtensionOrFail(JUnitConfigurationType.class);
    }
}
