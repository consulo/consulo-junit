package consulo.junit.impl.inspection;

import com.intellij.java.analysis.codeInspection.BaseJavaBatchLocalInspectionTool;
import com.intellij.java.language.psi.*;
import com.intellij.java.language.psi.util.InheritanceUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.java.analysis.impl.localize.JavaInspectionsLocalize;
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
import jakarta.annotation.Nullable;

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
public class AssertJAssertionsConverterInspection extends BaseJavaBatchLocalInspectionTool<AssertJAssertionsConverterInspectionState> {
    private static final String ORG_ASSERTJ_ASSERTIONS = "org.assertj.core.api.Assertions";

    private static final String ASSERT_ARRAY_EQUALS = "assertArrayEquals";
    private static final String ASSERT_EQUALS = "assertEquals";
    private static final String ASSERT_FALSE = "assertFalse";
    private static final String ASSERT_NOT_EQUALS = "assertNotEquals";
    private static final String ASSERT_NOT_NULL = "assertNotNull";
    private static final String ASSERT_NOT_SAME = "assertNotSame";
    private static final String ASSERT_NULL = "assertNull";
    private static final String ASSERT_SAME = "assertSame";
    private static final String ASSERT_THROWS = "assertThrows";
    private static final String ASSERT_TRUE = "assertTrue";

    @Nonnull
    @Override
    public String getGroupDisplayName() {
        return JavaInspectionsLocalize.groupNamesJunitIssues().get();
    }

    @Nonnull
    @Override
    public String getDisplayName() {
        return JUnitLocalize.inspectionsMigrateAssertToAssertjName().get();
    }

