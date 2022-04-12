package com.cmsc818g.ContextActors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cmsc818g.StressContextEngine.Reporters.ReporterMessages;

import akka.actor.typed.PostStop;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/** TODO:
 * Gut this and maybe use for the context engine?
 */

public class ContextManager extends AbstractBehavior<ContextManager.Command> {

    public interface Command {}
    
    public static final class RequestFollowContextReporter
        implements ContextManager.Command, ReporterGroup.Command {
            public final String repId;
            public final String groupId;
            public final String type;
            public final ActorRef<ReporterRegistered> replyTo;

            public RequestFollowContextReporter(String repId, String groupId, String type, ActorRef<ReporterRegistered> replyTo) {
                this.repId = repId;
                this.groupId = groupId;
                this.replyTo = replyTo;
                this.type = type;
            }
    }

    public static final class RequestReporterList implements ContextManager.Command, ReporterGroup.Command {
        final long requestId;
        final String groupId;
        final ActorRef<ReplyReporterList> replyTo;

        public RequestReporterList(long requestId, String groupId, ActorRef<ReplyReporterList> replyTo) {
            this.requestId = requestId;
            this.groupId = groupId;
            this.replyTo = replyTo;
        }
    }

    public static final class ReplyReporterList {
        final long requestId;
        final Set<String> ids;

        public ReplyReporterList(long requestId, Set<String> ids) {
            this.requestId = requestId;
            this.ids = ids;
        }
    }

    public static final class ReporterRegistered {
        public final ActorRef<ReporterMessages.Command> reporter; 

        public ReporterRegistered(ActorRef<ReporterMessages.Command> reporter) {
            this.reporter = reporter;
        }
    }

    private static class ReporterGroupTerminated implements ContextManager.Command {
        public final String groupId;

        ReporterGroupTerminated(String groupId) {
            this.groupId = groupId;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(ContextManager::new);
    }

    private final Map<String, ActorRef<ReporterGroup.Command>> groupIdToActor = new HashMap<>();

    private ContextManager(ActorContext<Command> context) {
        super(context);
        context.getLog().info("ContextManager started");
    }

    private ContextManager onFollowReporter(RequestFollowContextReporter trackMsg) {
        String groupId = trackMsg.groupId;
        ActorRef<ReporterGroup.Command> ref = groupIdToActor.get(groupId);

        if (ref != null) {
            ref.tell(trackMsg);
        } else {
            getContext().getLog().info("Creating reporter group actor for {}", groupId);
            ActorRef<ReporterGroup.Command> groupActor = getContext().spawn(ReporterGroup.create(groupId), "group-" + groupId);

            getContext().watchWith(groupActor, new ReporterGroupTerminated(groupId));
            groupActor.tell(trackMsg);
            groupIdToActor.put(groupId, groupActor);
        }

        return this;
    }

    private ContextManager onRequestReporterList(RequestReporterList request) {
        ActorRef<ReporterGroup.Command> ref = groupIdToActor.get(request.groupId);

        if (ref != null) {
            ref.tell(request);
        } else {
            request.replyTo.tell(new ReplyReporterList(request.requestId, Collections.emptySet()));
        }

        return this;
    }

    private ContextManager onTerminated(ReporterGroupTerminated t) {
        getContext().getLog().info("Reporter group actor for {} has been terminated", t.groupId);
        groupIdToActor.remove(t.groupId);
        return this;
    }

    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(RequestFollowContextReporter.class, this::onFollowReporter)
            .onMessage(RequestReporterList.class, this::onRequestReporterList)
            .onMessage(ReporterGroupTerminated.class, this::onTerminated)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private ContextManager onPostStop() {
        getContext().getLog().info("ContextManager stopped");
        return this;
    }
}

