package com.gentv.tv;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeSetupFlowTest {
    private static class Tv implements HomeSetupFlow.Platform {
        boolean home;
        final EnumSet<HomeSetupFlow.Route> available = EnumSet.noneOf(HomeSetupFlow.Route.class);
        final List<HomeSetupFlow.Route> attempts = new ArrayList<>();
        public boolean isDefaultHome() { return home; }
        public boolean open(HomeSetupFlow.Route route) { attempts.add(route); return available.contains(route); }
    }

    @Test public void alreadyDefaultDoesNotOpenAnotherScreen() {
        Tv tv = new Tv(); tv.home = true;
        assertEquals(HomeSetupFlow.Result.DEFAULT, new HomeSetupFlow(tv, 0).advance());
        assertTrue(tv.attempts.isEmpty());
    }

    @Test public void oldTvSkipsUnavailableRoleAndOpensHomeSettings() {
        Tv tv = new Tv(); tv.available.add(HomeSetupFlow.Route.HOME_SETTINGS);
        HomeSetupFlow flow = new HomeSetupFlow(tv, 0);
        assertEquals(HomeSetupFlow.Result.OPENED, flow.advance());
        assertEquals(2, tv.attempts.size());
        assertFalse(flow.verified());
    }

    @Test public void successfulLaunchOrCancelledChoiceIsNotReportedAsDefault() {
        Tv tv = new Tv(); tv.available.add(HomeSetupFlow.Route.ROLE);
        HomeSetupFlow flow = new HomeSetupFlow(tv, 0);
        assertEquals(HomeSetupFlow.Result.OPENED, flow.advance());
        assertFalse(flow.verified());
        tv.home = true;
        assertTrue(flow.verified());
        tv.home = false;
        assertFalse(flow.verified());
    }

    @Test public void retryUsesNextRouteWithoutReopeningRoleRequest() {
        Tv tv = new Tv(); tv.available.addAll(EnumSet.allOf(HomeSetupFlow.Route.class));
        HomeSetupFlow flow = new HomeSetupFlow(tv, 0);
        flow.advance();
        assertFalse(flow.verified());
        assertEquals(1, tv.attempts.size());
        flow.advance();
        assertEquals(HomeSetupFlow.Route.HOME_SETTINGS, tv.attempts.get(1));
    }

    @Test public void recreatedControllerContinuesAfterAlreadyTriedRoutes() {
        Tv tv = new Tv(); tv.available.add(HomeSetupFlow.Route.ANDROID_PICKER);
        HomeSetupFlow before = new HomeSetupFlow(tv, 0); before.advance();
        HomeSetupFlow after = new HomeSetupFlow(tv, before.nextRoute());
        tv.available.add(HomeSetupFlow.Route.HUAWEI_PICKER);
        assertEquals(HomeSetupFlow.Result.OPENED, after.advance());
        assertEquals(HomeSetupFlow.Route.HUAWEI_PICKER, tv.attempts.get(tv.attempts.size() - 1));
    }

    @Test public void missingOrProtectedPickersExhaustOnceWithoutLooping() {
        Tv tv = new Tv(); HomeSetupFlow flow = new HomeSetupFlow(tv, 0);
        assertEquals(HomeSetupFlow.Result.UNAVAILABLE, flow.advance());
        assertEquals(4, tv.attempts.size());
        assertEquals(HomeSetupFlow.Result.UNAVAILABLE, flow.advance());
        assertEquals(4, tv.attempts.size());
    }
}
