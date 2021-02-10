package com.streamsets.datacollector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class StaticCounter {

    private static StaticCounter instance=null;
    public static StaticCounter getInstance(){
        if(instance==null){
            synchronized (StaticCounter.class){
                if(instance==null) instance = new StaticCounter();
            }
        }
        return instance;
    }


    private ThreadLocal<AtomicLong> threadLocalCounter= new ThreadLocal<>();
    public void createNewThreadCounter(){
        synchronized (this){
            if(threadLocalCounter==null){
                this.threadLocalCounter = new ThreadLocal<>();
            }else if(this.threadLocalCounter.get()!=null){
                this.threadLocalCounter.remove();
            }
            this.threadLocalCounter.set(new AtomicLong(0));
        }
    }
    public Long getThreadLocalValue(){
        if(threadLocalCounter!=null && this.threadLocalCounter.get()!=null){
            return this.threadLocalCounter.get().get();
        }
        return 0L;
    }
    public void removeThreadLocalCounter(){
        if(threadLocalCounter!=null){
            this.threadLocalCounter.remove();
        }
    }
    public void increaseTreadCounter(){
        synchronized (this){
            if(threadLocalCounter==null || this.threadLocalCounter.get()==null){
                createNewThreadCounter();
            }
        }
        this.threadLocalCounter.get().incrementAndGet();
    }


    private ThreadLocal<List<Long>> listThreadLocal= new ThreadLocal<>();
    public void createListThreadLocal(){
        synchronized (this){
            if(listThreadLocal==null){
                this.listThreadLocal = new ThreadLocal<>();
            }else if(this.listThreadLocal.get()!=null){
                this.listThreadLocal.get().clear();
                this.listThreadLocal.remove();
            }
            this.listThreadLocal.set(new LinkedList<Long>());
        }
    }
    public List<Long> getListThreadLocalValues(){
        if(listThreadLocal!=null && this.listThreadLocal.get()!=null){
            return this.listThreadLocal.get();
        }
        return Collections.emptyList();
    }
    public void removeListThreadLocal(){
        if(listThreadLocal!=null){
            if(listThreadLocal.get()!=null){
                listThreadLocal.get().clear();
            }
            this.listThreadLocal.remove();
        }
    }
    public Boolean addValueToListTreadLocal(Long ele){
        synchronized (this){
            if(listThreadLocal==null){
                createListThreadLocal();
            }else if(this.listThreadLocal.get()==null){
                createListThreadLocal();
            }
            List<Long> list = this.listThreadLocal.get();
            return list.add(ele);
        }
    }


    private ThreadLocal<AtomicInteger> threadBatchCounter= new ThreadLocal<>();
    private ThreadLocal<Map<String, AtomicLong>> keyCounter = new ThreadLocal<>();
    private ThreadLocal<AtomicLong> threadTimeCounter= new ThreadLocal<>();
    public Long addThreadTimeAndBatchCount(final String key,final Long ele){
        Map<String,AtomicLong> counter = keyCounter.get();
        if(counter==null){
            synchronized (this){
                counter = new ConcurrentHashMap<>();
                keyCounter.set(counter);
            }
        }
        AtomicLong num = counter.getOrDefault(key,new AtomicLong());
        counter.put(key,num);
        return num.addAndGet(ele);
    }

    public void printTimeAndClean() {
        Map<String, AtomicLong> keyCounter = this.keyCounter.get();

        if(keyCounter!=null && !keyCounter.isEmpty()){
            long oneBatch = keyCounter.get(Key.PIPELINE_RUN_ONE_BATCH_KEY).get();
            long runSource = keyCounter.get(Key.PIPELINE_RUN_SOURCE_KEY).get();
            long runPros = keyCounter.get(Key.PIPELINE_RUN_PROCESSORS_KEY).get();
            if(keyCounter.containsKey(Key.PIPELINE_Batch_Maker_AllClone) && keyCounter.containsKey(Key.PIPELINE_Maker_InitClone)){
                long batchMarker = keyCounter.get(Key.PIPELINE_Batch_Maker_AllClone).get();
                long initClone = keyCounter.get(Key.PIPELINE_Maker_InitClone).get();
//                long consumerRead = keyCounter.get(Key.STAGE_KAFKA_CONSUMER_READ).get();
//                long descRecord = keyCounter.get(Key.STAGE_KAFKA_DESC_RECORD).get();

                if(batchMarker >0){
                    System.out.printf("总用时 %f: " +
                                    "{ SourceRunTime: %f (占比%f ) ,  " +
                                    "Processors &Targes: %f (占比%f) ," +
                                    " 其中BatchMaker共耗时: %f (占比%f, 其中initClone占比:%f )  } \n",
                            divide(oneBatch,1000000,3),
                            divide(runSource,1000000,3) ,divide(runSource,oneBatch,6)*100,
                            divide(runPros,1000000,3) ,divide(runPros,oneBatch,6)*100,
                            divide(batchMarker,1000000,3) ,divide(batchMarker,oneBatch,3)*100,(divide(initClone,batchMarker,3)*100 )
                    );

//                    System.out.printf("Source用时分析: { consumerRead用时: %f (占比%f ) ,   descRecord用时: %f (占比%f ) } \n\n ",
//                            divide(consumerRead,1000000,3) ,divide(consumerRead,runSource,6)*100,
//                            divide(descRecord,1000000,3) ,divide(descRecord,runSource,6)*100
//                    );

                }

            }

            keyCounter.forEach((k,v)->{
                v.set(0L);
            });

        }


    }


    private double divide(double num,double b,int newScale){
        BigDecimal result = BigDecimal.valueOf(num).divide(BigDecimal.valueOf(b),50, RoundingMode.HALF_UP);
        int scale = result.scale();
        double value= result.doubleValue();
        if(scale > newScale){
            try{
                value=result.setScale(newScale,RoundingMode.HALF_UP).doubleValue();
            }catch (Exception e){}
        }
        return value;
    }

    public static class Key{
//        public  static final String MY_SOURCE_KEY = "MySourceKey";
//        public  static final String Stage_MySource_CreateRecord = "StageMySourceCreateRecord";
//        public  static final String Stage_MySource_MakeBatch = "StageMySourceMakeBatch";
//        public  static final String STAGE_MY_TARGET_KEY = "MyTargetKey";
//        public  static final String PIPELINE_PRODUCT_PER_RUNNING = "PipelinePerProductPipeRun";
public  static final String PIPELINE_RUN_ONE_BATCH_KEY = "PipelineRunOneBatch";
        public  static final String PIPELINE_RUN_SOURCE_KEY = "PipelineRunSource";
        public  static final String PIPELINE_RUN_PROCESSORS_KEY = "PipelineRunProcessors";

//        public  static final String PIPELINE_Maker_GetRecord = "PipelineMakerGetRecord";
        public  static final String PIPELINE_Maker_InitClone = "PipelineMakerInitClone";
        public  static final String PIPELINE_Batch_Maker_AllClone = "PipelineMakerLaneClone";


        // 各Stage的Key
        public  static final String STAGE_KAFKA_CONSUMER_READ = "StageKafkaConsumerRead";
        public  static final String STAGE_KAFKA_DESC_RECORD = "StageKafkaDescRecord";

    }

}
