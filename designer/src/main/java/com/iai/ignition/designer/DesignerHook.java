package com.iai.ignition.designer;

import com.iai.ignition.common.InsightChatComponent;
import com.inductiveautomation.ignition.common.licensing.LicenseState;
import com.inductiveautomation.ignition.common.util.LoggerEx;
import com.inductiveautomation.ignition.designer.model.AbstractDesignerModuleHook;
import com.inductiveautomation.ignition.designer.model.DesignerContext;
import com.inductiveautomation.perspective.designer.DesignerComponentRegistry;
import com.inductiveautomation.perspective.designer.api.PerspectiveDesignerInterface;

/**
 * Designer hook for the Ignition AI module.
 */
public class DesignerHook extends AbstractDesignerModuleHook {

    private static final LoggerEx logger = LoggerEx.newBuilder().build("com.iai.ignition.designer.DesignerHook");

    private DesignerContext context;
    private DesignerComponentRegistry registry;

    public DesignerHook() {
        logger.info("Registering Ignition AI components in Designer!");
    }

    @Override
    public void startup(DesignerContext context, LicenseState activationState) {
        this.context = context;
        init();
    }

    private void init() {
        logger.debug("Initializing registry entrants...");

        PerspectiveDesignerInterface pdi = PerspectiveDesignerInterface.get(context);
        registry = pdi.getDesignerComponentRegistry();

        // Register components to get them on the palette
        registry.registerComponent(InsightChatComponent.DESCRIPTOR);
    }

    @Override
    public void shutdown() {
        removeComponents();
    }

    private void removeComponents() {
        if (registry != null) {
            registry.removeComponent(InsightChatComponent.COMPONENT_ID);
        }
    }
}
