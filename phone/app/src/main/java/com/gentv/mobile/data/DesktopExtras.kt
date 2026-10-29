package com.gentv.mobile.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.graphics.scale
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Pure draft operations: no connection or TV write until the owner saves. */
object DesktopFolders {
    fun isFolder(id: String) = id.matches(Regex("com\\.gentv\\.tv/Folder\\.[a-f0-9]{32}"))
    fun create(profile: JSONObject, name: String): String {
        require(name.isNotBlank() && name.length <= 60)
        val id = "com.gentv.tv/Folder." + UUID.randomUUID().toString().replace("-", "")
        val folders = profile.optJSONObject("folders") ?: JSONObject().also { profile.put("folders", it) }
        require(folders.length() < 30) { "Up to 30 folders" }
        folders.put(id, JSONObject().put("name", name.trim()).put("apps", JSONArray()))
        profile.getJSONArray("order").put(id)
        return id
    }
    fun assign(profile: JSONObject, app: String, target: String?) {
        require(!app.startsWith("com.gentv.tv/")) { "Only apps can go into folders" }
        val folders = profile.getJSONObject("folders")
        require(target == null || folders.has(target))
        for (key in folders.keys()) {
            val folder = folders.getJSONObject(key)
            val next = folder.getJSONArray("apps").values().filter { it != app }.toMutableList()
            if (key == target) { require(next.size < 100); next.add(app) }
            folder.put("apps", JSONArray(next))
        }
        profile.put("hidden", JSONArray(profile.getJSONArray("hidden").values().filter { it != app }))
    }
    fun dissolve(profile: JSONObject, id: String) {
        profile.getJSONObject("folders").remove(id)
        profile.getJSONObject("names").remove(id)
        for (field in listOf("order", "hidden")) profile.put(field, JSONArray(profile.getJSONArray(field).values().filter { it != id }))
    }
    private fun JSONArray.values() = (0 until length()).map { getString(it) }
}

/** Small offline JPEGs fit in the existing bounded atomic profile and its undo snapshot. */
object DesktopImages {
    const val MAX_BYTES = 12 * 1024
    const val TOTAL_BYTES = 24 * 1024
    fun encode(input: InputStream): String {
        val source = input.use { stream ->
            val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) { val count=stream.read(buffer);if(count<0)break;require(out.size()+count<=8*1024*1024) { "Choose an image smaller than 8 MiB" };out.write(buffer,0,count) };out.toByteArray()
        }
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(source,0,source.size,bounds)
        require(bounds.outWidth in 1..20000 && bounds.outHeight in 1..20000) { "Cannot read this image" }
        val options=BitmapFactory.Options().apply { inSampleSize=1 }
        while(bounds.outWidth/options.inSampleSize>640 || bounds.outHeight/options.inSampleSize>360) options.inSampleSize*=2
        val decoded=BitmapFactory.decodeByteArray(source,0,source.size,options) ?: error("Cannot read this image")
        val scale=min(1f,min(320f/decoded.width,180f/decoded.height))
        val bitmap=decoded.scale(max(1,(decoded.width*scale).toInt()),max(1,(decoded.height*scale).toInt()),true)
        if(bitmap!==decoded)decoded.recycle()
        try {
            for(quality in listOf(85,70,55,40,25)) {
                val out=ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out)
                if(out.size()<=MAX_BYTES) return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP)
            }
            error("Image is too detailed. Choose a simpler image.")
        } finally { bitmap.recycle() }
    }
    fun validate(profile: JSONObject) {
        val folders=profile.optJSONObject("folders")
        require(!profile.has("folders") || folders!=null) { "Invalid folders" }
        val members=mutableSetOf<String>()
        if(folders!=null) {
            require(folders.length()<=30)
            for(id in folders.keys()) {
                require(DesktopFolders.isFolder(id))
                val folder=folders.getJSONObject(id);require(folder.length()==2)
                val name=folder.get("name");require(name is String && name.isNotBlank() && name.length<=60 && name.none { it.isISOControl() || Character.getType(it)==Character.FORMAT.toInt() })
                val apps=folder.getJSONArray("apps");require(apps.length()<=100)
                for(i in 0 until apps.length()) { val app=apps.get(i);require(app is String && app.length<=256 && app.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")) && !app.startsWith("com.gentv.tv/") && members.add(app)) { "Duplicate, nested or invalid folder app" } }
            }
        }
        val images=profile.optJSONObject("images");require(!profile.has("images") || images!=null)
        var total=0
        if(images!=null) {
            require(images.length()<=12)
            for(id in images.keys()) {
                require(id.length<=256 && id.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+")) && !id.startsWith("com.gentv.tv/"))
                val raw=images.get(id);require(raw is String && raw.length<=((MAX_BYTES+2)/3)*4 && raw.matches(Regex("[A-Za-z0-9+/]+={0,2}")))
                val bytes=Base64.decode(raw,Base64.NO_WRAP);total+=bytes.size
                require(bytes.size in 4..MAX_BYTES && total<=TOTAL_BYTES) { "Images exceed 24 KiB total" }
                require(bytes[0]==0xff.toByte() && bytes[1]==0xd8.toByte() && bytes[bytes.size-2]==0xff.toByte() && bytes.last()==0xd9.toByte()) { "JPEG required" }
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                require(bounds.outWidth in 1..320 && bounds.outHeight in 1..180) { "Image exceeds 320 x 180" }
                val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size) ?: error("Cannot read this image");bitmap.recycle()
            }
        }
    }
}
