package com.gentv.tv.pond;

/** Rigid carapace with independently paddling, diagonally paired webbed limbs. */
public final class TurtlePose {
    public final float[] vertices=new float[(KoiPose.SEGMENTS+1)*KoiPose.ROWS*4];
    private static float smooth(float a,float b,float v){float t=PondWorld.clamp((v-a)/(b-a),0,1);return t*t*(3-2*t);}
    public TurtlePose(PondWorld.Turtle turtle){update(turtle);}
    public void update(PondWorld.Turtle t){
        float length=t.length*(1-t.depth*.08f),height=length*(2f/3f);
        float cs=(float)Math.cos(t.angle),sn=(float)Math.sin(t.angle);
        float left=(float)Math.sin(t.phase+(float)Math.PI),right=(float)Math.sin(t.phase);
        float neck=(float)Math.sin(t.phase)*length*.004f;
        int k=0;
        for(int i=0;i<=KoiPose.SEGMENTS;i++){
            float u=i/(float)KoiPose.SEGMENTS;
            float front=smooth(.12f,.23f,u)*(1-smooth(.34f,.45f,u));
            float rear=smooth(.56f,.67f,u)*(1-smooth(.85f,.95f,u));
            for(int j=0;j<KoiPose.ROWS;j++){
                float v=j/(float)(KoiPose.ROWS-1),side=v<.5f?-1:1;
                float fx=(.5f-u)*length,fy=(v-.5f)*height;
                float shellU=(u-.54f)/.292f,shellV=(v-.49f)/.34f;
                float outsideShell=smooth(1.04f,1.20f,(float)Math.hypot(shellU,shellV));
                float outer=smooth(.46f,.88f,Math.abs(v-.5f)*2)*outsideShell;
                float stroke=side<0?left:right;
                float weightFront=front*outer,weightRear=rear*outer;
                float rotatedX=fx,rotatedY=fy;
                if(weightFront>0){
                    float pivotX=(.5f-.30f)*length,pivotY=side*height*.22f;
                    float a=side*.66f*stroke*t.effort,c=(float)Math.cos(a),s=(float)Math.sin(a),dx=fx-pivotX,dy=fy-pivotY;
                    rotatedX+=(pivotX+dx*c-dy*s-fx)*weightFront;
                    rotatedY+=(pivotY+dx*s+dy*c-fy)*weightFront;
                }
                if(weightRear>0){
                    float pivotX=(.5f-.75f)*length,pivotY=side*height*.22f;
                    float a=-side*.46f*stroke*t.effort,c=(float)Math.cos(a),s=(float)Math.sin(a),dx=fx-pivotX,dy=fy-pivotY;
                    rotatedX+=(pivotX+dx*c-dy*s-fx)*weightRear;
                    rotatedY+=(pivotY+dx*s+dy*c-fy)*weightRear;
                }
                // The neck moves slightly; the shell and its scute pattern stay rigid.
                float head=1-smooth(.12f,.26f,u);
                rotatedY+=head*(neck+PondWorld.clamp(t.turn,-.3f,.3f)*length*.05f);
                vertices[k++]=(t.x+cs*rotatedX-sn*rotatedY)/800-1;
                vertices[k++]=1-(t.y+sn*rotatedX+cs*rotatedY)/450;
                vertices[k++]=u;vertices[k++]=v;
            }
        }
    }
}
