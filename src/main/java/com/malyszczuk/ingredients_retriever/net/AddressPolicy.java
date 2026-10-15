package com.malyszczuk.ingredients_retriever.net;

import java.net.InetAddress;

/** Which addresses the application may connect to when fetching a user-supplied URL. */
@FunctionalInterface
public interface AddressPolicy {

    boolean allows(InetAddress address);

    /** Production policy: ordinary public internet addresses only. */
    static AddressPolicy publicOnly() {
        return PublicAddresses::isPublic;
    }
}
