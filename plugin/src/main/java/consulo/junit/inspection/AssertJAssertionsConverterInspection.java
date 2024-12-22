package consulo.junit.inspection;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.junit.localize.JUnitLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.localize.CommonQuickFixLocalize;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.Set;

import static com.siyeh.ig.junit.JUnitCommonClassNames.*;
import static consulo.java.language.module.util.JavaClassNames.JAVA_UTIL_COLLECTION;
import static consulo.java.language.module.util.JavaClassNames.JAVA_UTIL_MAP;

/**
 * @author UNV
 * @since 2024-12-15
 */
@ExtensionImpl
public class AssertJAssertionsConverterInspection extends BaseJavaBatchLocalInspectionTool {
    protected static final String ORG_ASSERTJ_ASSERTIONS = "org.assertj.core.api.Assertions";

    protected static final String ASSERT_ARRAY_EQUALS = "assertArrayEquals";
    protected static final String ASSERT_EQUALS = "assertEquals";
    protected static final String ASSERT_FALSE = "assertFalse";
    protected static final String ASSERT_NOT_EQUALS = "assertNotEquals";
    protected static final String ASSERT_NOT_NULL = "assertNotNull";
    protected static final String ASSERT_NOT_SAME = "assertNotSame";
    protected static final String ASSERT_NULL = "assertNull";
    protected static final String ASSERT_SAME = "assertSame";
    protected static final String ASSERT_TRUE = "assertTrue";

    @Nonnull
    @Override
    public String getDisplayName() {
        return JUnitLocalize.inspectionsMigrateAssertToAssertjName().get();
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        PsiClass assertionsClass = JavaPsiFacade.getInstance(holder.getProject())
            .findClass(ORG_ASSERTJ_ASSERTIONS, holder.getFile().getResolveScope());
        if (assertionsClass == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new AssertJAssertionsConverterVisitor(holder);
    }

    @RequiredReadAction
    protected static boolean isCollectionMethodCall(@Nonnull PsiMethodCallExpression methodCall, @Nonnull String methodName) {
        PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
        if (!methodName.equals(methodExpr.getReferenceName())) {
            return false;
        }

        PsiClass psiClass = methodExpr.resolve() instanceof PsiMethod method ? method.getContainingClass() : null;
        return psiClass != null
            && (InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_COLLECTION) || InheritanceUtil.isInheritor(psiClass, JAVA_UTIL_MAP));
    }

    private static class AssertJAssertionsConverterVisitor extends JavaElementVisitor {
        private final ProblemsHolder holder;

        AssertJAssertionsConverterVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        @RequiredReadAction
        public void visitMethodCallExpression(@Nonnull PsiMethodCallExpression expression) {
            PsiReferenceExpression methodExpression = expression.getMethodExpression();
            String methodName = methodExpression.getReferenceName();
            if (methodName == null || !JUNIT_ASSERT_METHODS.contains(methodName)) {
                return;
            }

            PsiClass methodClass = methodExpression.resolve() instanceof PsiMethod method ? method.getContainingClass() : null;
            String methodClassName = methodClass != null ? methodClass.getQualifiedName() : null;
            if (methodClassName == null || !JUNIT_ASSERT_CLASS_NAMES.contains(methodClass.getQualifiedName())) {
                return;
            }

            if (isBooleanAssert(methodName)) {
                PsiExpression[] args = expression.getArgumentList().getExpressions();
                PsiExpression arg = args.length > 0 ? args[args.length - 1] : null;
                if (!(arg instanceof PsiBinaryExpression || arg instanceof PsiInstanceOfExpression
                    || arg instanceof PsiMethodCallExpression methodCall && isCollectionMethodCall(methodCall, "isEmpty"))) {
                    return;
                }
            }

            holder.newProblem(JUnitLocalize.inspectionsMigrateAssertToAssertjDescription("assertThat()"))
                .range(expression)
                .withFix(new MigrateToAssertThatQuickFix())
                .create();
        }

        private boolean isBooleanAssert(String methodName) {
            return ASSERT_TRUE.equals(methodName) || ASSERT_FALSE.equals(methodName);
        }

        private static final Set<String> JUNIT_ASSERT_METHODS = Set.of(
            ASSERT_ARRAY_EQUALS,
            ASSERT_EQUALS,
            ASSERT_FALSE,
            ASSERT_NOT_EQUALS,
            ASSERT_NOT_NULL,
            ASSERT_NOT_SAME,
            ASSERT_NULL,
            ASSERT_SAME,
            ASSERT_TRUE
        );

        private static final Set<String> JUNIT_ASSERT_CLASS_NAMES =
            Set.of(ORG_JUNIT_ASSERT, JUNIT_FRAMEWORK_ASSERT, ORG_JUNIT_JUPITER_API_ASSERTIONS);
    }

    private static class MigrateToAssertThatQuickFix implements LocalQuickFix {
        private static final Map<IElementType, IElementType> NEGATE_COMPARISON = Map.of(
            JavaTokenType.EQEQ, JavaTokenType.NE,
            JavaTokenType.NE, JavaTokenType.EQEQ,
            JavaTokenType.LT, JavaTokenType.GE,
            JavaTokenType.GE, JavaTokenType.LT,
            JavaTokenType.GT, JavaTokenType.LE,
            JavaTokenType.LE, JavaTokenType.GT
        );

        private static final Map<String, String> ASSERTJ_EQUIVALENT = Map.of(
            ASSERT_EQUALS, "isEqualTo",
            ASSERT_ARRAY_EQUALS, "isEqualTo",
            ASSERT_NOT_EQUALS, "isNotEqualTo",
            ASSERT_SAME, "isSameAs",
            ASSERT_NOT_SAME, "isNotSameAs",
            ASSERT_NULL, "isNull",
            ASSERT_NOT_NULL, "isNotNull"
        );

