package com.cmsc818g.StressContextEngine;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter;
import com.cmsc818g.StressContextEngine.Reporters.HeartRateReporter;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter;
import com.cmsc818g.StressContextEngine.Reporters.MedicalHistoryReporter;
import com.cmsc818g.StressContextEngine.Reporters.BusynessReporter;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressContextEngine.Reporters.SchedulerReporter;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter;
import com.cmsc818g.Utilities.SQLiteHandler;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import akka.actor.ActorPath;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.ChildFailed;
import akka.actor.typed.PostStop;
import akka.actor.typed.SupervisorStrategy;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public interface Command {}

    public static class contextEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final ArrayList<String> list;
        
        public contextEngineGreet(ActorRef<StressManagementController.Command> ref, ArrayList<String> list) {
          this.replyTo = ref;
          this.list = list;
        }
      }//end of class contextEngineGreet

    public static class TellAllReportersToReadRow implements Command {
      public final int rowToRead;

      public TellAllReportersToReadRow(int rowToRead) {
        this.rowToRead = rowToRead;
      }
    }

    public static enum TellAllReportersToPeriodicallyRead implements Command {
      INSTANCE
    }

    public static final class DatabaseReadStatus implements Command {
      final SQLiteHandler.StatusOfRead status;

      public DatabaseReadStatus(SQLiteHandler.StatusOfRead status) {
        this.status = status;
      }
    }
 
    public static Behavior<Command> create(String databaseURI, String tableName, String configFilename) {
        return Behaviors.<Command>supervise(
            Behaviors.setup(context ->
              Behaviors.withTimers(
                timers -> new StressContextEngine(context, timers, databaseURI, tableName, configFilename)
            )
          )
        )
        .onFailure(ClassNotFoundException.class, SupervisorStrategy.stop());
    }

    private final TimerScheduler<Command> timers;
    private final ActorRef<SQLiteHandler.StatusOfRead> statusAdapter;

    private final HashMap<String, ActorRef<Reporter.Command>> reporters;

    private final String periodicReporterTimer = "reporterReading";

    public StressContextEngine(ActorContext<Command> context, TimerScheduler<Command> timers, String databaseURI, String tableName, String configFilename) throws StreamReadException, DatabindException, IOException {
        super(context);
        getContext().getLog().info("context engine actor created");

        this.timers = timers;
        this.reporters = new HashMap<>();

        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        InputStream cfgFilestream = getClass().getClassLoader().getResourceAsStream(configFilename);
        ContextEngineConfig cfg = yamlReader.readValue(cfgFilestream, ContextEngineConfig.class);

        this.statusAdapter = context.messageAdapter(SQLiteHandler.StatusOfRead.class, DatabaseReadStatus::new);

        ActorRef<Reporter.Command> schedulerReporter = context.spawn(
          SchedulerReporter.create(
            this.statusAdapter,
            cfg.Scheduler.dbURI,
            cfg.Scheduler.table,
            cfg.Scheduler.readRate,
            "DemoCalendar"), // Not really used for the moment
          "Scheduler"
        );
        ServiceKey<Reporter.Command> schedulerKey = ServiceKey.create(Reporter.Command.class, "Scheduler");
        context.getSystem().receptionist().tell(Receptionist.register(schedulerKey, schedulerReporter));
        reporters.put("Scheduler", schedulerReporter);
        context.watch(schedulerReporter);

        ActorRef<Reporter.Command> busynessReporter = context.spawn(
          BusynessReporter.create(
            this.statusAdapter,
            cfg.Busyness.dbURI,
            cfg.Busyness.table,
            cfg.Busyness.readRate,
            schedulerReporter),
        "Busyness"
        );
        ServiceKey<Reporter.Command> busyKey = ServiceKey.create(Reporter.Command.class, "Busyness");
        context.getSystem().receptionist().tell(Receptionist.register(busyKey, busynessReporter));
        reporters.put("Busyness", busynessReporter);
        context.watch(busynessReporter);
        

        ActorRef<Reporter.Command> bpReporter = context.spawn(
          BloodPressureReporter.create(
            this.statusAdapter,
            cfg.BloodPressure.dbURI,
            cfg.BloodPressure.table,
            cfg.BloodPressure.readRate),
        "BloodPressure"
        );
        ServiceKey<Reporter.Command> bpKey = ServiceKey.create(Reporter.Command.class, "BloodPressure");
        context.getSystem().receptionist().tell(Receptionist.register(bpKey, bpReporter));
        reporters.put("BloodPressure", bpReporter);
        context.watch(bpReporter);

        ActorRef<Reporter.Command> heartReporter = context.spawn(
          HeartRateReporter.create(
            this.statusAdapter,
            cfg.HeartRate.dbURI,
            cfg.HeartRate.table,
            cfg.HeartRate.readRate),
        "HeartRate"
        );
        ServiceKey<Reporter.Command> hrKey = ServiceKey.create(Reporter.Command.class, "HeartRate");
        context.getSystem().receptionist().tell(Receptionist.register(hrKey, heartReporter));
        reporters.put("HeartRate", heartReporter);
        context.watch(heartReporter);

        ActorRef<Reporter.Command> sleepReporter = context.spawn(
          SleepReporter.create(
            this.statusAdapter,
            cfg.Sleep.dbURI,
            cfg.Sleep.table,
            cfg.Sleep.readRate),
      "Sleep"
        );
        ServiceKey<Reporter.Command> sleepKey = ServiceKey.create(Reporter.Command.class, "Sleep");
        context.getSystem().receptionist().tell(Receptionist.register(sleepKey, sleepReporter));
        reporters.put("Sleep", sleepReporter);
        context.watch(sleepReporter);

        ActorRef<Reporter.Command> locationReporter = context.spawn(
          LocationReporter.create(
            this.statusAdapter,
            cfg.Location.dbURI,
            cfg.Location.table,
            cfg.Location.readRate), 
        "Location"
        );
        ServiceKey<Reporter.Command> locationKey = ServiceKey.create(Reporter.Command.class, "Location");
        context.getSystem().receptionist().tell(Receptionist.register(locationKey, locationReporter));
        reporters.put("Location", locationReporter);
        context.watch(locationReporter);

        ActorRef<Reporter.Command> medicalReporter = context.spawn(
          MedicalHistoryReporter.create(
            this.statusAdapter,
            cfg.Medical.dbURI,
            cfg.Medical.table,
            cfg.Medical.readRate), 
        "MedicalHistory"
        );
        ServiceKey<Reporter.Command> medicalKey = ServiceKey.create(Reporter.Command.class, "Medical");
        context.getSystem().receptionist().tell(Receptionist.register(medicalKey, medicalReporter));
        reporters.put("Medical", medicalReporter);
        context.watch(medicalReporter);
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
        .onMessage(contextEngineGreet.class, this::onEngineResponse)
        .onMessage(TellAllReportersToReadRow.class, this::onTellAllReportersToReadRow)
        .onMessage(TellAllReportersToPeriodicallyRead.class, this::onTellAllReportersToPeriodicallyRead)
        .onMessage(DatabaseReadStatus.class, this::onDatabaseReadStatus)
        .onSignal(ChildFailed.class, signal -> onChildFailed(signal))
        .onSignal(PostStop.class, signal -> onPostStop())
        .build();
    }

    private Behavior<Command> onTellAllReportersToReadRow(TellAllReportersToReadRow msg) {
      this.reporters.forEach((name, reporter) -> {
        reporter.tell(new Reporter.ReadRowOfData(msg.rowToRead, this.statusAdapter));
      });

      return this;
    }

    private Behavior<Command> onTellAllReportersToPeriodicallyRead(TellAllReportersToPeriodicallyRead msg) {
      this.reporters.forEach((name, reporter) -> {
        reporter.tell(Reporter.StartReading.INSTANCE);
      });

      return this;
    }

    private Behavior<Command> onDatabaseReadStatus(DatabaseReadStatus msg) {
      String message = "Actor " + msg.status.actorPath + ": ";

      if (msg.status.success) {
        getContext().getLog().info(message + " : " + msg.status.message);
      } else {
        getContext().getLog().error(message + " : " + msg.status.message);
      }

      return this;
    }

    private Behavior<Command> onChildFailed(ChildFailed signal) {
      ActorPath childPath = signal.getRef().path();
      String errorMsg = "Child " + childPath + " failed";
      getContext().getLog().error(errorMsg, signal.getCause());

      return this;
    }

    private StressContextEngine onPostStop() {
      getContext().getLog().info("Context engine shutting down");
      return this;
    }
  
    private Behavior<Command> onEngineResponse(contextEngineGreet message) { //controller 
        message.replyTo.tell(new StressManagementController.ContextEngineToController("contextEngine"));       
      return this;
    }

    public static class ReporterConfig
    {
      public String dbURI;
      public String table;
      public int readRate;
    }
    public static class ContextEngineConfig
    {
      public ReporterConfig HeartRate;
      public ReporterConfig BloodPressure;
      public ReporterConfig Sleep;
      public ReporterConfig Busyness;
      public ReporterConfig Location;
      public ReporterConfig Scheduler;
      public ReporterConfig Medical;
    }
}
