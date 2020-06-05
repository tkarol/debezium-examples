package io.debezium.examples.cacheinvalidation.persistence;

import java.util.concurrent.TimeUnit;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.DefaultCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.connect.source.SourceRecord;
import javax.inject.Inject;
import org.apache.kafka.connect.data.Struct;
import io.debezium.data.Envelope.Operation;
import io.debezium.examples.cacheinvalidation.model.Item;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
/**
 * Keeps track of transactions initiated by this application, so we can tell
 * them apart from transactions initiated externally, e.g. via other DB clients.
 */
@ApplicationScoped
public class KnownTransactions {

    private static final Logger LOG = LoggerFactory.getLogger( KnownTransactions.class );

    private final DefaultCacheManager cacheManager;
    private final Cache<Long, Boolean> applicationTransactions;

    @Inject
    private KnownTransactions knownTransactions;

    @PersistenceUnit
    private EntityManagerFactory emf;


    private void handleDbChangeEvent(SourceRecord record) {
        if (record.topic().equals("dbserver1.public.item")) {
            Long itemId = ((Struct) record.key()).getInt64("id");
            Struct payload = (Struct) record.value();
            Operation op = Operation.forCode(payload.getString("op"));
            Long txId = ((Struct) payload.get("source")).getInt64("txId");

            if (!knownTransactions.isKnown(txId) &&
                    (op == Operation.UPDATE || op == Operation.DELETE)) {
                emf.getCache().evict(Item.class, itemId);
            }
        }
    }


    public KnownTransactions() {
        cacheManager = new DefaultCacheManager();
        cacheManager.defineConfiguration(
                "tx-id-cache",
                new ConfigurationBuilder()
                    .simpleCache(true)
                    .expiration()
                        .lifespan(60, TimeUnit.SECONDS)
                    .build()
                );

        applicationTransactions = cacheManager.getCache("tx-id-cache");
    }

    @PreDestroy
    public void stopCacheManager() {
        cacheManager.stop();
    }

    public void register(long txId) {
        LOG.info("Registering TX {} started by this application", txId);
        applicationTransactions.put(txId, true);
    }

    public boolean isKnown(long txId) {
        return Boolean.TRUE.equals(applicationTransactions.get(txId));
    }
}
