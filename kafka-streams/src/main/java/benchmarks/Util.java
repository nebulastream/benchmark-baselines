package benchmarks;

import com.github.javafaker.Faker;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.BytesSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import serialization.NexmarkAuctionRecord;
import serialization.NexmarkAuctionSerde;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Util implements AutoCloseable {

    private final Logger logger = LoggerFactory.getLogger(Util.class);
    private ExecutorService executorService = Executors.newFixedThreadPool(1);

    public class DummyProducer implements AutoCloseable, Runnable {
        private Properties props;
        private String topic;
        private boolean closed;
        private int counter = 0;

        public DummyProducer(Properties producerProps, String topic) {
            logger.info("Producer created!");
            this.closed = false;
            this.topic = topic;
            this.props = producerProps;
            this.props.setProperty("client.id", "faker");
        }

        public void run() {
            logger.info("Producer run() called!");
            try (KafkaProducer producer = new KafkaProducer<>(props,
                    new BytesSerializer(),
                    new NexmarkAuctionSerde.AuctionSerializer())) {
                Faker faker = new Faker();
                while (!closed) {
                    try {
                        Object result = producer.send(new ProducerRecord<>(
                                this.topic,
                                new NexmarkAuctionRecord(600 + counter)));
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                    counter++;
                }
            } catch (Exception ex) {
                logger.error(ex.toString());
            }
            logger.info("Producer run() finished!");
        }
        public void close()  {
            closed = true;
        }
    }

    public DummyProducer startNewDummyProducer(Properties producerProps, String topic) {
        logger.info("Producer startNewDummyProducer() called!");
        DummyProducer prod = new DummyProducer(producerProps, topic);
        executorService.submit(prod);
        return prod;
    }

    public void createTopics(final Properties allProps, List<NewTopic> topics)
            throws InterruptedException, ExecutionException, TimeoutException {
        try (final AdminClient client = AdminClient.create(allProps)) {
            logger.info("Creating topics");

            client.createTopics(topics).values().forEach( (topic, future) -> {
                try {
                    future.get();
                } catch (Exception ex) {
                    logger.info(ex.toString());
                }
            });

            Collection<String> topicNames = topics
                .stream()
                .map(t -> t.name())
                .collect(Collectors.toCollection(LinkedList::new));

            logger.info("Asking cluster for topic descriptions");
            client
                .describeTopics(topicNames)
                .all()
                .get(10, TimeUnit.SECONDS)
                .forEach((name, description) -> logger.info("Topic Description: {}", description.toString()));
        }
    }

    public void close() {
        if (executorService != null) {
            executorService.shutdownNow();
            executorService = null;
        }
    }
}
