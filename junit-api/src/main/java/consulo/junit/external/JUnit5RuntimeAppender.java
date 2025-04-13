package consulo.junit.external;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.execution.CantRunException;
import consulo.java.execution.configurations.OwnJavaParameters;
import consulo.language.psi.scope.GlobalSearchScope;

/**
 * @author VISTALL
 * @since 2025-04-12
 */
@ExtensionAPI(ComponentScope.PROJECT)
public interface JUnit5RuntimeAppender {
    boolean canHandle(OwnJavaParameters javaParameters, GlobalSearchScope globalSearchScope);

    void appendRuntime(OwnJavaParameters javaParameters, GlobalSearchScope globalSearchScope) throws CantRunException;
}
