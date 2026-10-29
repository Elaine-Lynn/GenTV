package com.gentv.mobile

import android.graphics.Bitmap
import android.util.Base64
import com.gentv.mobile.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class DesktopExtrasTest {
    private val app="com.example.app/.Main"
    private fun profile()=JSONObject().put("schema",2).put("order",JSONArray().put(app).put("com.missing.app/.Main"))
        .put("hidden",JSONArray().put(app)).put("names",JSONObject()).put("effects","auto").put("folders",JSONObject()).put("images",JSONObject())
    @Test fun foldersMoveBetweenFoldersAndHomeWithoutLosingOrder() {
        val p=profile();val first=DesktopFolders.create(p,"Video");val second=DesktopFolders.create(p,"Games")
        DesktopFolders.assign(p,app,first);parseDesktopProfile(p.toString())
        assertEquals(app,p.getJSONObject("folders").getJSONObject(first).getJSONArray("apps").getString(0))
        assertEquals(0,p.getJSONArray("hidden").length())
        DesktopFolders.assign(p,app,second)
        assertEquals(0,p.getJSONObject("folders").getJSONObject(first).getJSONArray("apps").length())
        DesktopFolders.assign(p,app,null)
        assertEquals(0,p.getJSONObject("folders").getJSONObject(second).getJSONArray("apps").length())
        assertEquals("com.missing.app/.Main",p.getJSONArray("order").getString(1))
    }
    @Test fun dissolvePreservesAppsAndImagesAndRemovesFolderMetadata() {
        val p=profile();val id=DesktopFolders.create(p,"Video");DesktopFolders.assign(p,app,id)
        p.getJSONObject("names").put(id,"Custom");DesktopFolders.dissolve(p,id)
        assertEquals(0,p.getJSONObject("folders").length());assertFalse(p.getJSONObject("names").has(id))
        assertEquals(2,p.getJSONArray("order").length());assertEquals(app,p.getJSONArray("order").getString(0))
    }
    @Test fun duplicateNestedAndMalformedFoldersAreRejected() {
        val p=profile();val a=DesktopFolders.create(p,"A");val b=DesktopFolders.create(p,"B");DesktopFolders.assign(p,app,a)
        p.getJSONObject("folders").getJSONObject(b).put("apps",JSONArray().put(app))
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
        p.getJSONObject("folders").getJSONObject(b).put("apps",JSONArray().put(a))
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
        p.getJSONObject("folders").getJSONObject(b).put("apps",JSONArray()).put("name","Bad\nname")
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
    }
    private fun jpeg(width:Int=320,height:Int=180):ByteArray {
        val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff3470a0.toInt())
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG,80,it);bitmap.recycle() }.toByteArray()
    }
    @Test fun imageIsResizedAndRoundTripsInExportedProfile() {
        val encoded=DesktopImages.encode(jpeg(1280,720).inputStream());val p=profile().apply {getJSONObject("images").put(app,encoded)}
        assertTrue(Base64.decode(encoded,Base64.NO_WRAP).size<=DesktopImages.MAX_BYTES)
        assertEquals(encoded,parseDesktopProfile(p.toString()).getJSONObject("images").getString(app))
    }
    @Test fun badImagesOversizeImagesAndCapacityOverflowAreRejected() {
        val p=profile();p.getJSONObject("images").put(app,"bm90LWEtanBlZw==")
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
        p.getJSONObject("images").put(app,Base64.encodeToString(jpeg(321,180),Base64.NO_WRAP))
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
        val encoded=DesktopImages.encode(jpeg().inputStream())
        for(i in 0..12)p.getJSONObject("images").put("com.example.app/.Entry$i",encoded)
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
    }
    @Test fun combinedImageBudgetIsEnforced() {
        val bytes=jpeg().copyOf(10000);bytes[bytes.size-2]=0xff.toByte();bytes[bytes.size-1]=0xd9.toByte()
        val encoded=Base64.encodeToString(bytes,Base64.NO_WRAP);val p=profile()
        for(i in 0..2)p.getJSONObject("images").put("com.example.app/.Entry$i",encoded)
        assertThrows(IllegalArgumentException::class.java) { parseDesktopProfile(p.toString()) }
    }
    @Test fun missingAppsRemainInFoldersAndEmptyFoldersAreValid() {
        val p=profile();val id=DesktopFolders.create(p,"Empty");parseDesktopProfile(p.toString())
        DesktopFolders.assign(p,"com.missing.app/.Main",id);parseDesktopProfile(p.toString())
        assertEquals("com.missing.app/.Main",p.getJSONObject("folders").getJSONObject(id).getJSONArray("apps").getString(0))
    }
}
