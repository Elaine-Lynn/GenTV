package com.gentv.tv;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Test;
import static org.junit.Assert.*;

public class HomeContentModelTest {
    private JSONObject valid() throws Exception {
        return new JSONObject().put("protocolMajor",1).put("snapshotId","one").put("template","summary")
                .put("title","杭州天气").put("privacy","public").put("ttlSeconds",30)
                .put("items",new JSONArray().put(new JSONObject().put("id","temperature").put("title","18°")));
    }
    private void rejects(String json) throws Exception {try{HomeContentModel.parse(json);fail("accepted malformed snapshot");}catch(JSONException expected){}}
    @Test public void acceptsOfflineSummaryWithoutBackgroundOrActions() throws Exception {
        HomeContentModel s=HomeContentModel.parse(valid().toString());assertEquals("",s.background);assertEquals(1,s.items.size());assertEquals(-1,s.items.get(0).progress,0);
    }
    @Test public void nullBackgroundClearsInsteadOfInheriting() throws Exception {assertEquals("",HomeContentModel.parse(valid().put("background",JSONObject.NULL).toString()).background);}
    @Test public void acceptsOptionalResourceAndSamePackageActionData() throws Exception {
        JSONObject j=valid().put("background","android.resource://example/123");
        j.getJSONArray("items").getJSONObject(0).put("activity","example/.Main").put("actionId","weather");
        assertEquals("weather",HomeContentModel.parse(j.toString()).items.get(0).actionId);
    }
    @Test public void incompatibleMajorFails() throws Exception {rejects(valid().put("protocolMajor",2).toString());}
    @Test public void numericStringsAreNotIntegers() throws Exception {rejects(valid().put("ttlSeconds","30").toString());rejects(valid().put("protocolMajor","1").toString());}
    @Test public void floatingPointIntegersRejected() throws Exception {rejects(valid().put("ttlSeconds",30.5).toString());}
    @Test public void boundedExpiry() throws Exception {rejects(valid().put("ttlSeconds",0).toString());rejects(valid().put("ttlSeconds",301).toString());}
    @Test public void explicitPrivacyRequired() throws Exception {JSONObject j=valid();j.remove("privacy");rejects(j.toString());rejects(valid().put("privacy","secret").toString());}
    @Test public void noArbitraryTemplates() throws Exception {rejects(valid().put("template","webview").toString());}
    @Test public void duplicateItemIdsRejected() throws Exception {JSONObject j=valid();j.getJSONArray("items").put(new JSONObject().put("id","temperature").put("title","other"));rejects(j.toString());}
    @Test public void duplicateObjectKeysRejectedIncludingEscapedKeys() throws Exception {
        rejects(valid().toString().replace("\"protocolMajor\":1","\"protocolMajor\":1,\"protocolMajor\":2"));
        rejects(valid().toString().replace("\"protocolMajor\":1","\"protocolMajor\":1,\"protocol\\u004dajor\":1"));
    }
    @Test public void tooDeepRejectedBeforeRecursiveParse() throws Exception {String s="0";for(int i=0;i<100;i++)s="["+s+"]";rejects(s);}
    @Test public void oversizeUtf8Rejected() throws Exception {StringBuilder b=new StringBuilder();for(int i=0;i<30000;i++)b.append('中');rejects(b.toString());}
    @Test public void noNetworkOrFilesystemImages() throws Exception {rejects(valid().put("background","https://example.com/image.jpg").toString());rejects(valid().put("background","file:///secret").toString());}
    @Test public void explicitEmptyResponseAccepted() throws Exception {assertTrue(HomeContentModel.parse(valid().put("items",new JSONArray()).toString()).items.isEmpty());}
    @Test public void unknownOptionalFieldsDoNotBreakCompatibility() throws Exception {assertNotNull(HomeContentModel.parse(valid().put("future",true).toString()));}
    @Test public void badProgressRejected() throws Exception {JSONObject j=valid();j.getJSONArray("items").getJSONObject(0).put("progress",1.1);rejects(j.toString());}
    @Test public void actionRequiresComponent() throws Exception {JSONObject j=valid();j.getJSONArray("items").getJSONObject(0).put("actionId","unlock");rejects(j.toString());}
    @Test public void controlsAndBidiSpoofingRejected() throws Exception {rejects(valid().put("title","hello\nworld").toString());rejects(valid().put("title","hello\u202eworld").toString());}
    @Test public void trailingPayloadRejected() throws Exception {rejects(valid().toString()+"{}");}
    @Test public void summaryIsBoundedAndMediaAllowsSix() throws Exception {
        JSONArray a=new JSONArray();for(int i=0;i<6;i++)a.put(new JSONObject().put("id","item"+i).put("title","Item"));
        rejects(valid().put("items",a).toString());assertEquals(6,HomeContentModel.parse(valid().put("template","media_row").put("items",a).toString()).items.size());
        a.put(new JSONObject().put("id","extra").put("title","Extra"));rejects(valid().put("template","media_row").put("items",a).toString());
    }
}
