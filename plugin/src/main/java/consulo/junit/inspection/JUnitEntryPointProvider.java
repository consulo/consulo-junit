package consulo.junit.inspection;

import com.intellij.java.analysis.codeInspection.ex.EntryPointProvider;
import com.intellij.java.execution.impl.junit.JUnitUtil;
import com.intellij.java.indexing.search.searches.ClassInheritorsSearch;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiMethod;
import com.intellij.java.language.psi.PsiModifier;
import com.intellij.java.language.psi.util.PsiClassUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.function.CommonProcessors;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 19/06/2023
 */
@ExtensionImpl
public class JUnitEntryPointProvider implements EntryPointProvider<JUnitEntryPointState> {
  @Nonnull
  @Override
  public String getId() {
    return "junit";
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("JUnit testcases");
  }

  @Nonnull
  @Override
  public JUnitEntryPointState createState() {
    return new JUnitEntryPointState();
  }

  @Override
  @RequiredReadAction
  public boolean isEntryPoint(PsiElement psiElement, @Nonnull JUnitEntryPointState state) {
    if (state.ADD_JUNIT_TO_ENTRIES) {
      if (psiElement instanceof PsiClass) {
        final PsiClass aClass = (PsiClass)psiElement;
        if (JUnitUtil.isTestClass(aClass, false, true)) {
          if (!PsiClassUtil.isRunnableClass(aClass, true, true)) {
            final CommonProcessors.FindProcessor<PsiClass> findProcessor = new CommonProcessors.FindProcessor<PsiClass>() {
              @Override
              protected boolean accept(PsiClass psiClass) {
                return !psiClass.hasModifierProperty(PsiModifier.ABSTRACT);
              }
            };
            return !ClassInheritorsSearch.search(aClass).forEach(findProcessor) && findProcessor.isFound();
          }
          return true;
        }
      }
      else if (psiElement instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)psiElement;
        if (method.isConstructor() && method.getParameterList().getParametersCount() == 0) {
          PsiClass psiClass = method.getContainingClass();
          return psiClass != null && JUnitUtil.isTestClass(psiClass);
        }
        if (JUnitUtil.isTestMethodOrConfig(method)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public String[] getIgnoreAnnotations() {
    return new String[]{
      "org.junit.Rule",
      "org.mockito.Mock",
      "org.junit.ClassRule"
    };
  }
}
