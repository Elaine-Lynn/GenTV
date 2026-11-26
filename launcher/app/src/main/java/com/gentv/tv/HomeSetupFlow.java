package com.gentv.tv;

/** Opening a settings screen is not evidence that the system default changed. */
final class HomeSetupFlow {
    enum Route { ROLE, HOME_SETTINGS, ANDROID_PICKER, HUAWEI_PICKER }
    enum Result { DEFAULT, OPENED, UNAVAILABLE }

    interface Platform {
        boolean isDefaultHome();
        boolean open(Route route);
    }

    private final Platform platform;
    private int next;

    HomeSetupFlow(Platform platform, int next) {
        this.platform = platform;
        this.next = Math.max(0, Math.min(Route.values().length, next));
    }

    int nextRoute() { return next; }
    boolean verified() { return platform.isDefaultHome(); }

    Result advance() {
        if (verified()) return Result.DEFAULT;
        Route[] routes = Route.values();
        while (next < routes.length) {
            Route route = routes[next++];
            if (platform.open(route)) return Result.OPENED;
        }
        return Result.UNAVAILABLE;
    }
}
