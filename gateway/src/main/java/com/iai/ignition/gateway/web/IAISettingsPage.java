package com.iai.ignition.gateway.web;

import com.iai.ignition.gateway.GatewayHook;
import com.iai.ignition.gateway.records.IAISettings;
import com.inductiveautomation.ignition.gateway.model.IgnitionWebApp;
import com.inductiveautomation.ignition.gateway.web.components.RecordEditForm;
import com.inductiveautomation.ignition.gateway.web.models.LenientResourceModel;
import com.inductiveautomation.ignition.gateway.web.pages.IConfigPage;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.wicket.Application;

/**
 * Settings page for Ignition AI module configuration.
 * Extends RecordEditForm to provide a page where admins can edit module settings.
 */
public class IAISettingsPage extends RecordEditForm {

    public static final Pair<String, String> MENU_LOCATION =
        Pair.of(GatewayHook.CONFIG_CATEGORY.getName(), "ignitionai-settings");

    public IAISettingsPage(final IConfigPage configPage) {
        super(
            configPage,
            null,
            new LenientResourceModel("IgnitionAI.nav.settings.panelTitle"),
            ((IgnitionWebApp) Application.get())
                .getContext()
                .getLocalPersistenceInterface()
                .find(IAISettings.META, 0L)
        );
    }

    @Override
    public Pair<String, String> getMenuLocation() {
        return MENU_LOCATION;
    }
}
