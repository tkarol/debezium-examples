/*
 * Copyright Debezium Authors.
 *
 * Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.debezium.examples.cacheinvalidation.persistence;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.enterprise.inject.spi.CDI;

import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate event listener obtains the current TX id and stores it in a cache.
 *
 * @author Gunnar Morling
 */
class TransactionRegistrationListener implements FlushEventListener {

    private static final long serialVersionUID = 1L;

    private final ConcurrentMap<Session, Boolean> sessionsWithBeforeTransactionCompletion;

    private volatile KnownTransactions knownTransactions;

    public TransactionRegistrationListener() {
        sessionsWithBeforeTransactionCompletion = new ConcurrentHashMap<>();
    }

    private static final Logger LOG = LoggerFactory.getLogger( TransactionRegistrationListener.class );

    /**
     * Synchronizes persistence context with database by "flushing"
     * Flushes when transaction is committed
     * @param event
     * @throws HibernateException
     */
    @Override
    public void onFlush(FlushEvent event) throws HibernateException {
     //   if (sessionsWithBeforeTransactionCompletion.containsKey(event.getSession())) {
     //       LOG.info("FLUSHED onFlush method invoked");
     //       return;
     //   }
        LOG.info("onFlush method invoked");

        sessionsWithBeforeTransactionCompletion.put(event.getSession(), true);

        event.getSession().getActionQueue().registerProcess( session -> {
            Number txId = (Number) event.getSession().createNativeQuery("SELECT txid_current()")
                    .setFlushMode(FlushMode.MANUAL)
                    .getSingleResult();

            getKnownTransactions().register(txId.longValue());

            sessionsWithBeforeTransactionCompletion.remove(session);
        } );
    }

    private  KnownTransactions getKnownTransactions() {
        KnownTransactions value = knownTransactions;

        if (value == null) {
            knownTransactions = value = CDI.current().select(KnownTransactions.class).get();
        }

        return value;
    }
}
