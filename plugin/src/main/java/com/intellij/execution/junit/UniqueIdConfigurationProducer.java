// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.execution.junit;

import consulo.annotation.component.ExtensionImpl;
import consulo.dataContext.DataContext;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.test.AbstractTestProxy;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.module.Module;
import consulo.project.Project;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.Objects;

@ExtensionImpl
public class UniqueIdConfigurationProducer extends JUnitConfigurationProducer {
    public UniqueIdConfigurationProducer() {
        super(JUnitConfigurationType.getInstance());
    }

    @Override
    protected boolean setupConfigurationFromContext(
        JUnitConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        Project project = configuration.getProject();
        DataContext dataContext = context.getDataContext();
        AbstractTestProxy[] testProxies = dataContext.getData(AbstractTestProxy.KEY_OF_ARRAY);
        if (testProxies == null) {
            return false;
        }
        RunConfiguration runConfiguration = dataContext.getData(RunConfiguration.KEY);
        if (!(runConfiguration instanceof JUnitConfiguration jUnitConfiguration)) {
            return false;
        }
        Module module = jUnitConfiguration.getConfigurationModule().getModule();
        configuration.setModule(module);
        GlobalSearchScope searchScope =
            module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(project);
        String[] nodeIds = Arrays.stream(testProxies)
            .map(testProxy -> TestUniqueId.getEffectiveNodeId(testProxy, project, searchScope))
            .filter(Objects::nonNull)
            .toArray(String[]::new);
        if (nodeIds == null || nodeIds.length == 0) {
            return false;
        }
        JUnitConfiguration.Data data = configuration.getPersistentData();
        data.setUniqueIds(nodeIds);
        data.TEST_OBJECT = JUnitConfiguration.TEST_UNIQUE_ID;
        configuration.setGeneratedName();
        return true;
    }

    //prefer to method
    @Override
    public boolean shouldReplace(@Nonnull ConfigurationFromContext self, @Nonnull ConfigurationFromContext other) {
        return self.isProducedBy(UniqueIdConfigurationProducer.class)
            && (other.isProducedBy(TestInClassConfigurationProducer.class) || other.isProducedBy(PatternConfigurationProducer.class));
    }
}
