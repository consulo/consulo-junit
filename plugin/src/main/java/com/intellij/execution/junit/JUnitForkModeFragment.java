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

import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.junit.localize.JUnitLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Runs the tests of a {@link JUnitConfiguration} in a new JVM per method or per class.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class JUnitForkModeFragment implements RunConfigurationFragment<JUnitConfiguration> {
    private static final List<String> FORK_MODES = List.of(
        JUnitConfiguration.FORK_NONE,
        JUnitConfiguration.FORK_METHOD,
        JUnitConfiguration.FORK_KLASS
    );

    @Override
    public String getId() {
        return "junit.fork.mode";
    }

    @Override
    public LocalizeValue getName() {
        return JUnitLocalize.runConfigurationFragmentForkMode();
    }

    @Override
    public LocalizeValue getGroup() {
        return JUnitLocalize.runConfigurationFragmentGroupJunit();
    }

    @Override
    public boolean isVisibleByDefault(JUnitConfiguration target) {
        return !JUnitConfiguration.FORK_NONE.equals(target.getForkMode());
    }

    @Override
    public void applyHidden(JUnitConfiguration target) {
        target.setForkMode(JUnitConfiguration.FORK_NONE);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<JUnitConfiguration> createEditor(Project project, Disposable parent) {
        ComboBox<String> forkBox = ComboBox.create(FORK_MODES);
        forkBox.setTextRenderer(JUnitForkModeFragment::getForkModeName);
        forkBox.setValue(JUnitConfiguration.FORK_NONE, false);

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(JUnitLocalize.junitConfigurationForkModeLabel(), forkBox);
            }

            @RequiredUIAccess
            @Override
            public void reset(JUnitConfiguration target) {
                String forkMode = target.getForkMode();
                forkBox.setValue(FORK_MODES.contains(forkMode) ? forkMode : JUnitConfiguration.FORK_NONE, false);
            }

            @RequiredUIAccess
            @Override
            public void apply(JUnitConfiguration target) {
                String forkMode = forkBox.getValue();
                target.setForkMode(forkMode == null ? JUnitConfiguration.FORK_NONE : forkMode);
            }
        };
    }

    private static LocalizeValue getForkModeName(@Nullable String forkMode) {
        if (JUnitConfiguration.FORK_METHOD.equals(forkMode)) {
            return JUnitLocalize.junitConfigurationForkModeMethod();
        }
        if (JUnitConfiguration.FORK_KLASS.equals(forkMode)) {
            return JUnitLocalize.junitConfigurationForkModeClass();
        }
        return JUnitLocalize.junitConfigurationForkModeNone();
    }
}
