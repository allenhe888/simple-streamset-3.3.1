package com.streamsets.datacollector.allenhedev;

import com.google.common.collect.ImmutableList;
import com.streamsets.pipeline.api.*;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@StageDef(
        version = 1,
        label = "Dev Processor",
        execution = {ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE},
        icon = "dev.png",
        recordsByRef = true,
        onlineHelpRefUrl = ""
)
//@ConfigGroups(value = KafkaOriginGroups.class)
@GenerateResourceBundle
public class DevProcessor implements Processor {

    @ConfigDef(
            required = false,
            type = ConfigDef.Type.MAP,
            defaultValue = "{}",
            label = "Props",
            description = "properties for dev",
            displayPosition = 10
    )
    public Map<String, String> props = new HashMap<>();
    private Processor.Context context;


    private AtomicLong batchCounter = new AtomicLong(0L);
    private Random random = new Random();
    private int processActRandom;
    public static final String KEY_INIT_ACTION_RANDOM = "dev.processor.init.action.random";



    @Override
    public List<ConfigIssue> init(Stage.Info info, Processor.Context context) {
        int actionRandom = 3;
        if(null !=props && props.containsKey(KEY_INIT_ACTION_RANDOM)){
            actionRandom  = Integer.parseInt(props.get(KEY_INIT_ACTION_RANDOM));
        }
        this.processActRandom = actionRandom + random.nextInt(actionRandom);


        this.context = context;
        return new ArrayList<>();
    }

    @Override
    public void process(Batch batch, BatchMaker batchMaker) throws StageException {
        doDevThing();

        justSendToNext(batch,batchMaker);
    }


    private void justSendToNext(Batch batch, BatchMaker batchMaker){
        Iterator<Record> records = batch.getRecords();
        while (records.hasNext()){
            Record record = records.next();
            batchMaker.addRecord(record);
        }
    }

    public static final String KEY_DESTORY_HANDLER_ENABLE = "dev.processor.destory.handler.enable";
    public static final String KEY_DESTORY_HANDLER_POLICY = "dev.processor.destory.handler.policy";
    public static final String KEY_DESCTORY_HANDLER_SLEEP_MILLIS = "dev.processor.destory.handler.sleep.millis";

