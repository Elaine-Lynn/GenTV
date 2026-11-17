package com.gentv.tv.pond;

/** Arc-length body following and carangiform propulsion, independent of Android/GL. */
public final class KoiPose {
    public static final int SEGMENTS=32, ROWS=9;
    public static final float VISUAL_SCALE=2f/3f;
    private static final int HISTORY=112;
    private static final float PI=(float)Math.PI;
    private static final float[] SIDES=new float[ROWS],OUTER=new float[ROWS];
    static { for(int j=0;j<ROWS;j++){SIDES[j]=j/(float)(ROWS-1)*2-1;OUTER[j]=smooth(.25f,.85f,Math.abs(SIDES[j]));} }
    public final float[] x=new float[SEGMENTS+1],y=new float[SEGMENTS+1];
    public final float[] normalX=new float[SEGMENTS+1],normalY=new float[SEGMENTS+1];
    public final float[] vertices=new float[(SEGMENTS+1)*ROWS*4];
    private final float[] trailX=new float[HISTORY],trailY=new float[HISTORY];
    private final float[] desiredX=new float[SEGMENTS+1],desiredY=new float[SEGMENTS+1];
    private int newest;
    private float bodyFollow;
    private final float spacing;
    public float length;

    public KoiPose(PondWorld.Fish fish){spacing=fish.length/64;reset(fish);update(fish,0);}
    private static float clamp(float value,float low,float high){return Math.max(low,Math.min(high,value));}
    private static float angle(float a){return (float)Math.atan2(Math.sin(a),Math.cos(a));}
    private static float smooth(float a,float b,float v){float t=clamp((v-a)/(b-a),0,1);return t*t*(3-2*t);}
    private void reset(PondWorld.Fish f){
        newest=0;bodyFollow=desiredBodyFollow(f);float cs=(float)Math.cos(f.angle),sn=(float)Math.sin(f.angle);
        for(int i=0;i<HISTORY;i++){int index=(HISTORY-i)%HISTORY;trailX[index]=f.x-cs*i*spacing;trailY[index]=f.y-sn*i*spacing;}
    }
    private static float desiredBodyFollow(PondWorld.Fish f) {
        return Math.max(smooth(.10f,.66f,f.effort),smooth(.08f,.58f,Math.abs(f.turn)));
    }
    public void update(PondWorld.Fish f, float dt){
        length=f.length*(1-f.depth*.1f);
        float dx=f.x-trailX[newest],dy=f.y-trailY[newest],distance=(float)Math.hypot(dx,dy);
        if(distance>f.length*2){reset(f);distance=0;}
        // Distance samples, rather than frame history, preserve body length at every speed/FPS.
        while(distance>=spacing){
            float px=trailX[newest]+dx/distance*spacing,py=trailY[newest]+dy/distance*spacing;
            newest=(newest+1)%HISTORY;trailX[newest]=px;trailY[newest]=py;
            dx=f.x-px;dy=f.y-py;distance=(float)Math.hypot(dx,dy);
        }
        // Keep some trailing curvature through the glide and release the old bend over time.
        // Recover body following faster when a new turn or stroke starts.
        float targetFollow=desiredBodyFollow(f);
        float response=targetFollow>bodyFollow?7f:3.8f;
        bodyFollow+=(targetFollow-bodyFollow)*(1-(float)Math.exp(-dt*response));
        float headingX=(float)Math.cos(f.angle),headingY=(float)Math.sin(f.angle);
        float fromX=f.x,fromY=f.y,toX=trailX[newest],toY=trailY[newest],walked=0;
        float span=(float)Math.hypot(toX-fromX,toY-fromY);int offset=0;
        for(int i=0;i<=SEGMENTS;i++){
            float u=i/(float)SEGMENTS,target=u*length;
            while(walked+span<target&&offset<HISTORY-1){
                walked+=span;fromX=toX;fromY=toY;offset++;
                int index=(newest-offset+HISTORY)%HISTORY;toX=trailX[index];toY=trailY[index];span=(float)Math.hypot(toX-fromX,toY-fromY);
            }
            float mix=span>.00001f?clamp((target-walked)/span,0,1):0;
            float px=fromX+(toX-fromX)*mix,py=fromY+(toY-fromY)*mix;
            float tailLag=.035f+.09f*smooth(.24f,.96f,u);
            float trailWeight=bodyFollow+(1-bodyFollow)*tailLag;
            float straightX=f.x-headingX*target,straightY=f.y-headingY*target;
            px=straightX+(px-straightX)*trailWeight;
            py=straightY+(py-straightY)*trailWeight;
            float nx=-headingY,ny=headingX;
            if(span>.00001f){
                nx+=((toY-fromY)/span-nx)*trailWeight;
                ny+=(-(toX-fromX)/span-ny)*trailWeight;
            }
            // The skull stays rigid. Quiet glides retain a small tail trim instead of a full stroke.
            float envelope=smooth(.16f,.94f,u);envelope*=envelope;
            float wave=(float)Math.sin(u*6.1f-f.phase)*length*(.004f+.060f*f.effort)*envelope;
            desiredX[i]=px+nx*wave;desiredY[i]=py+ny*wave;
        }
        x[0]=f.x;y[0]=f.y;
        float backwards=f.angle+PI,previous=backwards,step=length/SEGMENTS;
        for(int i=1;i<=SEGMENTS;i++){
            float u=i/(float)SEGMENTS;
            float target=(float)Math.atan2(desiredY[i]-desiredY[i-1],desiredX[i]-desiredX[i-1]);
            target=backwards+angle(target-backwards)*smooth(.12f,.34f,u);
            float maxBend=.055f+.055f*smooth(.25f,.95f,u);
            float direction=previous+clamp(angle(target-previous),-maxBend,maxBend);
            x[i]=x[i-1]+(float)Math.cos(direction)*step;y[i]=y[i-1]+(float)Math.sin(direction)*step;previous=direction;
        }
        for(int i=0;i<=SEGMENTS;i++){
            int before=Math.max(0,i-1),after=Math.min(SEGMENTS,i+1);dx=x[after]-x[before];dy=y[after]-y[before];float d=(float)Math.hypot(dx,dy);
            normalX[i]=dy/d;normalY[i]=-dx/d;
        }
        float leftStroke=(float)Math.sin(f.finPhase-.35f),rightStroke=(float)Math.sin(f.finPhase+.35f);
        float leftSweep=(float)Math.cos(f.finPhase-.4f),rightSweep=(float)Math.cos(f.finPhase+.4f);
        float steeringBias=clamp(f.turn*.45f,-.22f,.22f);
        int k=0;
        for(int i=0;i<=SEGMENTS;i++){
            float u=i/(float)SEGMENTS;
            float pectoral=smooth(.12f,.21f,u)*(1-smooth(.30f,.40f,u));
            for(int j=0;j<ROWS;j++){
                float v=j/(float)(ROWS-1),side=SIDES[j],outer=OUTER[j];
                float stroke=j<ROWS/2?leftStroke:rightStroke;
                float steering=side<0?-steeringBias:steeringBias;
                float fold=1-pectoral*outer*(.16f+stroke*.105f+steering);
                float lateral=side*length*.17f*fold;
                float sweep=pectoral*outer*length*.018f*(side<0?leftSweep:rightSweep);
                vertices[k++]=(f.x+(x[i]+normalX[i]*lateral+normalY[i]*sweep-f.x)*VISUAL_SCALE)/800-1;
                vertices[k++]=1-(f.y+(y[i]+normalY[i]*lateral-normalX[i]*sweep-f.y)*VISUAL_SCALE)/450;
                vertices[k++]=u;vertices[k++]=v;
            }
        }
    }
    public static short[] indices(){
        short[] result=new short[SEGMENTS*(ROWS-1)*6];int k=0;
        for(int i=0;i<SEGMENTS;i++)for(int j=0;j<ROWS-1;j++){
            short a=(short)(i*ROWS+j),b=(short)((i+1)*ROWS+j);
            result[k++]=a;result[k++]=b;result[k++]=(short)(a+1);result[k++]=(short)(a+1);result[k++]=b;result[k++]=(short)(b+1);
        }return result;
    }
}
