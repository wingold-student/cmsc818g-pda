package com.cmsc818g.StressContextEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter;
import com.cmsc818g.StressContextEngine.Reporters.BusynessReporter;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressContextEngine.Reporters.SchedulerReporter;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.ChildFailed;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.GroupRouter;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.Routers;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public interface Command {}

    public static class contextEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final ArrayList<String> list;
        public final StressManagementController.HealthInformation healthInfo; 
        
        public contextEngineGreet(ActorRef<StressManagementController.Command> ref,
                      StressManagementController.HealthInformation info, ArrayList<String> list) {
          this.replyTo = ref;
          this.healthInfo = info;
          this.list = list;
        }
      }//end of class contextEngineGreet

    public static final class StartPeriodicDatabaseReading implements Command {
      final Duration interval;

      public StartPeriodicDatabaseReading(Duration interval) {
        this.interval = interval;
      }
    }

    public static final class StopPeriodicDatabaseReading implements Command {
      public StopPeriodicDatabaseReading() {}
    }

    public static final class TellAllReportersToRead implements Command {
      final int rowToRead;

      public TellAllReportersToRead(int rowToRead) {
        this.rowToRead = rowToRead;
      }
    }
    public static final class DatabaseReadStatus implements Command {
      final Reporter.StatusOfRead status;

      public DatabaseReadStatus(Reporter.StatusOfRead status) {
        this.status = status;
      }
    }
 
    public static Behavior<Command> create(String databaseURI, String tableName) {
        return Behaviors.<Command>supervise(
            Behaviors.setup(context ->
              Behaviors.withTimers(
                timers -> new StressContextEngine(context, timers, databaseURI, tableName)
            )
          )
        )
        .onFailure(ClassNotFoundException.class, SupervisorStrategy.stop());
    }
/*
    public static Behavior<Command> create(ActorRef<StressManagementController.Command> controller) {
      return Behaviors.setup(context -> new StressContextEngine(context, controller));
    }
*/

    private final ServiceKey<Reporter.Command> reporterServiceKey;
    private final GroupRouter<Reporter.Command> reporterGroup;
    private final ActorRef<Reporter.Command> router;
    private final TimerScheduler<Command> timers;
    private final ActorRef<Reporter.StatusOfRead> statusAdapter;

    private final HashMap<String, ActorRef<Reporter.Command>> reporters;

    private int currentRowNumber; // TODO: Temporary whilst all reporters reading at same speed
    private final String periodicReporterTimer = "reporterReading";

    public StressContextEngine(ActorContext<Command> context, TimerScheduler<Command> timers, String databaseURI, String tableName) {
        super(context);
        getContext().getLog().info("context engine actor created");

        this.timers = timers;
        this.currentRowNumber = 1;
        this.reporters = new HashMap<>();

        reporterServiceKey = ServiceKey.create(Reporter.Command.class, "context-reporter");

        ActorRef<Reporter.Command> schedulerReporter = context.spawn(SchedulerReporter.create("demo", databaseURI, tableName), "Scheduler");
        context.getSystem().receptionist().tell(Receptionist.register(reporterServiceKey, schedulerReporter));
        reporters.put("Scheduler", schedulerReporter);

        ActorRef<Reporter.Command> busynessReporter = context.spawn(BusynessReporter.create(schedulerReporter), "Busyness");
        context.getSystem().receptionist().tell(Receptionist.register(reporterServiceKey, busynessReporter));
        reporters.put("Busyness", schedulerReporter);
        
        ActorRef<Reporter.Command> bpReporter = context.spawn(BloodPressureReporter.create(databaseURI, tableName), "BloodPressure");
        context.getSystem().receptionist().tell(Receptionist.register(reporterServiceKey, bpReporter));
        reporters.put("BloodPressure", bpReporter);

        reporterGroup = Routers.group(reporterServiceKey);
        router = context.spawn(reporterGroup, "reporter-group");

        context.watch(schedulerReporter);

        this.statusAdapter = context.messageAdapter(Reporter.StatusOfRead.class, DatabaseReadStatus::new);

    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
        .onMessage(contextEngineGreet.class, this::onEngineResponse)
        .onMessage(StartPeriodicDatabaseReading.class, this::onStartPeriodicDatabaseReading)
        .onMessage(StopPeriodicDatabaseReading.class, this::onStopPeriodicDatabaseReading)
        .onMessage(TellAllReportersToRead.class, this::onTellAllReportersToRead)
        .onMessage(DatabaseReadStatus.class, this::onDatabaseReadStatus)
        .onSignal(ChildFailed.class, signal -> onChildFailed(signal))
        .build();
    }

    private Behavior<Command> onStartPeriodicDatabaseReading(StartPeriodicDatabaseReading msg) {
      this.timers.startTimerAtFixedRate(this.periodicReporterTimer, new TellAllReportersToRead(this.currentRowNumber), msg.interval);
      return this;
    }

    private Behavior<Command> onStopPeriodicDatabaseReading(StopPeriodicDatabaseReading msg) {
      if (this.timers.isTimerActive(this.periodicReporterTimer)) {
        this.timers.cancel(this.periodicReporterTimer);
      }

      return this;
    }

    private Behavior<Command> onTellAllReportersToRead(TellAllReportersToRead msg) {
      final int toReadRow = this.currentRowNumber; // Copy so it doesn't change in the message

      this.router.tell(new Reporter.ReadRowOfData(toReadRow, this.statusAdapter));

      this.currentRowNumber++;
      return this;
    }

    private Behavior<Command> onDatabaseReadStatus(DatabaseReadStatus msg) {
      String message = "Actor " + msg.status.actorPath + ": ";

      if (msg.status.success) {
        getContext().getLog().info(message + " : " + msg.status.message);
      } else {
        getContext().getLog().error(message + " : " + msg.status.message);
        getContext().getSelf().tell(new StopPeriodicDatabaseReading());
      }

      return this;
    }

    private Behavior<Command> onChildFailed(ChildFailed signal) {
      ActorPath childPath = signal.getRef().path();
      String errorMsg = "Child " + childPath + " failed";
      getContext().getLog().error(errorMsg, signal.getCause());

      return this;
    }
  
    private Behavior<Command> onEngineResponse(contextEngineGreet message) { //when receive message
        //get information of connected entities
        StressManagementController.HealthInformation personalData = new StressManagementController.HealthInformation();
   
        message.replyTo.tell(new StressManagementController.ContextEngineToController("healthData", personalData));       
      return this;
    }
}
