/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.java.execution.impl.testframework.AbstractInClassConfigurationProducer;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;

import jakarta.annotation.Nonnull;

@ExtensionImpl
public class TestInClassConfigurationProducer extends JUnitConfigurationProducer {
    private JUnitInClassConfigurationProducerDelegate myDelegate = new JUnitInClassConfigurationProducerDelegate();

    public TestInClassConfigurationProducer() {
        super(JUnitConfigurationType.getInstance());
    }

    @Override
    protected boolean setupConfigurationFromContext(
        JUnitConfiguration configuration,
        ConfigurationContext context,
        Ref<PsiElement> sourceElement
    ) {
        return myDelegate.setupConfigurationFromContext(configuration, context, sourceElement);
    }

    @Override
    public void onFirstRun(
        @Nonnull ConfigurationFromContext configuration,
        @Nonnull ConfigurationContext fromContext,
        @Nonnull Runnable performRunnable
    ) {
        myDelegate.onFirstRun(configuration, fromContext, performRunnable);
    }

    private static class JUnitInClassConfigurationProducerDelegate extends AbstractInClassConfigurationProducer<JUnitConfiguration> {
        public JUnitInClassConfigurationProducerDelegate() {
            super(JUnitConfigurationType.getInstance());
        }

        @Override
        protected boolean setupConfigurationFromContext(
            JUnitConfiguration configuration,
            ConfigurationContext context,
            Ref<PsiElement> sourceElement
        ) {
            return super.setupConfigurationFromContext(configuration, context, sourceElement);
        }
    }
}
