package com.lightbend.akka.tutorial;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DeviceGroupQueryTest {
    static ActorSystem system;

    @BeforeClass
    public static void setup() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void teardown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testReturnTemperatureValueForWorkingDevices() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
                actorToDeviceId,
                1L,
                requester.getRef(),
                new FiniteDuration(3, TimeUnit.SECONDS)));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());
        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());

        DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

        assertEqualTemperatures(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testReturnTempatureNotAvailableForDevicesWithNoReadings() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
                actorToDeviceId,
                1L,
                requester.getRef(),
                new FiniteDuration(3, TimeUnit.SECONDS)
        ));

        queryActor.tell(new Device.RespondTemperature(0L, Optional.empty()), device1.getRef());
        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());

        DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.TemperatureNotAvailable());
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

        assertEqualTemperatures(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testReturnDeviceNotAvailableIfDeviceStopsBeforeAnswering() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
           actorToDeviceId,
           1L,
           requester.getRef(),
           new FiniteDuration(3, TimeUnit.SECONDS)
        ));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());
        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(2.0)), device2.getRef());
        device2.getRef().tell(PoisonPill.getInstance(), ActorRef.noSender());

        DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));

        assertEqualTemperatures(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testReturnDeviceTimedOutIfDeviceDoesNotAnswerInTime() {
        TestKit requester = new TestKit(system);

        TestKit device1 = new TestKit(system);
        TestKit device2 = new TestKit(system);

        Map<ActorRef, String> actorToDeviceId = new HashMap<>();
        actorToDeviceId.put(device1.getRef(), "device1");
        actorToDeviceId.put(device2.getRef(), "device2");

        ActorRef queryActor = system.actorOf(DeviceGroupQuery.props(
           actorToDeviceId,
           1L,
           requester.getRef(),
           new FiniteDuration(3, TimeUnit.SECONDS)
        ));

        assertEquals(0L, device1.expectMsgClass(Device.ReadTemperature.class).requestId);
        assertEquals(0L, device2.expectMsgClass(Device.ReadTemperature.class).requestId);

        queryActor.tell(new Device.RespondTemperature(0L, Optional.of(1.0)), device1.getRef());

        DeviceGroup.RespondAllTemperatures response = requester.expectMsgClass(
                java.time.Duration.ofSeconds(5),
                DeviceGroup.RespondAllTemperatures.class
        );
        assertEquals(1L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature((1.0)));
        expectedTemperatures.put("device2", new DeviceGroup.DeviceTimedOut());

        assertEqualTemperatures(expectedTemperatures, response.temperatures);
    }

    @Test
    public void testCollectTemperaturesFromAllActiveDevices() {
        TestKit probe = new TestKit(system);
        ActorRef groupActor = system.actorOf(DeviceGroup.props("group"));

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device1"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor1 = probe.getLastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device2"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor2 = probe.getLastSender();

        groupActor.tell(new DeviceManager.RequestTrackDevice("group", "device3"), probe.getRef());
        probe.expectMsgClass(DeviceManager.DeviceRegistered.class);
        ActorRef deviceActor3 = probe.getLastSender();

        // Check that the device actors are working
        deviceActor1.tell(new Device.RecordTemperature(0L, 1.0), probe.getRef());
        assertEquals(0L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
        deviceActor2.tell(new Device.RecordTemperature(1L, 2.0), probe.getRef());
        assertEquals(1L, probe.expectMsgClass(Device.TemperatureRecorded.class).requestId);
        // No temperature for device 3

        groupActor.tell(new DeviceGroup.RequestAllTemperatures(0L), probe.getRef());
        DeviceGroup.RespondAllTemperatures response = probe.expectMsgClass(DeviceGroup.RespondAllTemperatures.class);
        assertEquals(0L, response.requestId);

        Map<String, DeviceGroup.TemperatureReading> expectedTemperatures = new HashMap<>();
        expectedTemperatures.put("device1", new DeviceGroup.Temperature(1.0));
        expectedTemperatures.put("device2", new DeviceGroup.Temperature(2.0));
        expectedTemperatures.put("device3", new DeviceGroup.TemperatureNotAvailable());

        assertEqualTemperatures(expectedTemperatures, response.temperatures);
    }

    public static void assertEqualTemperatures(Map<String, DeviceGroup.TemperatureReading> expected, Map<String, DeviceGroup.TemperatureReading> actual) {
        for (Map.Entry<String, DeviceGroup.TemperatureReading> entry : expected.entrySet()) {
            DeviceGroup.TemperatureReading expectedReading = entry.getValue();
            DeviceGroup.TemperatureReading actualReading = actual.get(entry.getKey());
            assertNotNull(actualReading);
            assertEquals(expectedReading.getClass(), actualReading.getClass());
            if (expectedReading instanceof DeviceGroup.Temperature) {
                assertEquals(((DeviceGroup.Temperature) expectedReading).value, ((DeviceGroup.Temperature) actualReading).value, 0.01);
            }
        }
        assertEquals(expected.size(), actual.size());
    }
}
