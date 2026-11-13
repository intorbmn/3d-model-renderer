package com.shiroha.mmdskin.ui.selector.port;

import java.util.List;

public interface ModelSelectionGateway {
    List<String> loadAvailableModelNames();

    void refreshModelCatalog();

    /** True when the model comes from a PNG card. */
    default boolean isCardModel(String modelName) {
        return false;
    }

    String getSelectedModel();

    void setSelectedModel(String modelName);

    String getQuickSlotModel(int slot);
}
