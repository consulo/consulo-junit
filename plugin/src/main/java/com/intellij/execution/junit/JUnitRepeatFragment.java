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

import com.intellij.rt.execution.junit.RepeatCount;
import consulo.disposer.Disposable;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.execution.configuration.fragment.RunConfigurationFragmentEditor;
import consulo.execution.configuration.fragment.RunConfigurationFragmentLayout;
import consulo.junit.localize.JUnitLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.IntBox;
import consulo.ui.annotation.RequiredUIAccess;
import org.jspecify.annotations.Nullable;

/**
 * Repeats the tests of a {@link JUnitConfiguration} a number of times, until a failure or until stopped.
 *
 * @author VISTALL
 * @since 2026-10-24
 */
class JUnitRepeatFragment implements RunConfigurationFragment<JUnitConfiguration> {
    @Override
    public String getId() {
        return "junit.repeat";
    }

    @Override
    public LocalizeValue getName() {
        return JUnitLocalize.runConfigurationFragmentRepeat();
    }

    @Override
    public LocalizeValue getGroup() {
        return JUnitLocalize.runConfigurationFragmentGroupJunit();
    }

    @Override
    public boolean isVisibleByDefault(JUnitConfiguration target) {
        return !RepeatCount.ONCE.equals(target.getRepeatMode());
    }

    @Override
    public void applyHidden(JUnitConfiguration target) {
        target.setRepeatMode(RepeatCount.ONCE);
        target.setRepeatCount(1);
    }

    @RequiredUIAccess
    @Override
    public RunConfigurationFragmentEditor<JUnitConfiguration> createEditor(Project project, Disposable parent) {
        ComboBox<String> repeatBox = ComboBox.create(RepeatCount.REPEAT_TYPES);
        repeatBox.setTextRenderer(JUnitRepeatFragment::getRepeatModeName);
        repeatBox.setValue(RepeatCount.ONCE, false);

        IntBox repeatCountBox = IntBox.create(1).withRange(1, Integer.MAX_VALUE);
        repeatCountBox.setEnabled(false);

        repeatBox.addValueListener(event -> repeatCountBox.setEnabled(RepeatCount.N.equals(event.getValue())));

        return new RunConfigurationFragmentEditor<>() {
            @RequiredUIAccess
            @Override
            public void addRows(RunConfigurationFragmentLayout layout) {
                layout.addLabeled(JUnitLocalize.junitConfigurationRepeatLabel(), repeatBox);
                layout.addLabeled(JUnitLocalize.junitConfigurationRepeatCountLabel(), repeatCountBox);
            }

            @RequiredUIAccess
            @Override
            public void reset(JUnitConfiguration target) {
                String repeatMode = target.getRepeatMode();
                repeatBox.setValue(repeatMode, false);
                repeatCountBox.setValue(Math.max(1, target.getRepeatCount()), false);
                repeatCountBox.setEnabled(RepeatCount.N.equals(repeatMode));
            }

            @RequiredUIAccess
            @Override
            public void apply(JUnitConfiguration target) {
                String repeatMode = repeatBox.getValue();
                target.setRepeatMode(repeatMode == null ? RepeatCount.ONCE : repeatMode);
                Integer repeatCount = repeatCountBox.getValue();
                target.setRepeatCount(repeatCount == null ? 1 : repeatCount);
            }
        };
    }

    private static LocalizeValue getRepeatModeName(@Nullable String repeatMode) {
        if (RepeatCount.N.equals(repeatMode)) {
            return JUnitLocalize.junitConfigurationRepeatModeNTimes();
        }
        if (RepeatCount.UNTIL_FAILURE.equals(repeatMode)) {
            return JUnitLocalize.junitConfigurationRepeatModeUntilFailure();
        }
        if (RepeatCount.UNLIMITED.equals(repeatMode)) {
            return JUnitLocalize.junitConfigurationRepeatModeUntilStopped();
        }
        return JUnitLocalize.junitConfigurationRepeatModeOnce();
    }
}
