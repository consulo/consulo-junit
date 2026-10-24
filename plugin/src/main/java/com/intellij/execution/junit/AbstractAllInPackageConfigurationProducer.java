// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import com.intellij.java.execution.impl.junit.JavaRuntimeConfigurationProducerBase;
import com.intellij.java.execution.impl.junit2.info.LocationUtil;
import com.intellij.java.language.psi.PsiJavaPackage;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.configuration.ConfigurationType;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.SimpleReference;

public abstract class AbstractAllInPackageConfigurationProducer extends JUnitConfigurationProducer {
    protected AbstractAllInPackageConfigurationProducer(ConfigurationType configurationType) {
        super(configurationType);
    }

    @Override
    protected boolean setupConfigurationFromContext(
        JUnitConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        PsiJavaPackage psiPackage = JavaRuntimeConfigurationProducerBase.checkPackage(context.getPsiLocation());
        if (psiPackage == null) {
            return false;
        }
        sourceElement.set(psiPackage);
        if (!LocationUtil.isJarAttached(
            context.getLocation(),
            psiPackage,
            JUnitUtil.TEST_CASE_CLASS,
            JUnitUtil.TEST5_ANNOTATION,
            JUnitCommonClassNames.ORG_JUNIT_PLATFORM_ENGINE_TEST_ENGINE
        )) {
            return false;
        }
        JUnitConfigurationOptions data = configuration.getOptions();
        data.setPackageName(psiPackage.getQualifiedName());
        data.setTestObject(JUnitConfiguration.TEST_PACKAGE);
        configuration.setTestSearchScope(setupPackageConfiguration(context, configuration, configuration.getTestSearchScope()));
        configuration.setGeneratedName();
        return true;
    }

    @Override
    public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return !other.isProducedBy(AbstractAllInDirectoryConfigurationProducer.class)
            && !other.isProducedBy(PatternConfigurationProducer.class);
    }
}
