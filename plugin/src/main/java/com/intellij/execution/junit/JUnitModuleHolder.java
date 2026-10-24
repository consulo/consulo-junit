/*
 * Copyright 2013-2026 consulo.io
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

import com.intellij.java.execution.impl.ui.BaseConfigurationModuleSelector;
import consulo.execution.configuration.ModuleBasedConfiguration;
import consulo.module.Module;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * The module of a {@link JUnitConfiguration} as last reset or applied, for completion and the choosers of its fragments.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class JUnitModuleHolder extends BaseConfigurationModuleSelector {
    private volatile @Nullable Module myModule;

    JUnitModuleHolder(Project project) {
        super(project);
    }

    void setModule(@Nullable Module module) {
        myModule = module;
    }

    @Override
    public void applyTo(ModuleBasedConfiguration configurationModule) {
    }

    @Override
    public void reset(ModuleBasedConfiguration configuration) {
        myModule = configuration.getConfigurationModule().getModule();
    }

    @Override
    public @Nullable Module getModule() {
        return myModule;
    }

    @Override
    protected void setModules(Collection<Module> modules) {
    }
}
