package com.cmsc818g.ContextActors;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter;
import com.cmsc818g.StressContextEngine.Reporters.SchedulerReporter;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/** TODO:
 * Gut this and use with Entity Manager or Context Engine?
 */

public class ReporterGroup extends AbstractBehavior<ReporterGroup.Command> {
    public interface Command {}

    public static class ReporterTerminated implements Command {
        public final ActorRef<Command> reporter;
        public final String groupId;
        public final String reporterId;

        ReporterTerminated(ActorRef<Command> reporter, String groupId, String reporterId) {
            this.reporter = reporter;
            this.groupId = groupId;
            this.reporterId = reporterId;
        }
    }

    public static Behavior<Command> create(String groupId) {
        return Behaviors.setup(context -> new ReporterGroup(context, groupId));
    }

    private final String groupId;
    private final Map<String, ActorRef<BloodPressureReporter.Command>> reporterIdToActor = new HashMap<>();

    private ReporterGroup(ActorContext<Command> context, String groupId) {
        super(context);
        this.groupId = groupId;
        context.getLog().info("Reporter group {}  started", groupId);
    }

    private ReporterGroup onFollowReporter(ContextManager.RequestFollowContextReporter followMsg) {
        if (this.groupId.equals(followMsg.groupId)) {
            ActorRef<BloodPressureReporter.Command> reporterActor = reporterIdToActor.get(followMsg.repId);

            if (reporterActor != null) {
                followMsg.replyTo.tell(new ContextManager.ReporterRegistered(reporterActor));
            } else if (followMsg.type == "Scheduler" ) {
                getContext().getLog().info("Creating reporter actor for {}", followMsg.repId);
                reporterActor = getContext().spawn(SchedulerReporter.create(followMsg.repId, this.groupId), "reporter-" + followMsg.repId);
            }
        } else {
            getContext()
                .getLog()
                    .warn("Ignoring followReporter request for {}. This actor is responsible for {}.",
                        groupId, this.groupId);
        }

        return this;
    }

    private ReporterGroup onReporterList(ContextManager.RequestReporterList r) {
        r.replyTo.tell(new ContextManager.ReplyReporterList(r.requestId, reporterIdToActor.keySet()));
        return this;
    }

    private ReporterGroup onTerminated(ReporterTerminated t) {
        getContext().getLog().info("Reporter actor for {} has been terminated", t.reporterId);
        reporterIdToActor.remove(t.reporterId);
        return this;
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ContextManager.RequestFollowContextReporter.class, this::onFollowReporter)
            .onMessage(ContextManager.RequestReporterList.class, r ->
                r.groupId.equals(groupId),
                this::onReporterList)
            .onMessage(ReporterTerminated.class, this::onTerminated)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private ReporterGroup onPostStop() {
        getContext().getLog().info("ReporterGroup {} stopped", groupId);
        return this;
    }
}
