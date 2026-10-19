package io.xlogistx.payment.paypal.daos;

import org.zoxweb.shared.data.SetNameDescriptionImpl;
import org.zoxweb.shared.util.*;
import org.zoxweb.shared.util.NVConfigEntity.ArrayType;

@SuppressWarnings("serial")
public class PPComboResource extends SetNameDescriptionImpl {

    public enum Params
            implements GetNVConfig {
        SALE(NVConfigManager.createNVConfigEntity("sale", "Financial transactions related to a payment.", "RelatedResources", false, true, PPSale.NVC_PAYPAL_SALE_DAO, ArrayType.NOT_ARRAY)),

        ;

        private final NVConfig cType;

        Params(NVConfig c) {
            cType = c;
        }

        public NVConfig getNVConfig() {
            return cType;
        }
    }

    public static final NVConfigEntity NVC_COMBO_RESOURCE_DAO = new NVConfigEntityPortable(
            "pp_combo_resource_dao",
            null,
            "PPComboResource",
            true,
            false,
            false,
            false,
            PPComboResource.class,
            SUS.extractNVConfigs(Params.values()),
            null,
            false,
            SetNameDescriptionImpl.NVC_NAME_DESCRIPTION_IMPL
    );

    public PPComboResource() {
        super(NVC_COMBO_RESOURCE_DAO);
    }

    public PPSale getSale() {
        return lookupValue(Params.SALE);
    }

    public void setSale(PPSale sale) {
        setValue(Params.SALE, sale);
    }

}