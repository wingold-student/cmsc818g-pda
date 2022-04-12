package com.cmsc818g.StressEntityManager.Entities;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

import java.util.Optional;

public class SmartWatchDevice extends AbstractBehavior<SmartWatchDevice.Command> {
    public interface Command {}

    public static final class ReadHeartrate implements Command {
        final long requestId;
        final ActorRef<RespondHeartrate> replyTo;

        public ReadHeartrate(long requestId, ActorRef<RespondHeartrate> replyTo) {
            this.requestId = requestId;
            this.replyTo = replyTo;
        }
    }

    public static final class RespondHeartrate {
        final long requestId;
        final Optional<Integer> value;

        public RespondHeartrate(long requestId, Optional<Integer> value) {
            this.requestId = requestId;
            this.value = value;
        }
    }

    public static final class RecordHeartrate implements Command {
        final long requestId;
        final int value;
        final ActorRef<HeartrateRecorded> replyTo;

        public RecordHeartrate(long requestId, int value, ActorRef<HeartrateRecorded> replyTo) {
            this.requestId = requestId;
            this.value = value;
            this.replyTo = replyTo;
        }
    }

    public static final class HeartrateRecorded {
        final long requestId;

        public HeartrateRecorded(long requestId) {
            this.requestId = requestId;
        }
    }

    public static Behavior<Command> create(String groupId, String deviceId) {
        return Behaviors.setup(context -> new SmartWatchDevice(context, groupId, deviceId));
    }

    private final String groupId;
    private final String deviceId;

    private Optional<Integer> lastHeartrateReading = Optional.empty();

    private SmartWatchDevice(ActorContext<Command> context, String groupId, String deviceId) {
        super(context);
        this.groupId = groupId;
        this.deviceId = deviceId;

        context.getLog().info("Smartwatch actor {}-{} started", groupId, deviceId);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ReadHeartrate.class, this::onReadHeartrate)
            .onMessage(RecordHeartrate.class, this::onRecordHeartrate)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onReadHeartrate(ReadHeartrate r) {
        r.replyTo.tell(new RespondHeartrate(r.requestId, lastHeartrateReading));
        return this;
    }

    private Behavior<Command> onRecordHeartrate(RecordHeartrate r) {
        getContext().getLog().info("Recorded heartrate reading {} with {}", r.value, r.requestId);
        lastHeartrateReading = Optional.of(r.value);
        r.replyTo.tell(new HeartrateRecorded(r.requestId));
        return this;
    }

    private SmartWatchDevice onPostStop() {
        getContext().getLog().info("Smartwatch actor {}-{} stopped", groupId, deviceId);
        return this;
    }
}