    @Override
    public void destroy() {
        if(props!=null && props.containsKey(KEY_DESTORY_HANDLER_ENABLE)){
            String value = props.get(KEY_DESTORY_HANDLER_POLICY);
            if(value==null){
                throw new RuntimeException("主动异常测试: My DevProcessor.destroy()时,主动抛出 ");
            }else if(value.equalsIgnoreCase("throwexception")){
                throw new RuntimeException("主动异常测试: My DevProcessor.destroy()时,主动抛出 ");
            }else if(value.equalsIgnoreCase("sleep")){
                int sleepMillis = 1000 * 60 * 3;
                try {
                    String millisStr = props.get(KEY_DESCTORY_HANDLER_SLEEP_MILLIS);
                    if(millisStr!=null && !millisStr.isEmpty()){
                        sleepMillis = Integer.parseInt(millisStr);
                    }
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }else{
                throw new RuntimeException("主动异常测试: My DevProcessor.destroy()时,主动抛出 ");
            }
        }

    }




    public static final String KEY_PROCESS_HANDLER_SLEEP_ENABLE = "dev.processor.process.handler.sleep,enable";
    public static final String KEY_PROCESS_HANDLER_SLEEP_MILLIS = "dev.processor.process.handler.sleep.millis";

    public static final String KEY_PROCESS_HANDLER_ENABLE = "dev.processor.process.handler.enable";
    public static final String KEY_PROCESS_HANDLER_POLICY = "dev.processor.process.handler.policy";


    private void doDevThing() {
        if(props!=null && !props.isEmpty()){
            props.forEach((k,v)->{
                System.out.println(k+":"+v);
            });
        }
        long batchNum = batchCounter.incrementAndGet();

        if(props.containsKey(KEY_PROCESS_HANDLER_SLEEP_ENABLE) && props.get(KEY_PROCESS_HANDLER_SLEEP_ENABLE).equalsIgnoreCase("true")){
            int sleepMillis = 1000 * 60 * 1;
            try {
                String millisStr = props.get(KEY_PROCESS_HANDLER_SLEEP_MILLIS);
                if(millisStr!=null && !millisStr.isEmpty()){
                    sleepMillis = Integer.parseInt(millisStr);
                }
                awaitAndPrintPerSecond(sleepMillis);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(props!=null && props.containsKey(KEY_PROCESS_HANDLER_ENABLE)){
            if(batchNum>processActRandom){
                String value = props.get(KEY_PROCESS_HANDLER_POLICY);
                if(value==null){
                    throw new RuntimeException("主动异常测试: My DevProcessor.doDevThing()时,主动抛出 ");
                }else if(value.equalsIgnoreCase("throwexception")){
                    throw new RuntimeException("主动异常测试: My DevProcessor.doDevThing()时,主动抛出 ");
                }else if(value.equalsIgnoreCase("sleep")){
                    int sleepMillis = 1000 * 60 * 3;
                    try {
                        String millisStr = props.get(KEY_DESCTORY_HANDLER_SLEEP_MILLIS);
                        if(millisStr!=null && !millisStr.isEmpty()){
                            sleepMillis = Integer.parseInt(millisStr);
                        }
                        awaitAndPrintPerSecond(sleepMillis);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }else{
                    throw new RuntimeException("主动异常测试: My DevProcessor.destroy()时,主动抛出 ");
                }
            }


        }


    }

    private void awaitAndPrintPerSecond(long sleepMillis){
        long remaining = sleepMillis;
        long start = System.currentTimeMillis();
         do{
            System.out.println("继续Sleep...... 剩余秒数: "+remaining);
             long toSleepMillis= remaining> 1000? 1000:remaining;
             try {
                 Thread.sleep(toSleepMillis);
             } catch (InterruptedException e) {e.printStackTrace();}
             long usedTime = System.currentTimeMillis() - start;
             remaining = sleepMillis - usedTime;
         }while(remaining>0);



    }




    @Test
    public void testMemoryGenerator_TrashSucceed(){

        ImmutableList<Map<String, String>> props = ImmutableList.<Map<String, String>>builder()
                .add(createKeyValueMapEntry(DevProcessor.KEY_DESTORY_HANDLER_ENABLE, "true"))
                .add(createKeyValueMapEntry(DevProcessor.KEY_DESTORY_HANDLER_POLICY, "throwexception"))
                .add(createKeyValueMapEntry(DevProcessor.KEY_PROCESS_HANDLER_SLEEP_ENABLE, "true"))
                .add(createKeyValueMapEntry(DevProcessor.KEY_PROCESS_HANDLER_SLEEP_MILLIS, "5000"))
                .add(createKeyValueMapEntry(DevProcessor.KEY_PROCESS_HANDLER_ENABLE, "true"))
                .add(createKeyValueMapEntry(DevProcessor.KEY_PROCESS_HANDLER_POLICY, "throwexception"))
                .add(createKeyValueMapEntry(DevProcessor.KEY_INIT_ACTION_RANDOM, "2"))
                .build();

        PipelineHelper testHelper =  PipelineHelper.builder()
                .createDSource(PipelineHelper.StageProvider.MemoryGeneratorSource.class,ImmutableList.of(
                        new Config("batchRecordSize", 20000)
                ).toArray(new Config[]{}))
                .addProcessor(DevProcessor.class,ImmutableList.of(
                        new Config("props", props)
                ).toArray(new Config[]{}))
                .addTarget(PipelineHelper.StageProvider.MyTarget.class)
                .buildStages();
        testHelper.runPipeline();

    }
    private Map<String, String> createKeyValueMapEntry(String key,String value){
        Map<String, String> kv = new HashMap<>();
        kv.put("key", key);
        kv.put("value", value);
        return kv;
    }

}
