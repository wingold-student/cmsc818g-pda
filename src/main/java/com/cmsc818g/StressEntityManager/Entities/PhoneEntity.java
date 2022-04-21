package com.cmsc818g.StressEntityManager.Entities;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

import com.cmsc818g.StressEntityManager.StressEntityManager;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.model.DateTime;

public class PhoneEntity extends AbstractBehavior<PhoneEntity.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    public static final class AskingForSleepData implements Command {
        final ActorRef<Response> replyTo;

        public AskingForSleepData(ActorRef<Response> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class AskingForLocation implements Command {
        final ActorRef<Response> replyTo;

        public AskingForLocation(ActorRef<Response> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public static final class SubscribeForMediaEvents implements Command {
        final ActorRef<Response> subscriber;

        public SubscribeForMediaEvents(ActorRef<Response> subscriber) {
            this.subscriber = subscriber;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class SleepDataResponse implements Response {
        final Duration amountSlept;
        final float sleepQuality;
        final Optional<Date> onNightOf;

        public SleepDataResponse(Duration amountSlept, float sleepQuality, Optional<Date> onNightOf, ActorRef<Response> replyTo) {
            this.amountSlept = amountSlept;
            this.sleepQuality = sleepQuality;
            this.onNightOf = onNightOf;
        }
    }

    public static final class LocationResponse implements Response {
        final String location;

        public LocationResponse(String location) {
            this.location = location;
        }
    }

    public static final class MediaEvent implements Response {
        final Media mediaEvent;

        public MediaEvent(Media mediaEvent) {
            this.mediaEvent = mediaEvent;
        }
    }


    /************************************* 
     * CREATION 
     *************************************/
    public static Behavior<Command> create(ActorRef<StressEntityManager> entityManager) {
        return Behaviors.setup(context -> new PhoneEntity(context, entityManager));
    }

    private ArrayList<ActorRef<MediaEvent>> mediaSubscribers;
    private ActorRef<StressEntityManager> entityManager;

    public PhoneEntity(ActorContext<Command> context, ActorRef<StressEntityManager> entityManager) {
        super(context);
        this.mediaSubscribers = new ArrayList<>();
        this.entityManager = entityManager;
    }


    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AskingForSleepData.class, this::onAskingForSleepData)
            .onMessage(AskingForLocation.class, this::onAskingForLocation)
            .onMessage(SubscribeForMediaEvents.class, this::onSubscribeForMediaEvents)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onAskingForSleepData(AskingForSleepData msg) {
        return this;
    }

    private Behavior<Command> onAskingForLocation(AskingForLocation msg) {
        return this;
    }

    private Behavior<Command> onSubscribeForMediaEvents(SubscribeForMediaEvents msg) {
        return this;
    }

    private PhoneEntity onPostStop() {
        return this;
    }


    /************************************* 
     * HELPER CLASSES
     *************************************/
    // TODO: Part of Media Reporter or per phone??
    public class Media {
        private String name;
        private String genre;
        private DateTime playedAt;

        public Media(String name, String genre) {
            this.name = name;
            this.genre = genre;
        }
    }
    
}
