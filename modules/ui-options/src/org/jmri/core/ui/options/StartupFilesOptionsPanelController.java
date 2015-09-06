package org.jmri.core.ui.options;

import apps.PerformFilePanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.NbBundle;

@OptionsPanelController.SubRegistration(
        location = "StartupOptions",
        displayName = "#StartupOption_DisplayName_StartupFiles",
        keywords = "#StartupOption_Keywords_StartupFiles",
        keywordsCategory = "StartupOptions/Files"
)
@NbBundle.Messages({
    "StartupOption_DisplayName_StartupFiles=Files",
    "StartupOption_Keywords_StartupFiles=Files, Startup",
    "StartupFilesOptions.RestartReason=add, remove, or change files loaded at startup"
})
public final class StartupFilesOptionsPanelController extends PreferencesPanelController {

    public StartupFilesOptionsPanelController() {
        super(new PerformFilePanel());
    }

    @Override
    public String getRestartReason() {
        return Bundle.StartupFilesOptions_RestartReason();
    }
}
