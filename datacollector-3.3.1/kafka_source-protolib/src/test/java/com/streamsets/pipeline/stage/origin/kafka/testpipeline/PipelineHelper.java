package com.streamsets.pipeline.stage.origin.kafka.testpipeline;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.streamsets.datacollector.classpath.ClasspathValidatorResult;
import com.streamsets.datacollector.config.StageType;
import com.streamsets.datacollector.config.*;
import com.streamsets.datacollector.creation.PipelineConfigBean;
import com.streamsets.datacollector.definition.StageDefinitionExtractor;
import com.streamsets.datacollector.el.ElConstantDefinition;
import com.streamsets.datacollector.el.ElFunctionDefinition;
import com.streamsets.datacollector.execution.PipelineStatus;
import com.streamsets.datacollector.execution.SnapshotStore;
import com.streamsets.datacollector.execution.StateListener;
import com.streamsets.datacollector.execution.runner.common.*;
import com.streamsets.datacollector.execution.snapshot.cache.dagger.CacheSnapshotStoreModule;
import com.streamsets.datacollector.lineage.LineagePublisherTask;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.main.RuntimeModule;
import com.streamsets.datacollector.main.StandaloneRuntimeInfo;
import com.streamsets.datacollector.memory.MemoryUsageCollector;
import com.streamsets.datacollector.metrics.MetricsConfigurator;
import com.streamsets.datacollector.record.RecordImpl;
import com.streamsets.datacollector.runner.*;
import com.streamsets.datacollector.stagelibrary.StageLibraryTask;
import com.streamsets.datacollector.store.PipelineStoreTask;
import com.streamsets.datacollector.util.Configuration;
import com.streamsets.datacollector.util.PipelineDirectoryUtil;
import com.streamsets.pipeline.api.*;
import com.streamsets.pipeline.api.impl.Utils;
import dagger.ObjectGraph;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;


public class PipelineHelper {

    private final List<StageConfiguration> pipelineStages;
    private final List<StageDefinition> stageDefs;
    private final PipelineBuilder builder;
    private final ProductionPipeline productionPipeline;



    public PipelineHelper(List<StageConfiguration> pipelineStages, List<StageDefinition> stageDefs) {
        this.pipelineStages = pipelineStages;
        this.stageDefs = stageDefs;

        this.builder = new PipelineBuilder();
        // 0. 配置相关环境
        builder.setPipelineRunDirAndEnv();
        // 1. 配置Pipeline的运行参数 Configuration:
        Configuration pipelineConfig = builder.getPipelineDefaultConfiguration();
        // 2. 创建相应的pipelineRunner: ProductionPipelineRunner
        ProductionPipelineRunner pipelineRunner = builder.createProductionPipelineRunner(pipelineConfig);
        // 3. 创建 PipelineConfiguration pConf
        PipelineConfiguration pipelineConfiguration = builder.getPipelineConfiguration(this.pipelineStages);

        // 4. 创建 StageLibraryTask
        StageLibraryTask stageLibraryTask = builder.getStageLibraryTask(this.stageDefs);

        // 5. 创建 UserContext userContext

        UserContext userContext = builder.getRunnerUserContext();
        // 6. 创建 ProductionPipeline
        this.productionPipeline = builder.createProductionPipeline(userContext,pipelineConfiguration,pipelineConfig,stageLibraryTask,pipelineRunner);

    }





    @Test
    public void runPipeline(){
        // 7. 运行Pipeline
        try {
            productionPipeline.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            builder.closeResourcesAndCleanRuntimeDir();
        }
    }



    static class PipelineBuilder{
        private static final String sdcDataDir = "./target/sdcDataDir";

        private static final String PIPELINE_NAME = "myTestPipeline";
        private static final String REVISION = "1";
        private static final String SNAPSHOT_NAME = "mySnapshot";

        private static final String USER = "user";
        private static final String PIPELINE_REV = "0";
//        private static final String SNAPSHOT_ID = "Snapshot_info_id";
        private static final String SNAPSHOT_LABEL = "mySnapshotLabel";

        private static final long rateLimit =  -1L;
        StageProvider stageProvider = new StageProvider();

        private int maxBatchSize = 200000;

        public void setMaxBatchSize(int maxBatchSize){
            this.maxBatchSize = maxBatchSize;
        }


