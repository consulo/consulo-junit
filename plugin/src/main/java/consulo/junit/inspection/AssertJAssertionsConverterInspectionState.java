package consulo.junit.inspection;

import consulo.configurable.ConfigurableBuilder;
import consulo.configurable.UnnamedConfigurable;
import consulo.junit.localize.JUnitLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.annotation.Nullable;

/**
 * @author UNV
 * @since 2024-12-24
 */
public class AssertJAssertionsConverterInspectionState implements InspectionToolState<AssertJAssertionsConverterInspectionState> {
    private boolean myStaticImport = true;

    public boolean isStaticImport() {
        return myStaticImport;
    }

    public void setStaticImport(boolean staticImport) {
        myStaticImport = staticImport;
    }

    @Nullable
    @Override
    public UnnamedConfigurable createConfigurable() {
        return ConfigurableBuilder.newBuilder()
            .checkBox(JUnitLocalize.inspectionsMigrateAssertToAssertjStaticImportOption(), this::isStaticImport, this::setStaticImport)
            .buildUnnamed();
    }

    @Nullable
    @Override
    public AssertJAssertionsConverterInspectionState getState() {
        return this;
    }

    @Override
    public void loadState(AssertJAssertionsConverterInspectionState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
