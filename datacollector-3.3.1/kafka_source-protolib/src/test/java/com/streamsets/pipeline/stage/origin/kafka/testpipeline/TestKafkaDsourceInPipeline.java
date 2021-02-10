package com.streamsets.pipeline.stage.origin.kafka.testpipeline;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.streamsets.datacollector.record.RecordImpl;
import com.streamsets.pipeline.api.base.BaseSource;
import com.streamsets.pipeline.kafka.api.KafkaOriginGroups;
import com.streamsets.pipeline.lib.kafka.KafkaConstants;
import com.streamsets.pipeline.stage.origin.kafka.KafkaConfigBean;
import com.streamsets.pipeline.stage.origin.kafka.KafkaDSource;
import com.streamsets.pipeline.stage.origin.kafka.randomdatagenerator.RandomDataGeneratorSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TestKafkaDsourceInPipeline {

    @StageDef(
            version = 1,
            label = "MemoryGeneratorSource Label",
//            services = @ServiceDependency(service = Runnable.class),
            recordsByRef = true,
            onlineHelpRefUrl = ""
    )
    public static class MemoryGeneratorSource extends BaseSource implements ErrorListener {

        @ConfigDef(
                label = "StopAfterFirstBatch",
                type = ConfigDef.Type.BOOLEAN,
                defaultValue = "false",
                required = true
        )
        public boolean stopAfterFixedBatch;

        @ConfigDef(
                label = "batchRecordSize",
                type = ConfigDef.Type.NUMBER,
                defaultValue = "100000",
                required = true
        )
        public int batchRecordSize;

        private Source.Context context;
        private AtomicInteger batchCount = new AtomicInteger(10);
        private final AtomicInteger batchNum =  new AtomicInteger(10);
        private final AtomicInteger sizePerBatch =  new AtomicInteger(10000);
        private  List<Field> fields;
        private  List<Map<String, Object>> dataSamples;
        private final Random random = new Random();
        private AtomicLong usedNanoTimer = new AtomicLong(0L);
        private String stageInstanceName;
        @Override
        public List<Stage.ConfigIssue> init(Stage.Info info, Source.Context context) {
            this.context = context;
            this.fields = PipelineHelper.DataGenerator.getFields();
            this.dataSamples= PipelineHelper.DataGenerator.generateEnosRecord();
            System.err.println("\t Stage01.Source. init() context="+context);
            this.usedNanoTimer = new AtomicLong(0L);
            this.stageInstanceName = context.getStageInfo().getInstanceName();
            return new ArrayList<>();
        }

        private long consumerReadStart=0L;
        private long descRecordStart=0L;
        private long batchMakerStart;

        @Override
        public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
//            return produceImplByOrigin(lastSourceOffset,maxBatchSize,batchMaker);
            return produceImplByNotClone(lastSourceOffset,maxBatchSize,batchMaker);
        }

        private String produceImplByNotClone(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
            for(int i=0;i<batchRecordSize;i++){
                consumerReadStart = System.nanoTime();
                Record record = context.createRecord(this.stageInstanceName);
                RecordImpl recordImpl = (RecordImpl) record;
                recordImpl.setInitialRecord(false);
                recordImpl.getHeader().setSourceRecord(record);
                Map<String, Object> sample = dataSamples.get(random.nextInt(dataSamples.size()));
                Map<String, Field> root = new ConcurrentHashMap<>();
                root.put("assetId",Field.create(Field.Type.STRING,sample.get("assetId")));
                root.put("pointId",Field.create(Field.Type.STRING,sample.get("pointId")));
                root.put("orgId",Field.create(Field.Type.STRING,sample.get("orgId")));
                root.put("modelId",Field.create(Field.Type.STRING,sample.get("modelId")));
                root.put("modelIdPath",Field.create(Field.Type.STRING,sample.get("modelIdPath")));
                root.put("time",Field.create(Field.Type.LONG,sample.get("time")));
                try{
                    root.put("value", PipelineHelper.DataGenerator.objectToField(sample.get("value")));
                }catch (Exception e){
                    root.put("value",Field.create(Field.Type.STRING,sample.get("value").toString()));
                }
                try{
                    root.put("attr", PipelineHelper.DataGenerator.objectToField(sample.get("attr")));
                }catch (Exception e){
                    root.put("attr",Field.create(Field.Type.STRING,sample.get("attr").toString()));
                }

                record.set(Field.create(root));
                StaticCounter.ArrayCounter counter = StaticCounter.getTLArrayCounter();
                counter.addAndGet(StaticCounter.IntKey.STAGE_MEMORY_Create_Record,System.nanoTime() -consumerReadStart);


                this.batchMakerStart = System.nanoTime();
                batchMaker.addRecord(record);
                counter.addAndGet(StaticCounter.IntKey.STAGE_MEMORY_Batch_Maker,System.nanoTime() - this.batchMakerStart);
            }

            if(stopAfterFixedBatch){
                if( batchCount.incrementAndGet() > batchNum.get()){
                    System.err.println("\t\t\t\t\t  MySource.produce 运行批次结束, 将返回 offset=null ");
                    return null;
                }
                return null;
            }

            return String.valueOf(sizePerBatch.get() * batchCount.get());
        }

        private String produceImplByNotClone2(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
            for(int i=0;i<batchRecordSize;i++){
//                consumerReadStart = System.nanoTime();
                Record record = context.createRecord(this.stageInstanceName);
                RecordImpl recordImpl = (RecordImpl) record;
                recordImpl.setInitialRecord(false);
                recordImpl.getHeader().setSourceRecord(record);
                Field field = fields.get(random.nextInt(fields.size()));
                record.set(field);
//                StaticCounter.getInstance().addThreadTimeAndBatchCount(StaticCounter.Key.STAGE_KAFKA_CONSUMER_READ,System.nanoTime() -consumerReadStart);

//                descRecordStart = System.nanoTime();
                batchMaker.addRecord(record);
//                StaticCounter.getInstance().addThreadTimeAndBatchCount(StaticCounter.Key.STAGE_KAFKA_DESC_RECORD,System.nanoTime() -descRecordStart);
            }
            if(stopAfterFixedBatch){
                if( batchCount.incrementAndGet() > batchNum.get()){
                    System.err.println("\t\t\t\t\t  MySource.produce 运行批次结束, 将返回 offset=null ");
                    return null;
                }
                return null;
            }
            return String.valueOf(sizePerBatch.get() * batchCount.get());
        }



        @Override
        public void destroy() {}
        @Override
        public void errorNotification(Throwable throwable) {}
    }


    @StageDef(
            version = 1,
            label = "TestProcessor Label",
//            services = @ServiceDependency(service = Runnable.class),
//            recordsByRef = true,
            onlineHelpRefUrl = ""
    )
    public static class TestProcessor implements Processor {
        private Context context;
        @Override
        public List<ConfigIssue> init(Info info, Context context) {
            Info stageInfo = context.getStageInfo();
            this.context = context;
            System.err.println("\t Stage02.Processor. init() context="+context);
            return new ArrayList<>();
        }

        @Override
        public void process(Batch batch, BatchMaker batchMaker) throws StageException {
            List<Record> records = ImmutableList.copyOf(batch.getRecords());
            for(Record record: records){
                record.get().getValueAsMap().put("processor", Field.create("process"));
                for(String lane:context.getOutputLanes()){
                    batchMaker.addRecord(record,lane);
                }
            }
            System.err.println("\t\t\t\t\t 6.  自开发算子.MyProcessor.process  ");
        }

        @Override
        public void destroy() {}
    }


    @Test
    public void testMemoryGenerator2TrashSucceed(){
        PipelineHelper testHelper =  PipelineHelper.builder()
                .createDSource(MemoryGeneratorSource.class,ImmutableList.of(
                        new Config("batchRecordSize", 20000)
                ).toArray(new Config[]{}))
                .addProcessor(TestProcessor.class)
                .addTarget(PipelineHelper.StageProvider.MyTarget.class)
                .buildStages();

        testHelper.runPipeline();

    }




    @Test
    public void testRandomDataGenerator2Trash(){
        RandomDataGeneratorSource.DataGeneratorConfig dataGeneratorConfig = new RandomDataGeneratorSource.DataGeneratorConfig();
        dataGeneratorConfig.field= "assetId";

        PipelineHelper testHelper =  PipelineHelper.builder()
                .createDSource(RandomDataGeneratorSource.class,ImmutableList.of(
                        new Config("dataGenConfigs", ImmutableList.of(
                                ImmutableMap.of("field","orgId","type","STRING","precision",10,"scale",2),
                                ImmutableMap.of("field","value","type","DOUBLE","precision",10,"scale",2)
                        )),
                        new Config("delay", 0),
                        new Config("batchSize", 20000)
                ).toArray(new Config[]{}))
//                .addProcessor(TestProcessor.class)
                .addTarget(PipelineHelper.StageProvider.MyTarget.class)
                .buildStages();


        testHelper.runPipeline();

    }










    @Test
    public void testSimpleKafkaDSourceToTrash(){
        Map<String, String> kafkaProps = new HashMap<>();
        kafkaProps.put("key", ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        kafkaProps.put("value", "earliest");
        ImmutableList<Map<String, String>> kafkaConfigs = ImmutableList.<Map<String, String>>builder().add(kafkaProps).build();

        PipelineHelper testHelper =  PipelineHelper.builder()
                .createDSource(KafkaDSource.class,ImmutableList.of(
                        new Config("kafkaConfigBean.metadataBrokerList", "ldsver51:9092"),
                        new Config("kafkaConfigBean.zookeeperConnect", "ldsver51:2181"),
                        new Config("kafkaConfigBean.consumerGroup", "idea-sdc-kafkaDSource_02"),
                        new Config("kafkaConfigBean.topic", "testStringPerf"),
                        new Config("kafkaConfigBean.kafkaConsumerConfigs", kafkaConfigs),
                        new Config("kafkaConfigBean.dataFormat", "JSON")
                ).toArray(new Config[]{}))
                .addTarget(PipelineHelper.StageProvider.MyTarget.class)
                .buildStages();

        testHelper.runPipeline();

    }




    @StageDef(
            version = 1,
            label = "My KafkaSource",
            execution = {ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.CLUSTER_MESOS_STREAMING, ExecutionMode.STANDALONE},
            icon = "dev.png",
            recordsByRef = true,
            onlineHelpRefUrl = ""
    )
    @ConfigGroups(value = KafkaOriginGroups.class)
    @GenerateResourceBundle
    public static class MyKafkaSource extends BaseSource implements ErrorListener {

        @ConfigDefBean
        public KafkaConfigBean kafkaConfigBean;

        private Source.Context context;
        private KafkaConsumer<String,String> consumer;

        @Override
        public List<Stage.ConfigIssue> init(Stage.Info info, Source.Context context) {
            this.context = context;
            String bootStrapServers = "ldsver51:9092";
            if(null != kafkaConfigBean.metadataBrokerList && !kafkaConfigBean.metadataBrokerList.isEmpty() ){
                bootStrapServers = kafkaConfigBean.metadataBrokerList;
            }
            String consumerGroup = "my-kafkadsource-gid";
            if(null != kafkaConfigBean.consumerGroup && !kafkaConfigBean.consumerGroup.isEmpty() ){
                consumerGroup = kafkaConfigBean.consumerGroup;
            }
            String topic = "testEnosRecord";
            if(null != kafkaConfigBean.topic && !kafkaConfigBean.topic.isEmpty() ){
                topic = kafkaConfigBean.topic;
            }

            Properties props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServers);
            props.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup);
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            if (context.isPreview()) {
                props.setProperty(KafkaConstants.AUTO_OFFSET_RESET_CONFIG, KafkaConstants.AUTO_OFFSET_RESET_PREVIEW_VALUE);
            }

            Map<String, String> kafkaConsumerConfigs = kafkaConfigBean.kafkaConsumerConfigs;
            if(null !=kafkaConsumerConfigs && !kafkaConsumerConfigs.isEmpty()){
                props.putAll(kafkaConsumerConfigs);
            }
            consumer= new KafkaConsumer<>(props);

            consumer.subscribe(Collections.singleton(topic));
            consumer.poll(0);
            Set<TopicPartition> assignment=consumer.assignment();
            while (null ==assignment || assignment.isEmpty()){
                consumer.poll(0);
                assignment=consumer.assignment();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            consumer.seekToBeginning(assignment);
            return new ArrayList<>();
        }

        private ObjectMapper mapper = new ObjectMapper();
        private HashMap<String, Object> map = new HashMap<>();
        private final String sourceId = this.getClass().getSimpleName();
        private long consumerReadStart=0L;
        private long descRecordStart=0L;
        @Override
        public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
//            return doProduceByJSONToField(lastSourceOffset,maxBatchSize,batchMaker);
            return doProduceByDirectNewField(lastSourceOffset,maxBatchSize,batchMaker);
        }

        private String doProduceByJSONToField(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker)throws StageException {
            int recordCounter = 0;
            int batchSize = kafkaConfigBean.maxBatchSize > maxBatchSize ? maxBatchSize : kafkaConfigBean.maxBatchSize;
            long startTime = System.currentTimeMillis();
            while (recordCounter < batchSize && (startTime + kafkaConfigBean.maxWaitTime) > System.currentTimeMillis()) {
//                consumerReadStart = System.nanoTime();
                ConsumerRecords<String, String> poll = consumer.poll(500);
//                StaticCounter.getInstance().addThreadTimeAndBatchCount(StaticCounter.Key.STAGE_KAFKA_CONSUMER_READ,System.nanoTime() -consumerReadStart);
                for(ConsumerRecord<String, String> data:poll){
                    String value = data.value();
                    if(null !=value && !value.isEmpty()){
                        try {
                            HashMap<String, Object> json = mapper.readValue(value, map.getClass());
                            Field field = PipelineHelper.DataGenerator.objectToField(json);
                            Record record = context.createRecord(sourceId);
                            record.set(field);
                            batchMaker.addRecord(record);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    recordCounter ++;
                }
            }

            return String.valueOf(recordCounter);
        }

        private String doProduceByDirectNewField(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker)throws StageException {
            int recordCounter = 0;
            int batchSize = kafkaConfigBean.maxBatchSize > maxBatchSize ? maxBatchSize : kafkaConfigBean.maxBatchSize;
            long startTime = System.currentTimeMillis();
            while (recordCounter < batchSize && (startTime + kafkaConfigBean.maxWaitTime) > System.currentTimeMillis()) {
                ConsumerRecords<String, String> poll = consumer.poll(500);
                for(ConsumerRecord<String, String> data:poll){
                    String value = data.value();
                    if(null !=value && !value.isEmpty()){
                        HashMap<String, Field> rootMap = new HashMap<>();
                        try {
                            JSONObject jsonObj = JSON.parseObject(value);
                            for(Map.Entry<String, Object> entry: jsonObj.entrySet()){
                                switch (entry.getKey()){
                                    case "assetId": rootMap.put("assetId",Field.create(Field.Type.STRING,(String)entry.getValue()));break;
                                    case "modelId": rootMap.put("modelId",Field.create(Field.Type.STRING,(String)entry.getValue()));break;
                                    case "pointId": rootMap.put("pointId",Field.create(Field.Type.STRING,(String)entry.getValue()));break;
                                    case "orgId": rootMap.put("orgId",Field.create(Field.Type.STRING,(String)entry.getValue()));break;
                                    case "time": rootMap.put("time",Field.create(Field.Type.LONG,(Long)entry.getValue()));break;
                                    case "value": rootMap.put("assetId",PipelineHelper.DataGenerator.objectToField(entry.getValue()));break;
                                    default: break;
                                }
                            }
                            Record record = context.createRecord(sourceId);
                            record.set(Field.create(rootMap));

                            RecordImpl recordImpl = (RecordImpl) record;
                            recordImpl.setInitialRecord(false);
                            recordImpl.getHeader().setSourceRecord(record);
                            batchMaker.addRecord(record);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    recordCounter ++;
                }
            }

            return String.valueOf(recordCounter);
        }


        @Override
        public void destroy() {
            consumer.close();
        }
        @Override
        public void errorNotification(Throwable throwable) {}

    }
    @Test
    public void testMyKafkaDSourceToTrash(){
        Map<String, String> kafkaProps = new HashMap<>();
        kafkaProps.put("key", ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        kafkaProps.put("value", "earliest");
        ImmutableList<Map<String, String>> kafkaConfigs = ImmutableList.<Map<String, String>>builder().add(kafkaProps).build();

        PipelineHelper testHelper =  PipelineHelper.builder()
                .createDSource(MyKafkaSource.class,ImmutableList.of(
                        new Config("kafkaConfigBean.metadataBrokerList", "ldsver51:9092"),
                        new Config("kafkaConfigBean.zookeeperConnect", "ldsver51:2181"),
                        new Config("kafkaConfigBean.consumerGroup", "idea-sdc-my-kafkaDSource"),
                        new Config("kafkaConfigBean.topic", "testStringPerf"),
                        new Config("kafkaConfigBean.maxBatchSize", 20000),
                        new Config("kafkaConfigBean.kafkaConsumerConfigs", kafkaConfigs),
                        new Config("kafkaConfigBean.dataFormat", "JSON")
                ).toArray(new Config[]{}))
                .addTarget(PipelineHelper.StageProvider.MyTarget.class)
                .buildStages();

        testHelper.runPipeline();

    }


}
