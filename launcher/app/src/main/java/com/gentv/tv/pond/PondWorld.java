package com.gentv.tv.pond;

import java.util.ArrayList;
import java.util.Random;

/** Pure simulation. All mutation is confined to GLSurfaceView's render thread. */
public final class PondWorld {
    public static final float WIDTH=1600, HEIGHT=900;
    private static final float TAU=6.283185307f,TIME_PERIOD=TAU*100;
    public final ArrayList<Fish> fish=new ArrayList<>();
    public final ArrayList<Food> food=new ArrayList<>();
    public final ArrayList<Ripple> ripples=new ArrayList<>();
    public final ArrayList<Leaf> leaves=new ArrayList<>();
    public final Turtle turtle;
    private final Random weather;
    private float untilDrop,untilLeaf;
    private final Random random;
    public float time, tempo=1, cursorX=800, cursorY=440;
    public boolean cursorVisible;
    public int eaten;
    public int feedImpacts;
    private float lastFeed=-10, nextWake;
    public static final class Fish {
        public float x,y,angle,turn,speed,cruise,length,phase,depth,targetDepth,wander,wanderTime,response,fear;
        public float effort=.65f,beatHz=1,finPhase,motionTimer,soloClock,sociability=1;
        public boolean stroking;
        private float bankBlocked, bankEscape, bankHeading;
        public KoiPose pose;
        public int skin;
        public Food target;
    }
    public static final class Food { public float x,y,age,phase,fallTime; public boolean lead; }
    public static final class Ripple { public float x,y,age,power; }
    public static final class Leaf {
        static final float FALL_SECONDS=2.6f;
        public float x,y,age=-FALL_SECONDS,phase,size,angle,vx,vy;
        public float fade=1;
        public boolean amber;
    }
    public static final class Turtle {
        public float x=620,y=600,angle=-.4f,turn,speed=10,length=79,phase=1.2f,depth=.42f;
        public float effort=.65f,wander=-.4f,wanderTime=6,motionTime=8,fear;
        public boolean paddling=true;
        public int eaten;
        public Food target;
        public TurtlePose pose;
    }
    public PondWorld(long seed){this(seed,seed^0x66B81DA3L);}
    PondWorld(long seed,long ambientSeed) {random=new Random(seed);weather=new Random(ambientSeed);untilDrop=ambientRange(8,20);untilLeaf=ambientRange(18,42);setCount(20);turtle=new Turtle();turtle.pose=new TurtlePose(turtle);}
    // Traced water-facing envelopes on pond.png (1672 × 941). Border rock
    // shelves and foliage occlude fish; the submerged stones in the middle do not.
    private static final float[] LEFT={0,245,145,232,220,147,310,58,425,36,480,33,520,98,565,146,610,250,647,286,710,304,755,333,790,370,835,450,890,505,941,515};
    private static final float[] RIGHT={0,1260,75,1410,118,1400,155,1455,188,1428,241,1440,264,1460,296,1477,328,1510,367,1515,416,1510,457,1555,510,1550,566,1505,607,1470,653,1445,706,1460,757,1470,824,1450,895,1380,941,1350};
    private static final float[] TOP={0,260,120,228,235,178,315,153,390,138,490,173,555,185,628,146,700,132,775,143,840,102,900,124,955,133,1010,87,1080,69,1160,106,1240,141,1325,118,1395,120,1465,159,1540,230,1672,380};
    private static final float[] BOTTOM={0,715,400,831,490,887,535,922,930,928,1085,911,1160,895,1230,872,1290,813,1365,825,1410,791,1500,740,1672,667};
    private static float profile(float[] edge,float at){
        int lo=0,hi=edge.length/2-1;
        while(hi-lo>1){int mid=(lo+hi)/2;if(at<edge[mid*2])hi=mid;else lo=mid;}
        lo*=2;hi*=2;
        float t=clamp((at-edge[lo])/(edge[hi]-edge[lo]),0,1);
        return edge[lo+1]+t*(edge[hi+1]-edge[lo+1]);
    }
    public static float shoreX(float y){return profile(LEFT,y*941/HEIGHT)*WIDTH/1672;}
    public static float waterClearance(float x,float y){
        float px=x*1672/WIDTH,py=y*941/HEIGHT;
        return Math.min(Math.min(x-shoreX(y),profile(RIGHT,py)*WIDTH/1672-x),
            Math.min(y-profile(TOP,px)*HEIGHT/941,profile(BOTTOM,px)*HEIGHT/941-y));
    }
    private float range(float a,float b){return a+random.nextFloat()*(b-a);}
    public static float clamp(float x,float a,float b){return Math.max(a,Math.min(b,x));}
    private static float hypot(float x,float y){return (float)Math.hypot(x,y);}
    private static float cos(float a){return (float)Math.cos(a);}
    private static float sin(float a){return (float)Math.sin(a);}
    private static float wrap(float a){return (float)Math.atan2(Math.sin(a),Math.cos(a));}
    private static float escapeBank(Fish fish){
        float best=-Float.MAX_VALUE,heading=fish.angle;
        // Test complete paths: at a corner, the closest bank alone is not
        // enough to choose a heading clear of both edges.
        for(int i=0;i<24;i++){
            float a=fish.angle+(i/24f)*TAU,dx=cos(a),dy=sin(a);
            float near=waterClearance(fish.x+dx*12,fish.y+dy*12);
            float mid=waterClearance(fish.x+dx*35,fish.y+dy*35);
            float far=waterClearance(fish.x+dx*75,fish.y+dy*75);
            float score=Math.min(near,Math.min(mid,far))*3+far+.5f*cos(a-fish.angle);
            if(score>best){best=score;heading=a;}
        }
        return wrap(heading);
    }
    public void setCount(int n){
        n=(int)clamp(n,0,40);
        while(fish.size()<n)fish.add(newFish(false));
        while(fish.size()>n)fish.remove(fish.size()-1);
    }
    private Fish newFish(boolean atEdge){
        Fish f=new Fish();f.x=range(190,1410);f.y=range(140,760);
        for(int attempt=0;attempt<8&&waterClearance(f.x,f.y)<100;attempt++){f.x+=(800-f.x)*.25f;f.y+=(450-f.y)*.25f;}
        f.angle=range(0,6.283185f);
        if(atEdge){
            float dx=cos(f.angle),dy=sin(f.angle);
            f.x=800;f.y=450;
            while(waterClearance(f.x+dx*8,f.y+dy*8)>=86){f.x+=dx*8;f.y+=dy*8;}
            float low=0,high=8;
            for(int i=0;i<6;i++){
                float middle=(low+high)*.5f;
                if(waterClearance(f.x+dx*middle,f.y+dy*middle)>=86)low=middle;else high=middle;
            }
            f.x+=dx*low;f.y+=dy*low;
            f.angle=wrap(f.angle+(float)Math.PI+range(-.23f,.23f));
        }
        f.wander=f.angle;f.cruise=range(22,36);f.speed=f.cruise;f.length=range(69,105);
        f.phase=range(0,6.28f);f.soloClock=f.phase*5.2f;f.finPhase=range(0,TAU);
        f.stroking=random.nextBoolean();f.motionTimer=range(.5f,2.5f);
        f.depth=range(.15f,.9f);f.targetDepth=f.depth;f.response=range(.2f,1.5f);
        f.skin=random.nextInt(6);f.pose=new KoiPose(f);return f;
    }
    void replaceCaughtFish(int index){fish.set(index,newFish(true));}
    public boolean feedRandom(){return feed(range(320,1370),range(180,680));}
    public boolean feed(float x,float y){
        if(time-lastFeed<.65f)return false;
        y=clamp(y,190,800);x=clamp(Math.max(x,shoreX(y)+85),70,1340);
        for(int attempt=0;attempt<8&&waterClearance(x,y)<65;attempt++){x+=(800-x)*.22f;y+=(450-y)*.22f;}
        lastFeed=time;
        for(int i=0;i<12;i++){float a=range(0,6.28f),r=range(2,25);Food p=new Food();p.x=x+cos(a)*r;p.y=y+sin(a)*r;p.phase=range(0,6.28f);p.fallTime=.28f+i*.015f;p.age=-p.fallTime;p.lead=i==0;food.add(p);}
        while(food.size()>96)food.remove(0);return true;
    }
    public void ripple(float x,float y,float power){Ripple r=new Ripple();r.x=x;r.y=y;r.power=power;ripples.add(r);while(ripples.size()>12)ripples.remove(0);}
    public void disturb(float x,float y){ripple(x,y,1.5f);for(Fish f:fish)if(hypot(f.x-x,f.y-y)<260){f.fear=1;f.wander=(float)Math.atan2(f.y-y,f.x-x);}if(hypot(turtle.x-x,turtle.y-y)<220){turtle.fear=1;turtle.wander=(float)Math.atan2(turtle.y-y,turtle.x-x);}}
    public void moveCursor(float dx,float dy){cursorVisible=true;cursorX=clamp(cursorX+dx,70,1530);cursorY=clamp(cursorY+dy,70,830);}
    public void update(float realDt){
        // Preserve real-time motion at 15/20fps; bound catch-up after a long stall.
        float elapsed=clamp(realDt,0,.20f);
        int steps=Math.max(1,(int)Math.ceil(elapsed/.05f));
        for(int i=0;i<steps;i++)step(elapsed/steps);
    }
    private void step(float realDt){
        float dt=realDt*tempo;time+=dt;
        // Bound float magnitudes for a launcher that may remain open for days.
        // Shader time multipliers are hundredths, making this rollover visually periodic.
        if(time>=TIME_PERIOD){time-=TIME_PERIOD;lastFeed-=TIME_PERIOD;nextWake-=TIME_PERIOD;}
        for(Fish f:fish){
            // Stagger each fish's short solo outings so the shoal never changes as a block.
            f.soloClock+=dt;
            float cycle=31+f.response*12;
            if(f.soloClock>=cycle)f.soloClock-=cycle;
            float company=f.soloClock<cycle*.20f?.04f:.72f+f.response*.33f;
            f.sociability+=(company-f.sociability)*(1-(float)Math.exp(-dt*.65f));
            f.wanderTime-=dt;f.fear=Math.max(0,f.fear-dt*.45f);
            if(f.wanderTime<=0&&f.fear<=0){f.wander=wrap(f.angle+range(-.7f,.7f));f.wanderTime=range(2.4f,5);f.targetDepth=range(.15f,.85f);}
            float ax=cos(f.wander)*.85f,ay=sin(f.wander)*.85f,nearest=Float.MAX_VALUE;
            // Keep a chosen pellet until eaten; switching nearest targets caused head jitter.
            if(f.target!=null&&!food.contains(f.target))f.target=null;
            if(f.target==null){for(Food p:food){float d=hypot(p.x-f.x,p.y-f.y);if(p.age>f.response&&d<nearest){nearest=d;f.target=p;}}}
            else nearest=hypot(f.target.x-f.x,f.target.y-f.y);
            f.motionTimer-=dt;
            if(f.motionTimer<=0){f.stroking=!f.stroking;f.motionTimer=f.stroking?range(.7f,1.5f):range(1.2f,3.2f);}
            float wantedEffort=f.stroking?.72f:.11f;
            float wantedSpeed=f.cruise*(f.stroking?1.02f:.70f);
            if(f.target==null&&f.fear<=0){
                float solo=clamp(1-f.sociability,0,1);
                float explore=f.wander+sin(f.soloClock*.43f+f.response*8)*.65f;
                ax+=cos(explore)*solo*.55f;ay+=sin(explore)*solo*.55f;
                wantedSpeed*=1+solo*(f.response*.20f-.10f);
            }
            if(f.target!=null&&f.fear<=0){
                ax=(f.target.x-f.x)/Math.max(nearest,1)*2.2f;ay=(f.target.y-f.y)/Math.max(nearest,1)*2.2f;
                wantedSpeed=nearest<75?Math.max(9,nearest*.6f):f.cruise*1.6f;
                wantedEffort=nearest<70?.32f:.85f;
                if(nearest<16){food.remove(f.target);eaten++;ripple(f.x,f.y,.23f);f.target=null;}
                f.targetDepth=.08f;
            }
            if(f.fear>0){ax=cos(f.wander)*4;ay=sin(f.wander)*4;wantedSpeed=f.cruise*2.2f;wantedEffort=1;}
            for(Fish o:fish){if(o==f)continue;float dx=f.x-o.x,dy=f.y-o.y,d=hypot(dx,dy),separation=f.target!=null?34:64;
                if(d<separation&&d>.001f){float force=(separation-d)/separation*2;ax+=dx/d*force;ay+=dy/d*force;}
                else if(d<210&&f.target==null&&f.fear<=0){ax-=dx/d*.08f*f.sociability;ay-=dy/d*.08f*f.sociability;ax+=cos(o.angle)*.07f*f.sociability;ay+=sin(o.angle)*.07f*f.sociability;}
            }
            float turtleDx=f.x-turtle.x,turtleDy=f.y-turtle.y,turtleDistance=hypot(turtleDx,turtleDy);
            if(turtleDistance>1&&turtleDistance<88){float avoid=(88-turtleDistance)/88*1.8f;ax+=turtleDx/turtleDistance*avoid;ay+=turtleDy/turtleDistance*avoid;}
            // Look ahead along the heading, so the fish has room to turn at speed.
            float look=45+f.speed*.9f,px=f.x+cos(f.angle)*look,py=f.y+sin(f.angle)*look;
            float clearance=waterClearance(px,py);
            if(clearance<145){
                float dx=waterClearance(px+24,py)-waterClearance(px-24,py);
                float dy=waterClearance(px,py+24)-waterClearance(px,py-24);
                float n=Math.max(1,hypot(dx,dy)),push=clamp((145-clearance)/24,0,8);
                ax+=dx/n*push;ay+=dy/n*push;
            }
            float current=waterClearance(f.x,f.y);
            if(f.bankBlocked>=.30f){
                f.bankHeading=escapeBank(f);f.bankEscape=3;f.bankBlocked=0;
            }
            boolean escaping=f.bankEscape>0;
            if(escaping){
                f.bankEscape=Math.max(0,f.bankEscape-dt);
                f.wander=f.bankHeading;f.wanderTime=Math.max(f.wanderTime,1);
                ax=cos(f.bankHeading);ay=sin(f.bankHeading);
                wantedSpeed=Math.max(wantedSpeed,f.cruise);
            }
            float wanted=(float)Math.atan2(ay,ax),delta=wrap(wanted-f.angle);
            float maxTurn=f.speed/(f.length*.72f)+.12f+(f.target!=null?.12f:0)+f.fear*.24f;
            if(escaping)maxTurn=Math.max(maxTurn,1.05f);
            f.turn+=(clamp(delta*1.6f,-maxTurn,maxTurn)-f.turn)*(1-(float)Math.exp(-dt*2.8f));
            wantedSpeed*=1-clamp(Math.abs(delta)/(float)Math.PI,0,1)*.60f;
            f.angle=wrap(f.angle+f.turn*dt);f.speed+=(wantedSpeed-f.speed)*(1-(float)Math.exp(-dt*.9f));
            float nx=f.x+cos(f.angle)*f.speed*dt,ny=f.y+sin(f.angle)*f.speed*dt;
            float next=waterClearance(nx,ny);
            if(next>=Math.min(56,current)){f.x=nx;f.y=ny;f.bankBlocked=0;}
            else if(!escaping)f.bankBlocked+=dt;
            f.depth+=(f.targetDepth-f.depth)*dt*.2f;
            f.effort+=(wantedEffort-f.effort)*(1-(float)Math.exp(-dt*2.2f));
            float hz=.32f+f.speed/f.length*.9f+f.effort*.72f;
            f.beatHz+=(hz-f.beatHz)*(1-(float)Math.exp(-dt*2));
            f.phase=(f.phase+dt*TAU*f.beatHz)%TAU;
            f.finPhase=(f.finPhase+dt*(1.6f+f.effort*1.3f))%TAU;
            f.pose.update(f,dt);
        }
        updateTurtle(dt);
        for(int i=food.size()-1;i>=0;i--){Food p=food.get(i);float previous=p.age;p.age+=dt;if(p.age<0)continue;if(previous<0&&p.lead){ripple(p.x,p.y,.85f);feedImpacts++;}p.x+=sin(time*.4f+p.phase)*dt*.6f;p.y+=cos(time*.3f+p.phase)*dt*.5f;if(p.age>35)food.remove(i);}
        for(int i=ripples.size()-1;i>=0;i--){Ripple r=ripples.get(i);r.age+=dt;if(r.age>4.2f)ripples.remove(i);}
        updateAmbient(dt);
        if(time>nextWake){nextWake=time+1;for(Fish f:fish)if(f.depth<.22f&&random.nextFloat()<.2f)ripple(f.x,f.y,.12f);}
    }
    private float ambientRange(float lo,float hi){return lo+(hi-lo)*weather.nextFloat();}
    private void updateAmbient(float dt){
        untilDrop-=dt;untilLeaf-=dt;
        if(untilDrop<=0){
            untilDrop=ambientRange(14,37);
            for(int attempt=0;attempt<8;attempt++){
                float x=ambientRange(215,1420),y=ambientRange(185,785);
                if(waterClearance(x,y)>65){ripple(x,y,.54f);break;}
            }
        }
        if(untilLeaf<=0){
            untilLeaf=ambientRange(47,95);
            if(leaves.size()<3){
                Leaf leaf=new Leaf();
                leaf.x=ambientRange(320,1240);leaf.y=ambientRange(230,505);
                leaf.phase=ambientRange(0,TAU);leaf.size=ambientRange(8,13);
                leaf.angle=ambientRange(-.85f,.85f);leaf.amber=weather.nextBoolean();
                float direction=ambientRange(0,TAU),speed=ambientRange(7,12);
                leaf.vx=cos(direction)*speed*.5f;leaf.vy=sin(direction)*speed*.5f;
                leaves.add(leaf);
            }
        }
        for(int i=leaves.size()-1;i>=0;i--){
            Leaf leaf=leaves.get(i);
            float previous=leaf.age;leaf.age+=dt;
            if(leaf.age<0)continue;
            if(previous<0)ripple(leaf.x,leaf.y,.48f);
            float drift=leaf.age*.38f+leaf.phase;
            leaf.x+=(leaf.vx+sin(drift)*1.35f)*dt;
            leaf.y+=(leaf.vy+cos(drift*.9f)*1.05f)*dt;
            leaf.angle+=sin(drift*.8f)*dt*.14f;
            leaf.fade=clamp(waterClearance(leaf.x,leaf.y)/19,0,1);
            if(leaf.fade<=0)leaves.remove(i);
        }
    }
    private void updateTurtle(float dt){
        Turtle t=turtle;t.fear=Math.max(0,t.fear-dt*.35f);t.wanderTime-=dt;t.motionTime-=dt;
        if(t.wanderTime<=0&&t.fear<=0){t.wander=wrap(t.angle+range(-.7f,.7f));t.wanderTime=range(5,10);}
        if(t.motionTime<=0){
            t.paddling=!t.paddling;
            t.motionTime=t.paddling?range(2.8f,4.8f):range(1.4f,2.5f);
        }
        if(t.target!=null&&!food.contains(t.target))t.target=null;
        float nearest=440;
        if(t.target==null){for(Food p:food){float d=hypot(p.x-t.x,p.y-t.y);if(p.age>1.6f&&d<nearest){nearest=d;t.target=p;}}}
        else nearest=hypot(t.target.x-t.x,t.target.y-t.y);
        // Opposite limbs supply two short pushes per cycle. With the limbs relaxed, momentum
        // eases into a glide so the turtle keeps moving across the bed between strokes.
        float thrust=.5f-.5f*cos(t.phase*2);
        float ax=cos(t.wander),ay=sin(t.wander);
        float wantedSpeed=t.paddling?13f+4f*thrust:7.5f;
        float wantedEffort=t.paddling?.86f:.16f;
        if(t.target!=null&&t.fear<=0){
            ax=(t.target.x-t.x)/Math.max(1,nearest)*2;ay=(t.target.y-t.y)/Math.max(1,nearest)*2;
            wantedSpeed=nearest<50?Math.max(5,nearest*.28f):16;wantedEffort=.9f;
            if(nearest<17){food.remove(t.target);t.target=null;t.eaten++;eaten++;ripple(t.x,t.y,.2f);}
        }
        if(t.fear>0){ax=cos(t.wander)*3;ay=sin(t.wander)*3;wantedSpeed=19;wantedEffort=1f;}
        float px=t.x+cos(t.angle)*88,py=t.y+sin(t.angle)*88;
        float clearance=waterClearance(px,py);
        if(clearance<130){
            float dx=waterClearance(px+24,py)-waterClearance(px-24,py);
            float dy=waterClearance(px,py+24)-waterClearance(px,py-24);
            float n=Math.max(1,hypot(dx,dy)),push=clamp((130-clearance)/28,0,7);
            ax+=dx/n*push;ay+=dy/n*push;
        }
        float delta=wrap((float)Math.atan2(ay,ax)-t.angle),turnLimit=.38f+t.fear*.15f;
        t.turn+=(clamp(delta*.8f,-turnLimit,turnLimit)-t.turn)*(1-(float)Math.exp(-dt*1.8f));
        wantedSpeed*=1-clamp(Math.abs(delta)/(float)Math.PI,0,1)*.55f;
        t.angle=wrap(t.angle+t.turn*dt);
        float response=t.paddling||t.target!=null||t.fear>0?2.4f:.78f;
        t.speed+=(wantedSpeed-t.speed)*(1-(float)Math.exp(-dt*response));
        float nx=t.x+cos(t.angle)*t.speed*dt,ny=t.y+sin(t.angle)*t.speed*dt;
        if(waterClearance(nx,ny)>=Math.min(56,waterClearance(t.x,t.y))){t.x=nx;t.y=ny;}
        t.effort+=(wantedEffort-t.effort)*(1-(float)Math.exp(-dt*1.8f));t.phase=(t.phase+dt*TAU*(.18f+t.effort*.62f))%TAU;
        t.pose.update(t);
    }
}
