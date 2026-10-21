package hr.bertlab.saga.messaging.consume;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.amqp.autoconfigure.CachingConnectionFactoryConfigurer;
import org.springframework.boot.amqp.autoconfigure.RabbitConnectionDetails;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

final class SagaConnectionFactoryConfigurer extends CachingConnectionFactoryConfigurer implements DisposableBean {

    static final String THREAD_PREFIX = "saga-rabbit-";

    private static final int SPARE_THREADS = 2;

    // any Executor class bean would stop Boot from creating its applicationTaskExecutor
    private final ThreadPoolExecutor listenerPool;

    SagaConnectionFactoryConfigurer(
            RabbitProperties rabbitProperties, RabbitConnectionDetails connectionDetails, int partitions) {
        super(rabbitProperties, connectionDetails);
        this.listenerPool = listenerPool(partitions);
    }

    static ThreadPoolExecutor listenerPool(int partitions) {
        int threads = partitions + SPARE_THREADS;
        return new ThreadPoolExecutor(
                threads,
                threads,
                0,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new CustomizableThreadFactory(THREAD_PREFIX));
    }

    @Override
    public void configure(CachingConnectionFactory factory, RabbitProperties rabbitProperties) {
        super.configure(factory, rabbitProperties);
        factory.setExecutor(listenerPool);
        factory.setPublisherConfirmType(ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
    }

    @Override
    public void destroy() {
        listenerPool.shutdown();
    }
}
