package io.xlogistx.payment.paypal.daos;

import org.zoxweb.shared.data.SetNameDescriptionImpl;
import org.zoxweb.shared.util.*;
import org.zoxweb.shared.util.NVConfigEntity.ArrayType;

import java.util.List;

@SuppressWarnings("serial")
public class PPItemList
        extends SetNameDescriptionImpl {
    public enum Params
            implements GetNVConfig {
        ITEMS(NVConfigManager.createNVConfigEntity("items", "List of items.", "Items", true, true, PPItem.NVC_ITEM_DAO, ArrayType.LIST)),
        SHIPPING_ADDRESS(NVConfigManager.createNVConfigEntity("shipping_address", "Shipping address, if different than the payer address.", "ShippingAddress", false, true, PPAddress.NVC_PAYPAL_ADDRESS_DAO)),

        ;

        private final NVConfig cType;

        Params(NVConfig c) {
            cType = c;
        }

        public NVConfig getNVConfig() {
            return cType;
        }

    }

    public static final NVConfigEntity NVC_PAYPAL_ITEM_LIST_DAO = new NVConfigEntityPortable(
            "pp_item_list_dao",
            null,
            "PPItemList",
            true,
            false,
            false,
            false,
            PPItemList.class,
            SUS.extractNVConfigs(Params.values()),
            null,
            false,
            SetNameDescriptionImpl.NVC_NAME_DESCRIPTION_IMPL
    );

    public PPItemList() {
        super(NVC_PAYPAL_ITEM_LIST_DAO);
    }

    @SuppressWarnings("unchecked")
    public ArrayValues<PPItem> getItems() {
        return (ArrayValues<PPItem>) lookup(Params.ITEMS);
    }

    @SuppressWarnings("unchecked")
    public void setItems(ArrayValues<PPItem> values) {
        ArrayValues<PPItem> content = (ArrayValues<PPItem>) lookup(Params.ITEMS);
        content.add(values.values(), true);
    }

    @SuppressWarnings("unchecked")
    public void setItems(List<PPItem> values) {
        ArrayValues<PPItem> content = (ArrayValues<PPItem>) lookup(Params.ITEMS);
        content.add(values.toArray(new PPItem[0]), true);
    }

    public PPAddress getShippingAddress() {
        return lookupValue(Params.SHIPPING_ADDRESS);
    }

    public void setShippingAddress(PPAddress address) {
        setValue(Params.SHIPPING_ADDRESS, address);
    }

}