package com.gentv.tv;

import java.util.*;
import java.util.function.*;

/** The first thirty ordered tiles stay on Home; the rest stay reachable without duplicates. */
final class MoreApps {
    static final int HOME_LIMIT=30;
    static boolean hasOverflow(int count){return count>HOME_LIMIT;}
    static <T> List<T> missing(List<T> catalog,Function<T,String> packageName,Predicate<String> onHome) {
        List<T> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(T app:catalog){String key=packageName.apply(app);if(!onHome.test(key) && seen.add(key))result.add(app);}
        return result;
    }
}
