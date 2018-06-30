package com.lightbend.akka.sample;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class IotSupervisor extends AbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props() {
        return Props.create(IotSupervisor.class);
    }

    @Override
    public void preStart() {
        log.info("Iot Application started");
    }

    @Override
    public void postStop() {
        log.info("IoT Application stopped");
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .build();
    }
}
