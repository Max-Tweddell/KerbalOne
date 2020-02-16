package com.company;

import krpc.client.Connection;
import krpc.client.Event;
import krpc.client.RPCException;
import krpc.client.StreamException;
import krpc.client.services.KRPC;
import krpc.client.services.KRPC.Expression;
import krpc.client.services.SpaceCenter;
import krpc.client.services.SpaceCenter.Flight;
import krpc.client.services.SpaceCenter.Node;
import krpc.client.services.SpaceCenter.Resources;
import krpc.schema.KRPC.ProcedureCall;


import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException, RPCException, StreamException, InterruptedException {
        System.out.println("weee");

        Connection connection = Connection.newInstance("First Flight");
        KRPC krpc = KRPC.newInstance(connection);
        System.out.println("Connected to kRPC version " + krpc.getStatus().getVersion());
        SpaceCenter spaceCenter = SpaceCenter.newInstance(connection);
        SpaceCenter.Vessel vessel = spaceCenter.getActiveVessel();
        vessel.getAutoPilot().targetPitchAndHeading(90,90);
        vessel.getAutoPilot().engage();
        vessel.getControl().setThrottle(1);


        vessel.getControl().activateNextStage();
        System.out.println("Launch");

        // Waits until solid fuel boosters have run out
        liquidFuel(connection, vessel, krpc, 3);


        System.out.println("Booster separation");
        Thread.sleep(500);
        vessel.getControl().activateNextStage();

        System.out.println("Gravity turn");
        gravityTurn(connection,vessel, krpc);
        System.out.println("Circularising");
        circularisation(connection, vessel, krpc, 100000);
        vessel.getControl().setThrottle(0);
        liquidFuel(connection, vessel, krpc, 1);


        // meanAltitude(connection, vessel, krpc);

        System.out.println("Launch stage separation");
        vessel.getControl().setThrottle(0);

        Thread.sleep(1000);

        vessel.getControl().activateNextStage();
        vessel.getAutoPilot().disengage();

        apoapsisAltitude(connection,vessel,krpc);

        vessel.getControl().activateNextStage();

        while (vessel.flight(vessel.getOrbit().getBody().getReferenceFrame()).getVerticalSpeed() < -0.1) {
            System.out.printf("Altitude = %.1f meters\n", vessel.flight(null).getSurfaceAltitude());
            Thread.sleep(1000);
        }
        System.out.println("Landed!");
        connection.close();
    }

    public static void liquidFuel(Connection connection, SpaceCenter.Vessel vessel, KRPC krpc, int stage) throws RPCException, StreamException {
        Resources fuelAmount = vessel.resourcesInDecoupleStage(stage,false);
        double liquidFuelAmount =  fuelAmount.amount("LiquidFuel");

        ProcedureCall liquidFuel = connection.getCall(vessel.resourcesInDecoupleStage(stage, false), "amount", "LiquidFuel");
        Expression expr = Expression.lessThan(
                connection,
                Expression.call(connection, liquidFuel),
                Expression.constantFloat(connection, 0.1f));
        Event event = krpc.addEvent(expr);

        synchronized (event.getCondition()) {
            event.waitFor();
        }
    }
    public static void solidFuel(Connection connection, SpaceCenter.Vessel vessel, KRPC krpc) throws RPCException, StreamException {
        {
            ProcedureCall solidFuel = connection.getCall(vessel.getResources(), "amount", "SolidFuel");
            Expression expr = Expression.lessThan(
                    connection,
                    Expression.call(connection, solidFuel),
                    Expression.constantFloat(connection, 0.1f));
            Event event = krpc.addEvent(expr);

            synchronized (event.getCondition()) {
                event.waitFor();
            }
        }
    }

    public static void apoapsisAltitude(Connection connection, SpaceCenter.Vessel vessel, KRPC krpc) throws IOException, RPCException, StreamException {
        ProcedureCall srfAltitude = connection.getCall(
                vessel.flight(null), "getSurfaceAltitude");
        Expression expr = Expression.lessThan(
                connection,
                Expression.call(connection, srfAltitude),
                Expression.constantDouble(connection, 1000));
        Event event = krpc.addEvent(expr);
        synchronized (event.getCondition()) {
            event.waitFor();


        }
    }
    public static void meanAltitude(Connection connection, SpaceCenter.Vessel vessel, KRPC krpc) throws IOException, RPCException, StreamException {
        {
            ProcedureCall meanAltitude = connection.getCall(vessel.flight(null), "getMeanAltitude");
            Expression expr = Expression.greaterThan(
                    connection,
                    Expression.call(connection, meanAltitude),
                    Expression.constantDouble(connection, 10000));
            Event event = krpc.addEvent(expr);
            synchronized (event.getCondition()) {
                event.waitFor();
            }
        }
    }
    public static void gravityTurn(Connection connection, SpaceCenter.Vessel vessel, KRPC krpc) throws IOException, RPCException, StreamException, InterruptedException {
        float pitch = 90;
        SpaceCenter.Orbit orbit = vessel.getOrbit();
        double apoapsis = orbit.getApoapsisAltitude();
        double altitude = vessel.flight(null).getMeanAltitude();
        double speed  = vessel.flight(null).getSpeed();
        Thread.sleep(1000);
        vessel.getAutoPilot().targetPitchAndHeading(75, 90); // Kick
        Thread.sleep(1200);
        while (apoapsis < 100000 ) {
            vessel.getAutoPilot().targetPitchAndHeading(pitch, 90);
            Thread.sleep(700);
            pitch = pitch - (pitch/50f);
            apoapsis = orbit.getApoapsisAltitude();
            System.out.println(apoapsis);
        }
    }
    public static void circularisation(Connection connection, SpaceCenter.Vessel vessel, KRPC krpc, double orbitAltitude) throws RPCException, InterruptedException {
        SpaceCenter.Orbit orbit = vessel.getOrbit();
        double periapsis = orbit.getPeriapsisAltitude();
        vessel.getControl().setThrottle(0);
        while(vessel.flight(null) .getMeanAltitude() < 90000) {
            Thread.sleep(1000);
            System.out.println(vessel.flight(null).getMeanAltitude());
        }
        while(periapsis < 100000) {
            vessel.getControl().setThrottle(1);
            vessel.getAutoPilot().targetPitchAndHeading(0,90);
            Thread.sleep(1000);
            periapsis = orbit.getPeriapsisAltitude();
        }
    }
}