        public void setPipelineRunDirAndEnv() {
            cleanTheDataDirBeforeRun();

            // 初始化把 Instrumentation 塞给内存收集器;
            try {
                Class clazz = Class.forName("com.streamsets.pipeline.BootstrapMain");
//                Class clazz = Class.forName("com.streamsets.pipeline.stage.origin.kafka.testpipeline.TestKafkaDsourceInPipeline");
                java.lang.reflect.Field field = clazz.getDeclaredField("instrumentation");
                field.setAccessible(true);
                Instrumentation instrumentation = (Instrumentation) field.get(null);
                MemoryUsageCollector.initialize(instrumentation);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        private void cleanTheDataDirBeforeRun() {
            String dataDirKey = RuntimeModule.SDC_PROPERTY_PREFIX+ RuntimeInfo.DATA_DIR;
            System.out.println("创建目录前,  System.getProperty(dataDirKey)="+System.getProperty(dataDirKey));
            System.setProperty(dataDirKey, sdcDataDir);
            File dataDir = new File(System.getProperty(dataDirKey));
            try {
                FileUtils.deleteDirectory(dataDir);
                /* dataDir: 存于System.Properties中的 sdc.data.dir属性中， RuntimeInfo.getDataDir()
                    RuntimeInfo.getDataDir() 方法就是去 System.proerties中读取该属性的值；
                    runtimeInfo.init(){
                        this.id = this.getSdcId(this.getDataDir());{//RuntimeInfo.getDataDir()
                            return System.getProperty(this.propertyPrefix + ".data.dir", this.getRuntimeDir() + "/var");
                        }
                    }
                    测试时: createProductionPipelineRunner()方法中 在 runtimeInfo.init()
                    ProtoRunner 的类加载是static{}中 RuntimeInfo runtimeInfo = new StandaloneRuntimeInfo(), 应该是其init()时,触发了runtimeInfo.init()


                 */
                System.out.println("查看目录是否存在:  "+dataDir.exists());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void closeResourcesAndCleanRuntimeDir() {
            String dataDir = System.getProperty(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR);
            if(null !=dataDir && !dataDir.isEmpty()){
                File file = new File(dataDir);
                if(file.exists()){
                    try {
                        FileUtils.deleteDirectory(file);
                    } catch (IOException e) {
                        System.out.println("删除目录 "+dataDir+" 失败！");
                        e.printStackTrace();
                    }
                }
            }
            System.getProperties().remove(RuntimeModule.SDC_PROPERTY_PREFIX + RuntimeInfo.DATA_DIR);
        }


        private Configuration getPipelineDefaultConfiguration() {
            Configuration pipelineConfig = new Configuration();
            pipelineConfig.set("monitor.memory", true);
            pipelineConfig.set(Constants.MAX_BATCH_SIZE_KEY, this.maxBatchSize);
//            pipelineConfig.set("stage.conf_env.ou.id", "myOrgId");
//            pipelineConfig.set("stage.conf_testKey", "testValue");
            return pipelineConfig;
        }

        private ProductionPipelineRunner createProductionPipelineRunner(Configuration pipelineConfig) {

            // 定义RuntimeInfo: StandaloneRuntimeInfo
            MetricRegistry runtimeInfoMetrics = new MetricRegistry();
            MetricsConfigurator.registerJmxMetrics(runtimeInfoMetrics);
            RuntimeInfo runtimeInfo = new StandaloneRuntimeInfo(RuntimeModule.SDC_PROPERTY_PREFIX, runtimeInfoMetrics, Arrays.asList(getClass().getClassLoader()));
            runtimeInfo.init();

            MetricRegistry metrics = new MetricRegistry();
            SnapshotStore snapshotStore = getSnapshotStore();

            ThreadHealthReporter healthReporter = new ThreadHealthReporter(PIPELINE_NAME, PIPELINE_REV, metrics);
            // 创建 ProductionPipelineRunner
            ProductionPipelineRunner pipelineRunner = new ProductionPipelineRunner(PIPELINE_NAME, PIPELINE_REV, null, pipelineConfig, runtimeInfo, metrics, snapshotStore, healthReporter);

            // 为prodPipelineRunner设置 相关辅助类
            SourceOffsetTracker sourceOffsetTracker = getSourceOffsetTracker();
            pipelineRunner.setOffsetTracker(sourceOffsetTracker);

            pipelineRunner.setObserveRequests(new ArrayBlockingQueue<>(100, true));
            pipelineRunner.setMemoryLimitConfiguration(new MemoryLimitConfiguration());
            pipelineRunner.setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE);
            if (rateLimit > 0) {
                pipelineRunner.setRateLimit(rateLimit);
            }

            return pipelineRunner;
        }

        private SourceOffsetTracker getSourceOffsetTracker() {
            SourceOffsetTracker sourceOffsetTracker= new StageProvider.MySourceOffsetTrackerImpl(Collections.singletonMap(Source.POLL_SOURCE_OFFSET_KEY, "1"));
            return sourceOffsetTracker;
        }


        private List<List<StageOutput>> getSnapshotData() {
            List<List<StageOutput>> snapshotBatches = new ArrayList<>();
            snapshotBatches.add(createSnapshotData());
            snapshotBatches.add(createSnapshotData());
            return snapshotBatches;
        }

        private List<StageOutput> createSnapshotData() {
            String TEST_STRING = "TestSnapshotStore";
            String MIME = "application/octet-stream";
            List<StageOutput> snapshot = new ArrayList<>(2);

            List<Record> records1 = new ArrayList<>(2);

            Record r1 = new RecordImpl("s", "s:1", TEST_STRING.getBytes(), MIME);
            r1.set(Field.create(1));

            ((RecordImpl)r1).createTrackingId();
            ((RecordImpl)r1).createTrackingId();

            Record r2 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
            r2.set(Field.create(2));

            ((RecordImpl)r2).createTrackingId();
            ((RecordImpl)r2).createTrackingId();

            records1.add(r1);
            records1.add(r2);

            Map<String, List<Record>> so1 = new HashMap<>(1);
            so1.put("lane", records1);

            StageOutput s1 = new StageOutput("source", so1, new ErrorSink(), new EventSink());
            snapshot.add(s1);

            List<Record> records2 = new ArrayList<>(1);
            Record r3 = new RecordImpl("s", "s:3", TEST_STRING.getBytes(), MIME);
            r3.set(Field.create(1));

            ((RecordImpl)r3).createTrackingId();
            ((RecordImpl)r3).createTrackingId();

            Record r4 = new RecordImpl("s", "s:2", TEST_STRING.getBytes(), MIME);
            r4.set(Field.create(2));

            ((RecordImpl)r4).createTrackingId();
            ((RecordImpl)r4).createTrackingId();

            records2.add(r3);

            Map<String, List<Record>> so2 = new HashMap<>(1);
            so2.put("lane", records2);
            StageOutput s2 = new StageOutput("processor", so2, new ErrorSink(), new EventSink());
            snapshot.add(s2);

            return snapshot;
        }


        public SnapshotStore getSnapshotStore() {
            SnapshotStore snapshotStore= null;
            try {
                ObjectGraph objectGraph = ObjectGraph.create(CacheSnapshotStoreModule.class);
                snapshotStore = objectGraph.get(SnapshotStore.class);
                snapshotStore.create(USER, PIPELINE_NAME, PIPELINE_REV, SNAPSHOT_NAME, SNAPSHOT_LABEL);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return snapshotStore;
        }


        /**
         * int schemaVersion,
         * int version,
         * String pipelineId,
         * UUID uuid,
         * String title,
         * String description,
         * List<Config> configuration,
         * Map<String, Object> uiInfo,
         * List<StageConfiguration> stageConfigs,
         * StageConfiguration errorStage,
         * StageConfiguration statsAggregatorStage,
         * List<StageConfiguration> startEventStages,
         * List<StageConfiguration> stopEventStages
         * @return
         */
        public PipelineConfiguration getPipelineConfiguration(List<StageConfiguration> pipelineStages) {
            int schemaVersion = PipelineStoreTask.SCHEMA_VERSION;
            int version= PipelineConfigBean.VERSION;
            String pipelineId = "my_pipeline_id";
            UUID uuid= UUID.randomUUID();
            String title = "myTitle";
            String description= "myDescription";
            List<Config> configuration = new ArrayList<>();
            configuration.add(new Config("executionMode", ExecutionMode.STANDALONE.name()));
            configuration.add(new Config("deliveryGuarantee", DeliveryGuarantee.AT_LEAST_ONCE.name()));
            Map<String, Object> uiInfo= new HashMap<>();
//            List<PipelineFragmentConfiguration> fragments = Collections.emptyList();
            List<PipelineFragmentConfiguration> fragments = null;

            StageConfiguration errorStage = stageProvider.getErrorStageConfiguration();
            StageConfiguration statsAggregatorStage= stageProvider.getStatsAggregatorStageConfiguration();
            List<StageConfiguration> startEventStages = Collections.emptyList();
            List<StageConfiguration> stopEventStages = Collections.emptyList();

            PipelineConfiguration pipelineConfiguration = new PipelineConfiguration(schemaVersion, version, pipelineId, uuid, title, description,configuration,
                    uiInfo, fragments, pipelineStages, errorStage, statsAggregatorStage, startEventStages, stopEventStages);

            pipelineConfiguration.setMetadata(ImmutableMap.of("metaKey","metaValue"));
            return pipelineConfiguration;
        }


        public StageLibraryTask getStageLibraryTask(List<StageDefinition> stageDefs) {
//            List<StageDefinition> stageDefsForLibTask = stageProvider.generateStageDefinitionsForLibTask();
//            stageDefsForLibTask.addAll(stageDefs);
            StageProvider.MyStageLibraryTask myStageLibraryTask = new StageProvider.MyStageLibraryTask(stageDefs);
            return myStageLibraryTask;
        }

        public UserContext getRunnerUserContext() {
            UserContext userContext = new UserContext("test-user", false, false);
            return userContext;
        }

        public ProductionPipeline createProductionPipeline(UserContext userContext, PipelineConfiguration pipelineConf, Configuration configuration,
                                                           StageLibraryTask stageLib, ProductionPipelineRunner runner) {
            try {
                // 根据运行信息 RuntimeInfo中获取 dataDir,并创建相应目录
                Files.createDirectories(PipelineDirectoryUtil.getPipelineDir(runner.getRuntimeInfo(), PIPELINE_NAME, REVISION).toPath());

                // 通过 ProductionPipelineBuilder 来构建Pipeline
                LineagePublisherTask lineagePublisherTask = Mockito.mock(LineagePublisherTask.class);
                ProductionPipelineBuilder pipelineBuilder = new ProductionPipelineBuilder(PIPELINE_NAME, REVISION, configuration, runner.getRuntimeInfo(),stageLib,runner,null,lineagePublisherTask);

                ProductionPipeline productionPipeline = pipelineBuilder.build(userContext, pipelineConf, System.currentTimeMillis());

                // 对创建好的Pipeline注册一个运行状态监听器
//                productionPipeline.registerStatusListener(Mockito.mock(StateListener.class));
                productionPipeline.registerStatusListener(new StageProvider.MyStateListener());
                // 应该是 保存一版 快照
//                runner.capture(SNAPSHOT_NAME, 1, 1);

                return productionPipeline;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }



    }

    static class StageProvider{

        private static final AtomicInteger batchNum =  new AtomicInteger(1000);
        private static final AtomicInteger sizePerBatch =  new AtomicInteger(5);

        public static List<StageConfiguration> getThreeDefaultPipelineStageConfig(){
            List<StageConfiguration> stages = new ArrayList<>();
            String sourceLaneName = "sourceLane";
            String processLaneName = "processLane";
            StageConfiguration source = new StageConfigurationBuilder("mySource", ConvertHelper.getStageName(StageProvider.MySource.class))
                    .withOutputLanes(sourceLaneName)
                    .build();
            stages.add(source);

            stages.add(new StageConfigurationBuilder("myProcessor_01", ConvertHelper.getStageName(StageProvider.MyProcessor.class))
                    .withInputLanes(sourceLaneName)
                    .withOutputLanes(processLaneName)
                    .build());

            StageConfiguration target = new StageConfigurationBuilder("myTarget", ConvertHelper.getStageName(StageProvider.MyTarget.class))
                    .withInputLanes(processLaneName)
                    .build();
            stages.add(target);
            return stages;
        }

        public static class StageConfigurationBuilder {
            private String instanceName;
            private String library = "default";
            private String stageName;
            private int stageVersion = 1;
            private List<Config> configuration = Collections.emptyList();
            private Map<String, Object> uiInfo = null;
            private List<ServiceConfiguration> services = Collections.emptyList();
            private List<String> inputLanes = Collections.emptyList();
            private List<String> outputLanes = Collections.emptyList();
            private List<String> eventLanes = Collections.emptyList();

            public StageConfigurationBuilder(String instanceName, String stageName) {
                this.instanceName = instanceName;
                this.stageName = stageName;
            }

            public StageConfigurationBuilder withConfig(Config...configs) {
                this.configuration = Arrays.asList(configs);
                return this;
            }

            public StageConfigurationBuilder withInputLanes(String ...lanes) {
                this.inputLanes = Arrays.asList(lanes);
                return this;
            }

            public StageConfigurationBuilder withOutputLanes(String ...lanes) {
                this.outputLanes = Arrays.asList(lanes);
                return this;
            }

            public StageConfiguration build() {
                return new StageConfiguration(
                        instanceName,
                        library,
                        stageName,
                        stageVersion,
                        configuration,
                        uiInfo,
                        services,
                        inputLanes,
                        outputLanes,
                        eventLanes
                );
            }

        }

        public static class StageDefinitionBuilder {
            StageLibraryDefinition libraryDefinition;
            boolean privateClassLoader = false;
            Class<? extends Stage> klass;
            String name;
            int version = 1;
            String label;
            String description;
            com.streamsets.datacollector.config.StageType type;
            boolean errorStage = false;
            boolean preconditions = true;
            boolean onRecordError = true;
            List<ConfigDefinition> configDefinitions = Collections.emptyList();
            RawSourceDefinition rawSourceDefinition = null;
            String icon = "";
            ConfigGroupDefinition configGroupDefinition = null;
            boolean variableOutputStreams = false;
            int outputStreams;
            String outputStreamLabelProviderClass = null;
            List<ExecutionMode> executionModes = Arrays.asList(ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE, ExecutionMode.CLUSTER_BATCH);
            boolean recordsByRef = false;
            StageUpgrader upgrader = new StageUpgrader.Default();
            List<String> libJarsRegex = Collections.emptyList();
            boolean resetOffset = false;
            String onlineHelpRefUrl = "";
            boolean statsAggregatorStage = false;
            boolean pipelineLifecycleStage = false;
            boolean offsetCommitTrigger = false;
            boolean producesEvents = false;
            List<ServiceDependencyDefinition> services = Collections.emptyList();

            public StageDefinitionBuilder(ClassLoader cl, Class<? extends Stage> klass, String name) {
                this.libraryDefinition = createLibraryDef(cl);
                this.klass = klass;
                this.name = name;
                this.label = name + "Label";
                this.description = name + "Description";
                this.type = autoDetectStageType(klass);
                this.outputStreams = type.isOneOf(StageType.TARGET, StageType.EXECUTOR) ? 0 : 1;
            }

            public StageDefinitionBuilder withErrorStage(boolean errorStage) {
                this.errorStage = errorStage;
                return this;
            }

            public StageDefinitionBuilder withPreconditions(boolean preconditions) {
                this.preconditions = preconditions;
                return  this;
            }

            public StageDefinitionBuilder withConfig(ConfigDefinition... config) {
                this.configDefinitions = Arrays.asList(config);
                return this;
            }

            public StageDefinitionBuilder withExecutionModes(ExecutionMode...modes) {
                this.executionModes = Arrays.asList(modes);
                return this;
            }

            public StageDefinitionBuilder withStatsAggregatorStage(boolean statsAggregatorStage) {
                this.statsAggregatorStage = statsAggregatorStage;
                return this;
            }

            public StageDefinition build() {
                return new StageDefinition(
                        libraryDefinition,
                        privateClassLoader,
                        klass,
                        name,
                        version,
                        label,
                        description,
                        type,
                        errorStage,
                        preconditions,
                        onRecordError,
                        configDefinitions,
                        rawSourceDefinition,
                        icon,
                        configGroupDefinition,
                        variableOutputStreams,
                        outputStreams,
                        outputStreamLabelProviderClass,
                        executionModes,
                        recordsByRef,
                        upgrader,
                        libJarsRegex,
                        resetOffset,
                        onlineHelpRefUrl,
                        statsAggregatorStage,
                        pipelineLifecycleStage,
                        offsetCommitTrigger,
                        producesEvents,
                        services
                );
            }

            private static com.streamsets.datacollector.config.StageType autoDetectStageType(Class klass) {
                if(ProtoSource.class.isAssignableFrom(klass)) {
                    return StageType.SOURCE;
                }
                if(Processor.class.isAssignableFrom(klass)) {
                    return StageType.PROCESSOR;
                }
                if(Executor.class.isAssignableFrom(klass)) {
                    return StageType.EXECUTOR;
                }
                if(Target.class.isAssignableFrom(klass)) {
                    return StageType.TARGET;
                }

                throw new IllegalArgumentException("Don't know stage type for class: " + klass.getName());
            }

            public static final StageLibraryDefinition createLibraryDef(ClassLoader cl) {
                return new StageLibraryDefinition(cl, "default", "", new Properties(), null, null, null) {
                    @Override
                    public List<ExecutionMode> getStageExecutionModesOverride(Class klass) {
                        return ImmutableList.copyOf(ExecutionMode.values());
                    }
                };
            }

        }

        public static class MyStateListener implements StateListener {
            @Override
            public void stateChanged(PipelineStatus pipelineStatus, String message, Map<String, Object> attributes)
                    throws PipelineRuntimeException {
            }
        }
        public static class MySourceOffsetTrackerImpl implements SourceOffsetTracker {
            private final Map<String, String> offsets;
            private boolean finished;
            private long lastBatchTime;

            public MySourceOffsetTrackerImpl(Map<String, String> offsets) {
                this.offsets = new HashMap<>(offsets);
                finished = false;
            }

            @Override
            public boolean isFinished() {
                return finished;
            }

            @Override
            public void commitOffset(String entity, String newOffset) {
                lastBatchTime = System.currentTimeMillis();
                System.out.println(Utils.format("Committing entity({}), offset({}) on time({})", entity, newOffset, lastBatchTime));

                if(entity == null) {
                    return;
                }

                if(Source.POLL_SOURCE_OFFSET_KEY.equals(entity)) {
                    finished = (newOffset == null);
                }

                if(newOffset == null) {
                    offsets.remove(entity);
                } else {
                    offsets.put(entity, newOffset);
                }
            }

            @Override
            public Map<String, String> getOffsets() {
                return offsets;
            }

            @Override
            public long getLastBatchTime() {
                return lastBatchTime;
            }
        }
        public static class MyStageLibraryTask implements StageLibraryTask {
            private final List<StageDefinition> stages;

            public MyStageLibraryTask(Collection<StageDefinition> stages) {
                this.stages = ImmutableList.copyOf(stages);
            }
            @Override
            public String getName() {
                return null;
            }

            @Override
            public void init() {

            }

            @Override
            public void run() {

            }

            @Override
            public void waitWhileRunning() throws InterruptedException {

            }

            @Override
            public void stop() {}

            @Override
            public Status getStatus() {
                return null;
            }

            @Override
            public PipelineDefinition getPipeline() {
                return PipelineDefinition.getPipelineDef();
            }

            @Override
            public PipelineFragmentDefinition getPipelineFragment() {
                return PipelineFragmentDefinition.getPipelineFragmentDef();
            }

            @Override
            public PipelineRulesDefinition getPipelineRules() {
                return PipelineRulesDefinition.getPipelineRulesDef();
            }

            @Override
            public List<StageDefinition> getStages() {
                return stages;
            }

            @Override
            public List<LineagePublisherDefinition> getLineagePublisherDefinitions() {
                return Collections.emptyList();
            }

            @Override
            public LineagePublisherDefinition getLineagePublisherDefinition(String library, String name) {
                return null;
            }

            @Override
            public List<CredentialStoreDefinition> getCredentialStoreDefinitions() {
                return Collections.emptyList();
            }

            @Override
            public List<ServiceDefinition> getServiceDefinitions() {
                return Collections.emptyList();
            }

            @Override
            public ServiceDefinition getServiceDefinition(Class serviceInterface, boolean forExecution) {
                return null;
            }

            @Override
            public StageDefinition getStage(String library, String name, boolean forExecution) {
                for (StageDefinition def : stages) {
                    if (def.getLibrary().equals(library) && def.getName().equals(name)) {
                        return def;
                    }
                }
                return null;
            }

            @Override
            public Map<String, String> getLibraryNameAliases() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, String> getStageNameAliases() {
                return Collections.emptyMap();
            }

            @Override
            public List<ClasspathValidatorResult> validateStageLibClasspath() {
                return Collections.emptyList();
            }

            @Override
            public void releaseStageClassLoader(ClassLoader classLoader) {
            }

        }

        public StageConfiguration getErrorStageConfiguration() {
            StageConfiguration errorStage = new StageConfigurationBuilder("errorStage", "errorTarget")
                    .withConfig(new Config("errorTargetConfName", "/SDC_HOME/errorDir")).build();
            return errorStage;
        }

        public StageConfiguration getStatsAggregatorStageConfiguration() {
            StageConfiguration statsAggrStage = new StageConfigurationBuilder("statsAggregator", "statsAggregator").build();
            return statsAggrStage;
        }

        public static List<StageDefinition> generateStageDefinitionsForLibTask() {
            ClassLoader sysClassLoader = Thread.currentThread().getContextClassLoader();
            StageDefinition sourceDef = new StageDefinitionBuilder(sysClassLoader, StageProvider.MySource.class, ConvertHelper.getStageName(StageProvider.MySource.class))
                    .build();
            StageDefinition processorDef = new StageDefinitionBuilder(sysClassLoader, StageProvider.MyProcessor.class, ConvertHelper.getStageName(StageProvider.MyProcessor.class))
                    .build();

            ModelDefinition modelDef = new ModelDefinition(ModelType.FIELD_SELECTOR_MULTI_VALUE, null, Collections.<String>emptyList(),
                    Collections.<String>emptyList(), null, null, null);
            ConfigDefinition stageReqField = new ConfigDefinition("stageRequiredFields", ConfigDef.Type.MODEL, "stageRequiredFields",
                    "stageRequiredFields", null, false, "groupName", "stageRequiredFieldName", modelDef, "", null, 0, Collections.<ElFunctionDefinition>emptyList(),
                    Collections.<ElConstantDefinition>emptyList(), Long.MIN_VALUE, Long.MAX_VALUE, "text/plain", 0, Collections.<Class> emptyList(),
                    ConfigDef.Evaluation.IMPLICIT, new HashMap<String, List<Object>>());

            StageDefinition targetDef = new StageDefinitionBuilder(sysClassLoader, StageProvider.MyTarget.class, ConvertHelper.getStageName(StageProvider.MyTarget.class))
                    .withConfig(stageReqField)
                    .withExecutionModes(ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE, ExecutionMode.CLUSTER_BATCH, ExecutionMode.CLUSTER_MESOS_STREAMING)
                    .build();


//            ConfigDefinition errorTargetConf = new ConfigDefinition(
//                    "errorTargetConfName", ConfigDef.Type.STRING, "errorTargetConfLabel", "errorTargetConfDesc",
//                    "/SDC_HOME/errorDir", true, "groupName", "errorTargetConfFieldName", null, "", null , 0,
//                    Collections.<ElFunctionDefinition>emptyList(), Collections.<ElConstantDefinition>emptyList(), Long.MIN_VALUE, Long.MAX_VALUE, "text/plain", 0,
//                    Collections.<Class> emptyList(), ConfigDef.Evaluation.IMPLICIT, new HashMap<String, List<Object>>());
//
//            // 通用的两个
//            StageDefinition errorDef = new StageDefinitionBuilder(sysClassLoader, StageProvider.MyErrorTarget.class, "errorTarget")
//                    .withErrorStage(true)
//                    .withPreconditions(false)
//                    .withConfig(errorTargetConf)
//                    .withExecutionModes(ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE, ExecutionMode.CLUSTER_BATCH, ExecutionMode.CLUSTER_MESOS_STREAMING)
//                    .build();
//
//            StageDefinition statsDef = new StageDefinitionBuilder(sysClassLoader, StageProvider.MyStatsTarget.class, "statsAggregator")
//                    .withPreconditions(false)
//                    .withStatsAggregatorStage(true)
//                    .withExecutionModes(ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE, ExecutionMode.CLUSTER_BATCH, ExecutionMode.CLUSTER_MESOS_STREAMING)
//                    .build();
//            List<StageDefinition> errorAndStatsDef = ImmutableList.of(errorDef, statsDef);
            ArrayList<StageDefinition> stageDefinitions = new ArrayList<>();
            stageDefinitions.addAll(getErrorAndStatsDefinitions(sysClassLoader));
            stageDefinitions.add(sourceDef);
            stageDefinitions.add(processorDef);
            stageDefinitions.add(targetDef);
            return stageDefinitions;
        }


        public static List<StageDefinition> getErrorAndStatsDefinitions(ClassLoader classLoader){
            // 通用的两个
            ConfigDefinition errorTargetConf = new ConfigDefinition(
                    "errorTargetConfName", ConfigDef.Type.STRING, "errorTargetConfLabel", "errorTargetConfDesc",
                    "/SDC_HOME/errorDir", true, "groupName", "errorTargetConfFieldName", null, "", null , 0,
                    Collections.<ElFunctionDefinition>emptyList(), Collections.<ElConstantDefinition>emptyList(), Long.MIN_VALUE, Long.MAX_VALUE, "text/plain", 0,
                    Collections.<Class> emptyList(), ConfigDef.Evaluation.IMPLICIT, new HashMap<String, List<Object>>());
            StageDefinition errorDef = new StageDefinitionBuilder(classLoader, StageProvider.MyErrorTarget.class, "errorTarget")
                    .withErrorStage(true)
                    .withPreconditions(false)
                    .withConfig(errorTargetConf)
                    .withExecutionModes(ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE, ExecutionMode.CLUSTER_BATCH, ExecutionMode.CLUSTER_MESOS_STREAMING)
                    .build();

            StageDefinition statsDef = new StageDefinitionBuilder(classLoader, StageProvider.MyStatsTarget.class, "statsAggregator")
                    .withPreconditions(false)
                    .withStatsAggregatorStage(true)
                    .withExecutionModes(ExecutionMode.CLUSTER_YARN_STREAMING, ExecutionMode.STANDALONE, ExecutionMode.CLUSTER_BATCH, ExecutionMode.CLUSTER_MESOS_STREAMING)
                    .build();
            List<StageDefinition> errorAndStatsDef = ImmutableList.of(errorDef, statsDef);
            return new ArrayList<>(errorAndStatsDef);
        }


        @StageDef(
                version = 1,
                label = "MySource Label",
//            services = @ServiceDependency(service = Runnable.class),
                onlineHelpRefUrl = ""
        )
        public static class MySource implements Source, ErrorListener {
            private Context context;
            private AtomicInteger batchCount = new AtomicInteger(0);;
            @Override
            public List<ConfigIssue> init(Info info, Context context) {
                Info stageInfo = context.getStageInfo();
                this.context = context;
                System.err.println("\t Stage01.Source. init() context="+context);
                return new ArrayList<>();
            }

            @Override
            public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
                long current = System.currentTimeMillis();
                for(int i=0;i<sizePerBatch.get();i++){
                    Record record = context.createRecord("MySource.Creator");
                    Map<String, Field> rootMap = ImmutableMap.<String, Field>builder()
                            .put("orgId", Field.create("myOrgId"))
                            .put("pointId", Field.create("myPointId"))
                            .put("time", Field.create(current+(i*2000)))
                            .put("value", Field.create(i+Math.random()))
                            .build();
                    record.set(Field.create(rootMap));
                    batchMaker.addRecord(record);
                }

                if( batchCount.incrementAndGet() > batchNum.get()){
                    System.err.println("\t\t\t\t\t  MySource.produce 运行批次结束, 将返回 offset=null ");
                    return null;
                }else {
                    return String.valueOf(sizePerBatch.get() * batchCount.get());
                }
            }

            @Override
            public void destroy() {}
            @Override
            public void errorNotification(Throwable throwable) {}
        }
        @StageDef(
                version = 1,
                label = "MyProcessor Label",
//            services = @ServiceDependency(service = Runnable.class),
                onlineHelpRefUrl = ""
        )
        public static class MyProcessor implements Processor {
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

        @StageDef(
                version = 1,
                label = "TestTarget Label",
//            services = @ServiceDependency(service = Runnable.class),
                onlineHelpRefUrl = ""
        )
        public static class MyTarget implements Target {
            private Context context;

            @ConfigDef(
                    label = "onlyToTrash",
                    type = ConfigDef.Type.BOOLEAN,
                    defaultValue = "true",
                    required = true
            )
            public boolean onlyToTrash;

            @Override
            public List<ConfigIssue> init(Info info, Context context) {
                Info stageInfo = context.getStageInfo();
                this.context = context;
                System.err.println("\t Stage03.Target. init() context="+context);
                this.lastBatchTime.compareAndSet(0L,System.nanoTime());

                return new ArrayList<>();
            }

            private  AtomicLong lastBatchTime =new AtomicLong(0L);
            private  long lastBatchTimeMillis = System.currentTimeMillis();

            private double divide(double num,double b,int newScale){
                BigDecimal result = BigDecimal.valueOf(num).divide(BigDecimal.valueOf(b),50,RoundingMode.HALF_UP);
                int scale = result.scale();
                double value= result.doubleValue();
                if(scale > newScale){
                    try{
                        value=result.setScale(newScale,RoundingMode.HALF_UP).doubleValue();
                    }catch (Exception e){}
                }
                return value;
            }

            @Override
            public void write(Batch batch) throws StageException {
//                long start = System.nanoTime();
                if(onlyToTrash){
                    long currentTime = System.nanoTime();
                    long batchUsedTime = currentTime - lastBatchTime.get();
                    lastBatchTime.set(currentTime);

                    long currentTimeMillis = System.currentTimeMillis();
                    long batchUsedMillisTime = currentTimeMillis - lastBatchTimeMillis;
                    this.lastBatchTimeMillis = currentTimeMillis;

                    int outputSize= ImmutableList.copyOf(batch.getRecords()).size();
                    double batchUsedTimeAsMS= divide(batchUsedTime, 1000000,20);
                    double qpsInSec = divide(outputSize, divide(batchUsedTimeAsMS, 1000,20),3);
                    double qpsInMillisSec = divide(outputSize, divide(batchUsedMillisTime, 1000,20),3);
                    System.err.printf("outputSize:%d, batchUsedTime:%d, qpsInSec:%f ( %f w/sec); \t batchUsedMillisTime:%d , qpsInSec2:%f ( %f w/sec) ;    \n",
                            outputSize,
                            batchUsedTime,qpsInSec,divide(qpsInSec, 10000,5),
                            batchUsedMillisTime,qpsInMillisSec,divide(qpsInMillisSec, 10000,5));

                }

//                StaticCounter.getInstance().addThreadTimeAndBatchCount(StaticCounter.Key.STAGE_MY_TARGET_KEY,System.nanoTime() -start);
            }
            @Override
            public void destroy() {}
        }

        public static class MyErrorTarget implements Target {
            private Context context;
            public String errorTargetConfFieldName;
            @Override
            public List<ConfigIssue> init(Info info, Context context) {
                this.context = context;
                System.out.println("\t\t CommStage.A.MyError. init() context="+context);
                return Collections.emptyList();
            }

            @Override
            public void write(Batch batch) throws StageException {
                List<Record> records = ImmutableList.copyOf(batch.getRecords());
                for(Record record: records){
                    record.get().getValueAsMap().put("target", Field.create("write"));
                    System.out.println(record);
                }
                System.err.println("\t\t\t\t\t 6.  自开发算子.MyErrorTarget.write ");
            }
            @Override
            public void destroy() {}
        }
        public static class MyStatsTarget implements Target {
            private Context context;
            @Override
            public List<ConfigIssue> init(Info info, Context context) {
                this.context = context;
                System.out.println("\t\t CommStage.B.Stats. init() context="+context);
                return Collections.emptyList();
            }
            @Override
            public void destroy() {}

            @Override
            public void write(Batch batch) throws StageException {
                List<Record> records = ImmutableList.copyOf(batch.getRecords());
                for(Record record: records){
                    record.get().getValueAsMap().put("target", Field.create("write"));
                    System.out.println(record);
                }
                System.err.println("\t\t\t\t\t 6.  自开发算子.MyStatsTarget.write ");
            }
        }

    }

    public static class ConvertHelper {

        public static String getStageName(Class klass) {
            return klass.getName().replace(".", "_").replace("$", "_");
        }

        private static final Set<String> distinctSet = ImmutableSet.of("stageRecordPreconditions", "stageRequiredFields", "stageOnRecordError");
//        public static Map<String,Object> parseJsonConfigStr(String jsonConfiguration){
//            JSONArray configArray = JSON.parseObject(jsonConfiguration).getJSONArray("configuration");
//            Map<String,Object> configList = new HashMap<>();
//            for(JSONObject json:configArray.toArray(new JSONObject[]{})){
//                String configName = json.getString("name");
//                if(!distinctSet.contains(configName)){
//                    configList.put(configName,json.get("value"));
//                }
//            }
////      configList.forEach((k,v)->{
////        System.out.println(v);
////      });
//            return configList;
//        }

        public static Map<String,Object> parseJsonConfigStr(String jsonConfiguration){
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                HashMap<String, List<Map<String,Object>>> hashMap = objectMapper.readValue(jsonConfiguration,new HashMap<>().getClass());
                List<Map<String, Object>> configurations = hashMap.get("configuration");
                Map<String,Object> configList = new HashMap<>();
                for(Map<String, Object> config:configurations){
                    String name = config.get("name").toString();
                    if(!distinctSet.contains(name)){
                        configList.put(name,config.get("value"));
                    }
                }
                return configList;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    static class ProcessBuilder{
        private final String sourceLaneName;
        private final List<StageConfiguration> stageConfigs;
        private final Map<String, AtomicInteger> processInstanceIds;
        private String lastOutputLane;

        private final StageLibraryDefinition defaultLib;
        private final List<StageDefinition> stageDefs;
        private final ClassLoader classLoader;

        public ProcessBuilder() {
            this.stageConfigs = new ArrayList<>();
            this.processInstanceIds = new HashMap<>();;
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.stageDefs = StageProvider.getErrorAndStatsDefinitions(classLoader);
            this.sourceLaneName = "sourceLaneName";
            this.defaultLib = new StageLibraryDefinition(this.classLoader, "default", "", new Properties(), null, null, null) {
                @Override
                public List<ExecutionMode> getStageExecutionModesOverride(Class klass) {
                    return ImmutableList.copyOf(ExecutionMode.values());
                }
            };

//            // 2. 定义Pipeline的 StageConfig
//            String instanceName = getStageInstanceName(stageClazz);
//            StageConfiguration stageConfiguration = new StageProvider.StageConfigurationBuilder(instanceName, ConvertHelper.getStageName(stageClazz))
//                    .withOutputLanes(sourceLaneName)
//                    .build();
//            stageConfigs.add(stageConfiguration);
//            this.lastOutputLane = sourceLaneName;
//
//            // 3. 定义StageLib中的 StageDef
//            StageDefinition sourceDef = new StageProvider.StageDefinitionBuilder(this.classLoader, stageClazz,ConvertHelper.getStageName(stageClazz))
//                    .build();
//            stageDefs.add(sourceDef);
        }

        private String getStageInstanceName(Class<? extends Stage> stageClazz){
            String stageName = ConvertHelper.getStageName(stageClazz);
            AtomicInteger instanceCounter = processInstanceIds.getOrDefault(stageName, new AtomicInteger(0));
            processInstanceIds.put(stageName,instanceCounter);
            String instanceName = stageClazz.getSimpleName()+"_"+instanceCounter.incrementAndGet();
            return instanceName;
        }

//        public ProcessBuilder addProcessor(Class<? extends Stage> processorClazz,String outputLance){
//            StageDefinition stageDef = StageDefinitionExtractor.get().extract(defaultLib, processorClazz, "");
//            stageDefs.add(stageDef);
//            StageConfiguration stageConfiguration = new StageProvider.StageConfigurationBuilder(processorClazz.getSimpleName(), ConvertHelper.getStageName(processorClazz))
//                    .withInputLanes(this.lastOutputLane)
//                    .withOutputLanes(outputLance)
//                    .build();
//            stageConfigs.add(stageConfiguration);
//            this.lastOutputLane = outputLance;
//            return this;
//        }

        public ProcessBuilder addSource(Class<? extends Stage> stageClazz, Config...configs) {
            // 1. 定义Pipeline的 StageConfig
            String instanceName = getStageInstanceName(stageClazz);
            StageConfiguration stageConfiguration = new StageProvider.StageConfigurationBuilder(instanceName, ConvertHelper.getStageName(stageClazz))
                    .withOutputLanes(sourceLaneName)
                    .withConfig(configs)
                    .build();
            stageConfigs.add(stageConfiguration);
            this.lastOutputLane = sourceLaneName;

            // 3. 定义StageLib中的 StageDef
//            StageDefinition stageDef = new StageProvider.StageDefinitionBuilder(this.classLoader, stageClazz,ConvertHelper.getStageName(stageClazz))
//                    .build();
            StageDefinition stageDef = StageDefinitionExtractor.get().extract(defaultLib, stageClazz, "");
            stageDefs.add(stageDef);

            return this;
        }

        public ProcessBuilder addProcessor(Class<? extends Stage> stageClazz, Config...configs){

            // 1. 定义Pipeline的 StageConfig
            String instanceName = getStageInstanceName(stageClazz);
            StageConfiguration stageConfiguration = new StageProvider.StageConfigurationBuilder(instanceName, ConvertHelper.getStageName(stageClazz))
                    .withInputLanes(this.lastOutputLane)
                    .withOutputLanes(instanceName)
                    .withConfig(configs)
                    .build();
            stageConfigs.add(stageConfiguration);
            this.lastOutputLane = instanceName;

            // 2. 定义StageLib中的 StageDef
            StageDefinition stageDef = StageDefinitionExtractor.get().extract(defaultLib, stageClazz, "");
            stageDefs.add(stageDef);

            return this;
        }



        public ProcessBuilder addTarget(Class<? extends Stage> stageClazz) {
            // 1. 定义Pipeline的 StageConfig
            StageConfiguration stageConfiguration = new StageProvider.StageConfigurationBuilder(getStageInstanceName(stageClazz), ConvertHelper.getStageName(stageClazz))
                    .withInputLanes(this.lastOutputLane)
                    .build();
            stageConfigs.add(stageConfiguration);

            // 2. 定义StageLib中的 StageDef
            StageDefinition stageDef = StageDefinitionExtractor.get().extract(defaultLib, stageClazz, "");
            stageDefs.add(stageDef);
            return this;
        }



        public PipelineHelper buildStages() {
            return new PipelineHelper(stageConfigs, stageDefs);
        }


    }

    static class BuildHelper {

        public ProcessBuilder createDSource(Class<? extends Stage> sourceClazz, Config...configs) {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.addSource(sourceClazz,configs);
            return processBuilder;
        }

        public void useDefaultDSource(){

        }



    }
    public static BuildHelper builder(){

        return new BuildHelper();
    }

    public static class DataGenerator{

        public static List<Field> getFields(){
            List<Map<String, Object>> datas = generateEnosRecord();
            ArrayList<Field> fields = new ArrayList<>();
            datas.forEach(rc->{
                try{
                    Field field = objectToField(rc);
                    fields.add(field);
                }catch (Exception e){
                    e.printStackTrace();
                }

            });
            return fields;
        }

        public static List<Map<String, Object>> generateEnosRecord(){
            ImmutableList<String> list = ImmutableList.<String>builder()
                    .add("{\"pointId\": \"Meter.EpPositive\", \"orgId\": \"o15480404754831\", \"attr\": {}, \"time\": 1558169181162, \"assetId\": \"muPCyWLl\", \"quality\": 0, \"value\": 4.5, \"modelId\": \"SmartMeter\"}")
                    .build();
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> records = new ArrayList<>();
            for(String line:list){
                try {
                    HashMap<String, Object> map = objectMapper.readValue(line, new HashMap<String, Object>().getClass());
                    records.add(map);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return records;
        }


        public static Field objectToField(Object json) throws IOException {
            Field field;
            if (json == null) {
                field = Field.create(Field.Type.STRING, null);
            } else if (json instanceof List) {
                List jsonList = (List) json;
                List<Field> list = new ArrayList<>(jsonList.size());
                for (Object element : jsonList) {
                    list.add(objectToField(element));
                }
                field = Field.create(list);
            } else if (json instanceof Map) {
                Map<String, Object> jsonMap = (Map<String, Object>) json;
                Map<String, Field> map = new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : jsonMap.entrySet()) {
                    map.put(entry.getKey(), objectToField(entry.getValue()));
                }
                field = Field.create(map);
            } else if (json instanceof String) {
                field = Field.create((String) json);
            } else if (json instanceof Boolean) {
                field = Field.create((Boolean) json);
            } else if (json instanceof Character) {
                field = Field.create((Character) json);
            } else if (json instanceof Byte) {
                field = Field.create((Byte) json);
            } else if (json instanceof Short) {
                field = Field.create((Short) json);
            } else if (json instanceof Integer) {
                field = Field.create((Integer) json);
            } else if (json instanceof Long) {
                field = Field.create((Long) json);
            } else if (json instanceof Float) {
                field = Field.create((Float) json);
            } else if (json instanceof Double) {
                field = Field.create((Double) json);
            } else if (json instanceof byte[]) {
                field = Field.create((byte[]) json);
            } else if (json instanceof Date) {
                field = Field.createDatetime((Date) json);
            } else if (json instanceof BigDecimal) {
                field = Field.create((BigDecimal) json);
            } else if (json instanceof UUID) {
                field = Field.create(json.toString());
            } else {
                throw new IOException(Utils.format("Not recognized type '{}', value '{}'", json.getClass(), json));
            }
            return field;
        }


    }



}
