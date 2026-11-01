package com.tacz.guns.api.event.common;

import cn.sh1rocu.tacz.api.event.BaseEvent;
public interface KubeJSGunEventPoster<E extends BaseEvent> {
    default void postEventToKubeJS(E event) {
    }

    //客户端事件应调用此方法
    default void postClientEventToKubeJS(E event) {
    }

    //服务端事件应调用此方法
    default void postServerEventToKubeJS(E event) {
    }
}
