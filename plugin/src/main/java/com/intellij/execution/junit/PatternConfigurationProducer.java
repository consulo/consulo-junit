/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.java.execution.impl.junit2.PsiMemberParameterizedLocation;
import com.intellij.java.execution.impl.testframework.AbstractPatternBasedConfigurationProducer;
import com.intellij.java.language.psi.PsiMember;
import com.intellij.java.language.psi.PsiMethod;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.Location;
import consulo.language.psi.PsiElement;
import consulo.module.Module;
import consulo.util.lang.ref.SimpleReference;

import java.util.LinkedHashSet;
import java.util.Set;

@ExtensionImpl
public class PatternConfigurationProducer extends AbstractPatternBasedConfigurationProducer<JUnitConfiguration> {
    public PatternConfigurationProducer() {
        super(JUnitConfigurationType.getInstance());
    }

    @Override
    protected String getMethodPresentation(PsiMember member) {
        return member instanceof PsiMethod method
            ? JUnitConfiguration.Data.getMethodPresentation(method)
            : super.getMethodPresentation(member);
    }

    @Override
    @RequiredReadAction
    protected boolean setupConfigurationFromContext(
        JUnitConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        PsiElement element = checkPatterns(context, classes);
        if (element == null) {
            return false;
        }
        sourceElement.set(element);
        JUnitConfiguration.Data data = configuration.getPersistentData();
        data.setPatterns(classes);
        data.TEST_OBJECT = JUnitConfiguration.TEST_PATTERN;
        data.setScope(setupPackageConfiguration(context, configuration, data.getScope()));
        configuration.setGeneratedName();
        Location contextLocation = context.getLocation();
        if (contextLocation instanceof PsiMemberParameterizedLocation memberParameterizedLocation) {
            String paramSetName = memberParameterizedLocation.getParamSetName();
            if (paramSetName != null) {
                configuration.setProgramParameters(paramSetName);
            }
        }
        return true;
    }

    @Override
    @RequiredReadAction
    protected Module findModule(JUnitConfiguration configuration, Module contextModule) {
        Set<String> patterns = configuration.getPersistentData().getPatterns();
        return findModule(configuration, contextModule, patterns);
    }

    @Override
    @RequiredReadAction
    public boolean isConfigurationFromContext(JUnitConfiguration unitConfiguration, ConfigurationContext context) {
        TestObject testObject = unitConfiguration.getTestObject();
        if (testObject instanceof TestsPattern) {
            if (differentParamSet(unitConfiguration, context.getLocation())) {
                return false;
            }
            Set<String> patterns = unitConfiguration.getPersistentData().getPatterns();
            if (isConfiguredFromContext(context, patterns)) {
                return true;
            }
        }
        return false;
    }
}