    @Override
    @Nonnull
    public String getID() {
        return "JUnitMigrateAssertToAssertj";
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitorImpl(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        AssertJAssertionsConverterInspectionState inspectionState
    ) {
        PsiClass assertionsClass = JavaPsiFacade.getInstance(holder.getProject())
            .findClass(ORG_ASSERTJ_ASSERTIONS, holder.getFile().getResolveScope());
        if (assertionsClass == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new MyVisitor(holder, inspectionState.isStaticImport());
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

    @Nonnull
    @Override
    public AssertJAssertionsConverterInspectionState createStateProvider() {
        return new AssertJAssertionsConverterInspectionState();
    }

    private static class MyVisitor extends JavaElementVisitor {
        private final ProblemsHolder myHolder;
        private final boolean myStaticImport;

        MyVisitor(ProblemsHolder holder, boolean staticImport) {
            myHolder = holder;
            myStaticImport = staticImport;
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

            if (JUNIT_BOOLEAN_ASSERT_METHODS.contains(methodName)) {
                PsiExpression[] args = expression.getArgumentList().getExpressions();
                PsiExpression arg = args.length > 0 ? args[0] : null;
                if (!(arg instanceof PsiBinaryExpression || arg instanceof PsiInstanceOfExpression
                    || arg instanceof PsiMethodCallExpression methodCall && isCollectionMethodCall(methodCall, "isEmpty"))) {
                    return;
                }
            }

            myHolder.newProblem(JUnitLocalize.inspectionsMigrateAssertToAssertjDescription("assertThat()"))
                .range(expression)
                .withFix(new MyQuickFix(myStaticImport))
                .create();
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
            ASSERT_THROWS,
            ASSERT_TRUE
        );

        private static final Set<String> JUNIT_BOOLEAN_ASSERT_METHODS = Set.of(ASSERT_FALSE, ASSERT_TRUE);

        private static final Set<String> JUNIT_ASSERT_CLASS_NAMES =
            Set.of(ORG_JUNIT_ASSERT, JUNIT_FRAMEWORK_ASSERT, ORG_JUNIT_JUPITER_API_ASSERTIONS);
    }

    private static class MyQuickFix implements LocalQuickFix {
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

        private final boolean myStaticImport;

        private MyQuickFix(boolean staticImport) {
            myStaticImport = staticImport;
        }

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
            AssertJReplacer replacer = new AssertJReplacer(project, expression, myStaticImport);

            switch (methodName) {
                case ASSERT_TRUE, ASSERT_FALSE -> {
                    if (args.length == 2) {
                        replacer.as(args[1]);
                    }

                    boolean negate = ASSERT_FALSE.equals(methodName);
                    if (args[0] instanceof PsiBinaryExpression binary) {
                        IElementType tokenType = binary.getOperationTokenType();
                        if (negate) {
                            tokenType = NEGATE_COMPARISON.get(tokenType);
                        }
                        PsiExpression lOperand = binary.getLOperand();
                        if (lOperand instanceof PsiMethodCallExpression methodCall
                            && isCollectionMethodCall(methodCall, "size")
                            && ASSERTJ_SIZE_COMPARISON.containsKey(tokenType)) {

                            replacer.assertThat(methodCall.getMethodExpression().getQualifier())
                                .call(ASSERTJ_SIZE_COMPARISON.get(tokenType), binary.getROperand());
                        }
                        else {
                            String comparisonMethod = ASSERTJ_COMPARISON.get(tokenType);
                            if (comparisonMethod != null) {
                                replacer.assertThat(lOperand)
                                    .call(comparisonMethod, binary.getROperand());
                            }
                        }
                    }
                    else if (args[0] instanceof PsiInstanceOfExpression instanceOf) {
                        PsiTypeElement checkType = instanceOf.getCheckType();
                        assert checkType != null;
                        replacer.assertThat(instanceOf.getOperand())
                            .call(
                                negate ? "isNotInstanceOf" : "isInstanceOf",
                                checkType.getType().getCanonicalText() + ".class"
                            );
                    }
                    else if (args[0] instanceof PsiMethodCallExpression methodCall
                        && isCollectionMethodCall(methodCall, "isEmpty")) {

                        replacer.assertThat(methodCall.getMethodExpression().getQualifier())
                            .call(negate ? "isNotEmpty" : "isEmpty");
                    }

                    replacer.replace();
                }

                case ASSERT_EQUALS, ASSERT_ARRAY_EQUALS, ASSERT_NOT_EQUALS, ASSERT_SAME, ASSERT_NOT_SAME -> {
                    if (args.length == 3) {
                        replacer.as(args[2]);
                    }

                    if (ASSERT_EQUALS.equals(methodName)
                        && args[1] instanceof PsiMethodCallExpression methodCall
                        && isCollectionMethodCall(methodCall, "size")) {

                        replacer.assertThat(methodCall.getMethodExpression().getQualifier())
                            .call("hasSize", args[0]);
                    }
                    else {
                        replacer.assertThat(args[1])
                            .call(ASSERTJ_EQUIVALENT.get(methodName), args[0]);
                    }

                    replacer.replace();
                }

                case ASSERT_NULL, ASSERT_NOT_NULL -> {
                    if (args.length == 2) {
                        replacer.as(args[1]);
                    }

                    replacer.assertThat(args[0])
                        .call(ASSERTJ_EQUIVALENT.get(methodName))
                        .replace();
                }

                case ASSERT_THROWS -> {
                    if (args.length == 3) {
                        replacer.as(args[2]);
                    }

                    replacer.assertThat("assertThatThrownBy", args[1])
                        .call("isInstanceOf", args[0])
                        .replace();
                }
            }
        }
    }

    private static class AssertJReplacer {
        private final Project myProject;
        private final PsiMethodCallExpression myExpression;
        private final boolean myStaticImport;
        private final StringBuilder myCode = new StringBuilder();
        private PsiElement myDescription = null;

        private AssertJReplacer(Project project, PsiMethodCallExpression expression, boolean staticImport) {
            myProject = project;
            myExpression = expression;
            myStaticImport = staticImport;
        }

        public boolean isNotEmpty() {
            return !myCode.isEmpty();
        }

        public AssertJReplacer as(PsiElement description) {
            myDescription = description;
            return this;
        }

        @RequiredReadAction
        public AssertJReplacer assertThat(@Nullable PsiElement expression) {
            return assertThat("assertThat", expression);
        }

        @RequiredReadAction
        public AssertJReplacer assertThat(@Nonnull String methodName, @Nullable PsiElement expression) {
            if (expression != null) {
                if (!myStaticImport) {
                    myCode.append(ORG_ASSERTJ_ASSERTIONS).append('.');
                }
                myCode.append(methodName).append('(').append(expression.getText()).append(')');
            }
            return this;
        }

        @RequiredReadAction
        public AssertJReplacer call(@Nonnull String methodName) {
            return call(methodName, (String)null);
        }

        @RequiredReadAction
        public AssertJReplacer call(@Nonnull String methodName, PsiElement expectedExpr) {
            return call(methodName, expectedExpr != null ? expectedExpr.getText() : null);
        }

        @RequiredReadAction
        public AssertJReplacer call(@Nonnull String methodName, String paramExpr) {
            if (isNotEmpty()) {
                if (myDescription != null) {
                    myCode.append(".as(").append(myDescription.getText()).append(')');
                    myDescription = null;
                }
                myCode.append('.').append(methodName).append('(').append(paramExpr == null ? "" : paramExpr).append(')');
            }
            return this;
        }

        void replace() {
            if (myCode.isEmpty()) {
                return;
            }

            PsiExpression newExpression = JavaPsiFacade.getElementFactory(myProject)
                .createExpressionFromText(myCode.toString(), myExpression);

            WriteAction.run(() -> {
                PsiElement replacement = myExpression.replace(newExpression);

                if (myStaticImport) {
                    PsiReferenceExpression methodExpr = null;
                    for (PsiMethodCallExpression methodCall = (PsiMethodCallExpression)replacement; ; ) {
                        methodExpr = methodCall.getMethodExpression();
                        if (methodExpr.getQualifierExpression() instanceof PsiMethodCallExpression subMethodCall) {
                            methodCall = subMethodCall;
                        }
                        else {
                            break;
                        }
                    }
                    JavaPsiFacade facade = JavaPsiFacade.getInstance(myProject);
                    PsiClass assertionsClass = facade.findClass(ORG_ASSERTJ_ASSERTIONS, replacement.getResolveScope());
                    if (assertionsClass != null) {
                        methodExpr.bindToElementViaStaticImport(assertionsClass);
                    }
                }
            });
        }
    }
}
