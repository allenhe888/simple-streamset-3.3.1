package com.streamsets.pipeline.stage.origin.kafka.testpipeline;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

public class StaticCounter {

    private StaticCounter() {
        this.arrayCounter = new ThreadLocal<ArrayCounter>();
    }

    private static StaticCounter instance=null;
    public static StaticCounter getInstance(){
        if(instance==null){
            synchronized (StaticCounter.class){
                if(instance==null) instance = new StaticCounter();
            }
        }
        return instance;
    }




    public static class IntKey{
        public  static final int BATCH_TARGET_USED_TIME = 0;


        public  static final int STAGE_MEMORY_Create_Record = 1;
        public  static final int STAGE_MEMORY_Batch_Maker = 2;

        public  static final int STAGE_TRASH_DURATION = 3;
        public  static final int BATCH_TARGET_RECORD_SIZE = 4;

        public  static final int STAGE_LASTAPPEND_Filter_Records = 5;
        public  static final int STAGE_LASTAPPEND_Process_Append = 6;
        public  static final int STAGE_LASTAPPEND_BatchMaker = 7;


    }


    private ThreadLocal<ArrayCounter> arrayCounter;
    private ArrayCounter getArrayCounter(final int initSize){
        ArrayCounter keyCounter = this.arrayCounter.get();
        if(keyCounter==null){
            synchronized (this){
                keyCounter = new ArrayCounter(initSize);
                this.arrayCounter.set(keyCounter);
            }
        }
        return keyCounter;
    }


    public static ArrayCounter getTLArrayCounter(){
        return getInstance().getArrayCounter(10);
    }
    public static ArrayCounter createTLArrayCounter(final int initSize){
        return getInstance().getArrayCounter(initSize);
    }

    public class ArrayCounter{
        private final long ONE = 0L;
        private int size;
        private ArrayCounter(int initSize) {
            this.size = initSize;
            this.keyCounter = new AtomicLong[initSize];
        }
        private AtomicLong[] keyCounter;

        public boolean isCounterExists(int index){
            return this.size>index && null != keyCounter[index] ;
        }

        public long getCounterByKey(int index){
            return this.keyCounter[index].get();
        }

        public long addAndGet(int key,final long delta){
            return this.keyCounter[key].addAndGet(delta);
        }

        private void ensureArraySize(int key){
            if(key>= this.size){
                synchronized (this){
                    int newSize= (int)(this.size * 1.75);
                    AtomicLong[] newCounter = new AtomicLong[newSize];
                    System.arraycopy(this.keyCounter,0,newCounter,0,this.size);
                    this.keyCounter = newCounter;
                    this.size= newSize;
                }
            }
        }

        public long initCounterForKey(int key){
            ensureArraySize(key);
            this.keyCounter[key] = new AtomicLong(ONE);
            return this.keyCounter[key].get();
        }

        @Override
        public String toString() {
            return "ArrayCounter{" +
                    "size=" + size +
                    ", keyCounter=" + Arrays.toString(keyCounter) +
                    '}';
        }

        public void printQPSAndInit() {
//            System.out.println(toString());

            long batchUsedTime = getCounterByKey(IntKey.BATCH_TARGET_USED_TIME);
            long batchSize = getCounterByKey(IntKey.BATCH_TARGET_RECORD_SIZE);
            System.out.printf("\n\n\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:100 , QPS: %f } ","batchUsedTime",batchUsedTime,divide(batchUsedTime,1000000,3),divide(batchSize * 1000 * 100,batchUsedTime,3));
            if(isCounterExists(IntKey.STAGE_TRASH_DURATION)){
                long trashDuration = getCounterByKey(IntKey.STAGE_TRASH_DURATION);
                System.out.printf("\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:%f }","trashDuration",trashDuration,divide(trashDuration,1000000,3),divide(trashDuration,batchUsedTime,5)*100);
            }

            if(isCounterExists(IntKey.STAGE_MEMORY_Create_Record)){
                long memoryCreateRecord = getCounterByKey(IntKey.STAGE_MEMORY_Create_Record);
                long memoryBatchMaker = getCounterByKey(IntKey.STAGE_MEMORY_Batch_Maker);
                System.out.printf("\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:%f } ","memoryCreateRecord",memoryCreateRecord,divide(memoryCreateRecord,1000000,3),divide(memoryCreateRecord,batchUsedTime,5)*100);
                System.out.printf("\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:%f }","memoryBatchMaker",memoryBatchMaker,divide(memoryBatchMaker,1000000,3),divide(memoryBatchMaker,batchUsedTime,5)*100);
            }



            if(isCounterExists(IntKey.STAGE_LASTAPPEND_Filter_Records)){
                long lastAppendFilterRecords = getCounterByKey(IntKey.STAGE_LASTAPPEND_Filter_Records);
                long lastAppendProcessAppend = getCounterByKey(IntKey.STAGE_LASTAPPEND_Process_Append);
                long lastAppendBatchMarker = getCounterByKey(IntKey.STAGE_LASTAPPEND_BatchMaker);
                System.out.printf("\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:%f }","lastAppendFilterRecords",lastAppendFilterRecords,divide(lastAppendFilterRecords,1000000,3),divide(lastAppendFilterRecords,batchUsedTime,5)*100);
                System.out.printf("\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:%f }","lastAppendProcessAppend",lastAppendProcessAppend,divide(lastAppendProcessAppend,1000000,3),divide(lastAppendProcessAppend,batchUsedTime,5)*100);
                System.out.printf("\n%20s用时:{纳秒=%10d , 毫秒=%f, 百分占比:%f }","lastAppendBatchMarker",lastAppendBatchMarker,divide(lastAppendBatchMarker,1000000,3),divide(lastAppendBatchMarker,batchUsedTime,5)*100);
            }



            for(AtomicLong ele:keyCounter){
                if(null !=ele){
                    ele.set(0L);
                }
            }
//            System.err.println();
        }

        public void destroy() {
            if(this.keyCounter!=null){
                keyCounter = null;
            }

        }
    }

    public static void destroy() {
        StaticCounter instance = getInstance();
        instance.arrayCounter.get().destroy();
        if(instance.arrayCounter!=null){
            instance.arrayCounter.remove();
            instance.arrayCounter = null;
        }
    }

    public static double divide(double num,double b,int newScale){
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


}
