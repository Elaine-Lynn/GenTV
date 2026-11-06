package com.gentv.tv;

import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;

public class ChildServiceSettingsTest {
    private static final String TARGET="com.gentv.tv/com.gentv.tv.ChildWatchService";
    @Test public void preservesOthersAndRecognisesBothComponentForms() {
        assertEquals(TARGET,ChildServiceSettings.add("null","com.gentv.tv"));
        assertEquals("other/.Reader:"+TARGET,ChildServiceSettings.add("other/.Reader","com.gentv.tv"));
        String shortened="other/.Reader:com.gentv.tv/.ChildWatchService";
        assertEquals(shortened,ChildServiceSettings.add(shortened,"com.gentv.tv"));
        assertEquals(TARGET,ChildServiceSettings.add(TARGET,"com.gentv.tv"));
    }
    private static String execute(String services,String global,String action) throws Exception {
        String fixture="services="+ChildServiceSettings.quote(services)+"; global="+ChildServiceSettings.quote(global)+"; "
                +"settings() { case \"$1:$3\" in get:enabled_accessibility_services) printf '%s\\n' \"$services\";; "
                +"get:accessibility_enabled) printf '%s\\n' \"$global\";; "
                +"put:enabled_accessibility_services) services=\"$4\";; put:accessibility_enabled) global=\"$4\";; "
                +"delete:enabled_accessibility_services) services=null;; delete:accessibility_enabled) global=null;; esac; }; "
                +action+"; printf 'STATE:%s|%s' \"$services\" \"$global\"";
        Process process=new ProcessBuilder("sh","-c",fixture).redirectErrorStream(true).start();
        String result=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertEquals(0,process.waitFor());return result;
    }
    @Test public void guardedEnableAndExactRollbackPreserveOtherServices() throws Exception {
        String updated=ChildServiceSettings.add("other/.Reader","com.gentv.tv");
        assertTrue(execute("other/.Reader","0",ChildServiceSettings.enable("other/.Reader","0",updated))
                .contains("STATE:"+updated+"|1"));
        assertTrue(execute(updated,"1",ChildServiceSettings.rollback("other/.Reader","0",updated))
                .contains("STATE:other/.Reader|0"));
        assertTrue(execute(TARGET,"1",ChildServiceSettings.rollback("null","null",TARGET)).contains("STATE:null|null"));
    }
    @Test public void interveningEditsPreventBothEnableAndRollback() throws Exception {
        assertEquals("STATE:new/.Service|1",execute("new/.Service","1",ChildServiceSettings.enable("null","0",TARGET)));
        assertEquals("STATE:new/.Service|1",execute("new/.Service","1",ChildServiceSettings.rollback("null","0",TARGET)));
        assertEquals("STATE:"+TARGET+"|0",execute(TARGET,"0",ChildServiceSettings.rollback("null","0",TARGET)));
    }
    @Test public void valuesAreShellQuoted() throws Exception {
        String before="strange'entry; echo UNEXPECTED";
        String updated=ChildServiceSettings.add(before,"com.gentv.tv");
        assertTrue(execute(before,"0",ChildServiceSettings.enable(before,"0",updated)).endsWith("STATE:"+updated+"|1"));
    }
}
