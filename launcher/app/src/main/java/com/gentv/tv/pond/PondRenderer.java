package com.gentv.tv.pond;

import android.content.Context;
import android.graphics.*;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;
import java.io.InputStream;
import java.nio.*;
import java.util.HashMap;
import android.util.SparseArray;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Native GLES2 pond renderer adapted from the local 一池 desktop, version 1.10.3. */
public final class PondRenderer implements GLSurfaceView.Renderer {
    public final PondWorld world=new PondWorld(68431,System.nanoTime());
    public final FishingSession fishing=new FishingSession(System.nanoTime());
    java.util.function.Consumer<FishingSession.Catch> catchListener;
    private FishingSession.Stage previousFishingStage=FishingSession.Stage.DOZING;
    private final Context context;
    private int backgroundProgram,fishProgram,shadowProgram,colorProgram,spriteProgram,rodProgram,lineProgram,floatProgram,waterMaskTexture,bedTexture,causticTexture,koiTexture,turtleTexture,catDozingTexture,catAwakeTexture;
    private final FloatBuffer spriteMesh=buffer(new float[16]);
    private final float[] spriteVertices=new float[16],rodVertices=new float[33*2*4];
    private final FloatBuffer rodMesh=buffer(new float[33*2*4]);
    private final int[] waterPrograms=new int[3];
    private final SparseArray<Handles> handles=new SparseArray<>();
    private final int[] selectedRipples=new int[4];
    private static final class Handles {
        final int position,uv; final HashMap<String,Integer> uniforms=new HashMap<>();
        Handles(int p){position=GLES20.glGetAttribLocation(p,"aPosition");uv=GLES20.glGetAttribLocation(p,"aUV");for(String n:new String[]{"uTexture","uWaterMask","uCaustics","uCausticShift","uCausticGlow","uShoreMask","uTime","uRipples[0]","uRippleCount","uOffset","uAlpha","uVariant","uWaterline","uRodAA","uSize","uColor","uPoint"})uniforms.put(n,GLES20.glGetUniformLocation(p,n));}
    }
    private FloatBuffer quad,mesh,primitives;
    private final ShortBuffer fishIndices;
    private final int fishIndexCount;
    private final float[] primitiveData=new float[100*2],rippleData=new float[4*4];
    private long lastNs;
    private float pixelScale=1;
    // Mirror the composed pond in clip space. UVs, shoreline masks and simulation
    // coordinates stay in the same space, so sprites and water effects agree.
    private static final String POND_PROJECTION =
            "vec4 pondPosition(vec2 p){return vec4(-p.x,p.y,0.,1.);}";
    private static final String VERTEX=POND_PROJECTION+"attribute vec2 aPosition;attribute vec2 aUV;varying vec2 vUV;void main(){gl_Position=pondPosition(aPosition);vUV=aUV;}";
    private static final String FISH_VERTEX=POND_PROJECTION+"attribute vec2 aPosition;attribute vec2 aUV;varying vec2 vUV;varying vec2 vScreen;uniform vec2 uOffset;void main(){vec2 p=aPosition+uOffset;gl_Position=pondPosition(p);vUV=aUV;vScreen=vec2(p.x*.5+.5,.5-p.y*.5);}";
    private static final String SHORE="uniform sampler2D uWaterMask;float waterCover(vec2 uv){return texture2D(uWaterMask,uv).r;}";
    private static String backgroundShader(int slots){
        String ripple=slots==0?"":
            "for(int i=0;i<"+slots+";i++){if(i>=uRippleCount)break;vec4 r=uRipples[i];vec2 delta=(uv-r.xy)*vec2(1.77778,1.);float d=length(delta);float dr=d-r.z*.048;if(abs(dr)>.065)continue;float envelope=exp(-abs(dr)*110.)*r.w;flow+=delta/max(d,.0001)*sin(dr*370.)*envelope*.0018;}";
        return "precision mediump float;varying vec2 vUV;uniform sampler2D uTexture;uniform sampler2D uCaustics;uniform vec4 uCausticShift;uniform float uCausticGlow;uniform float uTime;"+
            (slots==0?"":"uniform vec4 uRipples["+slots+"];uniform int uRippleCount;")+
            SHORE+"void main(){vec2 uv=vUV;float water=waterCover(uv);vec2 p=uv*vec2(1.77778,1.);vec2 flow=vec2(sin(p.y*31.+uTime*.23)+sin(p.x*23.+p.y*17.-uTime*.19),cos(p.x*29.-uTime*.22)+cos(p.y*19.+uTime*.16))*.00065;"+ripple+
            "vec3 color=texture2D(uTexture,clamp(uv+flow*water,.001,.999)).rgb;vec2 cuv=p*1.5+flow*8.;float a=texture2D(uCaustics,cuv+uCausticShift.xy).r;float b=texture2D(uCaustics,cuv*.93+uCausticShift.zw+vec2(.37,.59)).r;float glow=(a*.07+a*b*.16)*water*uCausticGlow;color+=vec3(.93,.98,1.)*glow;gl_FragColor=vec4(color,1.);}";
    }
    private static final String SHADOW=
        "precision mediump float;varying vec2 vUV;varying vec2 vScreen;uniform sampler2D uTexture;uniform float uAlpha;"+
        SHORE+"void main(){float a=texture2D(uTexture,vUV).a;if(a<.015)discard;gl_FragColor=vec4(.015,.065,.057,a*uAlpha*waterCover(vScreen));}";
    private static final String FISH=
        "precision mediump float;varying vec2 vUV;varying vec2 vScreen;uniform sampler2D uTexture;uniform float uAlpha;uniform float uVariant;uniform float uShoreMask;uniform float uTime;"+
        SHORE+"void main(){vec4 tex=texture2D(uTexture,vUV);if(tex.a<.015)discard;float cover=mix(1.,waterCover(vScreen),uShoreMask);vec3 c=tex.rgb/max(tex.a,.001);float lum=dot(c,vec3(.3,.59,.11));float red=smoothstep(.04,.25,c.r-max(c.g,c.b));"+
        "if(uVariant>0.5&&uVariant<1.5)c=mix(c,vec3(c.r*.94,c.r*.57,c.r*.2),red*.9);"+
        "if(uVariant>1.5&&uVariant<2.5)c=vec3(.91,.70,.25)*(.28+lum*.73);"+
        "if(uVariant>2.5&&uVariant<3.5)c=mix(c,vec3(.12,.16,.14)*(.65+c.r*.5),red);"+
        "if(uVariant>3.5&&uVariant<4.5)c=mix(vec3(.11,.16,.15)+c*.12,vec3(.77,.53,.23),red*.7);"+
        "if(uVariant>4.5){float ink=smoothstep(.46,.64,sin(vUV.x*33.+sin(vUV.y*24.)*2.)*sin(vUV.y*27.+vUV.x*13.));c=mix(c,vec3(.13,.17,.16),ink*.85);}"+
        "c=mix(c,vec3(.35,.51,.52),.04+(1.-uAlpha)*.20);c+=vec3(.014,.014,.014)*sin(vUV.x*27.+uTime*.3);gl_FragColor=vec4(c,tex.a*uAlpha*cover);}";
    private static final String SPRITE="precision mediump float;varying vec2 vUV;uniform sampler2D uTexture;uniform float uAlpha;void main(){vec4 t=texture2D(uTexture,vUV);if(t.a<.015)discard;gl_FragColor=vec4(t.rgb/max(t.a,.001),t.a*uAlpha);}";
    private static final String ROD="precision mediump float;varying vec2 vUV;uniform float uAlpha;uniform float uRodAA;void main(){float u=vUV.x,v=vUV.y;float halfWidth=u<.16?2.05:1.7*(1.-u)+.27;float distance=abs(v*2.-1.)*(halfWidth+uRodAA);float coverage=clamp((halfWidth-distance)/uRodAA+.5,0.,1.);coverage*=1.-smoothstep(.993,1.,u);float coreV=clamp((v-.5)*(halfWidth+uRodAA)/halfWidth+.5,0.,1.);float cylindrical=.57+.43*sin(coreV*3.14159);vec3 bamboo=vec3(.70,.55,.31);float node=1.-smoothstep(.018,.05,abs(fract(u*8.)-.1));bamboo*=1.-node*.29;bamboo+=sin(u*105.+coreV*5.)*.012;if(u<.16)bamboo=vec3(.30,.22,.13)*(1.+.045*sin(u*140.));gl_FragColor=vec4(bamboo*cylindrical,uAlpha*coverage);}";
    private static final String FISHING_LINE="precision mediump float;varying vec2 vUV;void main(){float coverage=max(0.,1.-abs(vUV.y*2.-1.));gl_FragColor=vec4(.88,.90,.80,coverage*.42);}";
    private static final String FLOAT="precision mediump float;varying vec2 vUV;varying vec2 vScreen;uniform float uAlpha;uniform float uWaterline;void main(){if(vScreen.y>uWaterline)discard;float v=vUV.y;float w=v<.3?.16:v<.76?.22+.68*sin((v-.3)/.46*3.14159):.11;float x=(vUV.x-.5)*2.;if(abs(x)>w)discard;vec3 c=v<.12?vec3(.91,.39,.20):v<.22?vec3(.96,.92,.70):v<.3?vec3(.15,.20,.16):v<.53?vec3(.84,.28,.15):v<.76?vec3(.95,.86,.61):vec3(.25,.24,.13);c*=.62+.38*cos(x/w*1.5708);gl_FragColor=vec4(c,uAlpha);}";
    private static final String COLOR_VERTEX=POND_PROJECTION+"attribute vec2 aPosition;uniform float uSize;void main(){gl_Position=pondPosition(aPosition);gl_PointSize=uSize;}";
    private static final String COLOR_FRAGMENT="precision mediump float;uniform vec4 uColor;uniform float uPoint;void main(){float alpha=uColor.a;if(uPoint>.5){float d=length(gl_PointCoord-.5);alpha*=1.-smoothstep(.3,.5,d);}gl_FragColor=vec4(uColor.rgb,alpha);}";
    public PondRenderer(Context c){context=c.getApplicationContext();quad=buffer(new float[]{-1,1,0,0,-1,-1,0,1,1,1,1,0,1,-1,1,1});mesh=buffer(new float[(KoiPose.SEGMENTS+1)*KoiPose.ROWS*4]);primitives=buffer(new float[primitiveData.length]);short[] indices=KoiPose.indices();fishIndexCount=indices.length;fishIndices=ByteBuffer.allocateDirect(indices.length*2).order(ByteOrder.nativeOrder()).asShortBuffer();fishIndices.put(indices).position(0);}
    private static FloatBuffer buffer(float[] f){FloatBuffer b=ByteBuffer.allocateDirect(f.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(f).position(0);return b;}
    @Override public void onSurfaceCreated(GL10 gl,EGLConfig config){
        handles.clear();waterPrograms[0]=program(VERTEX,backgroundShader(0));waterPrograms[1]=program(VERTEX,backgroundShader(2));waterPrograms[2]=program(VERTEX,backgroundShader(4));
        fishProgram=program(FISH_VERTEX,FISH);shadowProgram=program(FISH_VERTEX,SHADOW);colorProgram=program(COLOR_VERTEX,COLOR_FRAGMENT);spriteProgram=program(VERTEX,SPRITE);rodProgram=program(VERTEX,ROD);lineProgram=program(VERTEX,FISHING_LINE);floatProgram=program(FISH_VERTEX,FLOAT);
        for(int p:new int[]{waterPrograms[0],waterPrograms[1],waterPrograms[2],fishProgram,shadowProgram,colorProgram,spriteProgram,rodProgram,lineProgram,floatProgram})handles.put(p,new Handles(p));
        waterMaskTexture=createWaterMask();
        bedTexture=texture("pond.webp",false);causticTexture=texture("caustics.webp",false);koiTexture=texture("koi.png",true);turtleTexture=texture("turtle.png",false);catDozingTexture=texture("fishing-cat-dozing.png",false);catAwakeTexture=texture("fishing-cat-awake.png",false);
        GLES20.glClearColor(.08f,.22f,.21f,1);GLES20.glEnable(GLES20.GL_BLEND);GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);lastNs=0;
    }
    @Override public void onSurfaceChanged(GL10 gl,int width,int height){GLES20.glViewport(0,0,width,height);pixelScale=width/1600f;}
    void resetClock() { lastNs = 0; }
    @Override public void onDrawFrame(GL10 gl){
        long start=System.nanoTime();float dt=lastNs==0?0:(start-lastNs)/1_000_000_000f;lastNs=start;world.update(dt);fishing.update(dt,world);
        if(previousFishingStage!=fishing.stage){previousFishingStage=fishing.stage;if(fishing.stage==FishingSession.Stage.REELING)world.disturb(fishing.bobberX,fishing.bobberY);if(fishing.stage==FishingSession.Stage.RESULT){if(catchListener!=null && fishing.result!=null)catchListener.accept(fishing.result);fishing.finish();}}
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        int count=selectRipples();backgroundProgram=waterPrograms[count==0?0:count<=2?1:2];
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glUseProgram(backgroundProgram);bindTexture(bedTexture,backgroundProgram);bindWaterMask(backgroundProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,causticTexture);GLES20.glUniform1i(loc(backgroundProgram,"uCaustics"),1);
        float t=world.time;
        GLES20.glUniform4f(loc(backgroundProgram,"uCausticShift"),(float)Math.sin(t*.08)*.25f,(float)Math.cos(t*.12)*.21f,(float)Math.sin(t*.12+1.2)*.18f,(float)Math.cos(t*.04+.8)*.25f);
        GLES20.glUniform1f(loc(backgroundProgram,"uCausticGlow"),.85f+.15f*(float)Math.sin(t*.12));
        GLES20.glUniform1f(loc(backgroundProgram,"uTime"),world.time);
        if(count>0){GLES20.glUniform1i(loc(backgroundProgram,"uRippleCount"),count);GLES20.glUniform4fv(loc(backgroundProgram,"uRipples[0]"),count,rippleData,0);}
        attributes(backgroundProgram,quad);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glUseProgram(shadowProgram);bindTexture(koiTexture,shadowProgram);bindWaterMask(shadowProgram);
        for(PondWorld.Fish f:world.fish)drawFish(f,true);
        drawTurtle(true);
        GLES20.glUseProgram(fishProgram);bindTexture(koiTexture,fishProgram);bindWaterMask(fishProgram);GLES20.glUniform1f(loc(fishProgram,"uTime"),world.time);
        for(int band=3;band>=0;band--){
            for(PondWorld.Fish f:world.fish)if(Math.min(3,(int)(f.depth*4))==band)drawFish(f,false);
            if(band==2){drawTurtle(false);bindTexture(koiTexture,fishProgram);}
        }
        drawDetails();drawFisherman();
    }
    private void sprite(int texture,int p,float cx,float cy,float width,float height,float angle){
        float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);int k=0;
        for(int i=0;i<4;i++){float u=i<2?0:1,v=i%2;float x=(u-.5f)*width,y=(v-.5f)*height;spriteVertices[k++]=(cx+x*cs-y*sn)/800-1;spriteVertices[k++]=1-(cy+x*sn+y*cs)/450;spriteVertices[k++]=u;spriteVertices[k++]=v;}
        spriteMesh.position(0);spriteMesh.put(spriteVertices).position(0);GLES20.glUseProgram(p);bindTexture(texture,p);GLES20.glUniform1f(loc(p,"uAlpha"),1);if(p==fishProgram){GLES20.glUniform2f(loc(p,"uOffset"),0,0);GLES20.glUniform1f(loc(p,"uShoreMask"),0);}attributes(p,spriteMesh);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
    }
    private void sceneLine(float x1,float y1,float x2,float y2,float sag,int count){
        // A feathered strip keeps the subpixel fishing line stable without MSAA.
        float dx=x2-x1,dy=y2-y1,half=1.2f/Math.max(.5f,pixelScale);int k=0;
        for(int i=0;i<count;i++){float t=i/(float)(count-1),x=x1+dx*t,y=y1+dy*t+(float)Math.sin(t*Math.PI)*sag,tangentY=dy+(float)Math.cos(t*Math.PI)*(float)Math.PI*sag,inv=1/(float)Math.hypot(dx,tangentY);float nx=-tangentY*inv,ny=dx*inv;
            for(int side=0;side<2;side++){float sign=side==0?-1:1;rodVertices[k++]=(x+nx*half*sign)/800-1;rodVertices[k++]=1-(y+ny*half*sign)/450;rodVertices[k++]=t;rodVertices[k++]=side;}
        }
        rodMesh.position(0);rodMesh.put(rodVertices,0,k).position(0);GLES20.glUseProgram(lineProgram);attributes(lineProgram,rodMesh);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,count*2);GLES20.glUseProgram(colorProgram);
    }
    private static final float CAT_X=180,CAT_Y=605;
    private void drawFisherman(){
        FishingSession.Stage stage=fishing.stage;float age=fishing.age,progress=Math.min(1,age/fishing.duration());
        float nod=stage==FishingSession.Stage.DOZING?.008f+.005f*(float)Math.sin(world.time*1.4f):stage==FishingSession.Stage.WAKING?.025f*(1-progress):0;
        // Hand landmark in the sprite's local coordinates. Apply the same body
        // transform so breathing/dozing cannot separate the hand and the grip.
        float pawX=stage==FishingSession.Stage.DOZING?47:48,pawY=stage==FishingSession.Stage.DOZING?-9:-12;
        float handX=CAT_X+pawX*(float)Math.cos(nod)-pawY*(float)Math.sin(nod);
        float handY=CAT_Y+pawX*(float)Math.sin(nod)+pawY*(float)Math.cos(nod);
        float angle=-.45f;
        if(stage==FishingSession.Stage.WAKING)angle=-.45f-progress*1.4f;
        else if(stage==FishingSession.Stage.CASTING){float t=progress*progress*(3-2*progress);angle=-1.85f+t*1.4f;}
        else if(stage==FishingSession.Stage.WAITING)angle=-.45f+(float)Math.sin(age*1.2f)*.012f;
        else if(stage==FishingSession.Stage.REELING)angle=-.45f-progress*.55f;
        else if(stage==FishingSession.Stage.LANDED||stage==FishingSession.Stage.RESULT)angle=-1;
        float bend=stage==FishingSession.Stage.CASTING?4-18*(float)Math.sin(progress*Math.PI*2)*(1-progress):stage==FishingSession.Stage.REELING?8+14*(float)Math.sin(progress*Math.PI):5;
        // The butt extends behind the palm; draw the fisherman over it so the
        // fingers occlude the handle rather than the pole floating beside them.
        float hx=handX-12*(float)Math.cos(angle),hy=handY-12*(float)Math.sin(angle);
        float rodLength=225,dx=(float)Math.cos(angle)*rodLength,dy=(float)Math.sin(angle)*rodLength;
        float tx=hx+dx,ty=hy+dy+bend,rodAA=1.1f/Math.max(.5f,pixelScale);int rk=0;
        for(int i=0;i<=32;i++){float u=i/32f,x=hx+dx*u,y=hy+dy*u+bend*u*u;float tangentY=dy+2*bend*u,inv=1/(float)Math.hypot(dx,tangentY);float nx=-tangentY*inv,ny=dx*inv;float half=(u<.16f?2.05f:1.7f*(1-u)+.27f)+rodAA;for(int side=0;side<2;side++){float sign=side==0?-1:1;rodVertices[rk++]=(x+nx*half*sign)/800-1;rodVertices[rk++]=1-(y+ny*half*sign)/450;rodVertices[rk++]=u;rodVertices[rk++]=side;}}
        rodMesh.position(0);rodMesh.put(rodVertices).position(0);GLES20.glUseProgram(rodProgram);GLES20.glUniform1f(loc(rodProgram,"uAlpha"),1);GLES20.glUniform1f(loc(rodProgram,"uRodAA"),rodAA);attributes(rodProgram,rodMesh);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,66);
        sprite(stage==FishingSession.Stage.DOZING?catDozingTexture:catAwakeTexture,spriteProgram,CAT_X,CAT_Y,142,182,nod);
        GLES20.glUseProgram(colorProgram);GLES20.glUniform1f(loc(colorProgram,"uPoint"),0);
        float fx=fishing.bobberX,fy=fishing.bobberY;
        if(stage==FishingSession.Stage.CASTING){fx=tx+(fx-tx)*progress;fy=ty+(fy-ty)*progress-(float)Math.sin(progress*Math.PI)*115;}
        if(stage==FishingSession.Stage.REELING){fx+=(232-fx)*progress;fy+=(CAT_Y-70-fy)*progress;fy-=(float)Math.sin(progress*Math.PI)*115;}
        else if(stage==FishingSession.Stage.LANDED||stage==FishingSession.Stage.RESULT){fx=232;fy=CAT_Y-70+(float)Math.sin(world.time*6)*3;}
        if(stage!=FishingSession.Stage.WAKING){
            float bob=(float)Math.sin(world.time*2.3f)*1.2f;
            float bite=stage==FishingSession.Stage.REELING?Math.max(0,1-age/.28f):0;
            float sink=bite*18+(float)Math.sin(bite*24)*bite*2;
            float floatY=fy-12+bob+sink,floatAngle=(float)Math.sin(world.time*1.8f)*.06f;
            float floatBaseX=fx-(float)Math.sin(floatAngle)*15,floatBaseY=floatY+(float)Math.cos(floatAngle)*15;
            color(.88f,.90f,.80f,.54f);GLES20.glLineWidth(Math.max(1,pixelScale*.65f));sceneLine(tx,ty,floatBaseX,floatBaseY,(stage==FishingSession.Stage.WAITING||stage==FishingSession.Stage.DOZING)?9:3,21);
            if(stage==FishingSession.Stage.WAITING||stage==FishingSession.Stage.DOZING){
                GLES20.glUniform1f(loc(colorProgram,"uPoint"),0);for(int i=0;i<=32;i++){float a=i/32f*6.283185f;primitiveData[i*2]=(fx+(float)Math.cos(a)*(7+2*bite))/800-1;primitiveData[i*2+1]=1-(fy+(float)Math.sin(a)*(2.5f+bite))/450;}color(.80f,.87f,.73f,.18f+bite*.16f);drawPrimitive(GLES20.GL_LINE_STRIP,33);
            }
            GLES20.glUseProgram(floatProgram);GLES20.glUniform2f(loc(floatProgram,"uOffset"),0,0);GLES20.glUniform1f(loc(floatProgram,"uWaterline"),(stage==FishingSession.Stage.WAITING||stage==FishingSession.Stage.DOZING)?fy/900:2);
            sprite(0,floatProgram,fx,floatY,9,34,floatAngle);
            if(fishing.result!=null){
                float caughtLength=67*KoiPose.VISUAL_SCALE;
                float swing=(float)Math.sin(world.time*6)*.10f,leaderLength=30;
                float caughtAngle=(float)Math.PI*.5f+swing+(float)Math.sin(world.time*10)*.08f;
                float mouthX=floatBaseX+(float)Math.sin(swing)*leaderLength;
                float mouthY=floatBaseY+(float)Math.cos(swing)*leaderLength;
                sceneLine(floatBaseX,floatBaseY,mouthX,mouthY,0,2);
                GLES20.glUseProgram(fishProgram);GLES20.glUniform1f(loc(fishProgram,"uTime"),world.time);GLES20.glUniform1f(loc(fishProgram,"uVariant"),fishing.result.skin);
                // The source fish faces left: +90 degrees puts its mouth above its tail.
                sprite(koiTexture,fishProgram,mouthX+(float)Math.cos(caughtAngle)*caughtLength*.5f,mouthY+(float)Math.sin(caughtAngle)*caughtLength*.5f,caughtLength,24*KoiPose.VISUAL_SCALE,caughtAngle);
            }
        }
        if(stage==FishingSession.Stage.DOZING){
            GLES20.glUseProgram(colorProgram);GLES20.glUniform1f(loc(colorProgram,"uPoint"),0);
            GLES20.glLineWidth(Math.max(1.5f,pixelScale*1.8f));
            for(int z=0;z<2;z++){
                float x=CAT_X+53+z*16,y=CAT_Y-102-z*18-(float)Math.sin(world.time*.8f)*4;
                float half=7-z,height=11-z;
                color(.08f,.14f,.12f,.65f);drawSleepMark(x-1,y+1.5f,half,height);
                color(1,1,1,.98f-z*.12f);drawSleepMark(x,y,half,height);
            }
        }
    }
    private void drawSleepMark(float x,float y,float half,float height){
        // The pond projection reflects X; pre-reflect the letter around its anchor so it reads as Z.
        detailVertex(0,x+half,y);detailVertex(1,x-half,y);
        detailVertex(2,x+half,y+height);detailVertex(3,x-half,y+height);
        drawPrimitive(GLES20.GL_LINE_STRIP,4);
    }
    private int selectRipples(){
        int count=Math.min(4,world.ripples.size());
        for(int n=0;n<count;n++){
            float best=-1;int chosen=-1;
            for(int i=0;i<world.ripples.size();i++){boolean used=false;for(int j=0;j<n;j++)if(selectedRipples[j]==i)used=true;if(used)continue;PondWorld.Ripple r=world.ripples.get(i);float score=r.power*Math.max(0,1-r.age/4.2f);if(score>best){best=score;chosen=i;}}
            selectedRipples[n]=chosen;PondWorld.Ripple r=world.ripples.get(chosen);int k=n*4;rippleData[k]=r.x/1600;rippleData[k+1]=r.y/900;rippleData[k+2]=r.age;rippleData[k+3]=r.power*Math.max(0,1-r.age/4.2f);
        }
        return count;
    }
    private void drawFish(PondWorld.Fish f,boolean shadow){
        int p=shadow?shadowProgram:fishProgram;
        float ox=shadow?10+f.depth*18:0,oy=shadow?13+f.depth*20:0;
        // Mesh normals derive from the same constrained spine as the texture deformation.
        mesh.position(0);mesh.put(f.pose.vertices).position(0);attributes(p,mesh);
        GLES20.glUniform2f(loc(p,"uOffset"),ox/800,-oy/450);
        GLES20.glUniform1f(loc(p,"uAlpha"),shadow?.12f:(1-f.depth*.10f));
        if(!shadow){GLES20.glUniform1f(loc(p,"uVariant"),f.skin);GLES20.glUniform1f(loc(p,"uShoreMask"),1);}
        fishIndices.position(0);GLES20.glDrawElements(GLES20.GL_TRIANGLES,fishIndexCount,GLES20.GL_UNSIGNED_SHORT,fishIndices);
    }
    private void drawTurtle(boolean shadow){
        int p=shadow?shadowProgram:fishProgram;
        PondWorld.Turtle turtle=world.turtle;bindTexture(turtleTexture,p);
        mesh.position(0);mesh.put(turtle.pose.vertices).position(0);attributes(p,mesh);
        GLES20.glUniform2f(loc(p,"uOffset"),shadow?19f/800:0,shadow?-21f/450:0);

        GLES20.glUniform1f(loc(p,"uAlpha"),shadow?.19f:.94f);
        if(!shadow){GLES20.glUniform1f(loc(p,"uVariant"),0);GLES20.glUniform1f(loc(p,"uShoreMask"),1);}
        fishIndices.position(0);GLES20.glDrawElements(GLES20.GL_TRIANGLES,fishIndexCount,GLES20.GL_UNSIGNED_SHORT,fishIndices);
    }

    private void drawDetails(){
        GLES20.glUseProgram(colorProgram);GLES20.glUniform1f(loc(colorProgram,"uPoint"),1);GLES20.glUniform1f(loc(colorProgram,"uSize"),Math.max(3,4.2f*pixelScale));
        for(PondWorld.Food p:world.food){
            float remaining=Math.max(0,-p.age/p.fallTime);
            float height=74*remaining*(2-remaining);
            primitiveData[0]=(p.x+remaining*(float)Math.cos(p.phase)*22)/800-1;
            primitiveData[1]=1-(p.y-height)/450;
            color(.88f,.72f,.4f,Math.min(1,(35-p.age)/3));drawPrimitive(GLES20.GL_POINTS,1);
        }
        drawFeedDroplets();
        GLES20.glUniform1f(loc(colorProgram,"uPoint"),0);GLES20.glLineWidth(Math.max(1,pixelScale));
        for(PondWorld.Ripple r:world.ripples){float fade=(1-r.age/4.2f);fade*=fade;for(int ring=0;ring<2;ring++){
            float radius=5+r.age*43*(.6f+r.power*.4f)-ring*10;if(radius<3)continue;
            for(int i=0;i<=64;i++){float a=i/64f*6.283185f;primitiveData[i*2]=(r.x+(float)Math.cos(a)*radius)/800-1;primitiveData[i*2+1]=1-(r.y+(float)Math.sin(a)*radius*.85f)/450;}
            color(.83f,.9f,.8f,fade*(.11f+r.power*.13f)*(ring==0?1:.55f));drawPrimitive(GLES20.GL_LINE_STRIP,65);
        }}
        drawLeaves();
        if(world.cursorVisible){
            float radius=15+(float)Math.sin(world.time*2)*1.3f;for(int i=0;i<=48;i++){float a=i/48f*6.283185f;primitiveData[i*2]=(world.cursorX+(float)Math.cos(a)*radius)/800-1;primitiveData[i*2+1]=1-(world.cursorY+(float)Math.sin(a)*radius)/450;}
            color(.95f,.94f,.8f,.85f);drawPrimitive(GLES20.GL_LINE_STRIP,49);
            float x=world.cursorX,y=world.cursorY;float[] ticks={x-24,y,x-20,y,x+20,y,x+24,y,x,y-24,x,y-20,x,y+20,x,y+24};for(int i=0;i<16;i+=2){primitiveData[i]=ticks[i]/800-1;primitiveData[i+1]=1-ticks[i+1]/450;}drawPrimitive(GLES20.GL_LINES,8);
        }
    }
    // Unit maple silhouette, from tip around five pointed lobes to the leaf base.
    private static final float[] MAPLE={
        1,0,.55f,.14f,.64f,.35f,.28f,.31f,.41f,.66f,.07f,.57f,
        -.02f,.76f,-.24f,.46f,-.42f,.52f,-.53f,.21f,-.69f,.27f,-.58f,0,
        -.69f,-.27f,-.53f,-.21f,-.42f,-.52f,-.24f,-.46f,-.02f,-.76f,
        .07f,-.57f,.41f,-.66f,.28f,-.31f,.64f,-.35f,.55f,-.14f,1,0
    };
    private void drawLeaves(){
        for(PondWorld.Leaf leaf:world.leaves){
            boolean falling=leaf.age<0;
            float t=falling?1+leaf.age/PondWorld.Leaf.FALL_SECONDS:1;
            float flutter=(float)Math.sin(t*10+leaf.phase)*(1-t);
            float x=leaf.x+flutter*41,y=leaf.y-142*(1-t)*(1-t);
            float alpha=falling?Math.min(1,t*9):leaf.fade*.94f;
            float angle=leaf.angle+(falling?flutter*.78f:(float)Math.sin(leaf.age*.84f+leaf.phase)*.08f);
            float cs=(float)Math.cos(angle),sn=(float)Math.sin(angle);
            // Account for the cutouts between lobes when sizing the silhouette.
            float size=leaf.size*1.3642f*1.41421356f,fold=falling?.64f+.36f*Math.abs((float)Math.cos(t*13+leaf.phase)):1;
            detailVertex(0,x,y);
            for(int i=0;i<MAPLE.length/2;i++){
                float u=MAPLE[i*2]*size,v=MAPLE[i*2+1]*size*fold;
                detailVertex(i+1,x+u*cs-v*sn,y+u*sn+v*cs);
            }
            if(leaf.amber)color(.82f,.36f,.12f,alpha);
            else color(.66f,.24f,.12f,alpha);
            drawPrimitive(GLES20.GL_TRIANGLE_FAN,MAPLE.length/2+1);
            detailVertex(0,x-size*.90f*cs,y-size*.90f*sn);
            detailVertex(1,x+size*.73f*cs,y+size*.73f*sn);
            color(.94f,.64f,.32f,alpha*.75f);drawPrimitive(GLES20.GL_LINES,2);
        }
    }
    private void detailVertex(int index,float x,float y){
        primitiveData[index*2]=x/800-1;primitiveData[index*2+1]=1-y/450;
    }
    private void drawFeedDroplets(){
        final float lifetime=.24f;
        GLES20.glUniform1f(loc(colorProgram,"uSize"),Math.max(3,5*pixelScale));
        for(PondWorld.Food p:world.food){
            if(p.age<0||p.age>=lifetime)continue;
            float t=p.age/lifetime;
            for(int i=0;i<3;i++){
                float angle=p.phase+i*2.0944f;
                float distance=3+t*(11+i*4);
                float lift=(11+i*3)*4*t*(1-t);
                primitiveData[i*2]=(p.x+(float)Math.cos(angle)*distance)/800-1;
                primitiveData[i*2+1]=1-(p.y+(float)Math.sin(angle)*distance*.58f-lift)/450;
            }
            color(.82f,.96f,.91f,.76f*(1-t));drawPrimitive(GLES20.GL_POINTS,3);
        }
    }
    private void color(float r,float g,float b,float a){GLES20.glUniform4f(loc(colorProgram,"uColor"),r,g,b,a);}
    private void drawPrimitive(int mode,int count){primitives.position(0);primitives.put(primitiveData,0,count*2).position(0);int a=handles.get(colorProgram).position;GLES20.glEnableVertexAttribArray(a);GLES20.glVertexAttribPointer(a,2,GLES20.GL_FLOAT,false,8,primitives);GLES20.glDrawArrays(mode,0,count);}
    private int loc(int program,String name){return handles.get(program).uniforms.get(name);}
    private void attributes(int program,FloatBuffer data){Handles h=handles.get(program);int p=h.position,uv=h.uv;data.position(0);GLES20.glEnableVertexAttribArray(p);GLES20.glVertexAttribPointer(p,2,GLES20.GL_FLOAT,false,16,data);data.position(2);GLES20.glEnableVertexAttribArray(uv);GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,data);data.position(0);}
    private void bindTexture(int texture,int program){GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);GLES20.glUniform1i(loc(program,"uTexture"),0);}
    private void bindWaterMask(int program){
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,waterMaskTexture);
        GLES20.glUniform1i(loc(program,"uWaterMask"),2);
    }
    private static int createWaterMask(){
        final int w=512,h=288;
        ByteBuffer pixels=ByteBuffer.allocateDirect(w*h);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            float clearance=PondWorld.waterClearance((x+.5f)*1600/w,(y+.5f)*900/h);
            // Fade only on the water side, so banks and leaves stay opaque.
            float t=PondWorld.clamp((clearance-4)/12,0,1);
            pixels.put((byte)Math.round(255*t*t*(3-2*t)));
        }
        pixels.flip();int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,ids[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_LUMINANCE,w,h,0,GLES20.GL_LUMINANCE,GLES20.GL_UNSIGNED_BYTE,pixels);
        return ids[0];
    }
    private int texture(String file,boolean fish){
        Bitmap image;
        try(InputStream in=context.getAssets().open("pond/" + file)){BitmapFactory.Options options=new BitmapFactory.Options();options.inScaled=false;options.inPremultiplied=true;image=BitmapFactory.decodeStream(in,null,options);if(image==null)throw new IllegalStateException("Cannot decode "+file);}
        catch(Exception ex){Log.e("Yichi","Missing texture: "+file,ex);if(file.equals("turtle.png")||file.startsWith("fishing-cat-"))throw new IllegalStateException("Turtle texture unavailable",ex);image=fallback(fish);}
        boolean turtle=file.equals("turtle.png"),mipmapped=turtle||fish;
        if(mipmapped){
            // Normalized UVs preserve each animal's mesh proportions independently of storage size.
            // Bundled sprites are already baked to these sizes. GLES2 needs power-of-two mipmaps.
            Bitmap smaller=Bitmap.createScaledBitmap(image,fish?1024:512,fish?256:512,true);if(smaller!=image){image.recycle();image=smaller;}
        }else{
            int limit=file.startsWith("fishing-cat-")?512:2048;
            if(image.getWidth()>limit||image.getHeight()>limit){float ratio=limit/(float)Math.max(image.getWidth(),image.getHeight());Bitmap smaller=Bitmap.createScaledBitmap(image,Math.round(image.getWidth()*ratio),Math.round(image.getHeight()*ratio),true);image.recycle();image=smaller;}
        }
        int[] id=new int[1];GLES20.glGenTextures(1,id,0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,mipmapped?GLES20.GL_LINEAR_MIPMAP_LINEAR:GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,file.equals("caustics.webp")?GLES20.GL_REPEAT:GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,file.equals("caustics.webp")?GLES20.GL_REPEAT:GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,image,0);if(mipmapped){GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);Log.i("Yichi",file+" texture="+image.getWidth()+"x"+image.getHeight()+" trilinear mipmaps");}
        image.recycle();return id[0];
    }
    private static Bitmap fallback(boolean fish){Bitmap b=Bitmap.createBitmap(512,256,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(3);if(!fish){c.drawColor(Color.rgb(31,73,67));return b;}p.setColor(Color.rgb(225,220,193));c.drawOval(25,70,410,186,p);Path tail=new Path();tail.moveTo(375,128);tail.lineTo(500,55);tail.lineTo(470,128);tail.lineTo(500,200);tail.close();c.drawPath(tail,p);p.setColor(Color.rgb(178,67,42));c.drawOval(70,84,160,165,p);c.drawOval(240,90,310,160,p);p.setColor(Color.rgb(24,46,38));c.drawCircle(45,108,5,p);c.drawCircle(45,148,5,p);return b;}
    private static int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Shader compile failed: "+GLES20.glGetShaderInfoLog(s));return s;}
    private static int program(String vertex,String fragment){int v=shader(GLES20.GL_VERTEX_SHADER,vertex),f=shader(GLES20.GL_FRAGMENT_SHADER,fragment),p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException("Shader link failed: "+GLES20.glGetProgramInfoLog(p));GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);return p;}
}