        private static final Map<IElementType, String> ASSERTJ_COMPARISON = Map.of(
            JavaTokenType.EQEQ, "isSameAs",
            JavaTokenType.NE, "isNotSameAs",
            JavaTokenType.LE, "isLessThanOrEqualTo",
            JavaTokenType.LT, "isLessThan",
            JavaTokenType.GE, "isGreaterThanOrEqualTo",
            JavaTokenType.GT, "isGreaterThan"
        );

        private static final Map<IElementType, String> ASSERTJ_SIZE_COMPARISON = Map.of(
            JavaTokenType.EQEQ, "hasSize",
            JavaTokenType.LE, "hasSizeLessThanOrEqualTo",
            JavaTokenType.LT, "hasSizeLessThan",
            JavaTokenType.GE, "hasSizeGreaterThanOrEqualTo",
            JavaTokenType.GT, "hasSizeGreaterThan"
        );

        @Nonnull
        @Override
        public String getFamilyName() {
            return CommonQuickFixLocalize.fixReplaceWithX("assertThat()").get();
        }

        @Override
        @RequiredReadAction
        public void applyFix(@Nonnull Project project, ProblemDescriptor descriptor) {
            PsiMethodCallExpression expression = descriptor.getPsiElement() instanceof PsiMethodCallExpression mce ? mce : null;
            PsiReferenceExpression methodExpression = expression != null ? expression.getMethodExpression() : null;
            String methodName = methodExpression != null ? methodExpression.getReferenceName() : null;
            if (methodName == null) {
                return;
            }

            PsiExpression[] args = expression.getArgumentList().getExpressions();
            PsiExpression lastArg = args[args.length - 1];

            AssertJReplacer replacer = new AssertJReplacer(project, expression);

            switch (methodName) {
                case ASSERT_TRUE, ASSERT_FALSE -> {
                    boolean negate = ASSERT_FALSE.equals(methodName);
                    if (lastArg instanceof PsiBinaryExpression binary) {
                        IElementType tokenType = binary.getOperationTokenType();
                        if (negate) {
                            tokenType = NEGATE_COMPARISON.get(tokenType);
                        }
                        PsiExpression lOperand = binary.getLOperand();
                        if (lOperand instanceof PsiMethodCallExpression methodCall
                            && isCollectionMethodCall(methodCall, "size")
                            && ASSERTJ_SIZE_COMPARISON.containsKey(tokenType)) {

                            String comparisonMethod = ASSERTJ_SIZE_COMPARISON.get(tokenType);
                            replacer.assertThat(
                                methodCall.getMethodExpression().getQualifier(),
                                comparisonMethod,
                                binary.getROperand()
                            );
                        }
                        else {
                            String comparisonMethod = ASSERTJ_COMPARISON.get(tokenType);
                            if (comparisonMethod != null) {
                                replacer.assertThat(lOperand, comparisonMethod, binary.getROperand());
                            }
                        }
                    }
                    else if (lastArg instanceof PsiInstanceOfExpression instanceOf) {
                        PsiTypeElement checkType = instanceOf.getCheckType();
                        assert checkType != null;
                        replacer.assertThat(
                            instanceOf.getOperand(),
                            negate ? "isNotInstanceOf" : "isInstanceOf",
                            checkType.getType().getCanonicalText() + ".class"
                        );
                    }
                    else if (lastArg instanceof PsiMethodCallExpression methodCall
                        && isCollectionMethodCall(methodCall, "isEmpty")) {

                        replacer.assertThat(
                            methodCall.getMethodExpression().getQualifier(),
                            negate ? "isNotEmpty" : "isEmpty"
                        );
                    }
                }

                case ASSERT_EQUALS, ASSERT_ARRAY_EQUALS, ASSERT_NOT_EQUALS, ASSERT_SAME, ASSERT_NOT_SAME -> {
                    if (ASSERT_EQUALS.equals(methodName)
                        && lastArg instanceof PsiMethodCallExpression methodCall
                        && isCollectionMethodCall(methodCall, "size")) {

                        replacer.assertThat(methodCall.getMethodExpression().getQualifier(), "hasSize", args[0]);
                    }
                    else {
                        replacer.assertThat(lastArg, ASSERTJ_EQUIVALENT.get(methodName), args[0]);
                    }
                }

                case ASSERT_NULL, ASSERT_NOT_NULL -> {
                    replacer.assertThat(lastArg, ASSERTJ_EQUIVALENT.get(methodName));
                }
            }
        }
    }

    private static class AssertJReplacer {
        private final Project myProject;
        private final PsiMethodCallExpression myExpression;

        private AssertJReplacer(Project project, PsiMethodCallExpression expression) {
            this.myProject = project;
            this.myExpression = expression;
        }

        @RequiredReadAction
        public void assertThat(PsiElement expression, String methodName) {
            assertThat(expression, methodName, (String)null);
        }

        @RequiredReadAction
        public void assertThat(PsiElement actualExpr, String methodName, PsiElement expectedExpr) {
            assertThat(actualExpr, methodName, expectedExpr != null ? expectedExpr.getText() : null);
        }

        @RequiredReadAction
        public void assertThat(PsiElement actualExpr, String methodName, String expectedExpr) {
            String code = "assertThat(" + actualExpr.getText() + ")." +
                methodName + "(" + (expectedExpr == null ? "" : expectedExpr) + ")";

            PsiExpression newExpression = JavaPsiFacade.getElementFactory(myProject)
                .createExpressionFromText(code, myExpression);

            WriteAction.run(() -> myExpression.replace(newExpression));
        }
    }
}