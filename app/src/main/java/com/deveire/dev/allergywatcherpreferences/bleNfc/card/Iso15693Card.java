package com.deveire.dev.allergywatcherpreferences.bleNfc.card;

import com.deveire.dev.allergywatcherpreferences.bleNfc.DeviceManager;

/**
 * Created by Administrator on 2016/9/21.
 */
public class Iso15693Card extends Card{
    public Iso15693Card(DeviceManager deviceManager) {
        super(deviceManager);
    }

    public Iso15693Card(DeviceManager deviceManager, byte[] uid, byte[] atr) {
        super(deviceManager, uid, atr);
    }
}
