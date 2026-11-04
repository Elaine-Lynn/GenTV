package com.gentv.tv.pond;

import java.util.Random;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Render-thread fishing state machine. Time is real foreground time, independent of pond tempo. */
public final class FishingSession {
    public enum Stage { DOZING, WAKING, CASTING, WAITING, REELING, LANDED, RESULT }
    public static final class Catch {
        public final String id,name;
        public final int grams,merit,skin;
        public Catch(String id,String name,int grams,int merit,int skin){this.id=id;this.name=name;this.grams=grams;this.merit=merit;this.skin=skin;}
    }
    private final Random random;
    public volatile Stage stage=Stage.DOZING;
    public volatile float age;
    public volatile Catch result;
    public float sessionSeconds,elapsedSeconds;
    public float bobberX=580,bobberY=470;
    private static final float BITE_RADIUS=24, BITE_OFFSET_Y=32;
    private final Set<PondWorld.Fish> inside=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<PondWorld.Fish> present=Collections.newSetFromMap(new IdentityHashMap<>());

    public FishingSession(long seed){random=new Random(seed);}
    public boolean start(){
        if(stage!=Stage.DOZING)return false;
        result=null;age=0;elapsedSeconds=0;sessionSeconds=300+random.nextFloat()*600;
        bobberX=500+random.nextFloat()*150;bobberY=410+random.nextFloat()*110;
        stage=Stage.WAKING;return true;
    }
    public float duration(){switch(stage){case WAKING:return 1.1f;case CASTING:return 1.5f;case REELING:return 1.8f;case LANDED:return 1.3f;default:return Float.POSITIVE_INFINITY;}}
    public void update(float dt,PondWorld world){
        if(stage==Stage.DOZING)return;
        float step=Math.max(0,Math.min(dt,.2f));elapsedSeconds+=step;
        if(elapsedSeconds>=sessionSeconds&&stage!=Stage.REELING&&stage!=Stage.LANDED&&stage!=Stage.RESULT){stage=Stage.DOZING;age=0;result=null;inside.clear();return;}
        if(stage==Stage.RESULT)return;
        age+=step;
        if(stage==Stage.WAITING){checkEntries(world);return;}
        while(age>=duration()){
            age-=duration();
            switch(stage){
                case WAKING:stage=Stage.CASTING;break;
                case CASTING:
                    stage=Stage.WAITING;inside.clear();
                    for(PondWorld.Fish fish:world.fish)if(inZone(fish))inside.add(fish);
                    break;
                case REELING:stage=Stage.LANDED;break;
                case LANDED:stage=Stage.RESULT;age=0;break;
                default:return;
            }
        }
    }
    private boolean inZone(PondWorld.Fish fish){
        float dx=fish.x-bobberX,dy=fish.y-(bobberY+BITE_OFFSET_Y);
        return dx*dx+dy*dy<=BITE_RADIUS*BITE_RADIUS;
    }
    private void checkEntries(PondWorld world){
        present.clear();
        for(int i=0;i<world.fish.size();i++){
            PondWorld.Fish fish=world.fish.get(i);
            if(!inZone(fish))continue;
            present.add(fish);
            if(inside.contains(fish)||!random.nextBoolean())continue;
            int merit=fish.skin==2?3:fish.skin==0||fish.skin==1?2:1;
            String name=merit==1?"银鳞鲫鱼":merit==2?"赤尾鲤鱼":"金鳞锦鲤";
            int grams=Math.round(180+(fish.length-69)/36*3320);
            result=new Catch(Long.toHexString(random.nextLong()),name,grams,merit,fish.skin);
            world.replaceCaughtFish(i);
            stage=Stage.REELING;age=0;
            inside.clear();present.clear();return;
        }
        inside.clear();inside.addAll(present);present.clear();
    }
    public void finish(){if(stage==Stage.RESULT){stage=Stage.CASTING;age=0;result=null;}}
}
