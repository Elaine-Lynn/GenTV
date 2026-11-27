package com.gentv.tv;

import org.json.JSONArray;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DesktopFoldersTest {
    private static String join(JSONArray array) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.length(); i++) text.append(i == 0 ? "" : ",").append(array.getString(i));
        return text.toString();
    }

    @Test public void newFolderTakesTheAppsPlace() throws Exception {
        JSONArray order = new JSONArray().put("a").put("b").put("c");
        assertEquals("a,F,b,c", join(DesktopFolders.insertBefore(order, "F", "b")));
    }

    @Test public void unknownAppPutsTheFolderFirst() throws Exception {
        JSONArray order = new JSONArray().put("a").put("b");
        assertEquals("F,a,b", join(DesktopFolders.insertBefore(order, "F", "z")));
    }

    @Test public void anExistingKeyIsMovedNotDuplicated() throws Exception {
        JSONArray order = new JSONArray().put("a").put("b").put("F");
        assertEquals("F,a,b", join(DesktopFolders.insertBefore(order, "F", "a")));
    }
}
