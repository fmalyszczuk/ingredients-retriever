package com.malyszczuk.ingredients_retriever.support;

import org.testcontainers.DockerClientFactory;

public final class DockerAvailable {

    private static Boolean available;

    private DockerAvailable() {
    }

    /** Used by {@link RequiresDocker}; checked once per test run. */
    public static synchronized boolean isAvailable() {
        if (available == null) {
            try {
                available = DockerClientFactory.instance().isDockerAvailable();
            } catch (Throwable e) {
                available = false;
            }
        }
        return available;
    }
}
